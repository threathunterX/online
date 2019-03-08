package com.threathunter.nebula.onlineserver.rpc.clickstream;

import com.threathunter.common.NamedType;
import com.threathunter.common.ObjectId;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.model.Property;
import com.threathunter.persistent.core.io.BufferedRandomAccessFile;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Current event:
 * 2  bytes for total size(exclude check code)
 * 2  bytes for jump offset, after jump bytes to end of header, or start of app field
 * 8  bytes for event timestamp
 * 1  byte  for version
 * 12 bytes for id
 * 12 bytes for pid
 * ?? bytes for values of keys, keys are defined in header_version.json file
 * ?  bytes for app
 * ?  bytes for name
 * ?? bytes for properties following their values.
 * (8-total_size%8) bytes for filling check code: -57
 *
 * Created by daisy on 18-1-20
 */
public class EventReader {
    public static final int NO_MATCH = -1;
    public static final int ERR_CHECK_CODE = -2;
    public static final int ERR_READ = -3;

    private Map<String, EventMeta> schemaMap;
    private List<String> indexKeys;

    public EventReader(List<EventMeta> schemas, List<String> indexKeys) {
        this.setIndexKeys(indexKeys);
        this.setSchemas(schemas);
    }

    public void setSchemas(List<EventMeta> schemas) {
        this.schemaMap = new HashMap<>();
        schemas.forEach(s -> schemaMap.put(s.getName(), s));
    }

    public Map<String, EventMeta> getSchemaMap() {
        return schemaMap;
    }

    public void setIndexKeys(List<String> indexKeys) {
        this.indexKeys = indexKeys;
    }

    public List<String> getIndexKeys() {
        return indexKeys;
    }

    public long readEvent(Event event, BufferedRandomAccessFile file, long startOffset, String keyName, String key, long fromTime, long endTime) {
        long currentOffset = startOffset;

        try {
            while (currentOffset < file.length()) {
                file.seek(currentOffset);

                short currentSize = getNextShort(file);
                short jumpSize = getNextShort(file);
                if (!this.checkCodeValid(file, currentOffset + currentSize, currentSize)) {
                    return ERR_CHECK_CODE;
                }

                int checkCodeSize = 8 - currentSize % 8;
                long nextOffset = currentOffset + (long) currentSize + (long) checkCodeSize;

                // turn back to read event after check code
                // first 2 bytes is the size, next 2 bytes is the jump size
                file.seek(currentOffset + 4L);
                long currentEventTs = getNextLong(file);
                if (fromTime > 0L) {
                    if (currentEventTs < fromTime) {
                        currentOffset = nextOffset;
                        continue;
                    }

                    if (currentEventTs >= endTime) {
                        break;
                    }
                }

                event.setTimestamp(currentEventTs);
                // skip version, version info in fact is useless, consider remove later.
                file.skipBytes(1);
                // skip id and pid
                file.skipBytes(24);
                if (key != null && !key.isEmpty() && !this.matchKey(file, keyName, key)) {
                    currentOffset = nextOffset;
                    continue;
                }

                // seek to offset of app
                file.seek(currentOffset + 4 + jumpSize);

                short appSize = getNextShort(file);
                event.setApp(getNextString(file, appSize));
                short eventNameSize = getNextShort(file);
                event.setName(getNextString(file, eventNameSize));

                for (Property p : getSchemaMap().get(event.getName()).getProperties()) {
                    short size = getNextShort(file);
                    Object value = parseValue(file, p.getType(), size);
                    event.getPropertyValues().put(p.getName(), value);
                }

                if (file.getFilePointer() - currentOffset < (long) currentSize) {
                    event.setValue(getNextDouble(file));
                }

                file.seek(currentOffset + 13L);
                // read pid and id
                byte[] id = new byte[12];
                file.read(id);
                event.setId((new ObjectId(id)).toHexString());
                file.read(id);
                event.setPid((new ObjectId(id)).toHexString());
                return nextOffset;
            }
        } catch (IOException e) {
            return ERR_READ;
        }

        return NO_MATCH;
    }


    private boolean getNextBoolean(BufferedRandomAccessFile file) throws IOException {
        return file.readByte() > 0;
    }
    private short getNextShort(BufferedRandomAccessFile file) throws IOException {
        return Shorts.fromBytes(file.readByte(), file.readByte());
    }
    private long getNextLong(BufferedRandomAccessFile file) throws IOException {
        return Longs.fromBytes(file.readByte(), file.readByte(), file.readByte(), file.readByte(),
                file.readByte(), file.readByte(), file.readByte(), file.readByte());
    }
    private double getNextDouble(BufferedRandomAccessFile file) throws IOException {
        return Double.longBitsToDouble(Longs.fromBytes(file.readByte(), file.readByte(), file.readByte(), file.readByte(),
                file.readByte(), file.readByte(), file.readByte(), file.readByte()));
    }
    private String getNextString(BufferedRandomAccessFile file, short size) throws IOException {
        byte[] stringBytes = new byte[size];
        for (short i = 0; i < size; i++) {
            stringBytes[i] = file.readByte();
        }
        return new String(stringBytes, "utf-8");
    }
    private boolean checkCodeValid(BufferedRandomAccessFile file, long checkCodeOffset, short eventSize) throws IOException {
        int checkCodeSize = 8 - eventSize % 8;
        if (checkCodeOffset + checkCodeSize > file.length()) {
            return false;
        }

        file.seek(checkCodeOffset);
        for (int i = 0; i < checkCodeSize; i++) {
            if (file.readByte() != -52) {
                return false;
            }
        }
        return true;
    }
    private boolean matchKey(BufferedRandomAccessFile file, String keyName, String key) throws IOException {
        List<String> headerKeys = getIndexKeys();
        int indexInHeaders = headerKeys.indexOf(keyName);
        for (int i = 0; i < headerKeys.size(); i++) {
            if (i > indexInHeaders) {
                return false;
            }

            short keySize = getNextShort(file);
            if (i < indexInHeaders) {
                file.skipBytes(keySize);
            } else {
                String readKey = getNextString(file, keySize);
                if (readKey.equals(key)) {
                    return true;
                }
            }
        }

        return false;
    }
    private Object parseValue(BufferedRandomAccessFile file, NamedType type, short size) throws IOException {
        if (size == 0) {
            return null;
        }
        if (type.equals(NamedType.DOUBLE)) {
            return getNextDouble(file);
        }
        if (type.equals(NamedType.LONG)) {
            return getNextLong(file);
        }
        if (type.equals(NamedType.BOOLEAN)) {
            return getNextBoolean(file);
        }
        if (type.equals(NamedType.STRING)) {
            return getNextString(file, size);
        }

        return null;
    }
}

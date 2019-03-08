package com.threathunter.nebula.onlineserver.rpc.clickstream;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.common.ObjectId;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.BaseEventMeta;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.persistent.core.EventPersistCommon;
import com.threathunter.persistent.core.EventReadHelper;
import com.threathunter.persistent.core.LevelDbIndexCache;
import com.threathunter.persistent.core.io.BufferedRandomAccessFile;
import com.threathunter.persistent.core.io.LevelDbIndexReadWriter;
import com.threathunter.persistent.core.util.PathHelper;
import com.threathunter.nebula.common.util.ConstantsUtil;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.mutable.MutableInt;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Will cache offset of a key, because index does not contains offsets
 *
 * Created by daisy on 16/9/1.
 */
public class ClickstreamQueryService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClickstreamQueryService.class);
    private final ServiceMeta meta;
    private Cache<String, Map<Integer, Map<Long, Integer>>> indexCache;
    private Cache<Long, EventReader> readerCache;
    private Map<String, String> headerFieldMap;
    private Set<String> streamFields;
    private Map<String, String> fieldDimensionMap;

    private final String persistPath = CommonDynamicConfig.getInstance().getString("persist_path", PathHelper.getModulePath() + "/persistent");

    private void initial() {
        indexCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
        readerCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
        headerFieldMap = new HashMap(4);
        headerFieldMap.put("ip", "c_ip");
        headerFieldMap.put("uid", "uid");
        headerFieldMap.put("did", "did");
        streamFields = new HashSet<>();
        streamFields.add("c_ip");
        streamFields.add("uid");
        streamFields.add("did");
        streamFields.add("sid");
        streamFields.add("page");

        fieldDimensionMap = new HashMap<>(4);
        fieldDimensionMap.put("c_ip", "ip");
        fieldDimensionMap.put("uid", "uid");
        fieldDimensionMap.put("did", "did");
        fieldDimensionMap.put("sid", "sid");
        fieldDimensionMap.put("page", "page");
    }

    public ClickstreamQueryService(boolean redisMode) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("ClickstreamQuery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("ClickstreamQuery_rmq.service");
        }
        initial();
    }

    @Override
    public Event process(Event event) {
        MetricsHelper.getInstance().addMetrics("onlinekv.events.clickstreamquery", 1.0);

        return getResponseEvent(event);
    }

    @Override
    public ServiceMeta getServiceMeta() {
        return this.meta;
    }

    @Override
    public EventMeta getRequestEventMeta() {
        return null;
    }

    @Override
    public EventMeta getResponseEventMeta() {
        return null;
    }

    @Override
    public void close() {
    }

    protected Event getResponseEvent(Event event) {
        // validate parameters
        if (event.getKey() == null || event.getKey().isEmpty()) {
            LOGGER.warn("clickstream query: key is empty");
            return null;
        }
        Map<String, Object> properties = event.getPropertyValues();
        String dimension = (String) properties.get("dimension");
        if (dimension == null || dimension.isEmpty()) {
            LOGGER.warn("clickstreamquery: dimension is empty");
            return null;
        }

        // query
        String key = event.getKey();
        String queryType = (String) properties.get("query_type");
        if (queryType == null) {
            LOGGER.error("miss query type, return null");
            return null;
        }

        Map<String, Object> responseProperties = new HashMap<>();

        long fromTime = (Long) properties.get("from_time");
        List<IndexData> sortedIndexData = getShardIndex(fromTime, dimension, key);
        if (sortedIndexData == null) {
            return new Event();
        }
        // get all 120(at most) point data
        if (queryType.equals("clicks_period")) {
            Map<Long, Map<String, Object>> clicksPeriod;
            clicksPeriod = getTimeGroupVisit(fromTime, sortedIndexData, dimension, key);
            if (clicksPeriod == null) {
                LOGGER.warn("clickstreamquery: clicksPeriod is empty");
                return new Event();
            }
            responseProperties.put("clicks_period", clicksPeriod);
        } else {

            // get related ip or user stream, at most 2000
            // recode read event but do not return a new event, avoid too many events that will be hard
            // for gc as well as a good performance, or we mast query several times with too many new events.
            // will not query all events out, or that will consume so much memory if too many events, 2000 is also too much.
            // If 400Kb per event, 2000 require 2000 * 400Kb = 800Mb... and only events will almost be 1Gb
            long endTime = (Long) properties.get("end_time");

            String keyField = headerFieldMap.get(dimension);

            if (queryType.equals("clicks")) {
                EventConditionHandler handler = new EventConditionHandler((List<List<Map<String, Object>>>) properties.getOrDefault("query", new ArrayList<>()));
                int eventsQueryCount = ((Number) properties.getOrDefault("clickscount", 20)).intValue();
                List<Map<String, Object>> queryEvents = getQueryEvent(keyField, key, sortedIndexData, fromTime, endTime, handler, eventsQueryCount);
                if (queryEvents == null || queryEvents.size() <= 0) {
                    return new Event();
                }
                responseProperties.put("clicks", queryEvents);
            } else if (queryType.equals("visit_stream")) {
                int maxStreamCount = ((Number) properties.getOrDefault("streamcount", 2000)).intValue();
                List<Map<String, Object>> visitStream = getVisitStream(keyField, key, sortedIndexData, fromTime, endTime, maxStreamCount);
                if (visitStream == null || visitStream.size() <= 0) {
                    return new Event();
                }
                responseProperties.put("visit_stream", visitStream);
            }
        }

        Event responseEvent = new Event("nebula", "clickstreamresponse", "");
        responseEvent.setTimestamp(System.currentTimeMillis());
        responseEvent.setPropertyValues(responseProperties);

        return responseEvent;
    }

    protected EventReader getReader(final long fromHourSlot) {
        EventReader reader = this.readerCache.getIfPresent(fromHourSlot);
        if (reader == null) {
            reader = new EventReader(loadEventsSchemaFromFile(fromHourSlot), Arrays.asList("c_ip", "uid", "did", "page"));
            this.readerCache.put(fromHourSlot, reader);
        }

        return reader;
    }

    private List<EventMeta> loadEventsSchemaFromFile(final long fromHourSlot) {
        File file = new File(String.format("%s/%s/events_schema.json", persistPath, (new DateTime(fromHourSlot)).toString("yyyyMMddHH")));
        if (!file.exists()) {
            throw new RuntimeException("event schema file is not exist");
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            List<EventMeta> list = new ArrayList<>();
            mapper.readValue(file, List.class).forEach(m -> list.add(BaseEventMeta.from_json_object(m)));
            return list;
        } catch (Exception e) {
            throw new RuntimeException("load event schema error");
        }
    }

    private LevelDbIndexReadWriter getIndexReadWriter(final long fromHourSlot) {
        long currentHourSlot = SystemClock.getCurrentTimestamp() / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS;
        if (fromHourSlot == currentHourSlot) {
            return LevelDbIndexReadWriter.getInstance();
        }
        LevelDbIndexReadWriter readWriter = null;
        try {
            readWriter = LevelDbIndexCache.getInstance().getIndexReadWriter(fromHourSlot);
        } catch (Exception e) {
            LOGGER.error("error when open leveldb dir");
        }
        return readWriter;
    }

    private List<IndexData> getShardIndex(final Long fromTime, final String dimension, final String key) {
        long fromHourSlot = fromTime / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS;
        LevelDbIndexReadWriter indexReadWriter = getIndexReadWriter(fromHourSlot);

        if (indexReadWriter == null) {
            LOGGER.error("error when getting leveldb instance");
        }
        String cacheKey = String.format("%d_%s_%s", fromHourSlot, dimension, key);
        if (indexCache.getIfPresent(cacheKey) == null) {
            // get all the data, about the key, this will cache the index of the key and the index is split by 10 seconds
            try {
                indexCache.put(cacheKey, indexReadWriter.getOffsets(EventPersistCommon.getIndexKeyBytes(headerFieldMap.get(dimension), key), fromTime));
            } catch (UnknownHostException e) {
                LOGGER.warn("clickstreamquery: index is empty");
                return null;
            }
        }
        Map<Integer, Map<Long, Integer>> shardIndex = this.indexCache.getIfPresent(cacheKey);
        if (shardIndex == null || shardIndex.size() <= 0) {
            LOGGER.warn("clickstreamquery: index is empty");
            return null;
        }

        List<IndexData> indexDataList = getIndexData(shardIndex);

        return indexDataList;
    }

    private List<IndexData> getIndexData(final Map<Integer, Map<Long, Integer>> shardIndex) {
        List<IndexData> indexDataList = new LinkedList<>();
        shardIndex.forEach((shard, timestampOffset) -> timestampOffset.forEach((timestamp, offset) -> indexDataList.add(
                new IndexData(shard, timestamp, offset))));

        Collections.sort(indexDataList, (data1, data2) -> (int) (data2.getTimestamp() - data1.getTimestamp()));

        return indexDataList;
    }

    private Map<Long, Map<String, Object>> getTimeGroupVisit(long fromTime, final List<IndexData> sortedIndexData, String dimension, String key) {
        // 30 seconds wil be a point, an hour will have 120 point
        Map<Long, Map<String, Object>> timeGroupVisit = new HashMap<>(256);
        long slotTime = fromTime / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS;
        long nextHour = fromTime / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS + ConstantsUtil.HOUR_MILLIS;

        Map<Long, Map<String, Object>> timeGroupVisitRaw = new HashMap<>();

        Map<String, Map<String, Object>> idVisitMap = new HashMap<>();
        Map<String, String> idPidMap = new HashMap<>();
        String nullId = ObjectId.ZEROID.toHexString();
        Map<Integer, BufferedRandomAccessFile> fileCache = new HashMap<>();

        int searchCount = 0;
        EventReader reader = getReader(slotTime);
        try {
            for (IndexData indexData : sortedIndexData) {
                long groupTime = indexData.getTimestamp() / 30000 * 30000;
                fileCache.putIfAbsent(indexData.getShard(), EventReadHelper.getBufferedRandomAccessFile(this.persistPath, fromTime, indexData.getShard()));
                BufferedRandomAccessFile file = fileCache.get(indexData.getShard());

                long currentOff = indexData.getOffset();
                while (currentOff < file.length()) {
                    Event event = new Event();
                    event.setPropertyValues(new HashMap<>());
                    currentOff = reader.readEvent(event, file, currentOff, headerFieldMap.get(dimension), key, indexData.getTimestamp(), indexData.getTimestamp() + 10000);
                    if (currentOff < 0) {
                        break;
                    }
                    searchCount++;
                    timeGroupVisitRaw.putIfAbsent(groupTime, new HashMap<>(3));
                    Map<String, Object> visitMap = timeGroupVisitRaw.get(groupTime);

                    String notices = (String) event.getPropertyValues().get("notices");
                    if (notices == null || !notices.isEmpty()) {
                        visitMap.putIfAbsent("if_notice", true);
                    }
                    if (!event.getPid().equals(nullId)) {
                        idPidMap.put(event.getId(), event.getPid());
                    }
                    idVisitMap.put(event.getId(), visitMap);
                    ((MutableInt) visitMap.computeIfAbsent("count", c -> new MutableInt(0))).increment();
                }
            }
        } catch (Exception e) {
            LOGGER.error("search time group visit error", e);
            return null;
        } finally {
            fileCache.values().forEach(file -> {
                if (file != null) {
                    try {
                        file.close();
                    } catch (Exception e) {
                        LOGGER.error("close file error", e);
                    }
                }
            });
        }

        if (searchCount <= 0) {
            return null;
        }
        LOGGER.warn(String.format("clickstream query, searchcount: %d, key: %s", searchCount, key));

        idPidMap.forEach((id, pid) -> {
            if (idVisitMap.containsKey(id) && idVisitMap.containsKey(pid)) {
                ((MutableInt) idVisitMap.get(id).get("count")).decrement();
            }
        });
        while (slotTime < nextHour) {
            Map<String, Object> m = new HashMap<>(3);
            if (!timeGroupVisitRaw.containsKey(slotTime)) {
                m.put("if_notice", false);
                m.put("count", 0);
            } else {
                m.put("if_notice", timeGroupVisitRaw.get(slotTime).getOrDefault("if_notice", false));
                m.put("count", ((MutableInt) timeGroupVisitRaw.get(slotTime).get("count")).intValue());
            }
            timeGroupVisit.put(slotTime, m);
            slotTime += 30000;
        }

        return timeGroupVisit;
    }

    private List<Map<String, Object>> getQueryEvent(final String keyField, final String key, final List<IndexData> sortedShardIndex, long fromTime, long endTime, final EventConditionHandler handler, int count) {
        int c = count * 3 / 2;
        List<Event> queryEvents = new LinkedList<>();
        Map<Integer, BufferedRandomAccessFile> fileCache = new HashMap<>();

        EventReader reader = getReader(fromTime / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS);
        try {
            for (IndexData indexData : sortedShardIndex) {
                if (endTime < indexData.getTimestamp()) {
                    continue;
                }
                if (fromTime >= indexData.getTimestamp() + 10000) {
                    break;
                }
                fileCache.putIfAbsent(indexData.getShard(), EventReadHelper.getBufferedRandomAccessFile(this.persistPath, fromTime, indexData.getShard()));
                BufferedRandomAccessFile file = fileCache.get(indexData.getShard());
                long currentOff = indexData.getOffset();
                while (currentOff < file.length()) {
                    if (c <= 0) {
                        return mergeNoticeEvent(queryEvents, count);
                    }
                    Event event = new Event();
                    event.setPropertyValues(new HashMap<>());
                    // read from file
                    currentOff = reader.readEvent(event, file, currentOff, keyField, key, indexData.getTimestamp(), indexData.getTimestamp() + 10000);
                    if (currentOff < 0) {
                        break;
                    }
                    if (event.getTimestamp() > endTime) {
                        break;
                    }
                    if (event.getTimestamp() < fromTime) {
                        continue;
                    }

                    boolean hit;
                    if (handler == null) {
                        hit = true;
                    } else {
                        hit = handler.match(event);
                    }
                    if (hit) {
                        queryEvents.add(event);
                        c--;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("events query error", e);
            return new ArrayList<>();
        } finally {
            fileCache.values().forEach(file -> {
                if (file != null) {
                    try {
                        file.close();
                    } catch (Exception e) {
                        LOGGER.error("close file error", e);
                    }
                }
            });
        }
        LOGGER.warn("clickstream clicks query, query count: " + queryEvents.size());
        return mergeNoticeEvent(queryEvents, count);
    }

    private List<Map<String, Object>> mergeNoticeEvent(final List<Event> queryEvents, int count) {
        if (queryEvents.size() > 0) {
            LinkedList<Map<String, Object>> resultList = new LinkedList<>();
            String nullId = ObjectId.ZEROID.toHexString();
            // merge notice of parent event and child event
            Map<String, Event> idEventMap = new HashMap<>();
            Map<String, String> idPidMap = new HashMap<>();
            Set<String> pidSet = new HashSet<>();
            queryEvents.forEach(event -> {
                idEventMap.put(event.getId(), event);
                if (!event.getPid().equals(nullId)) {
                    idPidMap.put(event.getId(), event.getPid());
                    pidSet.add(event.getPid());
                }
            });
            int c = count;
            for (Event event : queryEvents) {
                if (c <= 0) {
                    break;
                }
                if (!pidSet.contains(event.getId()) && !idPidMap.containsKey(event.getId())) {
                    // not a child nor a parent
                    c--;
                    resultList.add(getClickData(event, false));
                } else {
                    // child event, use the child properties directly, because the properties that parent have,
                    // child will also have, just merge the notices and just leave parent id as id.
                    if (idPidMap.containsKey(event.getId())) {
                        String pid = event.getPid();
                        if (idEventMap.containsKey(pid)) {
                            String notices = (String) event.getPropertyValues().get("notices");
                            if (notices == null) {
                                notices = "";
                            }
                            Event parentEvent = idEventMap.get(pid);
                            String[] childNotices = notices.split(",");
                            String pNotices = (String) parentEvent.getPropertyValues().get("notices");
                            if (pNotices == null) {
                                pNotices = "";
                            }
                            if (childNotices.length > 0) {
                                for (String childNotice : childNotices) {
                                    if (!pNotices.contains(childNotice)) {
                                        if (pNotices.length() > 0) {
                                            pNotices += ",";
                                        }
                                        pNotices += childNotice;
                                    }
                                }
                            }
                            event.getPropertyValues().put("notices", pNotices);
                            event.setPid(nullId);
                            event.setId(pid);

                            resultList.add(getClickData(event, true));
                        } else {
                            resultList.add(getClickData(event, false));
                        }
                        c--;
                    }
                    // directly ignore parent event, if both parent and child exist
                }
            }

            Collections.sort(resultList, (map1, map2) -> {
                long ret = (Long) map1.get("timestamp") - (Long) map2.get("timestamp");
                return (ret > 0) ? -1 : ((ret < 0) ? 1 : 0 );
            });
            return resultList;
        } else {
            return null;
        }
    }

    private List<Map<String, Object>> getVisitStream(String keyField, String key, List<IndexData> sortedIndexData, long fromTime, long endTime, int streamCount) {
        int c = streamCount;

        String nullId = ObjectId.ZEROID.toHexString();

        Map<Integer, BufferedRandomAccessFile> fileCache = new HashMap<>();
        List<Map.Entry<String, Map<String, Object>>> visitStreamWithId = new LinkedList<>();
        Map<String, Map<String, Object>> idVisitMap = new HashMap<>();
        Map<String, String> idPidMap = new HashMap<>();
        Set<String> pidSet = new HashSet<>();

        EventReader reader = getReader(fromTime / ConstantsUtil.HOUR_MILLIS * ConstantsUtil.HOUR_MILLIS);
        try {
            for (IndexData indexData : sortedIndexData) {
                if (endTime < indexData.getTimestamp()) {
                    continue;
                }
                if (fromTime >= indexData.getTimestamp() + 10000) {
                    break;
                }
                fileCache.putIfAbsent(indexData.getShard(), EventReadHelper.getBufferedRandomAccessFile(this.persistPath, fromTime, indexData.getShard()));
                BufferedRandomAccessFile file = fileCache.get(indexData.getShard());
                long currentOff = indexData.getOffset();
                while (currentOff < file.length()) {
                    if (c <= 0) {
                        return mergeSortedVisitMap(visitStreamWithId, idVisitMap, idPidMap, pidSet);
                    } else {
                        Event event = new Event();
                        event.setPropertyValues(new HashMap<>());
                        currentOff = reader.readEvent(event, file, currentOff, keyField, key, indexData.getTimestamp(), indexData.getTimestamp() + 10000);
                        if (currentOff < 0) {
                            break;
                        }
                        if (event.getTimestamp() > endTime) {
                            break;
                        }
                        if (event.getTimestamp() < fromTime) {
                            continue;
                        }

                        Map<String, Object> visitMap = new HashMap<>(3);
                        visitMap.put("timestamp", event.getTimestamp());
                        if (event.getPropertyValues().get("notice") == null) {
                            event.getPropertyValues().put("notice", "");
                        }
                        String notices = (String) event.getPropertyValues().get("notices");
                        visitMap.put("if_notice", notices != null && !notices.isEmpty());
                        streamFields.forEach(s -> {
                            visitMap.put(fieldDimensionMap.get(s), event.getPropertyValues().get(s));
                        });

                        String id = event.getId();
                        String pid = event.getPid();
                        idVisitMap.put(id, visitMap);
                        visitStreamWithId.add(new AbstractMap.SimpleEntry<>(id, visitMap));
                        c--;
                        if (!pid.equals(nullId)) {
                            idPidMap.put(id, pid);
                            pidSet.add(pid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("events query error", e);
            return new ArrayList<>();
        } finally {
            fileCache.values().forEach(file -> {
                if (file != null) {
                    try {
                        file.close();
                    } catch (Exception e) {
                        LOGGER.error("close file error", e);
                    }
                }
            });
        }
        return mergeSortedVisitMap(visitStreamWithId, idVisitMap, idPidMap, pidSet);
    }

    private List<Map<String, Object>> mergeSortedVisitMap(final List<Map.Entry<String, Map<String, Object>>> visitStreamWithId, Map<String, Map<String, Object>> idVisitMap, Map<String, String> idPidMap, Set<String> pidSet) {
        List<Map<String, Object>> resultList = new LinkedList<>();
        for (Map.Entry<String, Map<String, Object>> entry : visitStreamWithId) {
            String id = entry.getKey();
            Map<String, Object> visit = entry.getValue();

            if (!idPidMap.containsKey(id) && !pidSet.contains(id)) {
                // directly add to result
                resultList.add(visit);
            } else {
                if (idPidMap.containsKey(id)) {
                    // a child and may need to merge
                    if (idVisitMap.containsKey(idPidMap.get(id))) {
                        // parent exist, need to merge
                        String pid = idPidMap.get(id);
                        if (!(Boolean) visit.get("if_notice")) {
                            if ((Boolean) idVisitMap.get(pid).get("if_notice")) {
                                visit.put("if_notice", true);
                            }
                        }
                    }
                    resultList.add(visit);
                }
                // ignore parent
            }
        }
        Collections.sort(resultList, (map1, map2) -> {
                long ret = (Long) map1.get("timestamp") - (Long) map2.get("timestamp");
                return (ret > 0) ? -1 : ((ret < 0) ? 1 : 0 );
        });
        return resultList;
    }

    private Map<String, Object> getClickData(Event event, boolean merged) {
        if (event.getPropertyValues().get("notices") == null) {
            event.getPropertyValues().put("notices", "");
        }
        Map<String, Object> data = event.genAllData();
        String notices = (String) data.get("notices");
        List<String> validNotices = new ArrayList<>();
        if (!notices.isEmpty()) {
            Set<String> categories = new HashSet<>();
            Map<String, MutableInt> categoryScore = new HashMap<>();
            String[] noticeList = notices.split(",");
            for (String n : noticeList) {
                StrategyInfoCache.StrategyInfo info = StrategyInfoCache.getInstance().getStrategyInfo(n);
                if (info != null) {
                    String category = info.getCategory();
                    categories.add(category);
                    categoryScore.computeIfAbsent(category, c -> new MutableInt(0)).add(info.getScore());
                    validNotices.add(n);
                }
            }
            data.put("category", categories);
            int score = 0;
            for (MutableInt value : categoryScore.values()) {
                if (value.intValue() > score) {
                    score = value.intValue();
                }
            }
            data.put("risk_score", merged ? score/2 : score);
        }
        data.put("notices", String.join(", ", validNotices));
        data.remove("key");
        data.remove("value");

        return data;
    }

    private class IndexData {
        private int shard;
        private long timestamp;
        private long offset;

        public IndexData(int shard, long timestamp, int offset) {
            this.shard = shard;
            this.timestamp = timestamp;
            this.offset = offset;
        }

        public int getShard() {
            return shard;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getOffset() {
            return offset;
        }
    }
}

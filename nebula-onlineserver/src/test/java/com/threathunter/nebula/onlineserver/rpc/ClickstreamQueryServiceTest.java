package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.persistent.core.EventReadHelper;
import com.threathunter.persistent.core.io.BufferedRandomAccessFile;
import com.threathunter.nebula.onlineserver.rpc.clickstream.ClickstreamQueryService;
import com.threathunter.nebula.onlineserver.rpc.clickstream.EventReader;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by daisy on 16/9/2.
 */
public class ClickstreamQueryServiceTest {
    private FakeClickstreamQueryService service;
    Gson gson = new Gson();

    @Test
    public void testReadClicks() {
        CommonDynamicConfig.getInstance().addOverrideProperty("persist_path", "/home/daisy/workplace/Code_threathunter/nebula_java/nebula-onlineserver/persistent");
        service = new FakeClickstreamQueryService(true);
        Event event = buildRequest("ip","172.16.13.250", "clicks", 1518256500000l, 1518256799999l);
        System.out.println(gson.toJson(service.getResponse(event)));
    }

    @Test
    public void testReader() throws IOException {
        String persistPath = "/home/daisy/workplace/Code_threathunter/nebula_java/nebula-onlineserver/persistent";
        CommonDynamicConfig.getInstance().addOverrideProperty("persist_path", persistPath);
        service = new FakeClickstreamQueryService(true);
        EventReader reader = service.getReader(1518228000000l);
        long startOffset = 0;
        while (true) {
            Event event = new Event();
            event.setPropertyValues(new HashMap<>());
            BufferedRandomAccessFile file = EventReadHelper.getBufferedRandomAccessFile(persistPath, 1518228000000l, 15);
            startOffset = reader.readEvent(event, file, startOffset, "", "", 1518228000000l, 1518231600000l);
            if (startOffset > 0) {
                System.out.println(gson.toJson(event));
            } else {
                Assert.fail();
            }
            break;
        }
    }

    private Event buildRequest(String keyType, String key, String queryType, long fromTime, long endTime) {
        Event event = new Event("nebula", "clickstreamrequest", key);
        event.setTimestamp(System.currentTimeMillis());
        Map<String, Object> properties = new HashMap<>();
        properties.put("dimension", keyType);
        properties.put("from_time", fromTime);
        properties.put("end_time", endTime);
        properties.put("query_type", queryType);
        properties.put("streamcount", 10);
        properties.put("clickscount", 5);
        event.setPropertyValues(properties);
        event.setValue(1.0);

        return event;
    }

    // TODO replaced with spy
    class FakeClickstreamQueryService extends ClickstreamQueryService {

        public FakeClickstreamQueryService(boolean redisMode) {
            super(redisMode);
        }

        public Event getResponse(Event queryEvent) {
            return this.getResponseEvent(queryEvent);
        }

        public EventReader getReader(long fromTimeSlot) {
            return super.getReader(fromTimeSlot);
        }
    }
}

package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.bordercollie.slot.compute.SlotQuery;
import com.threathunter.common.Identifier;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by daisy on 17/5/7.
 */
public class OnlineSlotVariableQueryService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlineSlotVariableQueryService.class);

    private final ServiceMeta meta;
    private final SlotQuery slotQuery;

    public OnlineSlotVariableQueryService(boolean redisMode, final SlotQuery slotQuery) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("OnlineSlotVariableQuery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("OnlineSlotVariableQuery_rmq.service");
        }
        this.slotQuery = slotQuery;
    }

    @Override
    public Event process(final Event event) {
        MetricsHelper.getInstance().addMetrics("rpc.onlinevariablequery", 1.0);
        Map<String, Object> properties = event.getPropertyValues();

        List<String> variableNames = (List<String>) properties.get("var_list");
        List<String> keys = (List<String>) properties.getOrDefault("keys", new ArrayList<>());
        String app = "nebula";

        Map<String, Object> resultMap = new HashMap<>();

        variableNames.forEach(v -> {
            try {
                Identifier id = Identifier.fromKeys(app, v);
                Map<String, Object> map = (Map<String, Object>) slotQuery.queryCurrent(id, keys);
                if (map != null) {
                    map.forEach((key, value) -> {
                        if (value != null) {
                            ((Map) resultMap.computeIfAbsent(key, s -> new HashMap<>())).put(v, wrapperResult(value));
                        }
                    });
                } else {
                    LOGGER.error(String.format("[nebula:online:query] query result is empty, variable: %s, keys: %s", v, keys));
                }
            } catch (Exception e) {
                LOGGER.error(String.format("[nebula:online:query] query slot error, variable: %s, keys: %s", v, keys), e);
            }
        });

        Map<String, Object> resProperties = new HashMap<>();
        resProperties.put("result", resultMap);

        return new Event("nebula", "onlineslotvariablequery_response", "", SystemClock.getCurrentTimestamp(), 1.0, resProperties);
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

    private static Map wrapperResult(Object obj) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", System.currentTimeMillis());
        map.put("value", obj);

        return map;
    }
}

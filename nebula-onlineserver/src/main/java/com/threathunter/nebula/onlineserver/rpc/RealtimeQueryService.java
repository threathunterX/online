package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.model.VariableMeta;
import com.threathunter.model.VariableMetaRegistry;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Realtime key querying.
 *
 * Querying Limited(2, 3):
 * 1. a variable's value(global variables, need to provide list of variables)
 * 2. a key's variables' values(specific dimension, need to provide a key and list of variables)
 * 3. a key's top value(specific dimension, need to provide a key, )
 * 4. a variable's top value(specific dimension, key is sharded to different thread)
 *
 * @author daisy
 */
public class RealtimeQueryService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeQueryService.class);

//    private final EsperContainer container;
    private final ServiceMeta meta;

    public RealtimeQueryService(boolean redisMode) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("realtimequery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("realtimequery_rmq.service");
        }
//        this.container = container;
    }


    @Override
    public Event process(Event event) {
        MetricsHelper.getInstance().addMetrics("online.rpc.realtimequery", 1.0);
        Map<String, Object> properties = event.getPropertyValues();

        List<String> variableNames = (List<String>) properties.get("var_list");

        Map<String, Object> responseProperties = new HashMap<>();
        Map<String, Object> variableValueMap = new HashMap<>();

        int count = parseTopCount(properties.get("count"));

        variableNames.forEach(variableName -> {
            Map result = (Map) getQueryResult(variableName, event.getKey(), count);
            if (result.containsKey(variableName)) {
                variableValueMap.put(variableName, result.get(variableName));
            } else {
                variableValueMap.put(variableName, result);
            }
        });
        responseProperties.put("result", variableValueMap);
        Event responseEvent = new Event("nebula", "realtimeQuery_response", event.getKey(),
                SystemClock.getCurrentTimestamp(), 1.0, responseProperties);

        return responseEvent;
    }

    @Override
    public ServiceMeta getServiceMeta() {
        return meta;
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

    private int parseTopCount(Object countObj) {
        if (countObj != null) {
            if (countObj instanceof String) {
                return Integer.parseInt(countObj.toString());
            } else {
                return ((Number) countObj).intValue();
            }
        }
        return 20;
    }

    /**
     * Uniform querying entrance
     *
     * @param variableName
     * @param key
     * @param topCount, if the variable is a topped variable, will return topCount keys,
     *                  this is not required.
     * @return
     */
    private Object getQueryResult(final String variableName, String key, int topCount) {
        if (key == null || key.isEmpty()) {
            LOGGER.error("key is empty");
            return null;
        }
        VariableMeta meta = VariableMetaRegistry.getInstance().getVariableMeta("nebula", variableName);
        if (meta == null) {
            LOGGER.error("variable does not exist");
            return null;
        }
//        if (!(meta instanceof AggregateVariableMeta)) {
//            LOGGER.warn("variable is not type of aggregation");
//            return null;
//        }
//        String dimension = meta.getDimension();
//        boolean keyTopValue = meta.isKeyTopValue();
//
//        VariableQuery query;
//        if (keyTopValue) {
//            query = VariableQueryUtil.sendKeyTopQuery(container, variableName, dimension, key, topCount);
//        } else {
//            query = VariableQueryUtil.sendKeyQuery(container, variableName, dimension, key);
//        }
//        return query.waitForResults(500, TimeUnit.MILLISECONDS);
        return null;
    }
}

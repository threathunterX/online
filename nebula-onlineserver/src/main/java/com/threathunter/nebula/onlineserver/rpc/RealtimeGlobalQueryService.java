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
 * 
 */
public class RealtimeGlobalQueryService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeGlobalQueryService.class);
//    private final EsperContainer container;
    private final ServiceMeta meta;

    public RealtimeGlobalQueryService(boolean redisMode) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("realtimeglobalquery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("realtimeglobalquery_rmq.service");
        }
//        this.container = container;
    }

    @Override
    public Event process(final Event event) {
        MetricsHelper.getInstance().addMetrics("online.rpc.realtimeglobalquery", 1.0);
        Map<String, Object> properties = event.getPropertyValues();

        List<String> variableNames = (List<String>) properties.get("var_list");

        Map<String, Object> responseProperties = new HashMap<>();
        Map<String, Object> variableValueMap = new HashMap<>();

        int count = parseTopCount(properties.get("count"));
        variableNames.forEach(variableName -> {
            Map result = (Map) getQueryResult(variableName, count);
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

    private Object getQueryResult(final String variableName, int topCount) {
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
//        boolean topValue = meta.isTopValue();
//
//        VariableQuery query;
//        if (topValue) {
//            query = VariableQueryUtil.broadcastTopQuery(container, variableName, dimension, topCount);
//        } else {
//            query = VariableQueryUtil.broadcastQuery(container, variableName, dimension);
//        }
//        return query.waitForResults(500, TimeUnit.MILLISECONDS);
        return null;
    }

    private int parseTopCount(final Object countObj) {
        if (countObj != null) {
            if (countObj instanceof String) {
                return Integer.parseInt(countObj.toString());
            } else {
                return ((Number) countObj).intValue();
            }
        }
        return 20;
    }
}

package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.nebula.onlineserver.rpc.riskmonitor.RiskMonitorDataComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 */
public class RiskEventsInfoQueryService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(RiskEventsInfoQueryService.class);
    private final ServiceMeta meta;

    public RiskEventsInfoQueryService(boolean redisMode) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("RiskEventsInfoQuery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("RiskEventsInfoQuery_rmq.service");
        }
    }

    /**
     * Process request from outside.
     * 1. If request from_time is before lastStartMinMillis, data has expired, return null.
     * 2. If lastStartMinMillis is invalid(not positive
     *
     * @param event
     * @return
     */
    @Override
    public Event process(final Event event) {
        try {
            long fromTime = (Long) event.getPropertyValues().get("from_time");
            long endTime = (Long) event.getPropertyValues().get("end_time");
            int limit = ((Number) event.getPropertyValues().getOrDefault("limit", 1000)).intValue();

            List<Map<String, Object>> result = (List<Map<String, Object>>)
                    RiskMonitorDataComputer.getInstance().queryData(fromTime, endTime, limit);

            Map<String, Object> responseProperties = new HashMap<>();
            responseProperties.put("result", result);

            return new Event("nebula", "riskeventsinfoqueryresponse", "", System.currentTimeMillis(), 1.0, responseProperties);
        } catch (Exception e) {
            logger.error("error in processing request", e);
            throw e;
        }
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
}



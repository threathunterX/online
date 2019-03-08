package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.bordercollie.slot.compute.SlotQuery;
import com.threathunter.common.Identifier;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.nebula.common.util.MetricsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by daisy on 16/5/27.
 */
public class IncidentQueryService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(IncidentQueryService.class);
    private final SlotQuery slotQuery;

    private Map<String, Map<String, Object>> topCache = null;
    private final ServiceMeta meta;
    private int lastTopCount = 10;
    private String last_key_variable = "";

    private List<String> last_variableNames = new ArrayList<>();

    // for fuzzy search ip and url
    private volatile String lastSearchKey = "";
    private Map<String, Map<String, Object>> lastSearchedMap;

    public IncidentQueryService(boolean redisMode, final SlotQuery slotQuery) {
        if (redisMode) {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("incidentquery_redis.service");
        } else {
            this.meta = ServiceMetaUtil.getMetaFromResourceFile("incidentquery_rmq.service");
        }
        this.slotQuery = slotQuery;
    }

    @Override
    public Event process(final Event event) {
        MetricsHelper.getInstance().addMetrics("onlinekv.events.incidentquery", 1.0);
        Map<String, Object> properties = event.getPropertyValues();

        String app = (String) properties.get("app");
        List<String> variableNames = (List<String>) properties.get("var_list");
        String key_variable = properties.get("key_variable") == null ? "ip__visit__incident_score__1h__slot" : properties.get("key_variable").toString();
        Integer page = properties.get("page") != null ? Integer.parseInt(properties.get("page").toString()) : null;
        String key = event.getKey();
        int count = 20;
        if (properties.containsKey("count")) {
            count = Integer.parseInt(properties.get("count").toString());
            count = count <= 0 ? 20 : count;
        }
        int topCount = 10;
        if (properties.containsKey("topcount")) {
            topCount = Integer.parseInt(properties.get("topcount").toString());
            topCount = topCount <= 0 ? 10 : topCount;
        }

        if (((page == null || page < 1) && (key == null || key.isEmpty())) || topCache == null || topCount != lastTopCount ||
                !key_variable.equals(last_key_variable) || last_variableNames == null || !last_variableNames.containsAll(variableNames)) {
            // refresh cache
            topCache = refreshTopCache(app, key_variable, variableNames, topCount);
            // reset page
            page = 1;
            lastTopCount = topCount;
            last_key_variable = key_variable;
            last_variableNames = variableNames;
        }

        Map<String, Object> variableValueMap = new LinkedHashMap<>();
        Map<String, Object> responseProperties = new HashMap<>();

        Iterator<Map.Entry<String, Map<String, Object>>> iterator;
        int total;
        if (key == null || key.isEmpty()) {
            iterator = topCache.entrySet().iterator();
            total = topCache.size();
        } else {
            if (!key.equals(lastSearchKey)) {
                // fuzzy search
                String pageVariable = "ip__visit__page_incident_count__1h__slot";
                lastSearchKey = key;
                lastSearchedMap = new LinkedHashMap<>();
                topCache.forEach((ip, ipData) -> {
                    boolean match = false;
                    if (ip.contains(key)) {
                        match = true;
                    } else {
                        for (String p : ((Map<String, Object>) ipData.get(pageVariable)).keySet()) {
                            if (p.contains(key)) {
                                match = true;
                                break;
                            }
                        }
                    }
                    if (match) {
                        lastSearchedMap.put(ip, ipData);
                    }
                });
            }
            iterator = lastSearchedMap.entrySet().iterator();
            total = lastSearchedMap.size();
        }
        for (int i = 0; i < (page - 1) * count; i++) {
            if (iterator.hasNext()) {
                iterator.next();
            } else {
                break;
            }
        }
        for (int i = (page - 1) * count; i < page * count; i++) {
            if (iterator.hasNext()) {
                Map.Entry<String, Map<String, Object>> entry = iterator.next();
                variableValueMap.put(entry.getKey(), entry.getValue());
            } else {
                break;
            }
        }
        responseProperties.put("page", page);
        responseProperties.put("totalPage", total % count == 0 ? total / count : total / count + 1);
        responseProperties.put("count", count);
        responseProperties.put("total", total);

        responseProperties.put("result", variableValueMap);

        Event response = new Event("nebula", "incidentquery_response",
                event.getKey(), System.currentTimeMillis(), 1.0, responseProperties);
        return response;
    }


    private Map<String, Map<String, Object>> refreshTopCache(String app, String key_variable, List<String> var_list, int limit) {
        Map<String, Map<String, Object>> result = this.getIncidentsData(100);

        if (result.size() > 0) {
            List<String> keys = new ArrayList<>(result.keySet());
            for (String variable : var_list) {
                Identifier id = Identifier.fromKeys(app, variable);
                if (variable.equals(key_variable)) {
                    continue;
                }
//                if (slotManager.isIncidentVariable(id)) {
//                    continue;
//                }
//                Map<String, Object> queryData = (Map<String, Object>) slotQuery.queryCommon(0l, id, );
//                queryData.forEach((ip, data) -> result.get(ip).put(variable, data));
            }
        }
        return result;
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

    private Map<String, Map<String, Object>> getIncidentsData(int limit) {
        Map<String, Map<String, Object>> result = new HashMap();

        Map<String, Long> topScoresIps = getTopScoreIpsWithScore(limit);
        Set<String> ips = topScoresIps.keySet();
        if (ips.size() > 0) {
            topScoresIps.forEach((ip, score) -> {
                Map<String, Object> sub = new HashMap<>();
                sub.put("ip__visit__incident_score__1h__slot", score);
                result.put(ip, sub);
            });
            this.addQueryResult("ip__visit__incident_max_rate__1h__slot", ips, result);
            this.addQueryResult("ip__visit__scene_incident_count_strategy__1h__slot", ips, result);
            this.addQueryResult("ip__visit__tag_incident_count__1h__slot", ips, result);
        }
        return result;
    }

    private Map<String, Long> getTopScoreIpsWithScore(int limit) {
//        return (Map<String, Long>) this.slotManager.queryData(Identifier.fromKeys("nebula", "ip__visit__incident_score__1h__slot"), "", limit);
        return null;
    }

    private void addQueryResult(final String variableName, final Collection<String> ips, final Map<String, Map<String, Object>> ipMap) {
//        Object queryResult = this.slotManager.queryData(Identifier.fromKeys("nebula", variableName), ips, 1);
//        ((Map<String, Object>) queryResult).forEach((ip, result) -> ipMap.get(ip).put(variableName, result));
        return;
    }
}

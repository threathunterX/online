package com.threathunter.nebula.onlineserver.rpc.clickstream;

import com.threathunter.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 
 */
public class EventConditionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventConditionHandler.class);
    private List<QueryCondition> conditions;

    public EventConditionHandler(final List<List<Map<String, Object>>> queryCondition) {
        this.conditions = new ArrayList<>();
        queryCondition.forEach(q -> this.conditions.add(new QueryCondition(q)));
    }

    public boolean match(final Event event) {
        for (QueryCondition condition : conditions) {
            if (!condition.match(event)) {
                return false;
            }
        }
        return true;
    }

    private class QueryCondition {
        private List<FieldCondition> conditions;

        public QueryCondition(final List<Map<String, Object>> entries) {
            this.conditions = new ArrayList<>();
            entries.forEach(entry -> {
                FieldCondition condition = FieldConditionGenerator.generateCondition(entry);
                if (condition == null) {
                    LOGGER.error("condition error" + entry);
                    throw new RuntimeException("condition error: " + entry);
                }
                this.conditions.add(condition);
            });
        }

        public boolean match(final Event event) {
            for (FieldCondition condition : conditions) {
                if (condition.match(event)) {
                    return true;
                }
            }
            return false;
        }
    }
}

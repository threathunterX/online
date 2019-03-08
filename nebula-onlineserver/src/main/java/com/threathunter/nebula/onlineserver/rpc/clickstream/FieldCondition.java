package com.threathunter.nebula.onlineserver.rpc.clickstream;

import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.Event;

/**
 * Created by daisy on 17/4/30.
 */
public abstract class FieldCondition {
    protected String propertyName;
    protected Object param;

    public FieldCondition(String propertyName, Object param) {
        this.propertyName = propertyName;
        this.param = param;
    }

    public abstract boolean match(final Event event);

    public static class EqualFieldCondition extends FieldCondition {

        public EqualFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            return event.getPropertyValues().get(propertyName).equals(param);
        }
    }

    public static class NotEqualFieldCondition extends FieldCondition {

        public NotEqualFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(final Event event) {
            return !event.getPropertyValues().get(propertyName).equals(param);
        }
    }

    public static class ContainsFieldCondition extends FieldCondition {

        public ContainsFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            Object fieldValue = event.getPropertyValues().get(propertyName);
            if (fieldValue instanceof Number) {
                return fieldValue.equals(param);
            }
            return ((String) event.getPropertyValues().get(propertyName)).contains((String) param);
        }
    }

    public static class NotContainsFieldCondition extends FieldCondition {

        public NotContainsFieldCondition(String propertyName, String param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            return !((String) event.getPropertyValues().get(propertyName)).contains((String) param);
        }
    }

    public static class BiggerThanFieldCondition extends FieldCondition {

        public BiggerThanFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            return ((Number) event.getPropertyValues().get(propertyName)).doubleValue() >
                    ((Number) param).doubleValue();
        }
    }

    public static class LessThanFieldCondition extends FieldCondition {

        public LessThanFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            return ((Number) event.getPropertyValues().get(propertyName)).doubleValue() <
                    ((Number) param).doubleValue();
        }
    }

    public static class TagContainsFieldCondition extends FieldCondition {

        public TagContainsFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(Event event) {
            String notices = (String) event.getPropertyValues().get("notices");
            if (notices == null || notices.isEmpty()) {
                return false;
            }
            for (String strategy : notices.split(",")) {
                if (StrategyInfoCache.getInstance().getTags(strategy).contains(param)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class TagNotContainsFieldCondition extends FieldCondition {

        public TagNotContainsFieldCondition(String propertyName, Object param) {
            super(propertyName, param);
        }

        @Override
        public boolean match(final Event event) {
            String notices = (String) event.getPropertyValues().get("notices");
            if (notices == null || notices.isEmpty()) {
                return true;
            }
            for (String strategy : notices.split(",")) {
                if (StrategyInfoCache.getInstance().getTags(strategy).contains(param)) {
                    return false;
                }
            }
            return true;
        }
    }
}

package com.threathunter.nebula.onlineserver.rpc.clickstream;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by daisy on 17/4/30.
 */
public class FieldConditionGenerator {
    private static Map<String, ConditionMaker> generatorMap;
    static {
        generatorMap = new HashMap<>();
        generatorMap.put("==", (property, param) -> new FieldCondition.ContainsFieldCondition(property, param));

//        generatorMap.put("==", (property, param) -> new FieldCondition.EqualFieldCondition(property, param));
//        generatorMap.put("!=", (property, param) -> new FieldCondition.NotEqualFieldCondition(property, param));
//        generatorMap.put("contain", (property, param) -> new FieldCondition.ContainsFieldCondition(property, param));
//        generatorMap.put("!contain", (property, param) -> new FieldCondition.NotContainsFieldCondition(property, param));
//        generatorMap.put(">", (property, param) -> new FieldCondition.BiggerThanFieldCondition(property, param));
//        generatorMap.put("<", (property, param) -> new FieldCondition.LessThanFieldCondition(property, param));
    }

    public static FieldCondition generateCondition(final Map<String, Object> map) {
        String left = (String) map.get("left");
        if (left.equals("tag")) {
            if (map.get("op").equals("==")) {
                return new FieldCondition.TagContainsFieldCondition("tag", map.get("right"));
            } else if (map.get("op").equals("!=")) {
                return new FieldCondition.TagNotContainsFieldCondition("tag", map.get("right"));
            }
            return null;
        } else {

            if (!generatorMap.containsKey(map.get("op"))) {
                return null;
            }
            return generatorMap.get(map.get("op")).newCondition((String) map.get("left"), (String) map.get("right"));
        }
    }

    private interface ConditionMaker {
        FieldCondition newCondition(String propertyName, String param);
    }
}

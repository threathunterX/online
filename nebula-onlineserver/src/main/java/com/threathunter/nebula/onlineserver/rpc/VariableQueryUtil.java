package com.threathunter.nebula.onlineserver.rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Wen Lu
 */
public class VariableQueryUtil {

    public static String getEsperQueryString(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "__all__";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("@@");
        for(String n : names) {
            sb.append(n);
            sb.append("@@");
        }
        return sb.toString();
    }

    public static String getEsperQueryString(String name) {
        if (name == null || name.isEmpty()) {
            return "__all__";
        }
        return String.format("@@%s@@", name);
    }

//    /**
//     * Default return top 20
//     * @param container
//     * @param variableName
//     * @param dimension
//     * @param topCount
//     * @return
//     */
//    public static VariableQuery broadcastTopQuery(EsperContainer container, String variableName, String dimension, int topCount) {
//        return (time, unit) -> VariableTopValuesStore.getTopValues(variableName);
//    }

//    public static VariableQuery broadcastQuery(EsperContainer container, String variableName, String dimension) {
//        VariableGlobalValueQuery q = VariableGlobalValueQuery.createQuery(container.getInstanceCount(), "");
//
//        Event event = new Event("nebula", "_global__variablekeyvalue_request", "");
//        Map<String, Object> properties = new HashMap<>();
//        event.setTimestamp(SystemClock.getCurrentTimestamp());
//        event.setValue(1.0);
//        properties.put("query", getEsperQueryString(variableName));
//        properties.put("requestid", q.getRequestid());
//        event.setPropertyValues(properties);
//
//        container.broadcastQueryEvent(event, dimension);
//        return q;
//    }
//
//    public static VariableQuery sendKeyTopQuery(EsperContainer container, String variableName, String dimension, String key, int topCount) {
//        return null;
//    }
//
//    public static VariableQuery sendKeyQuery(EsperContainer container, String variableName, String dimension, String key) {
//        VariableValueQuery q = VariableValueQuery.createQuery(1, key);
//
//        Event event = new Event("nebula", "_global__variablekeyvalue_request", key);
//        Map<String, Object> properties = new HashMap<>();
//        event.setTimestamp(SystemClock.getCurrentTimestamp());
//        event.setValue(1.0);
//        properties.put("query", getEsperQueryString(variableName));
//        properties.put("requestid", q.getRequestid());
//        event.setPropertyValues(properties);
//
//        container.sendQueryEvent(event, dimension);
//        return q;
//    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("variable1");
        System.out.println(getEsperQueryString(list));
        System.out.println(getEsperQueryString("variable1"));
    }
}

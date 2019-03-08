//package com.threathunter.nebula.onlineserver;
//
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.model.Event;
//import com.threathunter.nebula.testt.babel.client.HttpLogSender;
//import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
//import com.google.common.hash.Hashing;
//import com.google.gson.Gson;
//import org.junit.Test;
//
//import java.nio.charset.Charset;
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.concurrent.TimeUnit;
//
///**
// * Created by daisy on 17/5/6.
// */
//public class HttplogSenderTest {
//
//    public static void setup() {
//        CommonDynamicConfig.getInstance().addOverrideProperty("metrics_server", "redis");
////        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
////        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);
//        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "rabbitmq");
//        CommonDynamicConfig.getInstance().addOverrideProperty("rmq_host", "172.16.10.76");
//        CommonDynamicConfig.getInstance().addOverrideProperty("rmq_username", "admin");
//        CommonDynamicConfig.getInstance().addOverrideProperty("rmq_password", "threathunter.cn");
//        CommonDynamicConfig.getInstance().addOverrideProperty("babel_batch_enable", false);
//    }
//
//    public static void main(String[] args) throws RemoteException {
//        setup();
//        try {
////            testSend();
//          testKeystatQuery();
////        testTotalGlobalQuery();
////        testIncidentQuery();
////            testSendTransaction();
////        testBaselineQuery();
////        testClickStream();
////            testShard();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        System.exit(0);
//    }
//
//    public static void testSendTransaction() throws RemoteException {
//        HttpLogSender sender = new HttpLogSender();
//        sender.start();
//
//        int count = 2;
//        HttpDynamicEventMaker maker = new HttpDynamicEventMaker(1);
//        String ip = "1.1.1.1";
//        String uid = "user1";
//        String did = "did1";
//        for (int i = 0; i < count; i++) {
//            Event event1 = maker.nextEvent();
//            event1.getPropertyValues().put("c_ip", ip);
//            event1.getPropertyValues().put("uid", uid);
//            event1.getPropertyValues().put("did", did);
//            Event event2 = maker.nextEvent();
//            event2.setName("TRANSACTION_ESCROW");
//            event2.getPropertyValues().put("c_ip", ip);
//            event2.getPropertyValues().put("uid", uid);
//            event2.getPropertyValues().put("did", did);
//            event2.getPropertyValues().put("user_name", "user1");
//            event2.getPropertyValues().put("transaction_id", "1111");
//            event2.getPropertyValues().put("escrow_type", "type1");
//            event2.getPropertyValues().put("escrow_account", "account1");
//            event2.getPropertyValues().put("pay_amount", 100.0);
//            event2.getPropertyValues().put("result", "T");
//
//            sender.notify(event1);
//            sender.notify(event2);
//        }
//        sender.stop();
//    }
//
//    public static void testShard() {
//        String ip = "1.1.1.1";
//        String key = "";
//        if (ip != null) {
//            int endIndex = ip.lastIndexOf(".");
//            if (endIndex > 0) {
//                key = ip.substring(0, ip.lastIndexOf("."));
//            }
//        }
//        int shard = Hashing.murmur3_32().hashString(key, Charset.defaultCharset()).asInt() % 16;
//        if (shard < 0) {
//            shard *= -1;
//        }
//        System.out.println(shard);
//    }
//
//    public static void testSend() throws RemoteException {
//        HttpLogSender sender = new HttpLogSender();
//        sender.start();
//
//        int count = 2;
//        Set<String> ips = new HashSet<>();
//        HttpDynamicEventMaker maker = new HttpDynamicEventMaker(10);
//        for (int i = 0; i < count; i++) {
//            Event event = maker.nextEvent();
//            event.setKey("1.1.1.1");
//            event.getPropertyValues().put("c_ip", "1.1.1.1");
//            event.getPropertyValues().put("status", 404);
//            event.getPropertyValues().put("method", "POST");
//            event.setValue(100.0);
//            sender.notify(event);
//            ips.add((String) event.getPropertyValues().get("c_ip"));
//        }
//
//        sender.stop();
//
//        System.out.println(ips);
//    }
//
//    public static void testKeystatQuery() throws RemoteException {
//        KeyStatQuerySender sender = new KeyStatQuerySender();
//        List<String> variables = new ArrayList<>();
//        variables.add("ip__visit__dynamic_distinct_user__1h__slot");
//        variables.add("ip__visit__dynamic_count__1h__slot");
//        variables.add("ip__visit__did_dynamic_count__1h__slot");
//
//        sender.start();
//        Event request = sender.getQueryEvent(variables, null);
////        request.setKey("72.69.71.60");
//        request.setKey("1.1.1.2");
////        request.setKey("1.1.1.1");
////        request.setKey("72.69.71.60");
////        request.setKey("159.121.9.175");
////        request.setKey("141.55.41.194");
////        request.setKey("114.120.41.156");
////        request.setKey("132.145.11.110");
////        request.setKey("84.83.67.55");
////        request.setKey("195.89.112.223");
//        request.getPropertyValues().put("key_type", "ip");
////        request.setKey("2.2.2.2");
////        request.setKey("120.52.76.4");
////        request.setKey("36.56.128.173");
//        System.out.println(new Gson().toJson(sender.rpc(request, 5, TimeUnit.SECONDS)));
//
//        sender.stop();
//    }
//
//    public static void testTotalGlobalQuery() throws RemoteException {
//        OnlineVariableQuerySender sender = new OnlineVariableQuerySender();
//        List<String> variables = new ArrayList<>();
//        variables.add("total__visit__dynamic_count__1h__slot");
//
//        sender.start();
//        Event request = sender.getQueryEvent(variables);
//
//        Gson gson = new Gson();
//        for (Event res : sender.polling(request)) {
//            System.out.println(gson.toJson(res));
//        }
//
//        sender.stop();
//    }
//
//    public static void testIncidentQuery() throws RemoteException {
//        IncidentQuerySender sender = new IncidentQuerySender();
//        Event request = sender.getQueryEvent();
//        sender.start();
//
//        Gson gson = new Gson();
////        for (Event res : sender.polling(request)) {
////            System.out.println(gson.toJson(res));
////        }
//        System.out.println(gson.toJson(sender.rpc(request, 1, TimeUnit.SECONDS)));
//
//        sender.stop();
//    }
//
//    public static void testBaselineQuery() throws RemoteException {
//        BaselineKeyStatQuerySender sender = new BaselineKeyStatQuerySender();
//        List<String> variables = new ArrayList<>();
//        variables.add("ip__visit__dynamic_distinct_user__1h__slot");
//        sender.start();
//        Event request = sender.getQueryEvent("ip__visit__dynamic_count__1h__slot", variables, null);
//        Gson gson = new Gson();
//        for (Event res : sender.polling(request)) {
//            System.out.println(gson.toJson(res));
//        }
//
//        sender.stop();
//    }
//
//    public static void testClickStream() throws RemoteException {
//        ClickstreamQuerySender sender = new ClickstreamQuerySender();
//        sender.start();
//
////        String key = "1.1.1.1";
//        String key = "72.69.71.60";
//        Event clicksRequest = sender.getQueryEvent(key, "clicks", new ArrayList<>());
//        clicksRequest.getPropertyValues().put("key_type", "ip");
//        Event clicksPeriodRequest = sender.getQueryEvent(key, "clicks_period", new ArrayList<>());
//        clicksPeriodRequest.getPropertyValues().put("key_type", "ip");
//        Event visitStreamRequest = sender.getQueryEvent(key, "visit_stream", new ArrayList<>());
//        visitStreamRequest.getPropertyValues().put("key_type", "ip");
//
//        Gson gson = new Gson();
//        System.out.println(gson.toJson(sender.rpc(clicksRequest)));
//        System.out.println(gson.toJson(sender.rpc(clicksPeriodRequest)));
//        System.out.println(gson.toJson(sender.rpc(visitStreamRequest)));
//
//        sender.stop();
//    }
//
//    @Test
//    public void testCommon() {
//        String json = "[[{\"key\":\"value\"},{\"key\":\"value2\"}],[]]";
//        List<Object> list = new Gson().fromJson(json, List.class);
//        System.out.println(list);
//    }
//}

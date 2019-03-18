//package com.threathunter.nebula.onlineserver.rpc;
//
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.model.Event;
//import com.threathunter.nebula.testt.babel.client.HttpLogSender;
//import com.threathunter.nebula.testt.babel.service.BabelServiceReceiverHelper;
//import com.threathunter.nebula.testt.babel.service.NotifyReceiver;
//import com.threathunter.nebula.testt.event.maker.AccountLoginEventMaker;
//import com.threathunter.nebula.testt.event.maker.EventMaker;
//import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
//import com.google.gson.Gson;
//
///**
// * 
// */
//public class ProfileStrategyTest {
//
//    public static void main(String[] args) throws RemoteException, InterruptedException {
//        CommonDynamicConfig.getInstance().addConfigFile("nebula.conf");
//
//        ProfileStrategyTest strategyTest = new ProfileStrategyTest();
//        strategyTest.sendProfileStrategyRelatedEvents();
//        Thread.sleep(10000);
//        strategyTest.getProfileStrategyTriggerEvent();
//    }
//
//    public void sendProfileStrategyRelatedEvents() throws RemoteException {
//        String did = "did0";
//        String ip = "172.16.0.10";
//
//        HttpLogSender eventSender = new HttpLogSender();
//        eventSender.start();
//
//        EventMaker eventMaker = new HttpDynamicEventMaker(2);
//        Event visitEvent = eventMaker.nextEvent();
//        visitEvent.getPropertyValues().put("c_ip", ip);
//        visitEvent.getPropertyValues().put("did", did);
//
//        for (int i = 0; i < 2; i++) {
//            eventSender.notify(visitEvent);
//        }
//
//        // triggerEvent is login event
//        EventMaker loginMaker = new AccountLoginEventMaker(2);
//        Event loginEvent = loginMaker.nextEvent();
//        loginEvent.setKey(ip);
//        loginEvent.getPropertyValues().put("c_ip", ip);
//        loginEvent.getPropertyValues().put("did", did);
//        loginEvent.getPropertyValues().put("useragent", "XXX");
//        loginEvent.getPropertyValues().put("platform", "9");
//        eventSender.notify(loginEvent);
//        loginEvent.getPropertyValues().put("platform", "90");
//        for (int i = 0; i < 2; i++) {
//            eventSender.notify(loginEvent);
//        }
//
//        eventSender.stop();
//    }
//
//    public void getProfileStrategyTriggerEvent() throws InterruptedException {
//        NotifyReceiver profileNoticeReceiver = BabelServiceReceiverHelper.getInstance().createSimpleGetEventReceiver("ProfileNoticeChecker_redis.service");
//        BabelServiceReceiverHelper.getInstance().start();
//
//        long current = System.currentTimeMillis();
//        Gson gson = new Gson();
//        while (System.currentTimeMillis() - current < 30000) {
//            Thread.sleep(100);
//            Event event = profileNoticeReceiver.fetchNextEvent();
//            if (event != null) {
//                System.out.println(gson.toJson(event));
//            }
//        }
//    }
//
////    @Test
////    public void testProfile() throws InterruptedException {
////        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
////        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);
//////        NotifyReceiver profileNoticeReceiver = BabelServiceReceiverHelper.getInstance().createSimpleGetEventReceiver("NoticeNotify_redis.service");
////        NotifyReceiver noticeRecevier = BabelServiceReceiverHelper.getInstance().createSimpleGetEventReceiver("NoticeNotify_redis.service");
////        BabelServiceReceiverHelper.getInstance().start();
////
////        long current = System.currentTimeMillis();
////        Gson gson = new Gson();
////        while (System.currentTimeMillis() - current < 300000) {
////            Thread.sleep(100);
////            Event event = noticeRecevier.fetchNextEvent();
////            if (event != null) {
////                System.out.println(gson.toJson(event.getPropertyValues()));
////            }
////        }
////    }
//}

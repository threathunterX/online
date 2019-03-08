//package com.threathunter.nebula.onlineserver.rpc;
//
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.babel.rpc.ServiceContainer;
//import com.threathunter.babel.rpc.impl.ServerContainerImpl;
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.metrics.MetricsAgent;
//import com.threathunter.model.Event;
//import com.threathunter.platform.persistent.EventOfflineWriter;
//import com.threathunter.nebula.onlineserver.OnlineEventTransport;
//import com.threathunter.nebula.onlineserver.OnlineServer;
//import com.threathunter.nebula.onlineserver.mock.OnlineEvnConstructor;
//import com.threathunter.nebula.sliding.esper.EsperContainer;
//import com.threathunter.nebula.slot.compute.SlotComputeManager;
//import com.threathunter.nebula.slot.compute.util.DimensionHelper;
//import com.threathunter.nebula.slot.compute.util.DimensionType;
//import com.threathunter.nebula.testt.babel.client.rpc.RealtimeQuerySender;
//import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
//import org.apache.commons.lang3.mutable.MutableInt;
//import org.junit.AfterClass;
//import org.junit.Assert;
//import org.junit.BeforeClass;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mockito;
//import org.powermock.api.mockito.PowerMockito;
//import org.powermock.core.classloader.annotations.PowerMockIgnore;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.powermock.modules.junit4.PowerMockRunner;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.TimeUnit;
//
///**
// * Created by daisy on 17/2/13.
// */
//@RunWith(PowerMockRunner.class)
//@PowerMockIgnore("javax.management.*")
//@PrepareForTest({EventVariablePoller.class, HourSlotMetaPoller.class, OnlineServer.class, CommonDynamicConfig.class})
//public class RealtimeQueryTest {
//    private static EsperContainer esperContainer;
//    private static ServiceContainer serviceContainer;
//    private static RealtimeQuerySender querySender;
//    private static RealtimeQuerySender globalQuerySender;
//
//    private static OnlineEventTransport eventTransport;
//    private static SlotComputeManager slotManager = PowerMockito.mock(SlotComputeManager.class);
//    private static EventOfflineWriter writer = PowerMockito.mock(EventOfflineWriter.class);
//
//    @BeforeClass
//    public static void setUp() throws Exception {
//        CommonDynamicConfig.getInstance().addOverrideProperty("sentry_enable", false);
//        CommonDynamicConfig.getInstance().addOverrideProperty("metrics_server", "redis");
//        MetricsAgent.getInstance().start();
//        OnlineEvnConstructor.getInstance().initialLocalOnlineServerEvn();
//        PowerMockito.doNothing().when(slotManager).addEvent(Mockito.any(Event.class));
//        PowerMockito.doNothing().when(writer).addLog(Mockito.any(Event.class));
//
//        Map<String, String> dimensionMap = new HashMap<>();
//        for (String dimension : CommonDynamicConfig.getInstance().getString("sliding_dimensions", "ip|user|did").split("\\|")) {
//            DimensionType type = DimensionType.valueOf(dimension);
//            dimensionMap.put(dimension, DimensionHelper.getDimensionKey(type));
//        }
//        esperContainer = new EsperContainer("nebula_esper", dimensionMap, false);
//        esperContainer.start();
//
//        serviceContainer = new ServerContainerImpl();
//        serviceContainer.addService(new RealtimeQueryService(true, esperContainer));
//        serviceContainer.addService(new RealtimeGlobalQueryService(true, esperContainer));
//        serviceContainer.start();
//
//        querySender = new RealtimeQuerySender("realtimequery_redis.service");
//        globalQuerySender = new RealtimeQuerySender("realtimeglobalquery_redis.service");
//        querySender.start();
//        globalQuerySender.start();
//
//        eventTransport = new OnlineEventTransport(esperContainer, slotManager, writer);
//        eventTransport.start();
//
//        System.out.println("start test");
//    }
//
//    @AfterClass
//    public static void tearDown() throws InterruptedException {
//        eventTransport.stop();
//        querySender.stop();
//        globalQuerySender.stop();
//
//        serviceContainer.stop();
//
//        esperContainer.stop();
//        MetricsAgent.getInstance().stop();
//    }
//
//    @Test
//    public void testGlobalValue() throws Exception {
//        String testVariable = "total__visit__count_dynamic__5m__rt";
//
//        HttpDynamicEventMaker eventMaker = new HttpDynamicEventMaker(3);
//        int count = 100;
//        for (int i = 0; i < count; i++) {
//            eventTransport.sendEvent(eventMaker.nextEvent());
//        }
//
//        Thread.sleep(5000);
//
//        List<String> varList = new ArrayList<>();
//        varList.add(testVariable);
//        Event request = globalQuerySender.getQueryEvent(varList, "");
//        Event response = globalQuerySender.rpc(request, 2, TimeUnit.SECONDS);
//        System.out.println(response.getPropertyValues().get("result"));
//        Assert.assertEquals(100.0, ((Map) response.getPropertyValues().get("result")).get(testVariable));
//    }
//
//    @Test
//    public void testTopValue() throws Exception {
//        String testVariable = "ip__visit__count_dynamic__5m__rt";
//
//        HttpDynamicEventMaker eventMaker = new HttpDynamicEventMaker(30);
//        int count = 1000;
//        eventTransport.sendEvent(eventMaker.nextEvent());
//        for (int i = 0; i < count; i++) {
//            eventTransport.sendEvent(eventMaker.nextEvent());
//        }
//
//        Thread.sleep(60000);
//        for (int i = 0; i < count; i++) {
//            eventTransport.sendEvent(eventMaker.nextEvent());
//        }
//        eventTransport.sendEvent(eventMaker.nextEvent());
//
//        Thread.sleep(2000);
//        List<String> varList = new ArrayList<>();
//        varList.add(testVariable);
//        Event request = globalQuerySender.getQueryEvent(varList, "");
//        Event response = globalQuerySender.rpc(request, 2, TimeUnit.SECONDS);
//        Map result = (Map) ((Map) response.getPropertyValues().get("result")).get("ip__visit__count_dynamic__5m__rt");
//        Assert.assertTrue(result.size() == 10);
//    }
//
//    @Test
//    public void testKeyValue() throws Exception {
//        HttpDynamicEventMaker eventMaker = new HttpDynamicEventMaker(3);
//        String[] ipArray = {"172.16.1.1", "172.16.2.2", "172.16.3.3"};
//        String[] ipcArray = {"172.16.1", "172.16.2", "172.16.3"};
//        eventMaker.setCommonRandomIps(ipArray, ipcArray);
//
//        Map<String, MutableInt> ipCount = new HashMap<>();
//        for (String ip : ipArray) {
//            ipCount.put(ip, new MutableInt(0));
//        }
//
//        int count = 100;
//        for (int i = 0; i < count; i++) {
//            Event event = eventMaker.nextEvent();
//            event.getPropertyValues().put("method", "GET");
//            ipCount.get(event.getPropertyValues().get("c_ip")).increment();
//            eventTransport.sendEvent(event);
//        }
//
//        Thread.sleep(5000);
//
//        List<String> varList = new ArrayList<>();
//        varList.add("ip__visit__count_dynamic__5m__rt");
//        varList.add("ip__visit__dynamic_did_distinct_count__5m__rt");
//        ipCount.forEach((ip, c) -> {
//            try {
//                Event request = querySender.getQueryEvent(varList, ip);
//                Event response = querySender.rpc(request, 2, TimeUnit.SECONDS);
//                Map<String, Object> result = (Map<String, Object>) response.getPropertyValues().get("result");
//                Assert.assertEquals(c.intValue(), ((Number) result.get("ip__visit__count_dynamic__5m__rt")).intValue());
//                Assert.assertEquals(3, ((Number) result.get("ip__visit__dynamic_did_distinct_count__5m__rt")).intValue());
//
//            } catch (RemoteException e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//}

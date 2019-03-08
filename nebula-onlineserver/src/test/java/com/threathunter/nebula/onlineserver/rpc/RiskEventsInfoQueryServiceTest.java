//package com.threathunter.nebula.onlineserver.rpc;
//
//import com.threathunter.babel.meta.ServiceMetaUtil;
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.babel.rpc.ServiceClient;
//import com.threathunter.babel.rpc.ServiceContainer;
//import com.threathunter.babel.rpc.impl.ServerContainerImpl;
//import com.threathunter.babel.rpc.impl.ServiceClientImpl;
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.geo.GeoUtil;
//import com.threathunter.model.Event;
//import com.threathunter.platform.StrategyInfoCache;
//import com.threathunter.platform.persistent.EventOfflineWriter;
//import com.threathunter.platform.persistent.EventPersistCommon;
//import com.threathunter.platform.persistent.schema.CurrentHourPersistInfoRegister;
//import com.threathunter.platform.riskevents.RiskEventsInfoComputer;
//import com.threathunter.nebula.onlineserver.OfflineHelper;
//import com.threathunter.nebula.testt.config.EventLogSchemaConfiguration;
//import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
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
//import org.powermock.reflect.Whitebox;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//import java.util.concurrent.TimeUnit;
//
///**
// * Created by daisy on 16/11/7.
// */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest(StrategyInfoCache.class)
//@PowerMockIgnore("javax.management.*")
//public class RiskEventsInfoQueryServiceTest {
//    private static RiskEventsInfoQueryService queryService;
//    private static ServiceContainer container;
//    private static ServiceClient client;
//
//    private final HttpDynamicEventMaker eventMaker = new HttpDynamicEventMaker(20);
//
//    private static final long ONE_MIN_MILLIS = 60 * 1000;
//
//    @BeforeClass
//    public static void setUp() throws Exception {
//        CommonDynamicConfig.getInstance().addConfigFile("nebula.conf");
//        CommonDynamicConfig.getInstance().addConfigFile("online.conf");
//        String persistentPath = CommonDynamicConfig.getInstance().getString("persist_path", "./persistent");
//        OfflineHelper.deleteFileOrFolder(persistentPath);
//        EventPersistCommon.ensure_dir(persistentPath);
//
//        queryService = new RiskEventsInfoQueryService(true);
//        container = new ServerContainerImpl();
//        container.addService(queryService);
//        container.start();
//
//        client = new ServiceClientImpl(ServiceMetaUtil.getMetaFromResourceFile("RiskEventsInfoQuery_redis.service"));
//        client.start();
//
//        RiskEventsInfoComputer.getInstance().start();
//        CurrentHourPersistInfoRegister.getInstance().update(EventLogSchemaConfiguration.getSchemaFileFromResource(),
//                EventLogSchemaConfiguration.getHeaderFileFromResource());
//        EventOfflineWriter.getInstance().start();
//
//        setMockito();
//    }
//
//    @AfterClass
//    public static void tearDown() {
//        container.stop();
//        client.stop();
//        RiskEventsInfoComputer.getInstance().stop();
//        EventOfflineWriter.getInstance().stop();
//    }
//
//    /**
//     * Test when the number of last minute's incident events is less than 1000, and query for the last minute's info.
//     * Should normally return the list that size less than 1000, and no query from persistent
//     * @throws InterruptedException
//     * @throws RemoteException
//     */
//    @Test
//    public void testLessDataFirstQueryWithoutPersist() throws InterruptedException, RemoteException {
//        int count = 999;
//
//        long firstMinStart = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS + ONE_MIN_MILLIS;
//        System.out.println("sleep to start of next minute");
//        Thread.sleep(firstMinStart - System.currentTimeMillis());
//
//        int incidentCount = fillData(count, true);
//        Assert.assertTrue(incidentCount > 0);
//
//        long secondMinStart = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS + ONE_MIN_MILLIS;
//        System.out.println("sleep to start of next query minute, sleep millis: " + (secondMinStart - System.currentTimeMillis()));
//        Thread.sleep(secondMinStart - System.currentTimeMillis());
//
//        fillData(100, false);
//
//        System.out.println("query...");
//
//        long last = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS - ONE_MIN_MILLIS;
//        Event requestEvent = buildRequestEvent(last);
//        Event response = client.rpc(requestEvent, "riskeventsinfoquery", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> list = (List<Map<String, Object>>) response.getPropertyValues().get("result");
//        Assert.assertEquals(incidentCount, list.size());
//
//        Event requestEvent2 = buildRequestEvent(last + 10000);
//        Event response2 = client.rpc(requestEvent2, "riskeventsinfoquery", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> list2 = (List<Map<String, Object>>) response2.getPropertyValues().get("result");
//        Assert.assertEquals(0, list2.size());
//    }
//
//    /**
//     * This test is for the query when last minute's events less than 1000, and no event for next
//     * 2 minutes or more.
//     * Then the query result should be am empty list
//     * @throws InterruptedException
//     * @throws RemoteException
//     */
//    @Test
//    public void testLessDataFirstLateQueryWithoutPersist() throws InterruptedException, RemoteException {
//        int count = 999;
//
//        long firstMinStart = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS + ONE_MIN_MILLIS;
//        System.out.println("sleep to start of next minute");
//        Thread.sleep(firstMinStart - System.currentTimeMillis());
//
//        // uncomment line to test with incident data
////        fillData(count, true);
//        // uncomment line to test without incident data
//        fillData(count, false);
//
//        long secondMinStart = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS + ONE_MIN_MILLIS;
//        System.out.println("sleep to start of next query minute, sleep millis: " + (secondMinStart - System.currentTimeMillis() + ONE_MIN_MILLIS));
//        Thread.sleep(secondMinStart - System.currentTimeMillis() + ONE_MIN_MILLIS);
//
//        System.out.println("query...");
//        Event requestEvent = buildRequestEvent(secondMinStart);
//        Event event = client.rpc(requestEvent, "riskeventsinfoquery", 1, TimeUnit.SECONDS);
//
//        List<Map<String, Object>> list = (List<Map<String, Object>>) event.getPropertyValues().get("result");
//        Assert.assertEquals(0, list.size());
//    }
//
//    @Test
//    public void testLargeDataQueryWithPersist() throws InterruptedException, RemoteException {
//        long firstMinStart = System.currentTimeMillis() / ONE_MIN_MILLIS * ONE_MIN_MILLIS + ONE_MIN_MILLIS;
//        System.out.println("sleep to start of next minute");
//        Thread.sleep(firstMinStart - System.currentTimeMillis());
//
//        int incidentCount = fillLargeDataWithPersist(4000, true);
//        Assert.assertTrue(incidentCount > 1000);
//
//        Event requestForNull = buildRequestEvent(System.currentTimeMillis());
//        Event responseNull = client.rpc(requestForNull, "riskeventsinfoquery", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> nullList = (List<Map<String, Object>>) responseNull.getPropertyValues().get("result");
//        Assert.assertNull(nullList);
//
//        Event requestForEmpty = buildRequestEvent(firstMinStart - ONE_MIN_MILLIS);
//        Event responseEmpty = client.rpc(requestForEmpty, "riskeventsinfoquery", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> emptyList = (List<Map<String, Object>>) responseEmpty.getPropertyValues().get("result");
//        Assert.assertEquals(0, emptyList.size());
//
//        Thread.sleep(firstMinStart + ONE_MIN_MILLIS - System.currentTimeMillis());
//        Event requestForFirst = buildRequestEvent(firstMinStart);
//        Event responseFirst = client.rpc(requestForFirst, "riskeventsinfoqueryy", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> firstList = (List<Map<String, Object>>) responseFirst.getPropertyValues().get("result");
//        Assert.assertEquals(1000, firstList.size());
//
//        Event requestForSecond = buildRequestEvent(firstMinStart + 10000);
//        Event responseSecond = client.rpc(requestForSecond, "riskeventsinfoqueryy", 1, TimeUnit.SECONDS);
//        List<Map<String, Object>> secondList = (List<Map<String, Object>>) responseSecond.getPropertyValues().get("result");
//        Assert.assertTrue(secondList.size() > 0);
//    }
//
//    // fill with half percentage of incident events
//    // not strictly half, maybe sum should be larger if want to make sure
//    // filled in more than 1000 data
//    private int fillData(int count, boolean randomIncident) {
//        System.out.println("fill in data");
//        Random random = new Random();
//        int incidentCount = 0;
//        int eventCount = 0;
//        while (eventCount < count) {
//            Event event = eventMaker.nextEvent();
//            event.getPropertyValues().put("geo_city", GeoUtil.getCNIPCity((String) event.getPropertyValues().get("c_ip")));
//            if (randomIncident) {
//                if (random.nextInt(10) % 2 == 0) {
//                    event.getPropertyValues().put("notices", "strategy1");
//                    incidentCount++;
//                }
//            }
//            eventCount++;
//            RiskEventsInfoComputer.getInstance().addEvent(event);
//        }
//        System.out.println("success in filling data");
//        return incidentCount;
//    }
//
//    private Event buildRequestEvent(long fromTime) {
//        Event event = new Event("nebula", "riskeventsinfoqueryrequest", "");
//        event.setTimestamp(System.currentTimeMillis());
//        Map<String, Object> properties = new HashMap<>();
//        properties.put("from_time", fromTime);
//        properties.put("end_time", fromTime + 10 * 1000);
//        event.setPropertyValues(properties);
//        return event;
//    }
//
//    private int fillLargeDataWithPersist(int sum, boolean randomIncident) throws InterruptedException {
//        System.out.println("fill in data");
//        Random random = new Random();
//        int incidentCount = 0;
//        int eventCount = 0;
//
//        while (eventCount < sum) {
//            Event event = eventMaker.nextEvent();
//            event.getPropertyValues().put("geo_city", GeoUtil.getCNIPCity((String) event.getPropertyValues().get("c_ip")));
//            if (randomIncident) {
//                if (random.nextInt(10) % 2 == 0) {
//                    event.getPropertyValues().put("notices", "strategy1");
//                    incidentCount++;
//                }
//                if (eventCount % 100 == 0) {
//                    Thread.sleep(1000);
//                }
//            }
//            eventCount++;
//            RiskEventsInfoComputer.getInstance().addEvent(event);
//            EventOfflineWriter.getInstance().addLog(event);
//        }
//
//        return incidentCount;
//    }
//
//    private static void setMockito() throws Exception {
//        StrategyInfoCache mockitoCache = PowerMockito.mock(StrategyInfoCache.class);
//
//        Whitebox.setInternalState(StrategyInfoCache.class, "INSTANCE", mockitoCache);
//
//        PowerMockito.when(mockitoCache.isTest(Mockito.anyString())).thenReturn(false);
//        PowerMockito.when(mockitoCache.containsStrategy(Mockito.anyString())).thenReturn(true);
//        PowerMockito.when(mockitoCache.getScore(Mockito.anyString())).thenReturn(60l);
//        PowerMockito.when(mockitoCache.getCategory(Mockito.anyString())).thenReturn("VISITOR");
//        PowerMockito.when(mockitoCache.getPriorCategory(Mockito.anyString(), Mockito.anyString())).thenReturn("VISITOR");
//    }
//}

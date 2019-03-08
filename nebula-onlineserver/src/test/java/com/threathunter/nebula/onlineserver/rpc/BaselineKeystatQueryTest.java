//package com.threathunter.nebula.onlineserver.rpc;
//
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.babel.rpc.Service;
//import com.threathunter.babel.rpc.ServiceContainer;
//import com.threathunter.babel.rpc.impl.ServerContainerImpl;
//import com.threathunter.common.Identifier;
//import com.threathunter.model.Event;
//import com.threathunter.nebula.onlineserver.mock.OnlineEvnConstructor;
//import com.threathunter.nebula.slot.compute.SlotComputeManager;
//import com.threathunter.nebula.slot.compute.util.SlotVariableMetaRegister;
//import com.threathunter.nebula.testt.JsonFileReader;
//import com.threathunter.nebula.testt.babel.client.rpc.BaselineKeyStatQuerySender;
//import org.junit.*;
//import org.junit.runner.RunWith;
//import org.mockito.Mockito;
//import org.powermock.api.mockito.PowerMockito;
//import org.powermock.core.classloader.annotations.PowerMockIgnore;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.powermock.modules.junit4.PowerMockRunner;
//
//import java.io.IOException;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
///**
// * Created by daisy on 16/8/31.
// */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest(SlotComputeManager.class)
//@PowerMockIgnore("javax.management.*")
//public class BaselineKeystatQueryTest {
//    private ServiceContainer container = new ServerContainerImpl();
//    private BaselineKeyStatQuerySender babelSender = new BaselineKeyStatQuerySender();
//
//    private Set<String> ips;
//    private Set<String> users;
//    private Set<String> dids;
//
//    private SlotComputeManager onlineSlotComputingManager = PowerMockito.mock(SlotComputeManager.class);
//
//    private Service service = new ListKeyVariablesQueryService(true, onlineSlotComputingManager);
//    @BeforeClass
//    public static void setUpStatic() throws IOException {
//        OnlineEvnConstructor.getInstance().initialSingleVariableMetaEvn();
//        SlotVariableMetaRegister.getInstance().update(JsonFileReader.getVariableMetas("slot.json", JsonFileReader.ClassType.LIST));
//    }
//
//    @Before
//    public void setUp() {
//        container.addService(service);
//        container.start();
//
//        babelSender.start();
//    }
//
//    @After
//    public void tearDown() {
//        babelSender.stop();
//        container.stop();
//    }
//
//    private Map<String, Object> buildIpDidVisitCountMap() {
//        Map<String, Object> map = new HashMap<>();
//        for (int i = 1; i < 4; i++) {
//            Map<String, Object> sub = new HashMap<>();
//            sub.put("did" + i, i);
//            map.put("172.16.0." + i, sub);
//        }
//        return map;
//    }
//
//    private Map<String, Object> buildIpVisitCountMap() {
//        Map<String, Object> ipVisitDynamicCountMap = new HashMap<>();
//        ipVisitDynamicCountMap.put("172.16.0.1", 10);
//        ipVisitDynamicCountMap.put("172.16.0.2", 20);
//        ipVisitDynamicCountMap.put("172.16.0.3", 5);
//        return ipVisitDynamicCountMap;
//    }
//
//    private Map<String, Object> buildIpGeoVisitCountMap() {
//        Map<String, Object> ipGeoVisitCountMap = new HashMap<>();
//        for (int i = 1; i < 4; i++) {
//            Map<String, Object> sub = new HashMap<>();
//            sub.put("city0", i);
//            ipGeoVisitCountMap.put("172.16.0." + i, sub);
//        }
//        return ipGeoVisitCountMap;
//    }
//
//    private List<String> buildKeySet() {
//        return new ArrayList<>(buildIpVisitCountMap().keySet());
//    }
//
//    @Test
//    public void testTopCountIpGeo() throws RemoteException {
//        String keyVar = "ip__visit__dynamic_count__1h__slot";
//        String didVar = "ip__visit__did_dynamic_count__1h__slot";
//        String mergeVar = "ip__visit__geo_dynamic_count__1h__slot";
//        List<String> keySets = buildKeySet();
//        PowerMockito.when(onlineSlotComputingManager.queryData(Mockito.eq(Identifier.fromKeys("nebula", keyVar)),
//                Mockito.anyString(), Mockito.anyInt())).thenReturn(buildIpVisitCountMap());
//        PowerMockito.when(onlineSlotComputingManager.queryData(Mockito.eq(Identifier.fromKeys("nebula", didVar)),
//                Mockito.eq(keySets), Mockito.anyInt())).thenReturn(buildIpDidVisitCountMap());
//        PowerMockito.when(onlineSlotComputingManager.queryData(Mockito.eq(Identifier.fromKeys("nebula", mergeVar)),
//                Mockito.eq(keySets), Mockito.anyInt())).thenReturn(buildIpGeoVisitCountMap());
//
//
//        List<String> list = new ArrayList<>();
//        list.add(didVar);
//        list.add(mergeVar);
//
//        List<String> merges = new ArrayList<>();
//        merges.add(mergeVar);
//
//        Event request = babelSender.getQueryEvent(keyVar, list, merges);
//
//        Event response = babelSender.rpc(request, 5, TimeUnit.SECONDS);
//
//        Map<String, Object> ipVars = (Map<String, Object>) response.getPropertyValues().get("result");
//        Map<String, Object> mergesVar = (Map<String, Object>) response.getPropertyValues().get("merges");
//
//        Assert.assertEquals(3, ipVars.size());
//        ipVars.forEach((ip, vars) -> {
//            Map<String, Object> map = (Map<String, Object>) vars;
//            list.forEach(var -> {
//                if (!merges.contains(var)) {
//                    Object value = map.get(var);
//                    try {
//                        assertContainsData(value);
//                    } catch (Exception e) {
//                        Assert.fail();
//                    }
//                }
//            });
//        });
//        Assert.assertEquals(1, mergesVar.size());
//        Assert.assertTrue(mergesVar.containsKey(mergeVar));
//        mergesVar.forEach((m, v) -> assertContainsData(v));
//    }
//
//    private void assertContainsData(Object value) {
//        Assert.assertNotNull(value);
//        if (value instanceof Map) {
//            Map<String, Number> valueMap = (Map<String, Number>) value;
//            Assert.assertTrue(valueMap.size() > 0);
//            valueMap.forEach((s, n) -> Assert.assertTrue(n.intValue() > 0));
//        } else if (value instanceof Number) {
//            Assert.assertTrue(((Number) value).intValue() > 0);
//        } else {
//            Assert.fail();
//        }
//    }
//
//    private Event getRequestEvent() {
//        Event event = new Event("nebula", "keystatquery_request", "");
//        event.setTimestamp(System.currentTimeMillis());
//        event.setValue(1.0);
//
//        Map<String, Object> properties = new HashMap<>();
//        properties.put("app", "nebula");
//        properties.put("count", 5);
//
//        event.setPropertyValues(properties);
//
//        return event;
//    }
//}

//package com.threathunter.nebula.onlineserver.rpc;
//
//import com.threathunter.babel.meta.ServiceMetaUtil;
//import com.threathunter.babel.rpc.RemoteException;
//import com.threathunter.babel.rpc.Service;
//import com.threathunter.babel.rpc.ServiceClient;
//import com.threathunter.babel.rpc.ServiceContainer;
//import com.threathunter.babel.rpc.impl.ServerContainerImpl;
//import com.threathunter.babel.rpc.impl.ServiceClientImpl;
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.metrics.MetricsAgent;
//import com.threathunter.model.Event;
//import com.threathunter.nebula.onlineserver.mock.OnlineEvnConstructor;
//import com.threathunter.nebula.slot.compute.SlotComputeManager;
//import com.threathunter.nebula.slot.compute.cache.StorageType;
//import com.threathunter.nebula.slot.compute.util.DimensionType;
//import com.threathunter.nebula.slot.compute.util.SlotVariableMetaRegister;
//import com.threathunter.nebula.testt.JsonFileReader;
//import com.threathunter.nebula.testt.babel.client.rpc.KeyStatQuerySender;
//import com.threathunter.nebula.testt.event.maker.IncidentEventMaker;
//import com.google.gson.Gson;
//import org.junit.*;
//
//import java.io.IOException;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
///**
// * Created by daisy on 16/8/20.
// */
//public class KeyStatQueryTest {
//    private ServiceContainer container = new ServerContainerImpl();
//    private KeyStatQuerySender sender = new KeyStatQuerySender();
//    private static Set<DimensionType> dimensionTypes = new HashSet<>();
//    private SlotComputeManager slotManager = new SlotComputeManager(dimensionTypes, StorageType.BYTES_ARRAY, true);
//    private Service service = new KeyVariablesQueryService(true, slotManager);
//
//    private Set<String> ips;
//    private Set<String> users;
//    private Set<String> dids;
//
//    @BeforeClass
//    public static void setUpStatic() throws IOException {
//        dimensionTypes.add(DimensionType.ip);
//        dimensionTypes.add(DimensionType.did);
//        dimensionTypes.add(DimensionType.user);
//        dimensionTypes.add(DimensionType.global);
//        dimensionTypes.add(DimensionType.page);
//        dimensionTypes.add(DimensionType.other);
//        OnlineEvnConstructor.getInstance().initialSingleVariableMetaEvn();
//        MetricsAgent.getInstance().start();
//        SlotVariableMetaRegister.getInstance().update(JsonFileReader.getVariableMetas("slot.json", JsonFileReader.ClassType.LIST));
//    }
//
//    @Before
//    public void setUp() throws InterruptedException {
//        this.ips = new HashSet<>();
//        this.users = new HashSet<>();
//        this.dids = new HashSet<>();
//        this.slotManager.start();
//        fillDynamicData();
//
//        container.addService(service);
//        container.start();
//
//        sender.start();
//    }
//
//    @After
//    public void tearDown() {
//        sender.stop();
//        container.stop();
//        this.slotManager.stop();
//    }
//
//    @AfterClass
//    public static void tearDownStatic() {
//        MetricsAgent.getInstance().stop();
//    }
//
//    private void fillDynamicData() throws InterruptedException {
//        IncidentEventMaker incidentEventMaker = new IncidentEventMaker(10);
//
//        for (int i = 0; i < 100; i++) {
//            Event event = incidentEventMaker.nextEvent();
//
//            if (i % 5 != 0) {
//                event.getPropertyValues().remove("notices");
//            }
//            this.ips.add((String) event.getPropertyValues().get("c_ip"));
//            this.users.add((String) event.getPropertyValues().get("uid"));
//            this.dids.add((String) event.getPropertyValues().get("did"));
//
//            slotManager.addEvent(event);
//        }
//        Thread.sleep(2000);
//    }
//
////    @Test
////    public void testTopData() throws RemoteException {
////        List<String> list = new ArrayList<>();
////        list.add("ip__visit__dynamic_count__1h__slot");
////        list.add("ip__visit__dynamic_distinct_page__1h__slot");
////        list.add("ip__visit__incident_distinct_strategy__1h__slot");
////
////        Event request = sender.getQueryEvent(list, null);
////
////        Event response = sender.rpc(request, 5, TimeUnit.SECONDS);
////        System.out.println(new Gson().toJson(response.getPropertyValues().get("result")));
////    }
//
//    @Test
//    public void testWithKeys() throws RemoteException {
//        List<String> list = new ArrayList<>();
//        list.add("ip__visit__did_dynamic_count__1h__slot");
//        list.add("ip__visit__incident_max_rate__1h__slot");
//
//        List<String> subList = new ArrayList<>(this.dids);
//        Map<String, List<String>> subKeys = new HashMap<>();
//        subKeys.put("ip__visit__did_dynamic_count__1h__slot", subList);
//
//        Event request = sender.getQueryEvent(list, subKeys);
//        request.setKey((String) this.ips.toArray()[0]);
//        System.out.println(request.getKey());
//        request.getPropertyValues().put("key_type", "ip");
//
//        Event response = sender.rpc(request, 5, TimeUnit.SECONDS);
//        System.out.println(new Gson().toJson(response.getPropertyValues().get("result")));
//    }
//
//    public static void main(final String[] args) throws RemoteException {
//        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
//        CommonDynamicConfig.getInstance().addOverrideProperty("redis_host", "127.0.0.1");
//        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 26379);
//        CommonDynamicConfig.getInstance().addOverrideProperty("sentry_enable", false);
//        List<String> list = new ArrayList<>();
//        list.add("ip__visit__incident_min_timestamp__1h__slot");
//        list.add("ip__visit__incident_score__1h__slot");
//        ServiceClient client = new ServiceClientImpl(ServiceMetaUtil.getMetaFromResourceFile("incidentquery_redis.service"));
//        client.start();
//
//        Event request = new Event("nebula", "incidentquery_request", "");
//        request.setTimestamp(System.currentTimeMillis());
//        request.setValue(1.0);
//
//        Map<String, Object> properties = new HashMap<>();
//        properties.put("app", "nebula");
//        properties.put("count", 5);
//
//        properties.put("var_list", list);
//        request.setPropertyValues(properties);
//        request.getPropertyValues().put("key_variable", "ip__visit__incident_score__1h__slot");
//        System.out.println(new Gson().toJson(client.rpc(request, "incidentquery", 5, TimeUnit.SECONDS)));
//    }
//}

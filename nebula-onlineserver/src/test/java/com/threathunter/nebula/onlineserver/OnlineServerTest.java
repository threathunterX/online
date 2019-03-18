package com.threathunter.nebula.onlineserver;

import com.threathunter.babel.rpc.RemoteException;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.nebula.testt.babel.client.HttpLogSender;
import com.threathunter.nebula.testt.babel.service.BabelServiceReceiverHelper;
import com.threathunter.nebula.testt.babel.service.NotifyReceiver;
import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
import org.junit.*;

import java.util.Map;

/**
 * 
 */
public class OnlineServerTest {
    private static OnlineServer server = new OnlineServer();

    private HttpDynamicEventMaker maker;
    private HttpLogSender sender;
//    private OnlineVariableQuerySender querySender;
    private NotifyReceiver notice;

    @BeforeClass
    public static void setupStatic() throws InterruptedException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);
        CommonDynamicConfig.getInstance().addOverrideProperty("metrics_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("sentry_enable", false);
        CommonDynamicConfig.getInstance().addOverrideProperty("app", "nebula.online");

//        CommonDynamicConfig.getInstance().addOverrideProperty("nebula.online.meta.realtime.variables.local", "variables.json");
//        CommonDynamicConfig.getInstance().addOverrideProperty("nebula.online.meta.slot.variables.local", "slot.json");
//        CommonDynamicConfig.getInstance().addOverrideProperty("nebula.online.meta.strategies.local", "strategies.json");
//        CommonDynamicConfig.getInstance().addOverrideProperty("greyhound.server.strategy.debug", true);
        CommonDynamicConfig.getInstance().addConfigFile("online.conf");

        Thread thread = new Thread(() -> server.start());
        thread.start();
        Thread.sleep(30 * 1000);
    }

    @Before
    public void setup() {
        this.maker = new HttpDynamicEventMaker(10);
        this.sender = new HttpLogSender();
        this.sender.start();
        this.notice = BabelServiceReceiverHelper.getInstance().createSimpleGetEventReceiver("NoticeNotify_redis.service");
        BabelServiceReceiverHelper.getInstance().start();
//        this.querySender = new OnlineVariableQuerySender();
//        this.querySender.start();
    }

    @AfterClass
    public static void tearDownStatic() {
        server.stop();
    }

    @After
    public void tearDown() {
        this.sender.stop();
//        this.querySender.stop();
        BabelServiceReceiverHelper.getInstance().stop();
    }

    @Test
    public void testHighVisit() throws RemoteException, InterruptedException {
        String testIP1 = "1.1.1.1";
        String testIP2 = "2.2.2.2";

        for (int i = 0; i < 5; i++) {
            Event event1 = maker.nextEvent();
            Event event2 = maker.nextEvent();
            event1.getPropertyValues().put("c_ip", testIP1);
            event2.getPropertyValues().put("c_ip", testIP2);
            sender.notify(event1);
            sender.notify(event2);
        }

        Event event1 = maker.nextEvent();
        event1.getPropertyValues().put("c_ip", testIP1);
        event1.setKey(testIP1);

        sender.notify(event1);
        Thread.sleep(2000);

        Event event = notice.fetchNextEvent();
        boolean trigger = false;
        while (event != null) {
            System.out.println(event);
            if (event.getPropertyValues().get("strategyName").equals("highvisit_strategy")) {
                if (((Map) event.getPropertyValues().get("triggerValues")).get("c_ip").equals(testIP1)) {
                    trigger = true;
                }
            }
            event = notice.fetchNextEvent();
        }

        Assert.assertTrue(trigger);
    }


    @Test
    public void testSlotQuery() throws RemoteException, InterruptedException {
//        Random rand = new Random();
//        for (int i = 0; i < 100; i++) {
//            Event event = maker.nextEvent();
//            event.getPropertyValues().put("c_ip", "1.1.1." + rand.nextInt(5));
//            event.getPropertyValues().put("did", "did_" + rand.nextInt(5));
//            sender.notify(event);
//        }
//
//        Thread.sleep(10000);


//        List<String> topVariables = new ArrayList<>();
//        topVariables.add("global__visit_dynamic_distinct_count_ip__1h__slot");
//        topVariables.add("ip__visit_dynamic_count_top100__1h__slot");
//        Event topEvent = querySender.getQueryEvent(topVariables, null);
//        System.out.println(new Gson().toJson(querySender.rpc(topEvent, 10, TimeUnit.SECONDS)));

//         query keys data
//        List<String> keysVariables = new ArrayList<>();
//        keysVariables.add("did__visit_dynamic_count__1h__slot");
//        keysVariables.add("did_page__visit_dynamic_count_top20__1h__slot");
//        List<String> keys = new ArrayList<>();
//        keys.add("did_1");
//        keys.add("did_2");
//        keys.add("did_3");
//        Event keysEvent = querySender.getQueryEvent(keysVariables, keys);
//        System.out.println(new Gson().toJson(querySender.rpc(keysEvent, 10, TimeUnit.SECONDS)));

        while (true) {
            Thread.sleep(10000000);
        }
    }
}

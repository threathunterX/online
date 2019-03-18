package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.rpc.RemoteException;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.nebula.testt.babel.client.rpc.ClickstreamQuerySender;
import com.threathunter.nebula.testt.babel.client.rpc.OnlineVariableQuerySender;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 
 */
public class OnlineSlotVariableQueryTest {
    private OnlineVariableQuerySender querySender;
    @Test
    public void testSlot() throws RemoteException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 6379);
        this.querySender = new OnlineVariableQuerySender();
        this.querySender.start();

//        List<String> topVariables = new ArrayList<>();
//        topVariables.add("global__visit_dynamic_distinct_count_ip__1h__slot");
//        topVariables.add("ip__visit_dynamic_count_top100__1h__slot");
//        topVariables.add("ip__visit_incident_score_top100__1h__slot");
//        topVariables.add("global__visit_incident_count__1h__slot");
//        Event topEvent = querySender.getQueryEvent(topVariables, null);
//        System.out.println(new Gson().toJson(querySender.rpc(topEvent, 10, TimeUnit.SECONDS)));

//         query keys data
        List<String> keysVariables = new ArrayList<>();
//        keysVariables.add("uid__visit_dynamic_count__1h__slot");
//        keysVariables.add("ip_page__visit_dynamic_count_top20__1h__slot");
//        keysVariables.add("ip_scene_strategy__visit_incident_group_count__1h__slot");
//        keysVariables.add("ip__visit_incident_max_rate__1h__slot");

//        keysVariables.add("ip__visit_incident_first_timestamp__1h__slot");
//        keysVariables.add("uid_scene__visit_incident_group_count__1h__slot");
//        keysVariables.add("scene_strategy__visit_incident_count_top20__1h__slot");
        keysVariables.add("ip_geo_city__visit_dynamic_count_top20__1h__slot");
        List<String> keys = new ArrayList<>();
        keys.add("121.23.1.1");
//        keys.add("uid80003");
//        keys.add("ACCOUNT");
        Event keysEvent = querySender.getQueryEvent(keysVariables, keys);
        System.out.println(new Gson().toJson(querySender.rpc(keysEvent, 10, TimeUnit.SECONDS)));

        this.querySender.stop();
    }

    @Test
    public void testClickStream() throws RemoteException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);

        ClickstreamQuerySender clickstreamQuerySender = new ClickstreamQuerySender();
//        Event event = clickstreamQuerySender.getQueryEvent("121.23.1.1", "visit_stream", new ArrayList<>());
        Event event = clickstreamQuerySender.getQueryEvent("uid0008", "uid","clicks", new ArrayList<>());
        Event response = clickstreamQuerySender.rpc(event, 10, TimeUnit.SECONDS);
        System.out.println(response);
    }
}

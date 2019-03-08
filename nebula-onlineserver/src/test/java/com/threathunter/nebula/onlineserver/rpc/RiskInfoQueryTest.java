package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.RemoteException;
import com.threathunter.babel.rpc.ServiceClient;
import com.threathunter.babel.rpc.impl.ServiceClientImpl;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by daisy on 17/8/3.
 */
public class RiskInfoQueryTest {
    public static void main(final String[] args) throws RemoteException, InterruptedException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);
        ServiceMeta meta = ServiceMetaUtil.getMetaFromResourceFile("RiskEventsInfoQuery_redis.service");
        ServiceClient myClient = new ServiceClientImpl(meta, "riskinfotest");
        myClient.start();
        Gson gson = new Gson();

        long current = System.currentTimeMillis();
        while (System.currentTimeMillis() - current < 10 * 60 * 1000) {
            Event event = new Event("nebula", "riskeventsinfoquery", "");
            event.setTimestamp(System.currentTimeMillis());
            Map<String, Object> properties = new HashMap<>();
            long fromTime = System.currentTimeMillis() / 60000 * 60000 - 60000;
            properties.put("from_time", fromTime);
            properties.put("end_time", fromTime + 60000);
            event.setPropertyValues(properties);

            System.out.println(gson.toJson(myClient.rpc(event, meta.getName(), 5, TimeUnit.SECONDS)));

            Thread.sleep(10000);
        }
        myClient.stop();
        System.exit(0);
    }

}

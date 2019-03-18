package com.threathunter.nebula.onlineserver;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.RemoteException;
import com.threathunter.babel.rpc.ServiceClient;
import com.threathunter.babel.rpc.impl.ServiceClientImpl;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.nebula.testt.babel.service.BabelServiceReceiverHelper;
import com.threathunter.nebula.testt.babel.service.NotifyReceiver;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * 
 */
public class BabelTest {

    @Test
    public void test() throws RemoteException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        ServiceMeta meta = ServiceMetaUtil.getMetaFromResourceFile("Httplog_redis.service");
        ServiceClient client = new ServiceClientImpl(meta);
        client.start();
        client.notify(new Event("test", "test", "test"), meta.getName());
        client.stop();
    }

    @Test
    public void testNotice() throws InterruptedException {
        CommonDynamicConfig.getInstance().addOverrideProperty("babel_server", "redis");
        CommonDynamicConfig.getInstance().addOverrideProperty("redis_port", 16379);
        NotifyReceiver receiver = BabelServiceReceiverHelper.getInstance().createSimpleGetEventReceiver("NoticeNotify_redis.service");
        BabelServiceReceiverHelper.getInstance().start();

        Set<String> notices = new HashSet<>();
        while (true) {
            Event event = receiver.fetchNextEvent();
            if (event != null) {
                String name = (String) event.getPropertyValues().get("strategyName");
                if (!notices.contains(name)) {
                    System.out.println(name);
                    notices.add(name);
                }
//                if (name.contains("visit_H_5m")) {
//                    notices.add();
//                }
            }
        }
    }
}

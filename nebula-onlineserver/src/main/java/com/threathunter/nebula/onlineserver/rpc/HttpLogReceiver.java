package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.babel.rpc.ServiceContainer;
import com.threathunter.babel.rpc.impl.ServerContainerImpl;
import com.threathunter.greyhound.server.esper.extension.BloomFilterHelper;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import com.threathunter.nebula.onlineserver.OnlineEventTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receiving sharded http logs from sniffer.
 *
 * @author Wen Lu
 */
public class HttpLogReceiver {
    private static final Logger logger = LoggerFactory.getLogger(HttpLogReceiver.class);

    private ServiceContainer server;
    private OnlineEventTransport eventTransport;
    private boolean redisMode;

    public HttpLogReceiver(final OnlineEventTransport onlineEventTransport, boolean redisMode) {
        this.eventTransport = onlineEventTransport;
        this.redisMode = redisMode;
    }

    public void start() {
        server = new ServerContainerImpl() {
        };
        String configName = "Httplog_rmq.service";
        if (redisMode) {
            configName = "Httplog_redis.service";
        }
//        Gson gson = new Gson();
//        AtomicInteger integer = new AtomicInteger(0);
        final ServiceMeta meta = ServiceMetaUtil.getMetaFromResourceFile(configName);
        Service httplog = new Service() {
            @Override
            public Event process(final Event e) {
                // preprocess for old python code
                if (e.getPropertyValues().containsKey("id")) {
                    e.setId((String) e.getPropertyValues().get("id"));
                }
                if (e.getPropertyValues().containsKey("pid")) {
                    e.setPid((String) e.getPropertyValues().get("pid"));
                }
                MetricsHelper.getInstance().addMetrics("events.income.count", 1.0, "name", e.getName(), "source", "httplog");
                String ip = (String) e.getPropertyValues().get("c_ip");
                String url = (String) e.getPropertyValues().get("uri_stem");
                String referer= (String) e.getPropertyValues().get("referer");

                boolean refererhit = BloomFilterHelper.isIPRefererVisted(ip, referer);
                if (refererhit) {
                    e.getPropertyValues().put("referer_hit", "T");
                } else {
                    e.getPropertyValues().put("referer_hit", "F");
                }

                BloomFilterHelper.addIPURL(ip, url);

                SystemClock.syncCustomerTimestamp(e.getTimestamp());
//                e.setTimestamp(System.currentTimeMillis());
                eventTransport.sendEvent(e);

//                if (integer.get() % 100 == 0) {
//                    System.out.println(gson.toJson(e));
//                }
//                integer.incrementAndGet();
                return null;
            }

            @Override
            public ServiceMeta getServiceMeta() {
                return meta;
            }

            @Override
            public EventMeta getRequestEventMeta() {
                return null;
            }

            @Override
            public EventMeta getResponseEventMeta() {
                return null;
            }

            @Override
            public void close() {

            }
        };

        server.addService(httplog);
        server.start();
        logger.warn("start:the http log receiver has started");
    }

    public void stop() {
        logger.warn("close:stopping the http log receiver");
        server.stop();
    }
}

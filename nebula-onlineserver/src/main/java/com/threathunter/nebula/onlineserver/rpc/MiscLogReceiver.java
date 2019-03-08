package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.babel.rpc.ServiceContainer;
import com.threathunter.babel.rpc.impl.ServerContainerImpl;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import com.threathunter.nebula.onlineserver.OnlineEventTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receiving misc logs from sniffer.
 *
 * @author Wen Lu
 */
public class MiscLogReceiver {
    private static final Logger logger = LoggerFactory.getLogger(MiscLogReceiver.class);

    private ServiceContainer server;
    private boolean redisMode;
    private OnlineEventTransport eventTransport;

    public MiscLogReceiver(final OnlineEventTransport onlineEventTransport, boolean redisMode) {
        this.eventTransport = onlineEventTransport;
        this.redisMode = redisMode;
    }

    public void start() {
        server = new ServerContainerImpl();
        String configName = "Misclog_rmq.service";
        if (redisMode) {
            configName = "Misclog_redis.service";
        }
        final ServiceMeta meta = ServiceMetaUtil.getMetaFromResourceFile(configName);
        Service misclog = new Service() {
            @Override
            public Event process(final Event e) {
                // preprocess for old python code
                if (e.getPropertyValues().containsKey("id")) {
                    e.setId((String) e.getPropertyValues().get("id"));
                }
                if (e.getPropertyValues().containsKey("pid")) {
                    e.setPid((String) e.getPropertyValues().get("pid"));
                }

                MetricsHelper.getInstance().addMetrics("events.income.count", 1.0, "name", e.getName(), "source", "misclog");

                SystemClock.syncCustomerTimestamp(e.getTimestamp());
                // online
                eventTransport.sendEvent(e);
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

        server.addService(misclog);
        server.start();
        logger.warn("start:the misc log receiver has started");
    }

    public void stop() {
        logger.warn("close:stopping the misc log receiver");
        server.stop();
    }
}

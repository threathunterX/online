package com.threathunter.nebula.onlineserver.notice;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.impl.ServiceClientImpl;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.nebula.common.util.MetricsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by daisy on 16/9/10.
 */
public class ProfileWriter {
    private static final Logger logger = LoggerFactory.getLogger(ProfileWriter.class);

    private static final ProfileWriter instance = new ProfileWriter();

    private volatile boolean running = false;
    private final BlockingDeque<Event> queue = new LinkedBlockingDeque<>(CommonDynamicConfig.getInstance().getInt("nebula.online.esper.sender.capacity", 10000));
    private final ProfileWriteThread profileWriteThread = new ProfileWriteThread();

    public static ProfileWriter getInstance() {
        return instance;
    }
    private ServiceClientImpl client;
    private ServiceMeta meta;

    public ProfileWriter() {
    }

    public void addEvent(Event event) {
        boolean success = queue.offer(event);
        if (!success) {
            logger.warn("data:fatal:the profile writer queue is full");
            MetricsHelper.getInstance().addMetrics("nebula.online.profile.writer.dropevents", 1.0);
        }
    }

    public void start(boolean redisMode) {
        this.running = true;

        if (redisMode) {
            meta = ServiceMetaUtil.getMetaFromResourceFile("ProfileWriter_redis.service");
        } else {
            meta = ServiceMetaUtil.getMetaFromResourceFile("ProfileWriter_rmq.service");
        }

        client = new ServiceClientImpl(meta);
        client.start();

        this.profileWriteThread.start();
    }

    public void stop() {
        if (!running) {
            return;
        }
        this.running = false;

        try {
            this.profileWriteThread.join(1000);
            client.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean needWrite(final String eventName) {
        return (eventName != null && !eventName.startsWith("HTTP") && !eventName.endsWith("DELAY"));
    }

    private class ProfileWriteThread extends Thread {
        public ProfileWriteThread() {
            super("profile write thread");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            int idle = 0;
            while (running) {
                List<Event> eventList = new ArrayList<>();
                queue.drainTo(eventList);
                if (eventList.isEmpty()) {
                    idle++;
                    if (idle >= 3) {
                        try {
                            Thread.sleep(500);
                        } catch (Exception e) {
                            logger.error("fail to get events", e);
                        }
                    }
                } else {
                    idle = 0;
                    try {
                        if (eventList.size() == 1) {
                            client.notify(eventList.get(0), meta.getName());
                        } else {
                            client.notify(eventList, meta.getName());
                        }
                    } catch (Exception e) {
                        logger.error("fail to send events", e);
                    }
                }
            }
        }
    }
}

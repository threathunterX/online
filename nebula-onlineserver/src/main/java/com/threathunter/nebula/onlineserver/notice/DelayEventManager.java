package com.threathunter.nebula.onlineserver.notice;

import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.Event;
import com.threathunter.model.VariableMetaRegistry;
import com.threathunter.variable.meta.DelayCollectorVariableMeta;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import com.threathunter.nebula.onlineserver.OnlineEventTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Handler the events that need to resend to online server because of delay strategies.
 * If a strategy contains sleep function, it will be separated to two parts in online.
 * First parts will compute normally, but when the collector update, it will be send to here,
 * and wait for a period until it is consider to send to online server.
 *
 * One thing we need to consider is the number of events that to be delay, if one event is consider to delay
 * 1 hour, meanwhile too many same events comes, memory will be a problem. Currently, we will do cache to prevent
 * same key-strategy pair redundancy, like notice. Notice, strategy should not depends on event's field after sleep.
 *
 * @author daisy
 */
public class DelayEventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelayEventManager.class);
    private static final DelayEventManager INSTANCE = new DelayEventManager();
    private static final long DUPLICATE_MILLIS = 1000 * 60 * 5;

    private DelayEventManager() {
    }

    // add a thread for the storage to check events that should be send
    private final DelayEventWorker worker =  new DelayEventWorker();

    private volatile Map<String, DelayStrategyInfo> delayStrategyCache;
    private final HashSet<String> existKeyStrategyPairSet = new HashSet<>();

    public static DelayEventManager getInstance() {
        return INSTANCE;
    }

    public void addDelayEvent(final Event event, final String strategyName) {
//        System.out.println("add event");
        DelayStrategyInfo info = this.delayStrategyCache.get(strategyName);
        if (info == null) {
            return;
        }
        Long checkTimestamp = info.getDelayMillis() / DUPLICATE_MILLIS * DUPLICATE_MILLIS;
        String checkStr = String.format("%s@@%s@@%d", strategyName,
                event.getPropertyValues().getOrDefault(info.getKeyField(), ""), checkTimestamp);
        if (existKeyStrategyPairSet.contains(checkStr)) {
            MetricsHelper.getInstance().addMetrics("nebula.online.delay.exist.drop.count", 1.0);
            return;
        }

        existKeyStrategyPairSet.add(checkStr);

        event.setTimestamp(event.getTimestamp() + info.getDelayMillis());
//        System.out.println(System.currentTimeMillis() - event.getTimestamp());
        worker.addDelayEvent(event, checkStr);
        MetricsHelper.getInstance().addMetrics("nebula.online.delay.add.count", 1.0);
    }

    public void start(final OnlineEventTransport eventTransport) {
        this.worker.setTransport(eventTransport);
        this.worker.start();
    }

    public void stop() {
        this.worker.stopWork();
        try {
            this.worker.join(1000);
        } catch (Exception e) {
            LOGGER.error("error happened when stopping delay worker", e);
        }
    }

    static class DelayEvent implements Delayed {

        private final Event event;
        private final String checkString;

        public DelayEvent(final Event event, final String checkString) {
            this.event = event;
            this.checkString = checkString;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(this.event.getTimestamp() - SystemClock.getCurrentTimestamp(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed o) {
            return (int) (this.event.getTimestamp() - ((DelayEvent) o).event.getTimestamp());
        }
    }

    public void updateStrategyCache() {
        Map<String, DelayStrategyInfo> cache = new HashMap<>();
        VariableMetaRegistry.getInstance().getAllVariableMetas().stream().filter(meta -> meta instanceof DelayCollectorVariableMeta).forEach(meta -> {
            DelayCollectorVariableMeta collectorMeta = (DelayCollectorVariableMeta) meta;
            String name = collectorMeta.getStrategyName();
            StrategyInfoCache.StrategyInfo info = StrategyInfoCache.getInstance().getStrategyInfo(name);
            if (info != null) {
                cache.put(name, new DelayStrategyInfo(name, info.getCheckValue(), collectorMeta.getSleepTimeMillis()));
            }
        });

        this.delayStrategyCache = cache;
    }

    class DelayEventWorker extends Thread {
        private volatile boolean running = false;
        private final DelayQueue<DelayEvent> delayQueue;
        private volatile OnlineEventTransport eventTransport;

        public DelayEventWorker() {
            super("delay events send worker");
            this.setDaemon(true);
            this.delayQueue = new DelayQueue<>();
        }

        public void setTransport(final OnlineEventTransport transport) {
            this.eventTransport = transport;
        }

        public void addDelayEvent(final Event event, final String checkString) {
//            System.out.println("DelayEventManager: add delay event to worker");
//            if (event.getTimestamp() <= SystemClock.getCurrentTimestamp()) {
//                System.out.println("expire");
//                return;
//            }
            if (this.delayQueue.size() >= CommonDynamicConfig.getInstance().getInt(
                    "nebula.online.esper.delay.max.count", 10000)) {
                MetricsHelper.getInstance().addMetrics("nebula.online.delay.worker.drop.count", 1.0);
                return;
            }
            this.delayQueue.offer(new DelayEventManager.DelayEvent(event, checkString));
//            System.out.println("event added");
            MetricsHelper.getInstance().addMetrics("nebula.online.delay.worker.add.count", 1.0);
        }

        public void stopWork() {
            this.running = false;
        }
        @Override
        public void run() {
            if (running) {
                LOGGER.warn("already running");
                return;
            }
            running = true;
            while (running) {
                try {
                    DelayEvent delayEvent = this.delayQueue.take();
                    transportEvent(delayEvent.event);
                    existKeyStrategyPairSet.remove(delayEvent.checkString);
                    MetricsHelper.getInstance().addMetrics("nebula.online.delay.resend.count", 1.0);
                    count++;
//                    System.out.println("take one");
                } catch (Exception e) {
                    LOGGER.error("taking expire events error", e);
                }
            }
        }
        int count = 0;
        long getCount() {
            return count;
        }

        void transportEvent(final Event event) {
//            System.out.println("send to transport");
            this.eventTransport.sendEvent(event);
        }
    }

    private class DelayStrategyInfo {
        private final String name;
        private final String keyField;
        private final Long delayMillis;

        public DelayStrategyInfo(final String name, final String keyField, final Long delayMillis) {
            this.name = name;
            this.keyField = keyField;
            this.delayMillis = delayMillis;
        }

        public String getName() {
            return name;
        }

        public String getKeyField() {
            return keyField;
        }

        public Long getDelayMillis() {
            return delayMillis;
        }
    }
}

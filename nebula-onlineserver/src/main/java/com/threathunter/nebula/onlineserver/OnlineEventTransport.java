package com.threathunter.nebula.onlineserver;
import com.threathunter.bordercollie.slot.compute.SlotEngine;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.greyhound.server.GreyhoundServer;
import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.Event;
import com.threathunter.persistent.core.api.SequenceReadContext;
import com.threathunter.persistent.core.io.EventOfflineWriter;
import com.threathunter.nebula.common.util.EventsGrouper;
import com.threathunter.nebula.common.util.LocationHelper;
import com.threathunter.nebula.common.util.MetricsHelper;
import com.threathunter.nebula.common.util.SystemClock;
import com.threathunter.nebula.onlineserver.notice.ProfileWriter;
import com.threathunter.nebula.onlineserver.rpc.riskmonitor.RiskMonitorDataComputer;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.threathunter.bordercollie.slot.util.SlotUtils.slotTimestampFormat;

/**
 * Created by daisy on 17/4/24.
 */
public class OnlineEventTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlineEventTransport.class);

    private final int eventsGroupInterval = CommonDynamicConfig.getInstance().getInt("nebula.online.transport.group.interval", 3000);
    private final BlockingQueue<Event> eventsCache = new LinkedBlockingQueue<>();
    private final EventsGrouper eventsGrouper = new EventsGrouper(eventsGroupInterval);

    private final GreyhoundServer ruleEngine;
    private final SlotEngine slotEngine;
    private final EventOfflineWriter offlineWriter;

    private volatile boolean running = false;
    private final TransportWorker worker;
    private final boolean enableSlot;
    private final boolean ruleEngineBatchMode;

    public OnlineEventTransport(final GreyhoundServer greyhoundServer, final SlotEngine slotEngine, final EventOfflineWriter writer, final boolean ruleEngineBatchMode) {
        this.ruleEngine = greyhoundServer;
        this.slotEngine = slotEngine;
        this.offlineWriter = writer;
        this.enableSlot = ruleEngineBatchMode ? false : CommonDynamicConfig.getInstance().getBoolean("nebula.online.slot.enable", true);
        if (this.enableSlot) {
            LOGGER.warn("[nebula:online:transport]start: enable slot");
        } else {
            LOGGER.warn("[nebula:online:transport]start: disable slot");
        }
        this.ruleEngineBatchMode = ruleEngineBatchMode;
        if (!this.ruleEngineBatchMode) {
            worker = new TransportWorker();
        } else {
            worker = null;
        }
    }

    public void start() {
        if (running) {
            LOGGER.error("already running");
            return;
        }
        this.running = true;
        if (!ruleEngineBatchMode) {
            this.worker.start();
        }
        if (this.enableSlot) {
            LOGGER.warn("[nebula:online:transport]start: loading slot data from current hour persistent data");
            SequenceReadContext context = new SequenceReadContext(new DateTime(System.currentTimeMillis()).toString(slotTimestampFormat), (event) ->{
                String notices = (String) event.getPropertyValues().get("notices");
                if (notices != null && !notices.isEmpty()) {
                    completeIncidentInfoToEvent(event);
                }
                this.slotEngine.add(event);
            });
            context.startQuery();
            LOGGER.warn("[nebula:online:transport]start: successfully loaded slot data from current hour persistent data");
        }
        LOGGER.warn("[nebula:online:transport]start: running online events transport");
    }

    public void stop() {
        if (!running) {
            LOGGER.error("[nebula:online:transport]stop: already stopped");
            return;
        }
        this.running = false;
        if (!this.ruleEngineBatchMode) {
            try {
                this.worker.join(1000);
            } catch (InterruptedException e) {
            }
        }
        LOGGER.warn("[nebula:online:transport]stop: stopping online events transport");
    }

    public void sendEvent(final Event event) {
        if (!isInternal(event)) {
            String ip = (String) event.getPropertyValues().get("c_ip");
            if (InetAddressValidator.getInstance().isValid(ip)) {
                String geoCity = LocationHelper.getLocation(ip, "city");
                String geoProvince = LocationHelper.getLocation(ip, "province");
                event.getPropertyValues().put("geo_city", geoCity);
                event.getPropertyValues().put("geo_province", geoProvince);
            } else {
                event.getPropertyValues().put("c_ip", "0.0.0.0");
                if (event.getKey() != null && event.getKey().contains(".")) {
                    event.setKey("0.0.0.0");
                }
                event.getPropertyValues().put("geo_city", "非法");
                event.getPropertyValues().put("geo_province", "非法");
            }
        }

        // send to sliding first
        this.ruleEngine.addEvent(event);
        // send to profile if needs
        if (ProfileWriter.getInstance().needWrite(event.getName())) {
            ProfileWriter.getInstance().addEvent(event);
        }

        if (!this.ruleEngineBatchMode) {
            if (this.eventsCache.offer(event)) {
                MetricsHelper.getInstance().addMetrics("events.transport.offer.count", 1.0,
                        "name", event.getName());
            } else {
                MetricsHelper.getInstance().addMetrics("events.transport.drop.count", 1.0,
                        "name", event.getName());
            }
        }
    }

    public boolean isInternal(final Event event) {
        if (event.getName().endsWith("DELAY")) {
            return true;
        }
        return false;
    }

    private class TransportWorker extends Thread {
        public TransportWorker() {
            super("event translator");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            LOGGER.warn("running transport worker");
            System.out.println("running transport worker");
            int idle = 0;
            List<Event> firstGroup = new ArrayList<>();
            long lastGroupTime = SystemClock.getCurrentTimestamp();
            while (running) {
                try {
                    List<Event> events = new ArrayList<>();
                    eventsCache.drainTo(events);

                    eventsGrouper.addEvents(events);

                    List<Event> secondGroup = eventsGrouper.getNextEventsGroup();
                    if (secondGroup.isEmpty()) {
                        idle++;
                        if (idle > 3) {
                            try {
                                Thread.sleep(100);
                                Event event = new Event();
//                                event.setTimestamp(SystemClock.getCurrentTimestamp() - eventsGroupInterval);
                                event.setTimestamp(SystemClock.getCurrentTimestamp());
                                eventsCache.add(event);
                            } catch (InterruptedException e) {
                            }
                            // child and parent will not appear separately
                            // lastGroupTime is the time that the last time get group events from eventsGrouper
                            // if long time no data from eventsGrouper, should check if there is firstGroup waiting
                            // for transfer
                            // however I think this will not happen...since will send dummy events after 3 trials
                            if (SystemClock.getCurrentTimestamp() - lastGroupTime > eventsGroupInterval) {
                                // flush first group if eventsCache exist, there will be no child eventsCache
                                // always transfer first group
                                if (!firstGroup.isEmpty()) {
                                    mergeNoticeToFirstGroup(firstGroup, secondGroup);
                                }
                                transferEvents(firstGroup);
                                firstGroup = new ArrayList<>();
                            }
                        }
                    } else {
                        lastGroupTime = SystemClock.getCurrentTimestamp();
                        idle = 0;
                        if (firstGroup == null || firstGroup.isEmpty()) {
                            firstGroup = secondGroup;
                        } else {
                            mergeNoticeToFirstGroup(firstGroup, secondGroup);
                            transferEvents(firstGroup);
                            firstGroup = secondGroup;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("transfer error", e);
                }
            }
        }
    }

    private void transferEvents(final List<Event> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(event -> {
            try {
                String eventName = event.getName();
                // Dummy event's name is null
                if (eventName != null && !eventName.isEmpty()) {
                    List<String> notices = (List<String>) event.getPropertyValues().get("noticelist");
                    if (notices == null) {
                        addIncidentInfoToEvent(event, this.ruleEngine.getTriggerNoticeList(event.getId()));
                    } else {
                        addIncidentInfoToEvent(event, notices);
                    }
                } else {
                    if (this.ruleEngineBatchMode) {
                        // for sending trigger events
                        this.ruleEngine.addEvent(event);
                    }
                }

                if (this.enableSlot) {
                    this.slotEngine.add(event);
                }

                offlineWriter.addLog(event);
                RiskMonitorDataComputer.getInstance().addEvent(event);

                if (eventName != null) {
                    MetricsHelper.getInstance().addMetrics("events.transport.send.count", 1.0,
                            "name", eventName);
                }
            } catch (Exception e) {
                LOGGER.error("transfer event error", e);
                MetricsHelper.getInstance().addMetrics("events.transport.send.error.count", 1.0,
                        "name", event.getName());
            }
        });
    }

    private void addIncidentInfoToEvent(final Event e, final List<String> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        e.getPropertyValues().put("notices", String.join(",", notices));

        Map<String, MutableLong> sceneScore = new HashMap<>();
        Map<String, List<String>> sceneStrategies = new HashMap<>();
        Set<String> tags = new HashSet<>();
        for (String notice : notices) {
            if (!StrategyInfoCache.getInstance().containsStrategy(notice)) {
                continue;
            }
            tags.addAll(StrategyInfoCache.getInstance().getTags(notice));
            sceneStrategies.computeIfAbsent(StrategyInfoCache.getInstance().getCategory(notice), s -> new ArrayList<>()).add(notice);
        }
        sceneStrategies.forEach((scene, strategies) ->
                strategies.forEach(strategy -> sceneScore.computeIfAbsent(strategy, s -> new MutableLong(0)).add(
                        StrategyInfoCache.getInstance().getScore(strategy))));
        e.getPropertyValues().put("scores", sceneScore);
        e.getPropertyValues().put("strategies", sceneStrategies);
        e.getPropertyValues().put("tags", tags);
    }

    private void completeIncidentInfoToEvent(final Event event) {
        String noticeString = (String) event.getPropertyValues().get("notices");
        if (noticeString != null && !noticeString.isEmpty()) {
            String[] notices = noticeString.split(",");

            Map<String, MutableLong> sceneScore = new HashMap<>();
            Map<String, List<String>> sceneStrategies = new HashMap<>();
            Set<String> tags = new HashSet<>();
            for (String notice : notices) {
                if (!StrategyInfoCache.getInstance().containsStrategy(notice)) {
                    continue;
                }
                tags.addAll(StrategyInfoCache.getInstance().getTags(notice));
                sceneStrategies.computeIfAbsent(StrategyInfoCache.getInstance().getCategory(notice), s -> new ArrayList<>()).add(notice);
            }
            sceneStrategies.forEach((scene, strategies) ->
                    strategies.forEach(strategy -> sceneScore.computeIfAbsent(strategy, s -> new MutableLong(0)).add(
                            StrategyInfoCache.getInstance().getScore(strategy))));
            event.getPropertyValues().put("scores", sceneScore);
            event.getPropertyValues().put("strategies", sceneStrategies);
            event.getPropertyValues().put("tags", tags);
        }
    }

    private void mergeNoticeToFirstGroup(final List<Event> firstGroup, final List<Event> secondGroup) {
        if (this.ruleEngineBatchMode) {
            return;
        }
        Map<String, Event> eventMap = new HashMap<>();
        firstGroup.forEach(event -> {
            if (event.getName() != null && !event.getName().isEmpty()) {
                eventMap.put(event.getId(), event);
            }
        });

        ListIterator<Event> secondIterator = secondGroup.listIterator(secondGroup.size());
        while (secondIterator.hasPrevious()) {
            Event previous = secondIterator.previous();
            if (previous.getPid() != null && eventMap.containsKey(previous.getPid())) {
                List<String> noticeList = this.ruleEngine.getTriggerNoticeList(previous.getId());
                if (noticeList != null) {
                    Event parent = eventMap.get(previous.getPid());
                    List<String> parentNoticeList = this.ruleEngine.getTriggerNoticeList(parent.getId());
                    Set<String> newParentNotices = new HashSet<>();
                    if (parentNoticeList != null) {
                        newParentNotices.addAll(parentNoticeList);
                    }
                    newParentNotices.addAll(noticeList);
                    parent.getPropertyValues().put("noticelist", new ArrayList<>(newParentNotices));
                }
            }
        }

        ListIterator<Event> firstIterator = firstGroup.listIterator(firstGroup.size());
        while (firstIterator.hasPrevious()) {
            Event previous = firstIterator.previous();
            if (previous.getPid() != null && eventMap.containsKey(previous.getPid())) {
                List<String> noticeList = this.ruleEngine.getTriggerNoticeList(previous.getId());
                if (noticeList != null) {
                    Event parent = eventMap.get(previous.getPid());
                    List<String> parentNoticeList = this.ruleEngine.getTriggerNoticeList(parent.getId());
                    Set<String> newParentNotices = new HashSet<>();
                    if (parentNoticeList != null) {
                        newParentNotices.addAll(parentNoticeList);
                    }
                    newParentNotices.addAll(noticeList);
                    parent.getPropertyValues().put("noticelist", new ArrayList<>(newParentNotices));
                }
            }
        }
    }
}

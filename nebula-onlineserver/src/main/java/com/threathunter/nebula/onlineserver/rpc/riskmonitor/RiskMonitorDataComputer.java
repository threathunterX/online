package com.threathunter.nebula.onlineserver.rpc.riskmonitor;

import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.Event;
import com.threathunter.persistent.core.EventReadHelper;
import com.threathunter.persistent.core.io.BufferedRandomAccessFile;
import com.threathunter.persistent.core.util.PathHelper;
import com.threathunter.util.SystemClock;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 */
public class RiskMonitorDataComputer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiskMonitorDataComputer.class);
    private static final RiskMonitorDataComputer INSTANCE = new RiskMonitorDataComputer();

    private static final long ONE_MIN_MILLIS = 60 * 1000;
    private static final long ONE_HOUR_MILLIS = 60 * 60 * 1000;
    private static final int CACHE_LIMIT = 1000;

    private final BlockingDeque<Event> eventQueue = new LinkedBlockingDeque<>(
            CommonDynamicConfig.getInstance().getInt("nebula.offline.sender.capacity", 10000));
    private final ComputingWorker computingWorker = new ComputingWorker("risk events info computer");
    private volatile boolean running = false;

    private volatile LinkedList<Map<String, Object>> lastMinInfoList = null;
    private volatile LinkedList<Map<String, Object>> curMinInfoList = null;
    private volatile long lastMinStartMillis = -1;
    private volatile long curMinStartMillis = -1;
    private volatile long nextMinStartMillis = -1;

    private final int shard = CommonDynamicConfig.getInstance().getInt("nebula.persistent.log.shard", 16);
    private final String persistDir = CommonDynamicConfig.getInstance().getString("persist_path", PathHelper.getModulePath() + "/persistent");

    private static final String SERVER_LOCATION = CommonDynamicConfig.getInstance().getString("server_geo_location", "未知");

    private volatile TimestampIndexCache[] tsIndexCache = null;
    private final EventReadHelper readHelper = new EventReadHelper();

    private RiskMonitorDataComputer() {}

    public static RiskMonitorDataComputer getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (running) {
            LOGGER.warn("already start.");
            return;
        }
        running = true;

        this.reset();
        this.tsIndexCache = new TimestampIndexCache[shard];

        this.computingWorker.setDaemon(true);
        this.computingWorker.start();
    }

    public void stop() {
        if (!running) {
            LOGGER.warn("already stopped.");
            return;
        }

        try {
            this.computingWorker.join(1000);
        } catch (InterruptedException e) {
        }
        LOGGER.warn("stop risk events info computer success");
    }

    int dropCount = 0;
    public void addEvent(Event event) {
        if (!this.eventQueue.offer(event)) {
            if (dropCount % 1000 == 0) {
                LOGGER.warn("data:fatal:the incident event info computer queue is full");
            }
            dropCount++;
        }
    }

    public Object queryData(long fromTime, long endTime, long limit) {
        Map<String, Object> properties = new HashMap();

        Semaphore s = new Semaphore(0);
        properties.put("condition", s);
        AtomicReference<Object> holder = new AtomicReference<>();
        properties.put("result", holder);
        properties.put("from_time", fromTime);
        properties.put("end_time", endTime);
        properties.put("limit", limit);

        Event requestEvent = new Event("__all__", "__event_query", "", SystemClock.getCurrentTimestamp(), 1.0, properties);
        requestEvent.setPropertyValues(properties);

        this.eventQueue.addFirst(requestEvent);

        try {
            if (!s.tryAcquire(1, TimeUnit.SECONDS)) {
                LOGGER.error("fail to get the last minute's incident events info");
                return null;
            } else {
                return holder.get();
            }
        } catch (InterruptedException e) {
            LOGGER.error("fail to get the last minute's incident events info");
            return null;
        }
    }

    // invoke in queue
    private void processEvent(Event event) throws IOException {
        if (event.getName().equals("__event_query")) {
            processQueryEvent(event);
            return;
        }
        cacheIncidentInfo(event);
    }

    private void processQueryEvent(Event event) throws IOException {
        Semaphore s = (Semaphore) event.getPropertyValues().get("condition");

        long fromTime = (long) event.getPropertyValues().get("from_time");
        long endTime = (long) event.getPropertyValues().get("end_time");
        int limit = ((Number) event.getPropertyValues().getOrDefault("limit", CACHE_LIMIT)).intValue();

        AtomicReference<Object> holder = (AtomicReference<Object>) event.getPropertyValues().get("result");
        holder.set(getIncidentInfoList(fromTime, endTime, limit));

        s.release(1);
    }

    private List<Map<String, Object>> getIncidentInfoList(long fromTime, long endTime, int limit) throws IOException {
        // from_time should not less than lastMinStartMillis
        // we think something error on the timestamp, so return null
        if (fromTime < this.lastMinStartMillis) {
            return null;
        }

        // from time need to meet the condition: fromTime >= lastMinStartMillis && fromTime < curMinStartMillis
        if (fromTime >= this.curMinStartMillis) {
            long rightCurStartMinMillis = SystemClock.getCurrentTimestamp() / ONE_MIN_MILLIS * ONE_MIN_MILLIS;
            // we haven't update for a period of time, the data is old
            if (rightCurStartMinMillis > this.curMinStartMillis) {
                reset();
            }
        }

        if (fromTime >= this.curMinStartMillis) {
            // after reset, our data is not old, if fromTime is still large,
            // we also something error on the timestamp, so return null
            return null;
        }

        // if lastMinStartMillis is less than 0, it means there is no data recorded.
        if (this.lastMinStartMillis <= 0) {
            return new ArrayList<>();
        }

        // start of last minute
        if (fromTime == this.lastMinStartMillis) {
            return new ArrayList<>(this.lastMinInfoList);
        }

        // fromTime is between lastMinStartMillis and curMinStartMills
        // 1. there is no more incident data other than that in the cached list, ie: less than CACHE_LIMIT
        // 2. if curMinStartMillis is the start of a new hour, directly return null
        if (this.lastMinInfoList.size() < 1000) {
            return new ArrayList<>();
        }
        if (curMinStartMillis / ONE_HOUR_MILLIS * ONE_HOUR_MILLIS > lastMinStartMillis / ONE_HOUR_MILLIS * ONE_HOUR_MILLIS) {
            return new ArrayList<>();
        }

        return getLastMinuteDataFromPersist(fromTime, endTime, limit);
    }

    @VisibleForTesting
    List<Map<String, Object>> getLastMinuteDataFromPersist(long fromTime, long endTime, int limit) throws IOException {
        long currentTimestamp = SystemClock.getCurrentTimestamp();
        int c = limit;

        List<Map<String, Object>> result = new ArrayList<>();

        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("timestamp", 0);
        propertyMap.put("notices", "");
        propertyMap.put("geo_city", "");

        for (int i = 0; i < shard; i++) {
            BufferedRandomAccessFile file = EventReadHelper.getBufferedRandomAccessFile(persistDir, currentTimestamp, i);
            long currentOffset;
            if (this.tsIndexCache[i] == null) {
                currentOffset = 0; // scan from 0
            } else {
                currentOffset = this.tsIndexCache[i].lastMinuteStartOffset;
            }
            while (currentOffset < file.length()) {
                if (c <= 0) {
                    return result;
                }
                currentOffset = readHelper.getEventData(file, currentOffset, "", "", fromTime, endTime, propertyMap);
                if (currentOffset < 0) { // error occur
                    break;
                }
                if (this.tsIndexCache[i] == null) {
                    this.tsIndexCache[i] = new TimestampIndexCache();
                    this.tsIndexCache[i].lastMinuteStartOffset = currentOffset;
                }
                if (this.tsIndexCache[i].lastMinuteLatestOffset < currentOffset) {
                    this.tsIndexCache[i].lastMinuteLatestOffset = currentOffset;
                }
                if (!((String) propertyMap.getOrDefault("notices", "")).isEmpty()) {
                    Map<String, Object> info = createIncidentInfo(propertyMap);
                    result.add(info);
                    c--;
                }
            }
        }
        // sort with timestamp
        Collections.sort(result, (map1, map2) -> {
            long ret = (Long) map1.get("timestamp") - (Long) map2.get("timestamp");
            return (ret < 0) ? -1 : ((ret > 0) ? 1 : 0 );
        });

        return result;
    }

    private Map<String, Object> createIncidentInfo(Map<String, Object> properties) {
        return createIncidentInfo((String) properties.get("geo_city"), SERVER_LOCATION, (String) properties.get("notices"), (Long) properties.get("timestamp"));
    }

    private Map<String, Object> createIncidentInfo(String from_city, String to_city, String noticesString, long timestamp) {
        Map<String, Object> info = new HashMap<>(8);
        info.put("from_city", from_city);
        info.put("to_city", to_city);
        info.put("timestamp", timestamp);

        String[] notices = noticesString.split(",");
        Boolean test = true;
        String maxCategory = "";
        long maxScore = 0;
        StrategyInfoCache cache = StrategyInfoCache.getInstance();
        for (String notice : notices) {
            if (cache.containsStrategy(notice)) {
                if (test && !cache.isTest(notice)) {
                    test = false;
                }
                long score = cache.getScore(notice);
                String category = cache.getCategory(notice);
                if (score > maxScore || maxCategory.isEmpty()) {
                    maxScore = score;
                    maxCategory = category;
                }
                if (score == maxScore) {
                    maxCategory = cache.getPriorCategory(maxCategory, category);
                }
            }
        }
        info.put("test", test);
        info.put("category", maxCategory);

        return info;
    }

    private void cacheIncidentInfo(Event event) {
        if (this.curMinInfoList.size() >= 1000) {
            return;
        }
        if (((String) event.getPropertyValues().getOrDefault("notices", "")).isEmpty()) {
            return;
        }
        // we need to check if jump to next minutes or hour
        if (this.curMinInfoList == null || event.getTimestamp() >= this.nextMinStartMillis) {
            this.reset();
        }

        String fromCity = (String) event.getPropertyValues().get("geo_city");
        String toCity = SERVER_LOCATION;
        String notices = (String) event.getPropertyValues().get("notices");
        this.curMinInfoList.add(createIncidentInfo(fromCity, toCity, notices, event.getTimestamp()));
    }

    // invoke only when events come, if no event comes, queue and lastMinStartMillis will not update,
    // then when query comes, there will no available data.
    private void reset() {
        long rightCurMinStartMillis = SystemClock.getCurrentTimestamp() / ONE_MIN_MILLIS * ONE_MIN_MILLIS;
        if (rightCurMinStartMillis == this.curMinStartMillis + ONE_MIN_MILLIS) {
            this.lastMinInfoList = this.curMinInfoList;
        } else {
            this.lastMinInfoList = new LinkedList<>();
        }
        this.curMinInfoList = new LinkedList<>();

        this.curMinStartMillis = rightCurMinStartMillis;
        this.lastMinStartMillis = this.curMinStartMillis - ONE_MIN_MILLIS;
        this.nextMinStartMillis = this.curMinStartMillis + ONE_MIN_MILLIS;

        if (this.curMinStartMillis / ONE_HOUR_MILLIS > this.lastMinStartMillis / ONE_HOUR_MILLIS) {
            // if an new hour, tsIndexCache will reconstruct again to present a new hour
            this.tsIndexCache = new TimestampIndexCache[shard];
        } else {
            // update cache
            if (this.tsIndexCache != null) {
                for (TimestampIndexCache cache : this.tsIndexCache) {
                    if (cache != null) {
                        cache.lastMinuteStartOffset = cache.lastMinuteLatestOffset;
                    }
                }
            }
        }
        this.dropCount = 0;
    }

    private static class TimestampIndexCache {
        long lastMinuteStartOffset = -1;
        long lastMinuteLatestOffset = -1;
    }

    private class ComputingWorker extends Thread {
        public ComputingWorker(String name) {
            super(name);
            this.setDaemon(true);
        }

        @Override
        public void run() {
            int idle = 0;
            while (running) {
                List<Event> events = new ArrayList<>();
                eventQueue.drainTo(events);

                if (events.isEmpty()) {
                    idle++;
                    if (idle >= 3) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                        }
                    }
                } else {
                    idle = 0;
                    for (Event event : events) {
                        try {
                            if (event.getName() == null || event.getName().isEmpty()) {
                                continue;
                            }
                            processEvent(event);
                        } catch (Exception e) {
                            LOGGER.error("error in processing event: ", e);
                        }
                    }
                }
            }
        }
    }

}

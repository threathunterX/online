package com.threathunter.nebula.onlineserver;

import com.threathunter.babel.rpc.ServiceContainer;
import com.threathunter.babel.rpc.impl.ServerContainerImpl;
import com.threathunter.bordercollie.slot.compute.SlotEngine;
import com.threathunter.bordercollie.slot.compute.SlotQuery;
import com.threathunter.bordercollie.slot.compute.cache.StorageType;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.greyhound.server.GreyhoundServer;
import com.threathunter.greyhound.server.engine.EngineConfiguration;
import com.threathunter.greyhound.server.utils.DaemonThread;
import com.threathunter.logging.logback.AppenderManager;
import com.threathunter.metrics.MetricsAgent;
import com.threathunter.model.*;
import com.threathunter.persistent.core.io.EventOfflineWriter;
import com.threathunter.variable.DimensionType;
import com.threathunter.nebula.onlineserver.notice.ProfileWriter;
import com.threathunter.nebula.onlineserver.rpc.*;
import com.threathunter.nebula.onlineserver.rpc.PersistentQueryService;
import com.threathunter.nebula.onlineserver.rpc.clickstream.ClickstreamQueryService;
import com.threathunter.nebula.onlineserver.rpc.riskmonitor.RiskMonitorDataComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by daisy on 16/9/1.
 */
public class OnlineServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlineServer.class);

    private volatile boolean running = false;
    private volatile HttpLogReceiver httpLogReceiver = null;
    private volatile MiscLogReceiver miscLogReceiver = null;
    private volatile ServiceContainer queryServer = null;
    private volatile GreyhoundServer greyhoundServer = null;
    private volatile SlotEngine slotEngine = null;
    private volatile ResourcePoller poller = null;

    private volatile OnlineEventTransport onlineEventTransport = null;

    private Set<DimensionType> slotDimensions;
    private Map<String, String> ruleEngineDimensions;
    private boolean ruleEngineBatchMode;
    private long rulesUpdateInterval;

    private Thread rulesUpdateThread;
    private Thread slotVariablesUpdateThread;

    public void start() {
        running = true;

        initSentry();
        initConfig();
        initMetrics();
        initMeta();
        initRuleEngine();

        boolean redisMode = false;
        if ("redis".equals(CommonDynamicConfig.getInstance().getString("babel_server"))) {
            redisMode = true;
        }

        LOGGER.warn("[nebula:online:server]start: starting profile writer");
        ProfileWriter.getInstance().start(redisMode);

        if (!ruleEngineBatchMode) {
            LOGGER.warn("[nebula:online:server]start: starting hour-slot variable computation");
            this.slotEngine = new SlotEngine(1, TimeUnit.HOURS, slotDimensions, getModuleSortedMetas("slot"), StorageType.BYTES_ARRAY);
            this.slotEngine.start();
            LOGGER.warn("[nebula:online:server]start: starting slot variables updater");
            this.slotVariablesUpdateThread = DaemonThread.INSTANCE.newThread(() -> updateSlotVariablesByInterval());
            this.slotVariablesUpdateThread.start();

            LOGGER.warn("[nebula:online:server]start: starting events persistent writer");
            EventOfflineWriter.getInstance().start();

            LOGGER.warn("[nebula:online:server]start: starting risk monitor query service");
            RiskMonitorDataComputer.getInstance().start();
        } else {
            LOGGER.warn("[nebula:online:server]start: disable hour-slot variable computation");
            LOGGER.warn("[nebula:online:server]start: disable events persistent module");
            LOGGER.warn("[nebula:online:server]start: disable risk monitor query service");
        }

        LOGGER.warn("[nebula:online:server]start: starting online events transport");
        this.onlineEventTransport = new OnlineEventTransport(this.greyhoundServer, this.slotEngine, EventOfflineWriter.getInstance(), this.ruleEngineBatchMode);
        this.onlineEventTransport.start();

        LOGGER.warn("[nebula:online:server]start: starting http log receiver");
        httpLogReceiver = new HttpLogReceiver(this.onlineEventTransport, redisMode);
        httpLogReceiver.start();

        LOGGER.warn("[nebula:online:server]start: starting misc log receiver");
        miscLogReceiver = new MiscLogReceiver(this.onlineEventTransport, redisMode);
        miscLogReceiver.start();

        LOGGER.warn("[nebula:online:server]start: starting variable query server");
        queryServer = new ServerContainerImpl();
        if (!this.ruleEngineBatchMode) {
            SlotQuery slotQuery = new SlotQuery(this.slotEngine);
            queryServer.addService(new OnlineSlotVariableQueryService(redisMode, slotQuery));
        }
        // TODO add realtime variable query
        queryServer.addService(new ClickstreamQueryService(redisMode));
        queryServer.addService(new RiskEventsInfoQueryService(redisMode));
        LOGGER.warn("[nebula:online:server]start: starting persistent query service.....");
        queryServer.addService(new PersistentQueryService());
        queryServer.start();

        LOGGER.warn("[nebula:online:server]start: starting rules updater");
        this.rulesUpdateThread = DaemonThread.INSTANCE.newThread(() -> updateRulesByInterval());
        this.rulesUpdateThread.start();

        LOGGER.warn("[nebula:online:server]start: nebula is started successfully, enjoy!");
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        LOGGER.warn("[nebula:online:server]stop: finish nebula processing");
    }

    public boolean isRunning() {
        return this.running;
    }

    public void stop() {
        running = false;

        LOGGER.warn("[nebula:online:server]stop: stopping event offline writer");
        EventOfflineWriter.getInstance().stop();

        LOGGER.warn("[nebula:online:server]stop: stopping online events transport");
        this.onlineEventTransport.stop();

        LOGGER.warn("[nebula:online:server]stop: stopping variable query server");
        if (queryServer != null) {
            queryServer.stop();
            queryServer = null;
        }

        LOGGER.warn("[nebula:online:server]stop: stopping resource poller");
        if (poller != null) {
            poller.stop();
            poller = null;
        }

        LOGGER.warn("[nebula:online:server]stop: stopping greyhound server");
        if (greyhoundServer != null) {
            greyhoundServer.stop();
            greyhoundServer = null;
        }

        LOGGER.warn("[nebula:online:server]stop: stopping http log reciever");
        if (httpLogReceiver != null) {
            httpLogReceiver.stop();
            httpLogReceiver = null;
        }
        LOGGER.warn("[nebula:online:server]stop: stopping misc log reciever");
        if (miscLogReceiver != null) {
            miscLogReceiver.stop();
            miscLogReceiver = null;
        }

        LOGGER.warn("[nebula:online:server]stop: stopping profile writer");
        ProfileWriter.getInstance().stop();

        if (!this.ruleEngineBatchMode) {
            LOGGER.warn("[nebula:online:server]stop: stopping slot variable computation");
            if (slotEngine != null) {
                this.slotEngine.stop();
                LOGGER.warn("[nebula:online:server]stop: stopping slot variables updater");
                try {
                    this.slotVariablesUpdateThread.interrupt();
                    this.slotVariablesUpdateThread.join(1000);
                } catch (Exception e) {
                    ;
                }
            }

            LOGGER.warn("[nebula:online:server]stop: stopping events persistent writer");
            EventOfflineWriter.getInstance().stop();

            LOGGER.warn("[nebula:online:server]stop: stopping risk monitor query service");
            RiskMonitorDataComputer.getInstance().stop();
        }

        try {
            LOGGER.warn("[nebula:online:server]stop: stopping rule updater");
            this.rulesUpdateThread.interrupt();
            this.rulesUpdateThread.join(1000);
        } catch (Exception e) {
            ;
        }

        LOGGER.warn("[nebula:online:server]stop: nebula online is stopped successfully");
    }

    private void initSentry() {
        if (CommonDynamicConfig.getInstance().getBoolean("sentry_enable", false)) {
            LOGGER.warn("[nebula:online:server]start: starting sentry logging");
            AppenderManager.getInstance().initSentryFromConfig();
        }
    }

    private void initConfig() {
        // initialize the config file
        LOGGER.warn("[nebula:online:server]start: init configuration");
        CommonDynamicConfig conf = CommonDynamicConfig.getInstance();
        // auth
        conf.addOverrideProperty("auth", "40eb336d9af8c9400069270c01e78f76");
        // highest priority: web config
        if (conf.containsKey("nebula.webconfig.url")) {
            conf.addConfigUrl(String.format("%s?auth=%s", conf.getString("nebula.webconfig.url"), conf.getString("auth")));
        }

        this.slotDimensions = new HashSet<>();
        for (String dimension : CommonDynamicConfig.getInstance().getString("nebula.online.slot.dimensions", "ip|uid|did|global|page|other").split("\\|")) {
            slotDimensions.add(DimensionType.getDimension(dimension));
        }
        this.ruleEngineDimensions = new HashMap<>();
        for (String dimension : CommonDynamicConfig.getInstance().getString("nebula.online.rule.dimensions", "ip|uid|did").split("\\|")) {
            DimensionType type = DimensionType.getDimension(dimension);
            this.ruleEngineDimensions.put(dimension, type.getFieldName());
        }
    }


    private void initMeta() {
        LOGGER.warn("[nebula:online:server]start: init internal class registry");
        // initialize the basic meta of events and variables

        PropertyCondition.init();
        PropertyMapping.init();
        PropertyReduction.init();
        VariableMeta.init();

        CommonDynamicConfig config = CommonDynamicConfig.getInstance();
        LOGGER.warn("[nebula:online:server]start: starting resource poller");
        // start poll meta data configuration
        ResourceConfiguration rc = new ResourceConfiguration();
        rc.setOnlineAuth(config.getString("auth"));
        rc.setOnlineEventsMetaUrl(config.getString("nebula.online.meta.events.url"));
        rc.setRealtimeVariablesMetaUrl(config.getString("nebula.online.meta.realtime.variables.url"));
        rc.setSlotVariablesMetaUrl(config.getString("nebula.online.meta.slot.variables.url"));
        rc.setOnlineStrategyInfoUrl(config.getString("nebula.online.meta.strategies.url"));

        rc.setLocalEventsMetaFile(config.getString("nebula.online.meta.events.local", "events.json"));
        rc.setLocalStrategyInfoFile(config.getString("nebula.online.meta.strategies.local", "strategies.json"));
        rc.setLocalRealtimeVariablesMetaFile(config.getString("nebula.online.meta.realtime.variables.local", "realtime_variables.json"));
        rc.setLocalSlotVariablesMetaFile(config.getString("nebula.online.meta.slot.variables.local", "slot_variables.json"));
        if (config.getBoolean("nebula.online.rule.batch.enable", false)) {
            rc.setOnlinePollingIntervalInMillis(300 * 1000);
        } else {
            rc.setOnlinePollingIntervalInMillis(30 * 1000);
        }

        poller = new ResourcePoller(rc);
        poller.start();
    }

    private void initMetrics() {
        LOGGER.warn("[nebula:online:server]start: init redis related config");
        MetricsAgent.getInstance().start();
    }

    private void initRuleEngine() {
        LOGGER.warn("[nebula:online:server]start: init rule engine");
        CommonDynamicConfig config = CommonDynamicConfig.getInstance();

        EngineConfiguration engineConfiguration = new EngineConfiguration();
        engineConfiguration.setRedisBabel(config.getString("babel_server", "redis").equals("redis"));

        HashSet<DimensionType> dimensions = new HashSet<>();
        for (String d : this.ruleEngineDimensions.keySet()) {
            dimensions.add(DimensionType.getDimension(d));
        }
        engineConfiguration.setEnableDimensions(dimensions);

        // set the expire time that in cache notices for sync into events.
        // if data is not huge that exceed the capacity of process, 3 times the group interval is ok.
        engineConfiguration.setNoticeSyncExpireSeconds(CommonDynamicConfig.getInstance().getInt("nebula.online.transport.group.interval", 3000) * 4);

        this.ruleEngineBatchMode = CommonDynamicConfig.getInstance().getBoolean("nebula.online.rule.batch.enable", false);
        if (this.ruleEngineBatchMode) {
            // batch mode dimensions, for slot compute
            String[] batchModeDimensions = config.getStringArray("nebula.online.rule.batch.dimensions");
            if (batchModeDimensions == null || batchModeDimensions.length <= 0) {
                batchModeDimensions = new String[] {"ip"};
            }
            HashSet<DimensionType> batchDimension = new HashSet<>();
            for (String d : batchModeDimensions) {
                batchDimension.add(DimensionType.getDimension(d));
            }
            engineConfiguration.setBatchModeDimensions(batchDimension);
            String[] batchNames = config.getStringArray("nebula.online.rule.batch.event.names");
            if (batchNames == null || batchNames.length <= 0) {
                batchNames = new String[] {"HTTP_DYNAMIC", "HTTP_STATIC"};
            }
            HashSet<String> batchNamesSet = new HashSet<>();
            for (String n : batchNames) {
                batchNamesSet.add(n);
            }
            engineConfiguration.setBatchModeEventNames(batchNamesSet);
            this.rulesUpdateInterval = 2 * 60 * 1000;
        } else {
            this.rulesUpdateInterval = 30 * 1000;
            engineConfiguration.setBatchModeDimensions(null);
        }

        engineConfiguration.setLoseTolerant(true);
        engineConfiguration.setSlidingWidthInSec(300);
        engineConfiguration.setSlotWidthInMin(1);

        engineConfiguration.setThreadCount(config.getInt("nebula.online.rule.engine.thread.count", 5));
        engineConfiguration.setShardCount(config.getInt("nebula.online.rule.engine.shard.count", 3));
        engineConfiguration.setCapacity(config.getInt("nebula.online.rule.engine.capacity", 10000));

        this.greyhoundServer = new GreyhoundServer(engineConfiguration, getModuleSortedMetas("realtime"));
        this.greyhoundServer.start();
        LOGGER.warn("[nebula:online:server]start: greyhound server started");
    }

    private void updateRulesByInterval() {
        while (running) {
            try {
                Thread.sleep(rulesUpdateInterval);
                LOGGER.warn("[nebula:online:server]runtime: try to update rules");
                this.greyhoundServer.updateRules(getModuleSortedMetas("realtime"));
            } catch (InterruptedException e) {
                ;
            } catch (Exception e) {
                LOGGER.error("[nebula:online:server]runtime: update rules error", e);
            }
        }
    }

    private void updateSlotVariablesByInterval() {
        while (running) {
            try {
                Thread.sleep(rulesUpdateInterval * 10);
                LOGGER.warn("[nebula:online:server]runtime: try to update slot variables");
                this.slotEngine.update(getModuleSortedMetas("slot"));
            } catch (InterruptedException e) {
                ;
            } catch (Exception e) {
                LOGGER.error("[nebula:online:server]runtime: update slot variables error", e);
            }
        }
    }

    private List<VariableMeta> getModuleSortedMetas(String module) {
        List<VariableMeta> ruleMetas = VariableMetaRegistry.getInstance().getAllVariableMetas().stream()
                .filter(m -> m.getModule().equals(module) || m.getModule().equals("base")).collect(Collectors.toList());
        ruleMetas.sort(Comparator.comparingInt(VariableMeta::getPriority));

        return ruleMetas;
    }

    public static void main(final String[] args) {
        OnlineServer server = new OnlineServer();
        server.start();
    }
}

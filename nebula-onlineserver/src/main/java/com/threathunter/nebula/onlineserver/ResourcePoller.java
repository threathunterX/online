package com.threathunter.nebula.onlineserver;

import com.threathunter.greyhound.server.utils.JsonFileReader;
import com.threathunter.greyhound.server.utils.StrategyInfoCache;
import com.threathunter.model.*;
import com.threathunter.persistent.core.CurrentHourPersistInfoRegister;
import com.threathunter.persistent.core.EventSchemaRegister;
import com.threathunter.variable.VariableMetaBuilder;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by lw on 2015/5/11.
 */
public class ResourcePoller {
    private static final Logger logger = LoggerFactory.getLogger(ResourcePoller.class);

    private volatile boolean running = false;
    private volatile Poller worker = null;

    private final ResourceConfiguration configuration;

    public ResourcePoller(ResourceConfiguration configuration) {
        this.configuration = configuration;
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        worker = new Poller("event variable poller");
        try {
            worker.doPolling();
        } catch (Exception ex) {
            logger.error("[nebula:online:resource] initial meet exception while polling, try loading from local files", ex);
            try {
                loadingFromLocal();
            } catch (Exception ex2) {
                logger.error("[nebula:online:resource] loading from local error", ex2);
                System.exit(0);
            }
        }
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        try {
            worker.interrupt();
            worker.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        worker = null;
    }

    private void loadingFromLocal() throws IOException {
        List<Map<String, Object>> events = JsonFileReader.getFromResourceFile(configuration.getLocalEventsMetaFile(), List.class);
        List<EventMeta> metas = new ArrayList<>();
        events.forEach(e -> metas.add(BaseEventMeta.from_json_object(e)));
        EventMetaRegistry.getInstance().updateEventMetas(metas);
        VariableMetaRegistry.getInstance().updateVariableMetas(new VariableMetaBuilder().buildFromJson(JsonFileReader.getFromResourceFile(configuration.getLocalRealtimeVariablesMetaFile(), List.class)));
        if (configuration.getLocalSlotVariablesMetaFile() != null && !configuration.getLocalSlotVariablesMetaFile().isEmpty()) {
            // update slot
            new VariableMetaBuilder().buildFromJson(JsonFileReader.getFromResourceFile(configuration.getLocalSlotVariablesMetaFile(), List.class));
        }
        StrategyInfoCache.getInstance().update(JsonFileReader.getFromResourceFile(configuration.getLocalStrategyInfoFile(), List.class));

        EventSchemaRegister.getInstance().update(EventMetaRegistry.getInstance().getAllEventMetas());
        CurrentHourPersistInfoRegister.getInstance().updateFromLogSchemaRegister();
    }

    class Poller extends Thread {
        Poller(String name) {
            super(name);
        }

        @Override
        public void run() {
            while(running) {
                try {
                    Thread.sleep(configuration.getOnlinePollingIntervalInMillis());
                } catch (InterruptedException e) {
                    continue;
                }

                try {
                    doPolling();
                } catch (Exception ex) {
                    logger.error("data:fatal:meet exception while polling", ex);
                }
            }
        }

        private String getRestfulResult(String url) throws Exception {
            InputStream inputStream = null;
            try {
                String authUrl;
                if (!url.contains("?")) {
                    authUrl = String.format("%s?auth=%s", url, configuration.getOnlineAuth());
                } else {
                    authUrl = String.format("%s&auth=%s", url, configuration.getOnlineAuth());
                }
                HttpURLConnection conn = getEventVariableHttpURLConnection(authUrl);
                inputStream = conn.getInputStream();
                return readInputStream(inputStream);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private HttpURLConnection getEventVariableHttpURLConnection(String curEventUrl) throws Exception {
            URL u = new URL(curEventUrl);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(1000 * 30);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("content-type", "application/json");
            conn.setDoOutput(false);
            conn.setDoInput(true);
            return conn;
        }

        void doPollingEvents() throws Exception {
            String eventUrl = configuration.getOnlineEventsMetaUrl();
            if (eventUrl == null || eventUrl.isEmpty()) {
                throw new Exception("events polling url is null");
            }
            String body = getRestfulResult(eventUrl);

            logger.debug("polling event and get {}", body);

            ObjectMapper mapper = new ObjectMapper();
            Map<Object, Object> response = mapper.reader(Map.class).readValue(body);
            List<Object> eventObjects = (List<Object>) response.get("values");
            List<EventMeta> eventMetas = new ArrayList<>();
            if (eventObjects != null) {
                eventObjects.forEach(o -> eventMetas.add(BaseEventMeta.from_json_object(o)));
            }
            EventMetaRegistry.getInstance().updateEventMetas(eventMetas);

        }

        void doPollingVariables() throws Exception {
            String realTimeVariableUrl = configuration.getRealtimeVariablesMetaUrl();
            if (realTimeVariableUrl == null || realTimeVariableUrl.isEmpty()) {
                throw new Exception("variables polling url is null.");
            }

            String realtimeBody = getRestfulResult(realTimeVariableUrl);

            logger.debug("polling realtime variables and get {}", realtimeBody);

            ObjectMapper mapper = new ObjectMapper();
            Map<Object, Object> realTimeResponse = mapper.reader(Map.class).readValue(realtimeBody);
            List<Object> realtimeObjects = (List<Object>) realTimeResponse.get("values");
            if (realtimeObjects != null) {
                VariableMetaRegistry.getInstance().updateVariableMetas(new VariableMetaBuilder().buildFromJson(realtimeObjects));
            }

            String slotVariableUrl = configuration.getSlotVariablesMetaUrl();
            if (slotVariableUrl != null && !slotVariableUrl.isEmpty()) {
                // polling slot together
                String slotBody = getRestfulResult(slotVariableUrl);
                logger.debug("polling slot variables and get {}", slotBody);
                Map<String, Object> slotResponse = mapper.reader(Map.class).readValue(slotBody);
                List<Object> slotVariableObjects = (List<Object>) slotResponse.get("values");
                if (slotVariableObjects != null) {
                    new VariableMetaBuilder().buildFromJson(slotVariableObjects);
                }
            }
            System.out.println("finish");
        }

        public void doPolling() throws Exception {
            doPollingEvents();
            doPollingVariables();
            doPollingStrategiesInfo();

            loadingPersistentSchemaFromEvent();
        }

        private void loadingPersistentSchemaFromEvent() {
            EventSchemaRegister.getInstance().update(EventMetaRegistry.getInstance().getAllEventMetas());
            CurrentHourPersistInfoRegister.getInstance().updateFromLogSchemaRegister();
        }

        private void doPollingStrategiesInfo() throws Exception {
            String strategyInfoUrl = configuration.getOnlineStrategyInfoUrl();
            if (strategyInfoUrl == null || strategyInfoUrl.isEmpty()) {
                throw new Exception("strategy information url is empty");
            }
            String body = getRestfulResult(strategyInfoUrl);
            ObjectMapper mapper = new ObjectMapper();
            Map<Object, Object> response = mapper.reader(Map.class).readValue(body);
            List<Map<String, Object>> strategyObjects = (List<Map<String, Object>>) response.get("values");

            StrategyInfoCache.getInstance().update(strategyObjects);

        }

        private String readInputStream(InputStream in) throws IOException {
            char[] buffer = new char[2000];
            StringBuilder result = new StringBuilder();
            InputStreamReader ins = new InputStreamReader(in);
            int readBytes;
            while ((readBytes = ins.read(buffer, 0, 2000)) >= 0) {
                 result.append(buffer, 0, readBytes);
            }
            return result.toString();
        }
    }
}

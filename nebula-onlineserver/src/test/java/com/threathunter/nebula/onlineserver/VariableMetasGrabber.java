package com.threathunter.nebula.onlineserver;

import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.nebula.testt.JsonFileReader;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 
 */
public class VariableMetasGrabber {

    public void filterVariables() throws Exception {
//        CommonDynamicConfig.getInstance().addOverrideProperty("auth", "40eb336d9af8c9400069270c01e78f76");
//        String variablePath = "http://172.16.10.65:9001/default/variables";

        List<Object> variableMetas = new ArrayList<>();
        JsonFileReader.getValuesFromFile("events_old.json", JsonFileReader.ClassType.LIST).forEach(vo -> {
            if (!filter(((Map) vo).get("name").toString())) {
                variableMetas.add(vo);
            }
        });
        Gson gson = new Gson();
        System.out.println(gson.toJson(variableMetas));
    }

    private boolean filter(String name) {
        if (name.startsWith("user__")) {
            return true;
        }
        if (name.startsWith("did__")) {
            return true;
        }
        if (name.startsWith("page__")) {
            return true;
        }
        return false;
    }

    private String getRestfulResult(String url) throws Exception {
        InputStream inputStream = null;
        try {
            String authUrl;
            if (!url.contains("?")) {
                authUrl = String.format("%s?auth=%s", url, CommonDynamicConfig.getInstance().getString("auth"));
            } else {
                authUrl = String.format("%s&auth=%s", url, CommonDynamicConfig.getInstance().getString("auth"));
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

package com.threathunter.nebula.onlineserver;

/**
 * Created by daisy on 17-11-15
 */
public class ResourceConfiguration {
    private long onlinePollingIntervalInMillis;
    private String realtimeVariablesMetaUrl;
    private String slotVariablesMetaUrl;
    private String onlineEventsMetaUrl;
    private String onlineStrategyInfoUrl;
    private String onlineAuth;

    private String localRealtimeVariablesMetaFile;
    private String localSlotVariablesMetaFile;
    private String localEventsMetaFile;
    private String localStrategyInfoFile;

    public long getOnlinePollingIntervalInMillis() {
        return onlinePollingIntervalInMillis;
    }

    public void setOnlinePollingIntervalInMillis(long onlinePollingIntervalInMillis) {
        this.onlinePollingIntervalInMillis = onlinePollingIntervalInMillis;
    }

    public String getRealtimeVariablesMetaUrl() {
        return realtimeVariablesMetaUrl;
    }

    public void setRealtimeVariablesMetaUrl(String realtimeVariablesMetaUrl) {
        this.realtimeVariablesMetaUrl = realtimeVariablesMetaUrl;
    }

    public String getOnlineEventsMetaUrl() {
        return onlineEventsMetaUrl;
    }

    public void setOnlineEventsMetaUrl(String onlineEventsMetaUrl) {
        this.onlineEventsMetaUrl = onlineEventsMetaUrl;
    }

    public String getOnlineStrategyInfoUrl() {
        return onlineStrategyInfoUrl;
    }

    public void setOnlineStrategyInfoUrl(String onlineStrategyInfoUrl) {
        this.onlineStrategyInfoUrl = onlineStrategyInfoUrl;
    }

    public String getOnlineAuth() {
        return onlineAuth;
    }

    public void setOnlineAuth(String onlineAuth) {
        this.onlineAuth = onlineAuth;
    }

    public String getLocalRealtimeVariablesMetaFile() {
        return localRealtimeVariablesMetaFile;
    }

    public void setLocalRealtimeVariablesMetaFile(String localRealtimeVariablesMetaFile) {
        this.localRealtimeVariablesMetaFile = localRealtimeVariablesMetaFile;
    }

    public String getLocalEventsMetaFile() {
        return localEventsMetaFile;
    }

    public void setLocalEventsMetaFile(String localEventsMetaFile) {
        this.localEventsMetaFile = localEventsMetaFile;
    }

    public String getLocalStrategyInfoFile() {
        return localStrategyInfoFile;
    }

    public void setLocalStrategyInfoFile(String localStrategyInfoFile) {
        this.localStrategyInfoFile = localStrategyInfoFile;
    }

    public String getSlotVariablesMetaUrl() {
        return slotVariablesMetaUrl;
    }

    public void setSlotVariablesMetaUrl(String slotVariablesMetaUrl) {
        this.slotVariablesMetaUrl = slotVariablesMetaUrl;
    }

    public String getLocalSlotVariablesMetaFile() {
        return localSlotVariablesMetaFile;
    }

    public void setLocalSlotVariablesMetaFile(String localSlotVariablesMetaFile) {
        this.localSlotVariablesMetaFile = localSlotVariablesMetaFile;
    }
}

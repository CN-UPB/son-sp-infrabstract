package sonata.kernel.placement.config;

import java.util.ArrayList;

public class PlacementConfig {

    public String pluginPath;
    public String placementPlugin;
    public ArrayList<PopResource> resources;
    public RestInterface restApi;
    public ArrayList<PerformanceThreshold> perfThreshold;

    public long monitorHistoryLimit;

    public long monitorIntervalMs;

    public String internalFunctionsPath;

	public ArrayList<PopResource> getResources() {
        return resources;
    }

    public ArrayList<PerformanceThreshold> getThreshold()
    {
        return perfThreshold;
    }

    public void setResources(ArrayList<PopResource> resources) {
        this.resources = resources;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public void setPluginPath(String pluginPath) {
        this.pluginPath = pluginPath;
    }

    public String getPlacementPlugin() {
        return placementPlugin;
    }

    public void setPlacementPlugin(String placementPlugin) {
        this.placementPlugin = placementPlugin;
    }

    public RestInterface getRestApi() {
        return restApi;
    }

    public void setRestApi(RestInterface restApi) {
        this.restApi = restApi;
    }

    public String getInternalFunctionsPath() {
        return internalFunctionsPath;
    }

    public void setInternalFunctionsPath(String internalFunctionsPath) {
        this.internalFunctionsPath = internalFunctionsPath;
    }

    public long getMonitorHistoryLimit() {
        return monitorHistoryLimit;
    }

    public void setMonitorHistoryLimit(long monitorHistoryLimit) {
        this.monitorHistoryLimit = monitorHistoryLimit;
    }

    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }

    public void setMonitorIntervalMs(long monitorIntervalMs) {
        this.monitorIntervalMs = monitorIntervalMs;
    }
}

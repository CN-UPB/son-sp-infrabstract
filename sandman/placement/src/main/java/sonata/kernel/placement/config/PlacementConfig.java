package sonata.kernel.placement.config;

import java.util.ArrayList;

/**
 * Configuration for the translator.
 * This class is used to map the YAML configuration file.
 */
public class PlacementConfig {

    /**
     * Overrides the log level specified in the log4j.properties file.
     * Possible values:
     *   ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF
     * (Only INFO and DEBUG are used in the Translator.)
     * The value is a direct mapping of the predefined org.apache.log4j.Level values.
     */
    public enum LogLevel {
        ALL, TRACE, DEBUG, INFO, WARN, ERROR, FATAL, OFF
    };
    public LogLevel logLevelOverride;
    /**
     * The path to the placement plugin class file.
     * The path can be relative to the application working directory or an absolute path.
     */
    public String pluginPath;
    /**
     * Class name of the placement plugin to use.
     * The name must include the package name.
     */
    public String placementPlugin;
    /**
     * An array of PopResource objects containing details about the usable Datacenter servers.
     */
    public ArrayList<PopResource> resources;
    /**
     * Details about the REST Api that serves the Gatekeeper API and control options.
     */
    public RestInterface restApi;
    /**
     * An array of PerformanceThreshold objects containing details about the known thresholds of the used vnfs.
     */
    public ArrayList<PerformanceThreshold> perfThreshold;
    /**
     * Number monitoring samples to store.
     * If more samples are being received the oldest one is removed accordingly.
     */
    public long monitorHistoryLimit;
    /**
     * The interval of sampling monitoring data per Vnf in Milliseconds.
     */
    public long monitorIntervalMs;
    /**
     * Path to Sonata descriptor files that are always usable by the placement plugin.
     * The path can be relative to the application working directory or an absolute path.
     */
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

    public LogLevel getLogLevelOverride() {
        return logLevelOverride;
    }

    public void setLogLevelOverride(LogLevel logLevelOverride) {
        this.logLevelOverride = logLevelOverride;
    }
}

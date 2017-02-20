package sonata.kernel.placement.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Finds, loads and stores the translator configuration file.
 */
public final class PlacementConfigLoader {

	final static Logger logger = Logger.getLogger(PlacementConfigLoader.class);

    /**
     * Default name of the configuration file
     */
    public final static String CONFIG_FILENAME = "placementd.yml";

    /**
     * Paths where to search for the configuration file.
     * Paths are checked in the order of definition inside the array.
     */
    public final static String[] CONFIG_FOLDERS = new String[]{"config","defaultConfig"};

    /**
     * Instance of the configuration object.
     */
    private static PlacementConfig config = null;

    /**
     * Loads, stores and returns the configuration file.
     * If the configuration file aready exists, simply returns it.
     * If no suitable file is found, a default object is created and returned.
     * @return translator configuration file
     */
    public static PlacementConfig loadPlacementConfig(){

        if(config!=null)
            return config;

    	logger.info("Placement config loader");

    	// Debug: print properties
    	//System.getProperties().list(System.out);

        for (String configFolder : CONFIG_FOLDERS){

            File configFile = new File(Paths.get(configFolder,CONFIG_FILENAME).toString());
            logger.debug("Check config file: "+ configFile.getPath());
            if (configFile.exists()) {

                config = mapConfigFile(configFile);

                if(config != null) {
                    logger.info("Use config file: "+ configFile.getPath());
                    break;
                }
            }
        }

        if(config == null)
            config = createDefaultConfig();

        if (config.getLogLevelOverride() != null) {
            logger.info("Override logging level with: "+config.getLogLevelOverride());
            switch(config.getLogLevelOverride()) {
                case ALL:
                    Logger.getRootLogger().setLevel(Level.ALL);
                    break;
                case TRACE:
                    Logger.getRootLogger().setLevel(Level.TRACE);
                    break;
                case DEBUG:
                    Logger.getRootLogger().setLevel(Level.DEBUG);
                    break;
                case INFO:
                    Logger.getRootLogger().setLevel(Level.INFO);
                    break;
                case WARN:
                    Logger.getRootLogger().setLevel(Level.WARN);
                    break;
                case ERROR:
                    Logger.getRootLogger().setLevel(Level.ERROR);
                    break;
                case FATAL:
                    Logger.getRootLogger().setLevel(Level.FATAL);
                    break;
                case OFF:
                    Logger.getRootLogger().setLevel(Level.OFF);
                    break;
            }
        } else
            logger.info("Logging level: "+Logger.getRootLogger().getLevel());



        return config;
    }

    /**
     * Reads a file and uses the YAML mapper to create a @PlacementConfig object.
     * Also overrides the logging level if specified in the config file.
     * @param configFile File to read in
     * @return null if the file does not exist or the mapping fails, else the mapped configuration object
     */
    public static PlacementConfig mapConfigFile(File configFile) {
        PlacementConfig config = null;
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SimpleModule module = new SimpleModule();

        try {

            StringBuilder bodyBuilder = new StringBuilder();
            BufferedReader in = null;

            in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(configFile), Charset.forName("UTF-8")));

            String line;
            while ((line = in.readLine()) != null)
                bodyBuilder.append(line + "\n\r");

            mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
            config = mapper.readValue(bodyBuilder.toString(), PlacementConfig.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    /**
     * Creates a very empty configuration object.
     * @return a very empty configuration object
     */
    public static PlacementConfig createDefaultConfig(){
    	logger.info("Create Default Config");
        PlacementConfig config = new PlacementConfig();
        config.pluginPath = "";
        config.placementPlugin = "";
        config.logLevelOverride = PlacementConfig.LogLevel.DEBUG;
        return config;
    }
}

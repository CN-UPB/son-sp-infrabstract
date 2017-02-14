package sonata.kernel.placement.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
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

        for (String configFolder : CONFIG_FOLDERS){

            File configFile = new File(Paths.get(configFolder,CONFIG_FILENAME).toString());
            logger.info("Config Folder is: "+ configFile.getPath());
            if (configFile.exists()) {

                config = mapConfigFile(configFile);

                if(config != null)
                    break;
            }
        }

        if(config == null)
            config = createDefaultConfig();

        return config;
    }

    /**
     * Reads a file and uses the YAML mapper to create a @PlacementConfig object.
     * @param configFile File to read in
     * @return null if the file does not exist or the mapping fails, else the mapped configuration object
     */
    public static PlacementConfig mapConfigFile(File configFile) {
    	logger.info("Map config file");
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
        return config;
    }
}

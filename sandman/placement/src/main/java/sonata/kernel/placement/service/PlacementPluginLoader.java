package sonata.kernel.placement.service;


import java.io.File;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.log4j.Logger;

public final class PlacementPluginLoader {
	
	final static Logger logger = Logger.getLogger(PlacementPluginLoader.class);
    public static Class placementPluginClass = DefaultPlacementPlugin.class;

    public static PlacementPlugin getNewPlacementPluginInstance(){
        logger.info("Creating placement Plugin");
        try {
            return (PlacementPlugin) placementPluginClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            placementPluginClass = DefaultPlacementPlugin.class;
            return new DefaultPlacementPlugin();
        }
    }

    public static void loadPlacementPlugin(String path, String pluginName){

        logger.info("Loading Placement Plugin");
    	logger.info("Plugin path: "+path);
    	logger.info("Plugin Name: "+pluginName);
    	placementPluginClass = DefaultPlacementPlugin.class;
        try {
            // Get ClassLoader for plugin folder
            File pluginFolder = new File(path);

            if (!pluginFolder.exists())
                return;

            URL[] urls = new URL[]{pluginFolder.toURI().toURL()};
            ClassLoader loader = new URLClassLoader(urls);

            // Get specified Class
            Class cls = loader.loadClass(pluginName);

            // Check if Class implements PlacementPlugin interface
            if(!PlacementPlugin.class.isAssignableFrom(cls)) {
                logger.error(cls.getName()+" is not an instance of the PlacementPlugin interface!");
                return;
            }

            // Check if Class can be instantiated
            if(Modifier.isInterface(cls.getModifiers()) || Modifier.isAbstract(cls.getModifiers())){
                logger.error(cls.getName()+" can not be instantiated!");
                return;
            }

            // If the Class misses an empty constructor instantiation will fail.
            placementPluginClass = cls;

        } catch (MalformedURLException | ClassNotFoundException e) {
            e.printStackTrace();
            placementPluginClass = DefaultPlacementPlugin.class;
        }
    }

}

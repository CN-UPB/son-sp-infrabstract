package sonata.kernel.placement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
//import com.sun.xml.internal.ws.api.wsdl.parser.ServiceDescriptor;

import org.apache.log4j.Logger;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.VimAdaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.Unit;
import sonata.kernel.VimAdaptor.commons.vnfd.UnitDeserializer;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;
import sonata.kernel.placement.config.PlacementConfig;
import sonata.kernel.placement.pd.PackageContentObject;
import sonata.kernel.placement.pd.PackageDescriptor;
import sonata.kernel.placement.pd.SonataPackage;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class Catalogue {

    final static Logger logger = Logger.getLogger(Catalogue.class);

    static public List<SonataPackage> packages = new ArrayList<SonataPackage>();

    // Maps Vnf name to VnfDescriptor
    static public Map<String,VnfDescriptor> functions = new HashMap<String,VnfDescriptor>();

    static public Map<String,VnfDescriptor> internalFunctions = new HashMap<String,VnfDescriptor>();

    public final static String[] INTERNAL_VNF_FOLDERS = new String[]{"sandman\\placement\\YAML\\internal", "sandman/placement/YAML/internal", "YAML/internal", "placement/YAML/internal"};

    static public int addPackage(SonataPackage sPackage){
        int newIndex = -1;
        int oldIndex = -1;
        for(int i=0; i<packages.size(); i++) {
        	SonataPackage  existPackage = packages.get(i);
            if(existPackage.descriptor.getName().equals(sPackage)) {
                oldIndex = i;
                break;
            }
        }
        // If already existing, replace!
        if(oldIndex != -1) {
            packages.remove(oldIndex);
            packages.add(oldIndex, sPackage);
            newIndex = oldIndex;
        }
        else { // Add new package
            packages.add(sPackage);
            newIndex = packages.size()-1;
        }
        // Replace or add Vnfs
        for(VnfDescriptor newVnf:sPackage.functions) {
            functions.put(newVnf.getName(),newVnf);
        }
        logger.info("Added package: "+sPackage.descriptor.getName());
        return newIndex;
    }
    
    static public DeployServiceData getPackagetoDeploy(int index) {
    	DeployServiceData data = new DeployServiceData();
    	List<VnfDescriptor> data1 = packages.get(index).functions;
    	for (int i = 0; i < data1.size(); i++) {
    		System.out.println("Data1 is "+data1.get(i));
    	}
    	List<ServiceDescriptor> data2 =  packages.get(index).services;
    	for (int j = 0; j < data2.size(); j++) {
    		System.out.println("Data1 is "+data2.get(j));
    	}
    	for (ServiceDescriptor sd:data2) {
    		data.setServiceDescriptor(sd);
    	}
    	//data.setServiceDescriptor(data2);
    	for (VnfDescriptor vnfd:data1) {
    		data.addVnfDescriptor(vnfd);
    	}
    	return data;
    }

    static public String getJsonPackageDescriptor(int index){
        String jsonData = null;
        ObjectMapper mapper = getJsonMapper();

       // String package_dir = packages.get(index);
        //String pack_dir = packages.get(index);
        PackageDescriptor desc = packages.get(index).descriptor;
        try {
            jsonData = mapper.writeValueAsString(desc);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return jsonData;
    }

    static public String getJsonPackageList(){
        String jsonList = null;

        List<PackageDescriptor> packageList = new ArrayList<PackageDescriptor>();
        for(SonataPackage p:packages)
            packageList.add(p.descriptor);

        ObjectMapper mapper  = getJsonMapper();
        try {
            jsonList = mapper.writeValueAsString(packageList);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
		
        return jsonList;
    }
    static protected ObjectMapper getJsonMapper(){
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        mapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        mapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Unit.class, new UnitDeserializer());
        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        return mapper;
    }

    static public void loadInternalFunctions(){
        logger.info("Load internal functions");

        List<String> internalFolders = new ArrayList<String>();

        // Add predefined path to path list
        internalFolders.addAll(Arrays.asList(INTERNAL_VNF_FOLDERS));

        // Add path from config to path list
        PlacementConfig config = PlacementConfigLoader.loadPlacementConfig();
        String configInternalFolder = config.getInternalFunctionsPath();
        if (configInternalFolder!=null)
            internalFolders.add(0, configInternalFolder);

        internalFunctions.clear();

        for(String folderPath:internalFolders) {
            File folder = new File(folderPath);
            if(!folder.exists())
                continue;

            // Get file list, only consider .yml files
            File[] files = folder.listFiles(new FilenameFilter(){
                @Override
                public boolean accept(File dir, String name) {
                    return name!=null && name.endsWith(".yml");
                }
            });

            if(files==null || files.length==0)
                continue;

            logger.info("Load files from "+folder.toString());

            // Try to load files as vnf descriptors and add them to the internal list
            for(File f:files) {
                VnfDescriptor vnfd = PackageLoader.fileToVnfDescriptor(f);

                if(vnfd==null)
                    continue;

                String vnfdName = vnfd.getName();
                if(vnfdName==null)
                    continue;
                //internalFunctions.put(vnfdName,vnfd);
                internalFunctions.put("vnf_" + vnfdName.split("-")[0], vnfd);

                logger.info("Add internal function "+vnfdName+" "+f);
            }
            logger.info("Loaded "+internalFunctions.size()+" functions");
            // Use only first non-empty folder
            break;
        }
    }
}

package sonata.kernel.placement.pd;

import sonata.kernel.VimAdaptor.commons.nsd.*;
import sonata.kernel.VimAdaptor.commons.vnfd.*;

import org.apache.log4j.Logger;
import sonata.kernel.placement.Catalogue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

public class Validation {

/*
 *  Additional rules
 *  service-descriptor:
 *  * connection_points
 *      * id structure: "ns:"<connection point name>
 *  * forwarding_graphs
 *      * fg_id starts with "ns:"
 *  * network_functions
 *      * vnf_id starts with "vnf_"
 *  * virtual_links
 *      * connection_points_reference
 *          * connections point structure: <vnf_id>":"<vnf connection point name> or "ns:"<connection point name>
 *
 *  function-descriptor:
 *  * connection_points
 *      * id structure: "vnf:"<connection point name>
 *  * name: arbitrary
 *  * virtual_deployment_units
 *      * connection_points
 *          * id structure: <vdu id>":"<connection point name>
 *  * virtual_links
 *      * connection_points_reference
 *          * connection point structure: <vdu_id>":"<connection point name> or "vnf:"<connection point name>
 *
 */

//TODO: add meaningful version check (function dependencies)

    /*Regex*/
    //static Pattern pattern_memory_units = Pattern.compile("B|kB|KiB|MB|MiB|GB|TB|TiB|PT|PiT");
    static Pattern pattern_md5 = Pattern.compile("^[A-Fa-f0-9]{32}$");

    // Package patterns
    static Pattern pattern_pkg_name = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_pkg_vendor = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_pkg_version = Pattern.compile("^[0-9\\-_.]+$");
    static Pattern pattern_pkg_descriptor_version = Pattern.compile("^[A-Za-z0-9\\-_.]+$");
    static Pattern pattern_pkg_content_name = Pattern.compile("^[A-Za-z0-9\\-_./]+$");
    static Pattern pattern_pkg_content_type = Pattern.compile("^[A-Za-z0-9\\-_./]+$");

    // Service patterns
    static Pattern pattern_serviced_name = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_serviced_vendor = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_serviced_version = Pattern.compile("^[0-9\\-_.]+$");
    static Pattern pattern_serviced_descriptor_version = Pattern.compile("^[A-Za-z0-9\\-_.]+$");

    static Pattern pattern_serviced_nf_version = Pattern.compile("^(== |>= |<= |!= )?[0-9\\-_.]+$");

    // VNFD patterns
    static Pattern pattern_vnfd_name = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_vnfd_vendor = Pattern.compile("^[a-z0-9\\-_.]+$");
    static Pattern pattern_vnfd_version = Pattern.compile("^[0-9\\-_.]+$");
    static Pattern pattern_vnfd_descriptor_version = Pattern.compile("^[A-Za-z0-9\\-_.]+$");



    final static Logger logger = Logger.getLogger(Validation.class);

    SonataPackage pkg;
    List<String> functions = new ArrayList<String>();

    protected ByteArrayOutputStream osError;
    protected PrintStream psError;
    protected ByteArrayOutputStream osFix;
    protected PrintStream psFix;
    public final List<Exception> exceptions = new ArrayList<Exception>();


    public Validation(SonataPackage pkg){
        this.pkg = pkg;
        this.osError = new ByteArrayOutputStream();
        this.psError = new PrintStream(osError);
        this.osFix = new ByteArrayOutputStream();
        this.psFix = new PrintStream(osFix);
    }

    protected void error(String errorMessage){
        logger.error(errorMessage);
        psError.println(errorMessage);
    }

    protected void fix(String fixMessage){
        logger.debug(fixMessage);
        psFix.println(fixMessage);
    }

    public String getValidationLog(){
        String log = "";
        if (exceptions.size() > 0) {
            log += "YAML descriptor parsing error:\n\n";
            for (Exception e : exceptions) {
                log += e.toString();
                log += "\n\n";
                log += e.getMessage();
                log += "\n\n";
            }
        }
        String errorLog = new String(osError.toByteArray(), StandardCharsets.UTF_8);
        String fixLog = new String(osFix.toByteArray(), StandardCharsets.UTF_8);
        if (errorLog.length()>0)
            log += "Other errors: \n\n"+errorLog+"\n\n";
        if (fixLog.length()>0)
            log += "Fix attempts: \n\n"+fixLog+"\n\n";
        return log;
    }

    public void validate(){
        // Check descriptor
        if(pkg.descriptor == null)
            error("missing descriptor");
        else
            validatePackage(pkg.descriptor);

        // Check for number of services
        if (pkg.services.size() == 0)
            error("(" + (pkg.descriptor!=null?pkg.descriptor.getName():"null") + ") invalid number of services: (" + pkg.services.size() + ") need at least one service");

        for (ServiceDescriptor service : pkg.services)
            if(service != null)
                validateService(service);
            else
                error("(" + (pkg.descriptor!=null?pkg.descriptor.getName():"null") + ") invalid YAML service descriptor");

        for (VnfDescriptor vnfd : pkg.functions)
            if(vnfd != null)
                validateFunction(vnfd);
            else
                error("(" + (pkg.descriptor!=null?pkg.descriptor.getName():"null") + ") invalid YAML function descriptor");
    }

    /* package descriptor */

    protected void validatePackage(PackageDescriptor pkgd){
        // Check name
        if(pkgd.getName() == null || !pattern_pkg_name.matcher(pkgd.getName()).matches())
            error("package ("+pkgd.getName()+") invalid name");
        // Check vendor
        if(pkgd.getVendor() == null || !pattern_pkg_vendor.matcher(pkgd.getVendor()).matches())
            error("package ("+pkgd.getName()+") invalid vendor ("+pkgd.getVendor()+")");
        // Check version
        if(pkgd.getVersion() == null || !pattern_pkg_version.matcher(pkgd.getVersion()).matches())
            error("package ("+pkgd.getName()+") invalid version ("+pkgd.getVersion()+")");
        // Check descriptor_version
        if(pkgd.getDescriptorVersion() == null || !pattern_pkg_descriptor_version.matcher(pkgd.getDescriptorVersion()).matches())
            error("package ("+pkgd.getName()+") invalid descriptor version ("+pkgd.getDescriptorVersion()+")");

        // schema optional
        // maintainer optional
        // description optional
        // md5 optional
        if(pkgd.getMd5()!=null && !pattern_md5.matcher(pkgd.getMd5()).matches())
            error("package ("+pkgd.getName()+") invalid md5 ("+pkgd.getMd5()+")");
        // signature optional
        // sealed optional
        // entry_service_template optional

        // package content optional
        if(pkgd.getPackageContent()!=null){
            List<PackageContentObject> contents = pkgd.getPackageContent();
            for(int i=0; i<contents.size(); i++){
                // Check name
                if(contents.get(i).getName() == null)
                    error("package ("+pkgd.getName()+") content name missing");
                else {
                    // Check name
                    if(!pattern_pkg_content_name.matcher(contents.get(i).getName()).matches())
                        error("package ("+pkgd.getName()+") content name invalid ("+contents.get(i).getName()+")");
                    // Check for double name
                    for(int j=i+1; j<contents.size(); j++)
                        if(contents.get(i).getName().equals(contents.get(j).getName()))
                            error("package ("+pkgd.getName()+") double content name ("+contents.get(i).getName()+")");
                }
                // Check content type
                if(contents.get(i).getContentType() == null || !pattern_pkg_content_type.matcher(contents.get(i).getContentType()).matches())
                    error("package ("+pkgd.getName()+") content ("+contents.get(i).getName()+") content type invalid ("+contents.get(i).getContentType()+")");
                // Check md5 optional
                if(contents.get(i).getMd5() != null && !pattern_md5.matcher(contents.get(i).getMd5()).matches())
                    error("package ("+pkgd.getName()+") content ("+contents.get(i).getName()+") invalid MD5 ("+contents.get(i).getMd5()+")");
                // Check sealed optional
            }
        }
        //TODO: package_resolvers optional
        //TODO: package_dependencies optional
        //TODO: artifact_dependencies optional
    }

    /* service descriptor */

    protected void validateService(ServiceDescriptor serviced){
        // Check for necessary simple attributes
            // Check name
            if(serviced.getName() == null || !pattern_serviced_name.matcher(serviced.getName()).matches())
                error("service ("+serviced.getName()+") invalid name ("+serviced.getName()+")");
            // Check descriptor version
            if(serviced.getDescriptorVersion() == null || !pattern_serviced_descriptor_version.matcher(serviced.getDescriptorVersion()).matches())
                error("service ("+serviced.getName()+") invalid descriptor version ("+serviced.getDescriptorVersion()+")");
            // Check vendor
            if(serviced.getVendor() == null || !pattern_serviced_vendor.matcher(serviced.getVendor()).matches())
                error("service ("+serviced.getName()+") invalid vendor ("+serviced.getVendor()+")");
            // Check version
            if(serviced.getVersion() == null || !pattern_serviced_version.matcher(serviced.getVersion()).matches())
                error("service ("+serviced.getName()+") invalid version ("+serviced.getVersion()+")");
            // $schema optional
            // author optional
            // description optional

        // Check network functions
        Map<String,VnfDescriptor> functionMap = new HashMap<String,VnfDescriptor>();
        List<NetworkFunction> nfunctions = serviced.getNetworkFunctions();
        if(nfunctions == null || nfunctions.size()==0)
            error("service ("+serviced.getName()+") invalid number of network functions: 0 need at least one network function");
        else {
            for(int i=0; i<nfunctions.size(); i++) {
                NetworkFunction nfunction = nfunctions.get(i);

                // Check id
                if(nfunction.getVnfId() == null)
                    error("service ("+serviced.getName()+") network function missing id");
                else {
                    // Check id
                    //FIXME: custom assumption
                    if(!nfunction.getVnfId().startsWith("vnf_"))
                        error("service ("+serviced.getName()+") invalid network function name ("+nfunction.getVnfId()+") does not start with \"vnf_\"");
                    // Check for double network function id
                    for(int j=i+1; j<nfunctions.size(); j++)
                        if(nfunction.getVnfId().equals(nfunctions.get(j).getVnfId()))
                            error("service ("+serviced.getName()+") double network function with id ("+nfunction.getVnfId()+")");
                    // Check for unconnected functions
                    if(serviced.getVirtualLinks() != null) {
                        int linkrefcount = 0;
                        for (VirtualLink vl : serviced.getVirtualLinks())
                            for (String cpref : vl.getConnectionPointsReference())
                                if (cpref != null && cpref.startsWith(nfunction.getVnfId()))
                                    linkrefcount++;
                        if(linkrefcount == 0)
                            error("service ("+serviced.getName()+") network function ("+nfunction.getVnfId()+") defined but never connected using a virtual link");
                    }
                }

                // Check version
                if(nfunction.getVnfVersion() == null)
                    error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") missing version");
                else
                    if(!pattern_serviced_nf_version.matcher(nfunction.getVnfVersion()).matches())
                        error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") invalid version ("+nfunction.getVnfVersion()+")");

                // Check name
                if(nfunction.getVnfName() == null)
                    error("service ("+serviced.getName()+") network function missing name");
                else {
                    if(!pattern_vnfd_name.matcher(nfunction.getVnfName()).matches())
                        error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") invalid name");
                    // Check if function descriptor exists in package or catalogue
                    boolean functionFound = false;
                    if(pkg.functions != null) {
                        for (VnfDescriptor vnfd : pkg.functions)
                            if(nfunction.getVnfName().equals(vnfd.getName())) {
                                functionFound = true;
                                if(nfunction.getVnfId() != null)
                                    functionMap.put(nfunction.getVnfId(), vnfd);
                            }
                    }
                    if(Catalogue.functions.containsKey(nfunction.getVnfName())) {
                        if(functionFound == false) { // Prefer function descriptor in package
                            functionMap.put(nfunction.getVnfId(), Catalogue.functions.get(nfunction.getVnfName()));
                        }
                        functionFound = true;
                    }
                    if(functionFound == false)
                        error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") no descriptor found in package or catalogue");
                }
                // Check vendor
                if(nfunction.getVnfVendor() == null)
                    error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") missing vendor");
                else
                if(!pattern_vnfd_vendor.matcher(nfunction.getVnfVendor()).matches())
                    error("service ("+serviced.getName()+") network function ("+nfunction.getVnfName()+") invalid vendor ("+nfunction.getVnfVendor()+")");
            }
        }

        // Check connection points (optional)
        if(serviced.getConnectionPoints() != null) {
            if (serviced.getConnectionPoints().size()<1)
                error("service ("+serviced.getName()+") connection point array need at least one connection point object");
            else {
                List<ConnectionPoint> cpoints = serviced.getConnectionPoints();
                for(int i=0; i<cpoints.size(); i++) {
                    // Check id
                    //FIXME: custom assumption
                    if(cpoints.get(i).getId() == null)
                        error("service ("+serviced.getName()+") connection point id missing");
                    else
                    if(!cpoints.get(i).getId().startsWith("ns:"))
                        error("service ("+serviced.getName()+") connection point ("+cpoints.get(i).getId()+") does not start with \"ns:\"");
                    // Check for double connection point
                    if(cpoints.get(i).getId() != null) {
                        for (int j = i + 1; j < cpoints.size(); j++)
                            if (cpoints.get(i).getId().equals(cpoints.get(j).getId()))
                                error("service ("+serviced.getName()+") double connection point ("+cpoints.get(i).getId()+")");
                    }
                    // Check type
                    if(cpoints.get(i).getType() == null)
                        error("service ("+serviced.getName()+") connection point ("+cpoints.get(i).getId()+") connection point type ("+cpoints.get(i).getType()+") invalid");
                    // virtual_link_reference (optional, deprecated)
                }
            }
        }

        //Check virtual links (optional)
        if(serviced.getVirtualLinks() != null) {
            List<VirtualLink> vlinks = serviced.getVirtualLinks();
            for(int i=0; i<vlinks.size(); i++){
                VirtualLink vlink = vlinks.get(i);
                // Check for case
                boolean referenceCase = (vlink.getVlName() != null | vlink.getVlGroup() != null | vlink.getVlVersion() != null);
                boolean descriptionCase = (vlink.getId() != null | vlink.getConnectivityType() != null | vlink.getConnectionPointsReference() != null);
                if( referenceCase & descriptionCase )
                    error("service ("+serviced.getName()+") virtual link should be either of reference or description case");
                if(referenceCase) {
                    // Check vl name
                    if(vlink.getVlName() == null)
                        error("service ("+serviced.getName()+") virtual link reference name is missing");
                    // Check vl group
                    if(vlink.getVlGroup() == null)
                        error("service ("+serviced.getName()+") virtual link reference ("+vlink.getVlName()+") group name is missing");
                    // Check vl version
                    if(vlink.getVlVersion() == null)
                        error("service ("+serviced.getName()+") virtual link reference ("+vlink.getVlName()+") version is missing");
                    // vl_description optional
                }
                if(descriptionCase) {
                    // Check id
                    if(vlink.getId() == null)
                        error("service ("+serviced.getName()+") virtual link id missing");
                    // Check for double id
                    if(vlink.getId() != null)
                        for(int j=i+1; j<vlinks.size(); j++)
                            if(vlink.getId().equals(vlinks.get(j).getId()))
                                error("service ("+serviced.getName()+") double virtual link ("+vlink.getId()+")");
                    // Check connectivity_type
                    if(vlink.getConnectivityType() == null)
                        error("service ("+serviced.getName()+") virtual link ("+vlink.getId()+") connectivity type invalid ("+vlink.getConnectivityType()+") should be \"E-Line\", \"E-Tree\" or \"E-LAN\"");
                    // Check connection_points_reference
                    List<String> cprefs = vlink.getConnectionPointsReference();
                    if(cprefs == null)
                        error("service ("+serviced.getName()+") virtual link ("+vlink.getId()+") connection points reference missing");
                    else {
                        for (int j = 0; j < cprefs.size(); j++) {
                            // Names are checked at definition (ns connection_points or vnf connection points)
                            // Check for double reference
                            for (int k = j + 1; k < cprefs.size(); k++)
                                if (cprefs.get(j).equals(cprefs.get(k)))
                                    error("service (" + serviced.getName() + ") virtual link (" + vlink.getId() + ") double connection points reference (" + cprefs.get(j) + ")");
                            // Check for unique connection point reference existence
                            int existenceCount = 0;
                            // Check if connection point is ns connection point
                            if (serviced.getConnectionPoints() != null)
                                for (ConnectionPoint nscp : serviced.getConnectionPoints())
                                    if (cprefs.get(j).equals(nscp.getId()))
                                        existenceCount++;
                            // Check if connection point is vnf connection point
                            String[] cprefParts = cprefs.get(j).split(":");
                            if(cprefParts.length == 2){
                                String vnfid = cprefParts[0];
                                String cpref = cprefParts[1];
                                if(functionMap.containsKey(vnfid)){
                                    VnfDescriptor vnfd = functionMap.get(vnfid);
                                    if(vnfd.getConnectionPoints() != null)
                                        for(ConnectionPoint vnfdcp : vnfd.getConnectionPoints()) {
                                            String vnfdid = vnfdcp.getId();
                                            if(vnfdid != null && vnfdid.equals("vnf:"+cpref))
                                                existenceCount++;
                                        }
                                } else
                                    if(!vnfid.equals("ns"))
                                        error("service (" + serviced.getName() + ") virtual link (" + vlink.getId() + ") connection points reference (" + cprefs.get(j) + ") references missing function");
                            }
                            if(existenceCount != 1)
                                error("service (" + serviced.getName() + ") virtual link ("+vlink.getId()+") ambiguous or non existing connection point reference: ("+cprefs.get(j)+") (found "+existenceCount+" references)");
                        }
                    }
                    // access optional
                    // external_access optional
                    // root_requirement optional
                    // leaf_requirement optional
                    // dhcp optional
                    // qos optional
                } /* description case */
            } /* for vlinks */
        } /* virtual link check */

        //Check forwarding graph (optional)
        if(serviced.getForwardingGraphs() != null){
            ArrayList<ForwardingGraph> fgraphs = serviced.getForwardingGraphs();
            for(int i=0; i<fgraphs.size(); i++) {
                // Check id
                if(fgraphs.get(i).getFgId() == null)
                    error("service (" + serviced.getName() + ") forwarding graph id is missing");
                else {
                    // Check for double id
                    for(int j=i+1; j<fgraphs.size(); j++)
                        if(fgraphs.get(i).getFgId().equals(fgraphs.get(j)))
                            error("service (" + serviced.getName() + ") double forwarding graph id ("+fgraphs.get(i)+")");
                }
                // TODO: Add check for reference case
                // descriptive case
                // number_of_endpoints optional
                // number_of_virtual_links optional
                // constituent_virtual_links optional
                // constituent_vnfs optional
                // constituent_services optional
                // network_forwarding_paths optional
            }
        }

        //TODO: Check lifecycle events (optional)
        //TODO: Check vnf_dependency (optional)
        //TODO: Check sevices_dependency (optional)
        //TODO: Check monitoring_parameters (optional)
        //TODO: Check auto_scale_policy (optional)
        //TODO: Check service_specific_managers (optional)

    }

    /* function descriptor */

    protected void validateFunction(VnfDescriptor vnfd){
        // Check for necessary simple attributes
            // Check name
            if(vnfd.getName() == null || !pattern_vnfd_name.matcher(vnfd.getName()).matches())
                error("vnf ("+vnfd.getName()+") invalid name ("+vnfd.getName()+")");
            // Check descriptor version
            if(vnfd.getDescriptorVersion() == null || !pattern_vnfd_descriptor_version.matcher(vnfd.getDescriptorVersion()).matches())
                error("vnf ("+vnfd.getName()+") invalid descriptor version ("+vnfd.getDescriptorVersion()+")");
            // Check vendor
            if(vnfd.getVendor() == null || !pattern_vnfd_vendor.matcher(vnfd.getVendor()).matches())
                error("vnf ("+vnfd.getName()+") invalid vendor ("+vnfd.getVendor()+")");
            // Check version
            if(vnfd.getVersion() == null || !pattern_vnfd_version.matcher(vnfd.getVersion()).matches())
                error("vnf ("+vnfd.getName()+") invalid version ("+vnfd.getVersion()+")");
            // $schema optional
            // author optional
            // description optional

        // Check connection points (optional)
        List<ConnectionPoint> cpoints = vnfd.getConnectionPoints();
        if (cpoints != null) {
            // Check for number of connection points
            if(cpoints.size()==0)
                error("vnf ("+vnfd.getName()+") number of connection points: ("+cpoints.size()+") if \"connection_points\" section is specified it has to have at least one item");
            for (int i = 0; i < cpoints.size(); i++) {
                // Check name
                //FIXME: custom assumption
                if(cpoints.get(i).getId() == null)
                    error("vnf ("+vnfd.getName() + ") connection point id missing");
                else
                    if(!cpoints.get(i).getId().startsWith("vnf:"))
                        error("vnf ("+vnfd.getName() + ") connection point ("+ cpoints.get(i).getId() + ") invalid name, does not start with \"vnf:\"");
                // Check double connection point
                if(cpoints.get(i).getId() != null)
                    for(int j=i+1;j<cpoints.size(); j++)
                        if(cpoints.get(i).getId().equals(cpoints.get(j).getId()))
                            error("vnf ("+vnfd.getName() + ") double connection point ("+ cpoints.get(i).getId() + ") ");
                // Check type
                if (cpoints.get(i).getType() == null || !cpoints.get(i).getType().toString().equals("interface"))
                    error("vnf ("+vnfd.getName() + ") connection point (" + cpoints.get(i).getId() + ") invalid type: (" + cpoints.get(i).getType() + ") allowed types: \"interface\"");
                //TODO: virtual link reference (optional, deprecated)
            }
        }

        // Check virtual deployment units
        List<VirtualDeploymentUnit> vdus = vnfd.getVirtualDeploymentUnits();
        // Check number of virtual deployment units
        if(vdus == null || vdus.size()==0)
                error("vnf ("+vnfd.getName()+") invalid number of virtual deployment units: (0),  need at least one virtual deployment unit");
        else
            for(int i=0; i<vdus.size(); i++){
                VirtualDeploymentUnit vdu = vdus.get(i);
                // Check for double id
                if(vdu.getId() == null)
                    error("vnf ("+vnfd.getName()+") virtual deployment unit missing id");
                else
                    for(int j=i+1; j<vdus.size(); j++)
                        if(vdu.getId().equals(vdus.get(j).getId()))
                            error("vnf ("+vnfd.getName()+") double virtual deployment unit ("+vdu.getId()+")");
                // vm_image optional
                // vm_image_format optional
                // vm_image_md5 optional
                if(vdu.getVmImageMd5() != null && !pattern_md5.matcher(vdu.getVmImageMd5()).matches())
                    error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") vm_image_md5 not valid ("+vdu.getVmImageMd5()+")");
                // Check connection points (optional)
                List<ConnectionPoint> vducpoints = vdu.getConnectionPoints();
                for(int j=0; j<vducpoints.size(); j++){
                    // Check id
                    //FIXME: custom assumption
                    if(vducpoints.get(j).getId() == null)
                        error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") connection point missing id");
                    else {
                        if (!vducpoints.get(j).getId().startsWith(vdu.getId() + ":"))
                            error("vnf (" + vnfd.getName() + ") virtual deployment unit (" + vdu.getId() + ") connection point (" + vducpoints.get(j).getId() + ") does not start with the vdu id + ':'");
                        // Check for double id
                        for (int k = j + 1; k < vducpoints.size(); k++) {
                            if (vducpoints.get(j).getId().equals(vducpoints.get(k).getId()))
                                error("vnf (" + vnfd.getName() + ") virtual deployment unit (" + vdu.getId() + ") double connection point: (" + vducpoints.get(j).getId() + ")");
                        }
                    }
                    // Check for type
                    if(vducpoints.get(j).getType() == null || !vducpoints.get(j).getType().toString().equals("interface"))
                        error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") connection point ("+vducpoints.get(j).getId()+") invalid type: ("+vducpoints.get(j).getType()+") allowed types: \"interface\"");
                    //TODO: virtual link reference (optional, deprecated)
                }
                // Check resource requirements
                ResourceRequirements rr = vdu.getResourceRequirements();
                if (rr == null)
                    error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") missing resource requirements");
                else {
                    // Check cpu
                    if(rr.getCpu() == null)
                        error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: missing cpu");
                    else {
                        // Check vcpus
                        if(!(rr.getCpu().getVcpus()>0))
                            error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: cpu - vcpus too small: ("+rr.getCpu().getVcpus()+")");
                        // cpu_support_accelerator optional
                    }
                    // Check memory
                    if(rr.getMemory() == null)
                        error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: missing memory");
                    else {
                        // Check size
                        if(!(rr.getMemory().getSize()>0))
                            error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: memory - size too small ("+rr.getMemory().getSize()+")");
                        // size_unit optional, if not null then checked by object mapping
                        //FIXME: custom assumption: size unit is expected not to be null
                        if(rr.getMemory().getSizeUnit() == null)
                            error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: memory - size unit missing");
                        // large_pages_required optional
                        // numa_allocation_policy optional
                    }
                    // Check storage
                    if(rr.getStorage() == null)
                        error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: missing storage");
                    else {
                        // Check size
                        if(!(rr.getStorage().getSize()>0))
                            error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: storage - size too small ("+rr.getStorage().getSize()+")");
                        //FIXME: custom assumption: size unit is expected not to be null
                        if(rr.getStorage().getSizeUnit() == null)
                            error("vnf ("+vnfd.getName()+") virtual deployment unit ("+vdu.getId()+") resource requirements: storage - size unit missing");
                    }
                    //TODO: network optional
                    //TODO: pcie optional
                    //TODO: hypervisor_parameters optional
                    //TODO: vswitch_capabilities optional
                }
                //TODO: Check monitoring parameters (optional)
                //TODO: Check scale in out parameters (optional)
            }

        // Check virtual links
        List<VnfVirtualLink> vlinks = vnfd.getVirtualLinks();
        if(vlinks != null)
            for(int i=0; i<vlinks.size(); i++) {
                VnfVirtualLink vlink = vlinks.get(i);
                // name arbitrary?
                // Check for double id
                for (int j=i+1; j<vlinks.size(); j++) {
                    if(vlink.getId().equals(vlinks.get(j).getId()))
                        error("vnf ("+vnfd.getName()+") double virtual link: ("+vlink.getId()+")");
                }
                // Check connectivity type (only "interface")
                if (vlink.getConnectivityType()==null || !(vlink.getConnectivityType().toString().equals("E-Line") || vlink.getConnectivityType().toString().equals("E-Tree") || vlink.getConnectivityType().toString().equals("E-LAN")))
                    error("vnf ("+vnfd.getName()+") virtual link ("+vlink.getId()+") connectivity type invalid: ("+vlink.getConnectivityType()+") allowed values: \"E-Line\",\"E-Tree\",\"E-LAN\"");
                // Check connection point references
                List<String> cprefs = vlink.getConnectionPointsReference();
                // Check for number of connection point references
                if(cprefs.size()<2)
                    error("vnf ("+vnfd.getName()+") virtual link ("+vlink.getId()+") number of connection point references smaller than two");
                if(cprefs.size()>2 && "E-Line".equals(vlink.getConnectivityType()))
                    error("vnf ("+vnfd.getName()+") virtual link ("+vlink.getId()+") number of connection point references for \"E-Line\" connection type greater than two");
                for (int j=0; j<cprefs.size(); j++) {
                    // Names are checked at definition (function connection_points or vdu connection points)
                    // Check for double reference
                    for (int k=j+1; k<cprefs.size(); k++) {
                        if(cprefs.get(j).equals(cprefs.get(k)))
                            error("vnf ("+vnfd.getName()+") virtual link ("+vlink.getId()+") double connection point reference: ("+cprefs.get(j)+")");
                    }
                    // Check for unique connection point reference existence
                    int existenceCount = 0;
                    // Check if connection point is vnf connection point
                    for(ConnectionPoint cpoint : vnfd.getConnectionPoints()){
                        if(cprefs.get(j).equals(cpoint.getId()))
                            existenceCount++;
                    }
                    // Check if connection point is vdu connection point
                    for(VirtualDeploymentUnit vdu : vnfd.getVirtualDeploymentUnits()){
                        for(ConnectionPoint vduCpoint : vdu.getConnectionPoints()){
                            if(cprefs.get(j).equals(vduCpoint.getId()))
                                existenceCount++;
                        }
                    }
                    if(existenceCount != 1)
                        error("vnf ("+vnfd.getName()+") virtual link ("+vlink.getId()+") ambiguous or non existing connection point reference: ("+cprefs.get(j)+")");

                }
                // access optional
                // external_access optional
                // root_requirement optional
                // leaf_requirement optional
                // dhcp optional
                // qos optional
            }
        //TODO: Check VNF Lifecycle Evens section (optional)
        //TODO: Check deployment flavours (optional)
        //TODO: Check monitoring rules (optional)
    }

    /**
     * Checks custom assumptions only and fixes deviations
     */
    public void fixCustomAssumptions() {

        for (ServiceDescriptor sd : pkg.services) {
            if (sd == null)
                continue;

            if(sd.getConnectionPoints() != null){
                for (ConnectionPoint scp : sd.getConnectionPoints()) {
                    if(scp.getId() != null) {

                        String oldId = scp.getId();
                        String newId;
                        if(!oldId.startsWith("ns:"))
                            newId = "ns:"+oldId.replace(':','-');
                        else if(countChar(oldId,':')>1)
                            newId = replaceSkip(oldId, ':', '-', 1);
                        else
                            continue;
                        fix("Fix networks service connection point id "+oldId);

                        // Add "ns:" prefix and fix occurences of old id
                        // Connection Point
                        scp.setId(newId);
                        // Forwarding Graphs
                        if(sd.getForwardingGraphs() != null)
                            for(ForwardingGraph fg : sd.getForwardingGraphs()) {
                                if(fg.getNetworkForwardingPaths() != null)
                                    for(NetworkForwardingPath nfp : fg.getNetworkForwardingPaths()) {
                                        if(nfp.getConnectionPoints() != null)
                                            for(ConnectionPointReference cpr : nfp.getConnectionPoints()) {
                                                if(oldId.equals(cpr.getConnectionPointRef())) {
                                                    String old = cpr.getConnectionPointRef();
                                                    cpr.setConnectionPointRef(newId);
                                                    fix("--> forwarding graph connection point reference \""+old+"\" replaced with \""+newId+"\"");
                                                }
                                            }
                                    }
                            }
                        // Virtual Links
                        if(sd.getVirtualLinks() != null)
                            for(VirtualLink vl : sd.getVirtualLinks()) {
                                if(vl.getConnectionPointsReference() != null)
                                    for(int i=0; i<vl.getConnectionPointsReference().size(); i++) {
                                        if (oldId.equals(vl.getConnectionPointsReference().get(i))) {
                                            String old = vl.getConnectionPointsReference().get(i);
                                            vl.getConnectionPointsReference().add(i,newId);
                                            vl.getConnectionPointsReference().remove(i+1);
                                            fix("--> virtual link connection point reference \""+old+"\" replaced with \""+newId+"\"");
                                        }
                                    }
                            }
                    }
                }
            }
            // Forwarding graphs id
            if(sd.getForwardingGraphs() != null)
                for(ForwardingGraph fg : sd.getForwardingGraphs()) {
                    if(fg.getFgId() != null) {
                        String oldId = fg.getFgId();
                        String newId;
                        if(!oldId.startsWith("ns:"))
                            newId = "ns:" + fg.getFgId().replace(':','-');
                        else if(countChar(oldId, ':') > 1)
                            newId = replaceSkip(oldId, ':', '-', 1);
                        else
                            continue;
                        fix("Fix networks service forward graph id \""+oldId+"\" replaced with \""+newId+"\"");
                        fg.setFgId(newId);
                    }
                }
            // Network Functions id
            if(sd.getNetworkFunctions() != null)
                for(NetworkFunction nf: sd.getNetworkFunctions()) {
                    if(nf.getVnfId() != null) {

                        String oldId = nf.getVnfId();
                        String newId;

                        if(!oldId.startsWith("vnf_"))
                            newId = "vnf_"+oldId.replace('_','-');
                        else if(countChar(oldId, '_') > 1)
                            newId = replaceSkip(oldId, '_', '-', 1);
                        else
                            continue;

                        fix("Fix networks service network function id "+oldId);
                        // Fix initial defintion
                        nf.setVnfId(newId);
                        // Fix Forwarding graph
                        if(sd.getForwardingGraphs() != null)
                            for(ForwardingGraph fg: sd.getForwardingGraphs()) {
                                // Constituent vnfs
                                if(fg.getConstituentVnfs() != null) {
                                    for(int i=0; i<fg.getConstituentVnfs().size(); i++) {
                                        if(oldId.equals(fg.getConstituentVnfs().get(i))) {
                                            String old = fg.getConstituentVnfs().get(i);
                                            fg.getConstituentVnfs().add(i, newId);
                                            fg.getConstituentVnfs().remove(i+1);
                                            fix("--> forwarding graph constituent vnf \""+old+"\" replaced with \""+newId+"\"");
                                        }
                                    }
                                }
                                // Forwarding path
                                if(fg.getNetworkForwardingPaths() != null) {
                                    for(NetworkForwardingPath nfp: fg.getNetworkForwardingPaths()) {
                                        if(nfp.getConnectionPoints() != null)
                                            for(ConnectionPointReference cpr: nfp.getConnectionPoints()) {
                                                if(cpr.getConnectionPointRef().startsWith(oldId)) {
                                                    String old = cpr.getConnectionPointRef();
                                                    cpr.setConnectionPointRef(newId + cpr.getConnectionPointRef().substring(oldId.length()));
                                                    fix("--> forwarding graph forwarding path connection point reference \""+old+"\" replaced with \""+cpr.getConnectionPointRef()+"\"");
                                                }
                                            }
                                    }
                                }
                            }
                        // Fix virtual links
                        if(sd.getVirtualLinks() != null)
                            for(VirtualLink vl: sd.getVirtualLinks()) {
                                if(vl.getConnectionPointsReference() != null)
                                    for(int i=0; i<vl.getConnectionPointsReference().size(); i++) {
                                        if(vl.getConnectionPointsReference().get(i) != null && vl.getConnectionPointsReference().get(i).startsWith(oldId)) {
                                            String old = vl.getConnectionPointsReference().get(i);
                                            vl.getConnectionPointsReference().add(i, newId + vl.getConnectionPointsReference().get(i).substring(oldId.length()));
                                            vl.getConnectionPointsReference().remove(i+1);
                                            fix("--> virtual link \""+old+"\" replace with \""+vl.getConnectionPointsReference().get(i)+"\"");
                                        }
                                    }
                            }
                    }
                }
        }

        for(VnfDescriptor vnfd: pkg.functions) {
            if(vnfd == null)
                continue;

            if(vnfd.getConnectionPoints() != null)
                for(ConnectionPoint vnfcp: vnfd.getConnectionPoints()) {
                    if(vnfcp.getId() != null) {

                        String oldId = vnfcp.getId();
                        String newId;

                        if(!oldId.startsWith("vnf:"))
                            newId = "vnf:"+vnfcp.getId().replace(':','-');
                        else if(countChar(oldId, ':') > 1)
                            newId = replaceSkip(oldId, ':', '-', 1);
                        else
                            continue;

                        fix("Fix vnf connection point id "+oldId);
                        // Fix initial defintion
                        vnfcp.setId(newId);

                        // Fix virtual links
                        if(vnfd.getVirtualLinks() != null)
                            for(VnfVirtualLink vl: vnfd.getVirtualLinks()) {
                                if(vl.getConnectionPointsReference() != null)
                                    for(int i=0; i<vl.getConnectionPointsReference().size(); i++) {
                                        if(vl.getConnectionPointsReference().get(i) != null && vl.getConnectionPointsReference().get(i).equals(oldId)) {
                                            String old = vl.getConnectionPointsReference().get(i);
                                            vl.getConnectionPointsReference().add(i, newId);
                                            vl.getConnectionPointsReference().remove(i);
                                            fix("--> virtual link connection point reference \""+old+"\" replaced with \""+vl.getConnectionPointsReference().get(i)+"\"");
                                        }
                                    }
                            }
                    }
                }

            if(vnfd.getVirtualDeploymentUnits() != null)
                for(VirtualDeploymentUnit vdu: vnfd.getVirtualDeploymentUnits()) {
                    if(vdu.getId() == null)
                        continue;
                    if(vdu.getConnectionPoints() != null) {
                        for (ConnectionPoint vducp : vdu.getConnectionPoints()) {
                            if (vducp.getId() != null) {

                                String oldId = vducp.getId();
                                String newId;

                                if (!oldId.startsWith(vdu.getId()))
                                    newId = vdu.getId() + ":" + vducp.getId().replace(':', '-');
                                else if (countChar(oldId, ':') > 1)
                                    newId = replaceSkip(oldId, ':', '-', 1);
                                else
                                    continue;

                                fix("Fix vnf vdu connection point id " + oldId);
                                // Fix initial definition
                                vducp.setId(newId);
                                // Fix virtual links
                                if (vnfd.getVirtualLinks() != null)
                                    for (VnfVirtualLink vl : vnfd.getVirtualLinks()) {
                                        if (vl.getConnectionPointsReference() != null)
                                            for (int i = 0; i < vl.getConnectionPointsReference().size(); i++) {
                                                if (vl.getConnectionPointsReference().get(i) != null && vl.getConnectionPointsReference().get(i).equals(oldId)) {
                                                    String old = vl.getConnectionPointsReference().get(i);
                                                    vl.getConnectionPointsReference().add(i, newId);
                                                    vl.getConnectionPointsReference().remove(i + 1);
                                                    fix("--> virtual deployment unit virtual link connection point reference \"" + old + "\" replaced with \"" + vl.getConnectionPointsReference().get(i) + "\"");
                                                }
                                            }
                                    }
                            }
                        }
                    }
                    if (vdu.getResourceRequirements() != null) {
                        ResourceRequirements rr = vdu.getResourceRequirements();
                        if (rr.getCpu() == null) {
                            rr.setCpu(defaultCpu);
                            fix("Set cpu resource to default 1 vcpu for vnf "+vnfd.getName());
                        } else {
                            if (rr.getCpu().getVcpus() <= 0) {
                                rr.getCpu().setVcpus(1);
                                fix("Set cpu resource to default 1 vcpu for vnf "+vnfd.getName());
                            }
                        }
                        if (rr.getMemory() == null) {
                            rr.setMemory(defaultMemory);
                            fix("Set memory resource to default 100 MB for vnf "+vnfd.getName());
                        } else {
                            if (rr.getMemory().getSize() <= 0) {
                                rr.getMemory().setSize(100);
                                fix("Set memory resource to default 100 MB for vnf "+vnfd.getName());
                            }
                            if (rr.getMemory().getSizeUnit() == null) {
                                rr.getMemory().setSizeUnit(Unit.MemoryUnit.MB);
                                fix("Set memory resource to default size unit MB for vnf "+vnfd.getName());
                            }
                        }
                        if (rr.getStorage() == null) {
                            rr.setStorage(defaultStorage);
                            fix("Set storage resource to default 500 MB for vnf "+vnfd.getName());
                        } else {
                            if (rr.getStorage().getSize() <= 0) {
                                rr.getStorage().setSize(100);
                                fix("Set memory resource to default 500 MB for vnf "+vnfd.getName());
                            }
                            if (rr.getStorage().getSizeUnit() == null) {
                                rr.getStorage().setSizeUnit(Unit.MemoryUnit.MB);
                                fix("Set memory resource to default size unit MB for vnf "+vnfd.getName());
                            }
                        }
                    } else {
                        vdu.setResourceRequirements(defaultRR);
                        fix("Set vdu resources to default 1 vcpu, 100 MB memory, 500 MB storage for vnf "+vnfd.getName());
                    }
                }

        }
    }

    /**
     * Count occurences of character in String
     * @param input
     * @param c
     * @return
     */
    public static int countChar(String input, char c){
        int count = 0;
        for(int i=0; i<input.length(); i++)
            if(input.charAt(i) == c)
                count++;
        return count;
    }

    /**
     * Replaces characters in String but skips certain amount of occurences
     * @param input
     * @param c
     * @param skip
     * @return
     */
    public static String replaceSkip(String input, char c, char r, int skip) {
        StringBuilder output = new StringBuilder(input);
        int current = input.indexOf(c, 0);
        while(current < input.length() && current != -1) {
            if (skip > 0)
                skip--;
            else {
                output.setCharAt(current, r);
                current++;
            }
            current = input.indexOf(c, current);
        }
        return output.toString();
    }

    static ResourceRequirements defaultRR = new ResourceRequirements();
    static Cpu defaultCpu = new Cpu();
    static Memory defaultMemory = new Memory();
    static Storage defaultStorage = new Storage();

    static {
        defaultCpu.setVcpus(1);
        defaultRR.setCpu(defaultCpu);
        defaultMemory.setSize(100);
        defaultMemory.setSizeUnit(Unit.MemoryUnit.MB);
        defaultRR.setMemory(defaultMemory);
        defaultStorage.setSize(500);
        defaultStorage.setSizeUnit(Unit.MemoryUnit.MB);
        defaultRR.setStorage(defaultStorage);
    }
}

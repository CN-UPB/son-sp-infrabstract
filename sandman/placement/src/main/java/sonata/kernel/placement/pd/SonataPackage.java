package sonata.kernel.placement.pd;


import sonata.kernel.VimAdaptor.commons.nsd.ServiceDescriptor;
import sonata.kernel.VimAdaptor.commons.vnfd.VnfDescriptor;

import java.util.ArrayList;
import java.util.List;

public class SonataPackage {

    public PackageDescriptor descriptor;
    public final List<ServiceDescriptor> services;
    public final List<VnfDescriptor> functions;
    public final Validation validation;

    public SonataPackage(){
        this.services = new ArrayList<ServiceDescriptor>();
        this.functions = new ArrayList<VnfDescriptor>();
        this.validation = new Validation(this);
    }

    public SonataPackage(PackageDescriptor descriptor){
        this.descriptor = descriptor;
        this.services = new ArrayList<ServiceDescriptor>();
        this.functions = new ArrayList<VnfDescriptor>();
        this.validation = new Validation(this);
    }

}

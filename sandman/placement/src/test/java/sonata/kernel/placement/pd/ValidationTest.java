package sonata.kernel.placement.pd;

import org.junit.Test;
import sonata.kernel.VimAdaptor.commons.DeployServiceData;
import sonata.kernel.placement.PackageLoader;

import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ValidationTest {
    @Test
    public void validate() throws Exception {
        SonataPackage brokenPkg = PackageLoader.loadSonataPackageFromDisk(Paths.get("YAML","packages","sonata-demo-totally-broken","sonata-demo-totally-broken.son").toString());
        brokenPkg.validation.validate();
        String validationLog = brokenPkg.validation.getValidationLog();
        assert validationLog != null && validationLog.length()>0 : "Validation log should contain several lines.";
    }

    @Test
    public void fix() {
        SonataPackage brokenPkg = PackageLoader.loadSonataPackageFromDisk(Paths.get("YAML","packages","mild-broken","mild-broken.son").toString());
        brokenPkg.validation.validate();
        brokenPkg.validation.fixCustomAssumptions();
        String validationLog = brokenPkg.validation.getValidationLog();
        Validation v = new Validation(brokenPkg);
        v.validate();
        String validationLogFixed = v.getValidationLog();
        assert validationLogFixed.length() < validationLog.length();
    }

}
package sonata.kernel.placement.pd;

/**
 * Class to map PackageResolver objects from Sonata package descriptors.
 */
public class PackageResolver {

    private String name;
    private String credentials;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }
}

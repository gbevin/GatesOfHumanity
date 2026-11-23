package gatesofhumanity;

import gatesofhumanity.elements.Failure;
import rife.config.RifeConfig;
import rife.engine.Server;
import rife.ioc.HierarchicalProperties;

/**
 * The site as it's deployed, as a war, from the uber jar or as a native executable.
 */
public class AppSiteDeployed extends AppSite {
    @Override
    public void setup() {
        super.setup();
        // visitors get a page of the site instead of one with the stack trace
        RifeConfig.engine().setPrettyEngineExceptions(false);
        exception(new Failure(this));
    }

    public static void main(String[] args) {
        new Server()
            .port(port(HierarchicalProperties.createSystemInstance()))
            .staticUberJarResourceBase("webapp")
            .start(new AppSiteDeployed());
    }
}

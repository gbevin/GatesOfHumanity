package gatesofhumanity;

import rife.engine.Site;
import rife.ioc.HierarchicalProperties;
import rife.nativeapp.NativeApp;
import rife.nativeapp.SiteProvider;

/**
 * Makes the site inside an app, where RIFE2 runs it without a server, see rife.nativeapp: with
 * what its pages say and show otherwise in an app than on the web, unless the app said so
 * itself.
 */
public class AppSiteProvider implements SiteProvider {
    private static void unlessSaid(HierarchicalProperties properties, String name, String value) {
        if (!properties.contains(name)) {
            properties.put(name, value);
        }
    }

    @Override
    public Site site(HierarchicalProperties properties) {
        // which app the pages are in, for what they do there themselves, see app.js
        var platform = properties.getValueString(NativeApp.PLATFORM_PROPERTY, "");
        properties.put("gatesofhumanity.app", platform);
        // the pages say that the saved people stay in the app, rather than in a browser
        unlessSaid(properties, "gatesofhumanity.keptIn", "this app");
        // the pages are as wide as the screen, a column between two edges is how a web page looks on an iPad
        properties.put("gatesofhumanity.columnWidth", "none");
        // what the app has inside it that the site alone doesn't, under the license of the GraalVM that made it
        var gpl = "the GNU General Public License, version 2 with the Classpath Exception.";
        properties.put("gatesofhumanity.credit", "This app has the pages inside it as native code, made with " +
            (System.getProperty("java.vendor.version", "").startsWith("Oracle")
                ? "Oracle GraalVM under the GraalVM Free Terms and Conditions, with the class library of OpenJDK, licensed under " + gpl
                : "GraalVM Community Edition, with the class library of OpenJDK, both licensed under " + gpl));
        unlessSaid(properties, "gatesofhumanity.widgetsCredit", "The widgets of this app are drawn with SwiftDraw by Simon Whitty, licensed under the zlib License.");
        // a shared link opens the chart on the web, the address of the app means nothing elsewhere
        properties.put("gatesofhumanity.shareRootUrl", "https://hd.uwyn.com");
        // the welcome says how a part of a chart is chosen where the app is: with a finger, or on a Mac with a mouse
        properties.put("gatesofhumanity.tap", "mac".equals(platform) ? "Click" : "Tap");
        // the About page says which version and build of the app this is
        if (properties.contains(NativeApp.VERSION_PROPERTY)) {
            properties.put("gatesofhumanity.version", properties.getValueString(NativeApp.VERSION_PROPERTY));
        }
        if (properties.contains(NativeApp.BUILD_NUMBER_PROPERTY)) {
            properties.put("gatesofhumanity.build", properties.getValueString(NativeApp.BUILD_NUMBER_PROPERTY));
        }
        return new AppSiteEmbedded();
    }
}

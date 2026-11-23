package gatesofhumanity;

import rife.bld.BuildCommand;
import rife.bld.extension.NativeAppProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static rife.bld.dependencies.Repository.*;
import static rife.bld.dependencies.Scope.*;
import static rife.bld.operations.TemplateType.*;

public class AppBuild extends NativeAppProject {
    public AppBuild() {
        pkg = "gatesofhumanity";
        name = "gatesofhumanity";
        mainClass = "gatesofhumanity.AppSite";
        uberJarMainClass = "gatesofhumanity.AppSiteDeployed";
        version = version(2,0,0);
        javaRelease = 25;

        downloadSources = true;
        autoDownloadPurge = true;

        repositories = List.of(MAVEN_CENTRAL, CENTRAL_SNAPSHOTS);
        scope(compile)
            .include(dependency("com.uwyn.rife2", "rife2", version(1,11,0,"SNAPSHOT")));
        scope(test)
            .include(dependency("org.jsoup", "jsoup", version(1,24,1)))
            .include(dependency("org.junit.jupiter", "junit-jupiter", version(6,1,3)))
            .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(6,1,3)))
            .include(dependency("jakarta.servlet", "jakarta.servlet-api", version(6,0,0)))
            .include(dependency("org.graalvm.polyglot", "polyglot", version(25,0,5)))
            .include(dependency("org.graalvm.js", "js-language", version(25,0,5)));
        scope(standalone)
            .include(dependency("org.eclipse.jetty.ee10", "jetty-ee10", version(12,1,15)))
            .include(dependency("org.eclipse.jetty.ee10", "jetty-ee10-servlet", version(12,1,15)))
            .include(dependency("org.slf4j", "slf4j-simple", version(2,0,20)));

        precompileOperation()
            .templateTypes(HTML, SVG, TXT, XML);

        // The site as a native library inside the apps, which ./bld app-library builds and
        // ./bld app-check checks, see the README of ios, mac, vision and android: what it's
        // called there, the addresses that it's asked for on the JVM before each build and that
        // a check compares the library's answers for, whose content doesn't depend on the
        // moment, and what goes through its pages.
        nativeApp()
            .libraryName("gatesofhumanity")
            // beside build, which a clean build empties: a library takes minutes to build
            .destinationDirectory(new File(workDirectory(), "native"))
            .bundleIdentifier("com.uwyn.gatesofhumanity.site")
            .privacyManifest(new File(workDirectory(), "ios/app/Library-PrivacyInfo.xcprivacy"))
            .frameworkVersion(appSetting("MARKETING_VERSION"), appSetting("CURRENT_PROJECT_VERSION"))
            .addresses(
                "/robots.txt",
                "/wheel/image/202403011000/1850147",
                "/wheel/image/birth/197506211430/2643743",
                "/wheel/image/birth/197506211430/2643743?name=Test",
                "/wheel/image/birth-transit/197506211430/2643743/202403011000/1850147",
                "/wheel/image/connection/194709210130/4975802/195502241915/5391959",
                "/wheel/image/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147",
                "/wheel/image/180001011200/2643743",
                "/wheel/image/219912312359/2643743",
                "/wheel/image/197513011430",
                "/wheel/explore/birth/197506211430/2643743",
                "/wheel/explore/birth-transit/197506211430/2643743/202403011000/1850147",
                "/wheel/explore/202403011000/1850147",
                "/wheel/explore/connection/194709210130/4975802/195502241915/5391959",
                "/wheel/explore/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147",
                "/wheel/volume/birth/197506211430/2643743",
                "/wheel/volume/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147",
                "/range/explore/d/202403011000/1850147",
                "/range/table/202403011000/2643743",
                "/range/table/d/202403011000/2643743",
                "/cities?query=z%C3%BCr",
                "/cities?query=50%25%2F",
                "/cities/zone?zone=Europe%2FBrussels",
                "/cities/zone?zone=Asia%2FCalcutta",
                "/cycle/saturn-return/195502241915/5391959/2643743",
                "/cycle/chiron-return/218502241915/5391959",
                "/widget/transits/systemSmall/202611151700/ahead",
                "/widget/transits/systemSmall/202611151700",
                "/widget/transits/systemSmall/202608080000",
                "/widget/transits/systemMedium/202608080000",
                "/widget/transits/accessoryRectangular/202608080000",
                "/widget/transits/accessoryInline/202611151700",
                "/widget/transits/accessoryCircular/202611151700",
                "/widget/bodygraph/systemSmall/202611151700/ahead",
                "/widget/bodygraph/systemSmall/202611151700",
                "/widget/bodygraph/systemMedium/202611151700",
                "/widget/bodygraph/systemLarge/202611151700",
                "/widget/bodygraph/watch/202611151700",
                "/widget/transits/watch/202611151700",
                "/widget/person/198803140625/watch/202611151700",
                "/widget/person/198803140625/systemSmall/202611151700/ahead?name=Nora",
                "/widget/person/198803140625/systemMedium/202611151700?name=Nora",
                "/widget/person/198803140625/systemLarge/202611151700?name=Nora",
                "/widget/born/198803140725/2800866",
                "/css/main.css",
                "/js/main.js",
                "/js/app.js",
                "/js/awesomplete.min.js",
                "/images/favicon.svg",
                "/images/apple-touch-icon.png",
                "/images/og-image.png")
            // the pages have something new in them each time, so they're gone through rather than compared
            .walk("gatesofhumanity.AppWalk");

        // The Android app around that library, which ./bld app-android puts together, see
        // android/README.md. With GATES_PROBED set it's one whose pages a program on this Mac
        // can look into, which isn't one to hand to anyone. Its key is kept outside the
        // project: Android only installs a newer build over an older one when the same key
        // signed both, so losing it means that the app, and the people saved in it, have to be
        // removed first.
        var probed = System.getenv("GATES_PROBED") != null;
        var key = new File(System.getProperty("user.home"), ".android/gatesofhumanity.jks");
        nativeApp().androidApp()
            .manifest(new File(workDirectory(), "android/app/AndroidManifest.xml"))
            .resources(new File(workDirectory(), "android/app/res"))
            .sources(new File(workDirectory(), "android/app/java"))
            // AndroidSVG draws the widgets, which the site describes in SVG: android/build.sh fetches it
            .libraries(new File(workDirectory(), "android/build/lib/androidsvg-1.4.jar"))
            .version(appSetting("MARKETING_VERSION"), Long.parseLong(appSetting("CURRENT_PROJECT_VERSION")))
            .debuggable(probed)
            .key(key, new File(key.getPath() + ".pass"), "gates")
            .apk(new File(workDirectory(), "android/build/Gates" + (probed ? "-probed" : "") + ".apk"));
    }

    // the version and the build number of every app are those of the iOS app
    private String appSetting(String name) {
        try {
            var set = Pattern.compile(name + ": \"?([^\"\\s]+)").matcher(Files.readString(new File(workDirectory(), "ios/project.yml").toPath()));
            if (!set.find()) {
                throw new IllegalStateException("ios/project.yml has no " + name);
            }
            return set.group(1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BuildCommand(value = "generate-ephemeris", summary = "Regenerates the ephemeris tables from JPL Horizons, optionally only for the bodies named",
        description = """
            Regenerates the ephemeris tables in src/main/resources/ephemeris from JPL Horizons.

            Without arguments every body is regenerated. Name bodies to regenerate only those,
            for instance: ./bld generate-ephemeris moon true_node

            The bodies are sun, moon, mercury, venus, mars, jupiter, saturn, uranus, neptune,
            pluto, true_node and chiron. The Horizons answers are cached in .cache/horizons.""")
    public void generateEphemeris()
    throws Exception {
        var names = new ArrayList<>(arguments());
        arguments().clear();
        new EphemerisGenerator(new File(workDirectory(), ".cache/horizons"), new File(srcMainResourcesDirectory(), "ephemeris"))
            .generate(names);
    }

    @BuildCommand(value = "generate-cities", summary = "Regenerates the cities for the location search from GeoNames")
    public void generateCities()
    throws Exception {
        new CitiesGenerator(new File(workDirectory(), ".cache/geonames"), new File(srcMainResourcesDirectory(), "cities.tsv.gz"))
            .generate();
    }

    private File nativeImageConfiguration() {
        return new File(srcMainResourcesDirectory(), "META-INF/native-image/gatesofhumanity");
    }

    @BuildCommand(value = "native-config", summary = "Records the native image configuration by visiting every route under the GraalVM tracing agent")
    public void nativeConfig()
    throws Exception {
        uberjar();
        new NativeImage(new File(buildDistDirectory(), uberJarFileName())).recordConfiguration(nativeImageConfiguration());
    }

    @BuildCommand(value = "native", summary = "Builds a native executable with GraalVM native-image into build/dist")
    public void nativeExecutable()
    throws Exception {
        uberjar();
        new NativeImage(new File(buildDistDirectory(), uberJarFileName())).build(nativeExecutableFile());
    }

    @BuildCommand(value = "native-check", summary = "Starts the native executable and checks that every route answers as expected")
    public void nativeCheck()
    throws Exception {
        NativeImage.check(nativeExecutableFile());
    }

    private File nativeExecutableFile() {
        return new File(buildDistDirectory(), archiveBaseName() + (NativeImage.WINDOWS ? ".exe" : ""));
    }

    public static void main(String[] args) {
        new AppBuild().start(args);
    }
}

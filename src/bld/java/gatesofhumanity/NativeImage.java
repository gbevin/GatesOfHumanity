package gatesofhumanity;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds a GraalVM native executable of the application, and records the configuration it needs
 * by running the application under the GraalVM tracing agent.
 */
public class NativeImage {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build();

    record Visit(String path, int status) {
    }

    // a page or image of every kind, with every outcome, so the tracing agent sees all the
    // reflection and resources the application uses
    static final List<Visit> VISITS = List.of(
        new Visit("/", 302),
        new Visit("/robots.txt", 200),
        new Visit("/sitemap.xml", 200),
        new Visit("/wheel", 200),
        new Visit("/wheel/birth", 200),
        new Visit("/wheel/birth-transit", 200),
        new Visit("/wheel/connection", 200),
        new Visit("/wheel/connection-transit", 200),
        new Visit("/range", 200),
        new Visit("/range/d", 200),
        new Visit("/wheel?datetime=garbage", 200),
        new Visit("/wheel/birth?datetime=1955-02-24T19:15", 200),
        new Visit("/range?datetime=2024-03-01T10:00", 200),
        new Visit("/wheel/image/202403011000/1850147", 200),
        new Visit("/wheel/image/202403011000", 200),
        new Visit("/wheel/image/birth/197506211430/2643743", 200),
        new Visit("/wheel/image/birth/197506211430/2643743?name=Test", 200),
        new Visit("/wheel/image/birth-transit", 200),
        new Visit("/wheel/image/birth-transit/197506211430/2643743/202403011000/1850147", 200),
        new Visit("/wheel/image/connection/194709210130/4975802/195502241915/5391959", 200),
        new Visit("/wheel/image/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147", 200),
        new Visit("/wheel/explore/birth/197506211430/2643743", 200),
        new Visit("/wheel/explore/birth-transit/197506211430/2643743/202403011000/1850147", 200),
        new Visit("/wheel/explore/202403011000/1850147", 200),
        new Visit("/wheel/explore/connection/194709210130/4975802/195502241915/5391959", 200),
        new Visit("/wheel/explore/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147", 200),
        new Visit("/range/explore/d/202403011000/1850147", 200),
        new Visit("/learn", 200),
        new Visit("/learn/channels", 200),
        new Visit("/learn/gates", 200),
        new Visit("/learn/planets", 200),
        new Visit("/learn/charts", 200),
        new Visit("/people", 200),
        new Visit("/about", 200),
        new Visit("/nonexistent", 404),
        new Visit("/range/table/202403011000/2643743", 200),
        new Visit("/range/table/d/202403011000", 200),
        new Visit("/wheel/image/197513011430", 400),
        new Visit("/wheel/image/180001011200", 200),
        new Visit("/wheel/image/202403011000/1", 200),
        new Visit("/cities?query=z%C3%BCr", 200),
        new Visit("/cities?query=50%25%2F", 200),
        new Visit("/cities/zone?zone=Europe%2FBrussels", 200),
        new Visit("/cities/zone?zone=Asia%2FCalcutta", 200),
        new Visit("/embed/transits/small", 200),
        new Visit("/embed/transits/medium", 200),
        new Visit("/embed/transits/large", 404),
        new Visit("/now", 200),
        new Visit("/now/1850147", 200),
        new Visit("/cycle/saturn-return/195502241915/5391959/2643743", 200),
        new Visit("/cycle/chiron-return/218502241915/5391959", 404),
        new Visit("/css/main.css", 200),
        new Visit("/css/normalize.css", 200),
        new Visit("/css/awesomplete.css", 200),
        new Visit("/css/second-row.css", 200),
        new Visit("/js/main.js", 200),
        new Visit("/js/store.js", 200),
        new Visit("/js/awesomplete.min.js", 200),
        new Visit("/images/favicon.svg", 200),
        new Visit("/images/apple-touch-icon.png", 200),
        new Visit("/images/og-image.png", 200));

    private final File uberJar_;

    public NativeImage(File uberJar) {
        uberJar_ = uberJar;
    }

    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private static File javaHomeTool(String name) {
        for (var home : new String[]{System.getenv("GRAALVM_HOME"), System.getenv("JAVA_HOME"), System.getProperty("java.home")}) {
            if (home != null) {
                var tool = new File(home, "bin/" + name);
                if (tool.canExecute()) {
                    return tool;
                }
            }
        }
        return null;
    }

    private static int freePort()
    throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public void recordConfiguration(File outputDirectory)
    throws Exception {
        var java = javaHomeTool(WINDOWS ? "java.exe" : "java");
        if (java == null) {
            throw new IllegalStateException("No java executable was found in GRAALVM_HOME, JAVA_HOME or the running Java");
        }
        var port = freePort();
        Files.createDirectories(outputDirectory.toPath());
        var log = File.createTempFile("gatesofhumanity-native-config", ".log");
        var process = new ProcessBuilder(java.getAbsolutePath(),
            // written every second, since the process is stopped rather than left to exit by itself
            "-agentlib:native-image-agent=config-output-dir=" + outputDirectory.getAbsolutePath() +
            ",config-write-period-secs=1,config-write-initial-delay-secs=1",
            "-Dgatesofhumanity.port=" + port,
            "-jar", uberJar_.getAbsolutePath())
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start();
        try {
            visitAll(process, port, log);
            Thread.sleep(3000);
        } finally {
            process.destroy();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }

        var metadata = new File(outputDirectory, "reachability-metadata.json");
        if (!metadata.exists() || !Files.readString(metadata.toPath()).contains("gatesofhumanity.elements.WheelImage")) {
            throw new IllegalStateException("The tracing agent didn't record the application, see " + log);
        }
        System.out.println("Recorded the native image configuration in " + outputDirectory);
    }

    private static void visitAll(Process process, int port, File log)
    throws Exception {
        var base = "http://localhost:" + port;
        waitUntilServing(process, CLIENT, base, log);
        var failures = new ArrayList<String>();
        for (var visit : VISITS) {
            var request = HttpRequest.newBuilder(URI.create(base + visit.path())).header("Cookie", "locationId=1850147").build();
            var status = CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != visit.status()) {
                failures.add(visit.path() + " answered " + status + " instead of " + visit.status());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("The application didn't answer as expected, see " + log + ":\n" + String.join("\n", failures));
        }
    }

    /**
     * Starts a native executable and visits every route, like the tracing run does.
     */
    public static void check(File executable)
    throws Exception {
        if (!executable.canExecute()) {
            throw new IllegalStateException(executable + " doesn't exist, build it with ./bld native");
        }
        var port = freePort();
        var log = File.createTempFile("gatesofhumanity-native-check", ".log");
        var builder = new ProcessBuilder(executable.getAbsolutePath()).redirectErrorStream(true).redirectOutput(log);
        builder.environment().put("GATES_PORT", String.valueOf(port));
        var process = builder.start();
        try {
            visitAll(process, port, log);
            System.out.println("The native executable answered all " + VISITS.size() + " routes as expected");
        } finally {
            process.destroy();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    private static void waitUntilServing(Process process, HttpClient client, String base, File log)
    throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The application stopped while starting:\n" + Files.readString(log.toPath()));
            }
            try {
                client.send(HttpRequest.newBuilder(URI.create(base + "/now")).build(), HttpResponse.BodyHandlers.discarding());
                return;
            } catch (IOException _) {
                Thread.sleep(10);
            }
        }
        throw new IllegalStateException("The application didn't start within a minute, see " + log);
    }

    public void build(File executable)
    throws Exception {
        var tool = javaHomeTool(WINDOWS ? "native-image.cmd" : "native-image");
        if (tool == null) {
            throw new IllegalStateException("No native-image was found, run with a GraalVM JDK or set GRAALVM_HOME");
        }
        var process = new ProcessBuilder(tool.getAbsolutePath(), "--no-fallback",
            "-jar", uberJar_.getAbsolutePath(), "-o", executable.getAbsolutePath())
            .inheritIO()
            .start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("native-image failed");
        }
    }
}

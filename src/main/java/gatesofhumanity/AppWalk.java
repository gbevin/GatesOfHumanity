package gatesofhumanity;

import rife.nativeapp.NativeApp;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Goes through the pages of the site as someone in an app can: from the first page and the
 * ones that only an app asks for, along every link to another page. The build of the native
 * library does that on the JVM first, see AppBuild, which shows it in seconds what's wrong with
 * a page and shows GraalVM what the pages need that it can't see by itself. What the pages ask
 * for with their scripts, as the drawings of the charts, is among the addresses of the build.
 */
public final class AppWalk {
    private static final Pattern LINK = Pattern.compile("\\shref=\"([^\"#]+)");

    private AppWalk() {
    }

    public static void main(String[] arguments) {
        try (var app = new NativeApp()) {
            var runner = app.runner();
            // where the app has the site, which it says as the app does or leaves to RIFE2
            var root = System.getProperty(NativeApp.SCHEME_PROPERTY, NativeApp.DEFAULT_SCHEME) + "://" + System.getProperty(NativeApp.HOST_PROPERTY, NativeApp.DEFAULT_HOST);
            var seen = new LinkedHashSet<>(List.of("/", "/panel", "/nothing-here"));
            var ahead = new ArrayDeque<>(seen);
            var pages = 0;
            while (!ahead.isEmpty()) {
                var address = ahead.remove();
                var response = runner.handle(address);
                if (response.getStatus() >= 500) {
                    throw new IllegalStateException(address + " answers with status " + response.getStatus());
                }
                var links = new LinkedHashSet<String>();
                // the first page is somewhere else
                var elsewhere = response.getHeader("Location");
                if (elsewhere != null) {
                    links.add(elsewhere);
                }
                var type = response.getContentType();
                if (type != null && type.startsWith("text/html")) {
                    ++pages;
                    var linked = LINK.matcher(response.getText());
                    while (linked.find()) {
                        links.add(linked.group(1).replace("&amp;", "&"));
                    }
                }
                var here = URI.create(root + address);
                for (var link : links) {
                    URI to;
                    try {
                        to = here.resolve(link);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    // a link out of the site leaves the app
                    if (!here.getScheme().equals(to.getScheme()) || !here.getHost().equals(to.getHost())) {
                        continue;
                    }
                    var next = to.getRawPath() + (to.getRawQuery() == null ? "" : "?" + to.getRawQuery());
                    if (seen.add(next)) {
                        ahead.add(next);
                    }
                }
            }
            if (pages < 20) {
                throw new IllegalStateException("Only " + pages + " pages were found from the first one");
            }
            System.out.println("Went through " + pages + " pages and " + (seen.size() - pages) + " of their files");
        }
    }
}

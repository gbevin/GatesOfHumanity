package gatesofhumanity;

import gatesofhumanity.elements.*;
import rife.config.RifeConfig;
import rife.engine.*;
import rife.ioc.HierarchicalProperties;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static gatesofhumanity.elements.ChartType.*;

public class AppSite extends Site {
    static final String MOMENT = "\\d{12}";
    static final String ID = "\\d+";
    private static final String DURATION = Pattern.quote(RangeTable.DAY);

    public Route wheel = get("/wheel", Wheel.class);
    public Route wheelChart = get("/wheel", PathInfoHandling.MAP(m -> m.p("type", ChartType.TYPED_PATTERN)), Wheel.class);
    public Route wheelImage = get("/wheel/image", wheelPathInfo(), WheelImage.class);
    public Route wheelExplore = get("/wheel/explore", wheelPathInfo(), WheelExplore.class);
    public Route range = get("/range", Range.class);
    public Route rangeDuration = get("/range", PathInfoHandling.MAP(m -> m.p("duration", DURATION)), Range.class);
    public Route rangeTable = get("/range/table", rangePathInfo(), RangeTable.class);
    public Route rangeExplore = get("/range/explore", rangePathInfo(), RangeExplore.class);
    public Route learn = get("/learn", Learn.class);
    public Route learnTopic = get("/learn", PathInfoHandling.MAP(m -> m.p("topic", LearnTopic.PATTERN)), Learn.class);
    public Route people = get("/people", People.class);
    public Route about = get("/about", About.class);
    public Route cities = get("/cities", Cities.class);
    public Route zoneCity = get("/cities/zone", ZoneCity.class);
    public Route now = get("/now", Now.class);
    public Route nowAt = get("/now", PathInfoHandling.MAP(m -> m.p("locationId", ID)), Now.class);
    public Route cycle = get("/cycle", PathInfoHandling.MAP(
        m -> m.p("cycle", CycleTime.PATTERN).s().p("birth", MOMENT).s().p("birthLocationId", ID),
        m -> m.p("cycle", CycleTime.PATTERN).s().p("birth", MOMENT).s().p("birthLocationId", ID).s().p("locationId", ID)), CycleTime.class);
    public Route transitsEmbed = get("/embed/transits", PathInfoHandling.MAP(m -> m.p("size", TransitsEmbed.SIZE_PATTERN)), TransitsEmbed.class);
    // the birth chart, where a visitor starts, temporary since browsers keep a permanent redirect
    // after the page it leads to changes
    public Route home = get("/", c -> c.redirect(ChartType.BIRTH.url(c, this)));
    public Route notFound = fallback(NotFound.class);
    public Route robots = get("/robots.txt", c -> {
        var t = c.templateTxt("robots");
        // the charts, tables and their explanations form an endless space of URLs that the pages already show
        for (var route : List.of(wheelImage, wheelExplore, rangeTable, rangeExplore)) {
            t.setValue("path", route.path());
            t.appendBlock("disallowed", "disallow");
        }
        c.print(t);
    });
    public Route sitemap = get("/sitemap.xml", c -> {
        var t = c.templateXml("sitemap");
        for (var page : PageDescription.listed()) {
            t.setValueEncoded("loc", page.url(c, this).toString());
            t.appendBlock("urls", "url");
        }
        c.print(t);
    });

    /**
     * The public root URL when the site runs behind a reverse proxy, from {@code hdapp.proxyRootUrl}
     * or the {@code HDAPP_PROXY_ROOT_URL} environment variable. Without one, the site is in
     * development and disables caching.
     */
    static String proxyRootUrl(HierarchicalProperties properties) {
        // the environment variable for shells, which don't allow dots in names; both are named hdapp,
        // which is what the servers that run the site set
        return properties.getValueString("hdapp.proxyRootUrl", properties.getValueString("HDAPP_PROXY_ROOT_URL"));
    }

    /**
     * The port of the embedded server, from {@code gatesofhumanity.port} or the {@code GATES_PORT}
     * environment variable, 8080 by default.
     */
    static int port(HierarchicalProperties properties) {
        var port = properties.getValueString("gatesofhumanity.port", properties.getValueString("GATES_PORT"));
        if (port == null) {
            return 8080;
        }
        try {
            var number = Integer.parseInt(port.trim());
            if (number >= 0 && number <= 65535) {
                return number;
            }
        } catch (NumberFormatException _) {
        }
        throw new IllegalArgumentException("The port '" + port + "' isn't a number from 0 to 65535");
    }

    @Override
    public void setup() {
        // RIFE2 leaves .txt paths to the container's static files, which would never reach the robots route
        var pass_through = new HashSet<>(RifeConfig.engine().getPassThroughSuffixes());
        pass_through.remove("txt");
        // the source maps of the scripts and styles are static files too, which the fallback would answer otherwise
        pass_through.add("map");
        // as is what a browser installs the site by
        pass_through.add("webmanifest");
        RifeConfig.engine().setPassThroughSuffixes(pass_through);

        var proxy_root_url = proxyRootUrl(properties());
        if (proxy_root_url != null) {
            RifeConfig.engine().setProxyRootUrl(proxy_root_url);
        } else {
            RifeConfig.engine().setGlobalNoCacheHeaders(true);
        }

        // a few headers that the pages don't need to opt out of
        before(c -> {
            c.setHeader("X-Content-Type-Options", "nosniff");
            c.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
            c.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        });
    }

    /**
     * The moments and places of the range tables and their explanations, in their URLs.
     */
    private static PathInfoHandling rangePathInfo() {
        return PathInfoHandling.MAP(
            m -> m.p("duration", DURATION),
            m -> m.p("duration", DURATION).s().p("datetime", MOMENT),
            m -> m.p("duration", DURATION).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("datetime", MOMENT),
            m -> m.p("datetime", MOMENT).s().p("locationId", ID));
    }

    /**
     * The moments and places of the wheel images and their explanations, in their URLs.
     */
    static PathInfoHandling wheelPathInfo() {
        return PathInfoHandling.MAP(
            m -> m.p("type", Pattern.quote(BIRTH_TRANSIT.parameter_)),
            m -> m.p("type", Pattern.quote(BIRTH_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID),
            m -> m.p("type", Pattern.quote(BIRTH_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("datetime", MOMENT),
            m -> m.p("type", Pattern.quote(BIRTH_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("type", Pattern.quote(CONNECTION.parameter_)),
            m -> m.p("type", Pattern.quote(CONNECTION.parameter_)).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("type", Pattern.quote(CONNECTION.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("type", Pattern.quote(CONNECTION_TRANSIT.parameter_)),
            m -> m.p("type", Pattern.quote(CONNECTION_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("thirdDatetime", MOMENT).s().p("thirdLocationId", ID),
            m -> m.p("type", Pattern.quote(CONNECTION_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("thirdDatetime", MOMENT).s().p("thirdLocationId", ID).s().p("datetime", MOMENT),
            m -> m.p("type", Pattern.quote(CONNECTION_TRANSIT.parameter_)).s().p("secondDatetime", MOMENT).s().p("secondLocationId", ID).s().p("thirdDatetime", MOMENT).s().p("thirdLocationId", ID).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("type", Pattern.quote(BIRTH.parameter_)),
            m -> m.p("type", Pattern.quote(BIRTH.parameter_)).s().p("datetime", MOMENT),
            m -> m.p("type", Pattern.quote(BIRTH.parameter_)).s().p("datetime", MOMENT).s().p("locationId", ID),
            m -> m.p("datetime", MOMENT),
            m -> m.p("datetime", MOMENT).s().p("locationId", ID)
        );
    }

    public static void main(String[] args) {
        new Server()
            .port(port(HierarchicalProperties.createSystemInstance()))
            .staticResourceBase("src/main/webapp")
            .start(new AppSite());
    }
}

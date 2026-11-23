package gatesofhumanity;

import gatesofhumanity.elements.LearnTopic;
import gatesofhumanity.elements.PageDescription;
import gatesofhumanity.ephemeris.Body;
import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.CityDirectory;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.Position;
import gatesofhumanity.models.Upcoming;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import rife.config.RifeConfig;
import rife.engine.RequestMethod;
import rife.ioc.HierarchicalProperties;
import rife.json.Json;
import rife.json.JsonArray;
import rife.servlet.RifeFilter;
import rife.test.MockConversation;
import rife.test.MockRequest;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {
    // the address of every page, written out rather than taken from the routes that build them
    private static final Map<PageDescription, String> PAGE_PATHS = pagePaths();

    private static Map<PageDescription, String> pagePaths() {
        var paths = new LinkedHashMap<PageDescription, String>();
        paths.put(PageDescription.TRANSIT, "/wheel");
        paths.put(PageDescription.BIRTH, "/wheel/birth");
        paths.put(PageDescription.BIRTH_TRANSIT, "/wheel/birth-transit");
        paths.put(PageDescription.CONNECTION, "/wheel/connection");
        paths.put(PageDescription.CONNECTION_TRANSIT, "/wheel/connection-transit");
        paths.put(PageDescription.RANGE_MONTH, "/range");
        paths.put(PageDescription.RANGE_DAY, "/range/d");
        paths.put(PageDescription.PEOPLE, "/people");
        paths.put(PageDescription.LEARN, "/learn");
        paths.put(PageDescription.ABOUT, "/about");
        for (var topic : LearnTopic.values()) {
            paths.put(topic.page(), "/learn/" + topic.slug_);
        }
        return Collections.unmodifiableMap(paths);
    }

    @Test
    void theRootRedirectsToTheBirthChartForNow() {
        try (var m = new MockConversation(new AppSite())) {
            var response = m.doRequest("/");
            assertEquals(302, response.getStatus());
            assertEquals("http://localhost/wheel/birth", response.getHeader("Location"));
        }
    }

    @Test
    void birthWheelShowsTheChartActivations() {
        try (var m = new MockConversation(new AppSite())) {
            var response = m.doRequest("/wheel/image/birth/197506211430");
            assertEquals(200, response.getStatus());
            var svg = response.getText();
            assertTrue(svg.contains(">http://localhost</text>"));
            assertFalse(svg.contains("uwyn"));
            var birth = ZonedDateTime.of(1975, 6, 21, 14, 30, 0, 0, ZoneId.of("Europe/London"));
            var chart = Charts.birth(birth.withZoneSameInstant(ZoneOffset.UTC));
            var positions = new ArrayList<>(chart.personality().positions());
            positions.addAll(chart.design().positions());
            for (var p : positions) {
                var activation = p.gateNumber() + "." + p.gateLine();
                assertTrue(svg.contains(">" + activation + "<"), p.planet() + " " + activation);
            }
        }
    }

    // in the order of their columns
    private static final List<Planet> PLANETS = Arrays.stream(Planet.values()).filter(planet -> planet != Planet.NONE).toList();
    private static final List<String> PLANET_NAMES = List.of("Sun", "Earth", "Moon", "North Node", "South Node", "Mercury", "Venus", "Mars",
        "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto", "Chiron");

    @Test
    void transitTablesHaveARowForEveryMomentAndAColumnForEveryPlanet() {
        try (var m = new MockConversation(new AppSite())) {
            var ranges = new LinkedHashMap<String, String>();
            ranges.put("/range/table/202403011000", "Month");
            ranges.put("/range/table/d/202403011000", "Day");
            // the scripts of the page know that it isn't a drawing
            assertTrue(m.doRequest("/range").getText().contains("chart_type = 'table';"));
            assertTrue(m.doRequest("/range/d").getText().contains("chart_type = 'table';"));
            assertTrue(m.doRequest("/wheel").getText().contains("chart_type = '';"));
            ranges.forEach((url, name) -> {
                var response = m.doRequest(url);
                assertEquals(200, response.getStatus(), url);
                assertTrue(response.getContentType().startsWith("text/html"), response.getContentType());
                var html = response.getText();
                assertFalse(html.contains("uwyn"), url);
                var table = Jsoup.parseBodyFragment(html).selectFirst("#range-table");

                // the planets above and below the rows, with the symbols of the charts, which the table brings along
                var symbols = PLANETS.stream().map(planet -> "#planet-" + planet.id_).toList();
                assertEquals(14, symbols.size());
                for (var planets : List.of(table.selectFirst("thead tr"), table.selectFirst("tfoot tr"))) {
                    assertEquals("range.planets", planets.attr("data-explore"), url);
                    assertEquals(symbols, planets.select("th[scope=col] use").eachAttr("href"), url);
                    assertEquals(PLANET_NAMES, planets.select("th[scope=col]").eachAttr("aria-label"), url);
                    assertEquals(15, planets.children().size(), url);
                }
                for (var symbol : symbols) {
                    assertEquals(1, table.select("svg.range-symbols " + symbol.replace("_", "\\_")).size(), symbol);
                }

                // a moment in every row, and the gates and lines that changed in the columns of their planets
                var rows = table.select("tbody tr");
                assertEquals(name.equals("Day") ? 24 : 31, rows.size(), url);
                assertEquals(name.equals("Day") ? "Mar 1, 10 AM" : "Fri, Mar 1", rows.first().selectFirst("th[scope=row]").text(), url);
                assertEquals(name.equals("Day") ? "Mar 2, 9 AM" : "Sun, Mar 31", rows.last().selectFirst("th[scope=row]").text(), url);
                var first = Charts.positions(Instant.parse("2024-03-01T10:00:00Z")).stream().collect(Collectors.toMap(Position::planet, p -> p.gateNumber() + "." + p.gateLine()));
                assertEquals(PLANETS.stream().map(first::get).toList(), rows.first().select("td").eachText(), url);
                for (var row : rows) {
                    assertEquals("range.cells", row.attr("data-explore"), url);
                    assertEquals(1, row.select("th[data-explore=range.dates]").size(), url);
                    assertEquals(14, row.select("td").size(), url);
                }
                // what didn't change is left out, except in the last row
                assertEquals("", rows.get(1).select("td").get(PLANETS.indexOf(Planet.PLUTO)).text(), url);
                assertTrue(rows.last().select("td").stream().allMatch(cell -> cell.text().matches("\\d+\\.[1-6]")), rows.last().text());

                // its name, moment and place
                assertEquals("Human Design Transits by " + name, table.selectFirst("table").attr("aria-label"), url);
                assertEquals(List.of("Human Design Transits by " + name, "Fri, March 1, 2024 @ 10:00 AM GMT", "Fri, March 1, 2024 @ 10:00 AM UTC", "51.50853\u00b0 N, 0.12574\u00b0 W"),
                    table.select("p").eachText(), url);
            });
        }
    }

    @Test
    void rangesStepInTheLocalTimeAcrossDaylightSavingTime() {
        try (var m = new MockConversation(new AppSite())) {
            // London's clocks go back at 2 AM on October 25, 2026, so 1 AM comes twice
            var day = m.doRequest("/range/table/d/202610250000/2643743").getText();
            var hours = new ArrayList<String>();
            var matcher = Pattern.compile("<th scope=\"row\" data-explore=\"range.dates\">Oct 25, ([^<]+)<").matcher(day);
            while (matcher.find()) {
                hours.add(matcher.group(1));
            }
            assertEquals(List.of("12 AM", "1 AM", "1 AM", "2 AM"), hours.subList(0, 4));
            assertEquals("10 PM", hours.getLast());

            // the last day of a month across the change is at noon GMT, 12:00 UTC, not 11:00 UTC
            var month = m.doRequest("/range/table/202610201200/2643743").getText();
            var moons = Jsoup.parseBodyFragment(month).select("tbody tr").stream().map(row -> row.select("td").get(PLANETS.indexOf(Planet.MOON)).text()).toList();
            assertEquals(moon(Instant.parse("2026-11-19T12:00:00Z")), moons.getLast());
            assertNotEquals(moon(Instant.parse("2026-11-19T11:00:00Z")), moons.getLast());
        }
    }

    // the color of the channels, gates and legend of the first, second or third chart of an image, which its styles
    // all agree on and write out, since design programs don't know the variables of CSS
    private static String channelColor(String svg, String chart) {
        assertFalse(svg.contains("var("), svg);
        var colors = Pattern.compile("\\.(?:hd-channel|stripe|legend)-" + chart + "(?:-stroke)? \\{\\s+fill: ([^;]+);(?:\\s+stroke: ([^;]+);)?").matcher(svg).results()
            .flatMap(rule -> Stream.of(rule.group(1), rule.group(2))).filter(Objects::nonNull).toList();
        assertEquals(5, colors.size(), chart + " " + colors);
        assertEquals(1, new HashSet<>(colors).size(), chart + " " + colors);
        return colors.getFirst();
    }

    private static String moon(Instant moment) {
        var moon = Charts.positions(moment).stream().filter(p -> p.planet() == Planet.MOON).findFirst().orElseThrow();
        return moon.gateNumber() + "." + moon.gateLine();
    }

    @Test
    void citiesAnswerWithJson() {
        try (var m = new MockConversation(new AppSite())) {
            var response = m.doRequest("/cities", new MockRequest().parameter("query", "zürich\" 50%/"));
            assertEquals(200, response.getStatus());
            assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
            var result = Json.parseArray(response.getText());
            assertEquals("zürich\" 50%/", ((JsonArray) result.get(0)).getString(0));
            assertEquals(1, result.size());
            assertEquals(0, ((JsonArray) result.get(0)).getInt(1));

            result = Json.parseArray(m.doRequest("/cities", new MockRequest().parameter("query", "zurich")).getText());
            assertEquals("zurich", ((JsonArray) result.get(0)).getString(0));
            assertEquals("Zürich, Zurich, Switzerland", ((JsonArray) result.get(1)).getString(0));
            assertEquals(2657896, ((JsonArray) result.get(1)).getInt(1));
        }
    }


    @Test
    void aTimeZoneHasItsMostPopulatedCity() {
        try (var m = new MockConversation(new AppSite())) {
            var response = m.doRequest("/cities/zone", new MockRequest().parameter("zone", "America/New_York"));
            assertEquals("application/json; charset=UTF-8", response.getContentType());
            assertEquals("[\"New York City, New York, United States\",5128581]", response.getText());
            assertEquals("[\"Brussels, Brussels Capital, Belgium\",2800866]", m.doRequest("/cities/zone", new MockRequest().parameter("zone", "Europe/Brussels")).getText());
            // a zone's own city, rather than a larger one of a zone with the same rules, here Dubai
            assertEquals("[\"Muscat, Oman\",287286]", m.doRequest("/cities/zone", new MockRequest().parameter("zone", "Asia/Muscat")).getText());
            // an older name of a zone, which browsers still give, leads to the cities of its new one
            var calcutta = Json.parseArray(m.doRequest("/cities/zone", new MockRequest().parameter("zone", "Asia/Calcutta")).getText());
            assertEquals("Asia/Kolkata", CityDirectory.find(((Number)calcutta.get(1)).intValue()).timezone());
            assertEquals(calcutta, Json.parseArray(m.doRequest("/cities/zone", new MockRequest().parameter("zone", "Asia/Kolkata")).getText()));
            // no city for a zone that's only an offset, for what isn't a zone, or without one
            for (var zone : new String[] {"UTC", "Etc/GMT+5", "Mars/Phobos", "", "'\"<"}) {
                assertEquals("[]", m.doRequest("/cities/zone", new MockRequest().parameter("zone", zone)).getText(), zone);
            }
            assertEquals("[]", m.doRequest("/cities/zone").getText());
            // the pages know where to ask
            assertTrue(m.doRequest("/wheel/birth").getText().contains("zone: 'http://localhost/cities/zone'"));
        }
    }
    @Test
    void unknownLocationsAreRefused() {
        try (var m = new MockConversation(new AppSite())) {
            m.cookie("locationId", "1850147");
            for (var url : List.of("/wheel/image/202403011000/1826645935", "/wheel/image/birth/197506211430/1826645935",
                                   "/wheel/image/birth-transit/197506211430/1826645935/202403011000/2643743",
                                   "/wheel/image/birth-transit/197506211430/2643743/202403011000/1826645935",
                                   "/range/table/202403011000/1826645935", "/wheel/image/birth/197506211430/99999999999")) {
                var response = m.doRequest(url);
                assertEquals(200, response.getStatus(), url);
                assertTrue(response.getText().contains("This city isn&apos;t known, please choose it again."), url);
                assertFalse(response.getText().contains("51.50853"), url);
                assertTrue(response.getNewCookieNames().isEmpty(), url);
            }
            assertEquals("1850147", m.getCookieValue("locationId"));
        }
    }

    @Test
    void anUnknownRememberedCityIsLikeNoChoice() {
        try (var m = new MockConversation(new AppSite())) {
            m.cookie("locationId", "1826645935");
            var response = m.doRequest("/wheel/image/202403011000");
            assertEquals(200, response.getStatus());
            assertTrue(response.getText().contains("51.50853° N"));
            assertEquals(200, m.doRequest("/wheel").getStatus());
        }
    }

    private static HierarchicalProperties behindProxy() {
        return new HierarchicalProperties().put("hdapp.proxyRootUrl", "https://charts.example.com");
    }

    // the site sets up the global RifeConfig, which the tests that configure it restore
    private static void restoringRifeConfig(Runnable test) {
        var proxy = RifeConfig.engine().getProxyRootUrl();
        var no_cache = RifeConfig.engine().getGlobalNoCacheHeaders();
        var pretty = RifeConfig.engine().getPrettyEngineExceptions();
        try {
            RifeConfig.engine().setGlobalNoCacheHeaders(false);
            RifeConfig.engine().setPrettyEngineExceptions(true);
            test.run();
        } finally {
            RifeConfig.engine().setProxyRootUrl(proxy);
            RifeConfig.engine().setGlobalNoCacheHeaders(no_cache);
            RifeConfig.engine().setPrettyEngineExceptions(pretty);
        }
    }

    @Test
    void proxyRootUrlComesFromConfiguration() {
        restoringRifeConfig(() -> {
            try (var m = new MockConversation(new AppSite(), behindProxy())) {
                assertEquals("https://charts.example.com", RifeConfig.engine().getProxyRootUrl());
                assertFalse(RifeConfig.engine().getGlobalNoCacheHeaders());
            }
            RifeConfig.engine().setProxyRootUrl(null);
            try (var m = new MockConversation(new AppSite(), new HierarchicalProperties().put("HDAPP_PROXY_ROOT_URL", "https://other.example.com"))) {
                assertEquals("https://other.example.com", RifeConfig.engine().getProxyRootUrl());
                assertFalse(RifeConfig.engine().getGlobalNoCacheHeaders());
            }
            RifeConfig.engine().setProxyRootUrl(null);
            try (var m = new MockConversation(new AppSite(), behindProxy().put("HDAPP_PROXY_ROOT_URL", "https://other.example.com"))) {
                assertEquals("https://charts.example.com", RifeConfig.engine().getProxyRootUrl());
            }
            RifeConfig.engine().setProxyRootUrl(null);
            try (var m = new MockConversation(new AppSite(), new HierarchicalProperties())) {
                assertNull(RifeConfig.engine().getProxyRootUrl());
                assertTrue(RifeConfig.engine().getGlobalNoCacheHeaders());
            }
        });
    }

    @Test
    void whatDependsOnTheVisitorIsNeverCached() {
        restoringRifeConfig(() -> {
            try (var m = new MockConversation(new AppSite(), behindProxy())) {
                for (var url : List.of("/wheel", "/wheel/birth", "/range/d", "/now/1850147", "/wheel/image/202403011000")) {
                    var response = m.doRequest(url);
                    assertTrue(String.valueOf(response.getHeaders("Cache-Control")).contains("no-cache"), url);
                }
                for (var url : List.of("/wheel/image/202403011000/1850147", "/learn", "/about")) {
                    assertTrue(m.doRequest(url).getHeaders("Cache-Control").isEmpty(), url);
                }
            }
        });
    }

    @Test
    void invalidDatesAreRefused() {
        try (var m = new MockConversation(new AppSite())) {
            for (var url : List.of("/wheel/image/birth/197513011430", "/wheel/image/197502301430",
                                   "/wheel/image/birth-transit/197502301430/2643743", "/range/table/d/202402301000",
                                   "/wheel/image/connection/197502301430/2643743", "/wheel/image/connection/195502241915/5391959/197502301430/2643743",
                                   "/wheel/image/connection-transit/195502241915/5391959/197502301430/5391959/202403011000/2643743",
                                   "/wheel/image/connection-transit/195502241915/5391959/195008110945/5391959/202402301000/2643743")) {
                var response = m.doRequest(url);
                assertEquals(400, response.getStatus(), url);
                assertTrue(response.getText().contains("This isn&apos;t a valid date."), url);
            }
        }
    }

    @Test
    void datesOutsideTheEphemerisAreExplained() {
        try (var m = new MockConversation(new AppSite())) {
            for (var url : List.of("/wheel/image/birth/180001101200", "/wheel/image/birth-transit/180001101200/2643743/202403011000")) {
                var response = m.doRequest(url);
                assertEquals(200, response.getStatus(), url);
                assertTrue(response.getText().contains("Birth charts can be made from April 8, 1800 to December 17, 2199."), url + response.getText());
            }
            for (var url : List.of("/wheel/image/220001011200", "/wheel/image/180001011200",
                                   "/wheel/image/birth-transit/197506211430/2643743/220001011200", "/range/table/219912011200")) {
                var response = m.doRequest(url);
                assertEquals(200, response.getStatus(), url);
                assertTrue(response.getText().contains("Transits can be made from January 3, 1800 to December 17, 2199."), url + response.getText());
            }
            for (var url : List.of("/wheel/image/birth/180004101200", "/wheel/image/219912161200", "/range/table/d/219912160000",
                                   "/wheel/image/180002011200", "/wheel/image/birth-transit/180004101200/2643743/180002011200")) {
                var response = m.doRequest(url);
                assertEquals(200, response.getStatus(), url);
                assertFalse(response.getText().contains("can be made"), url);
            }
        }
    }

    @Test
    void invalidPageDatesUseTheLocationTime() {
        try (var m = new MockConversation(new AppSite())) {
            var tokyo = ZoneId.of("Asia/Tokyo");
            var format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            for (var invalid : List.of("garbage", "2024-02-30T10:00")) {
                var before = LocalDateTime.now(tokyo).format(format);
                var text = m.doRequest("/wheel", new MockRequest().parameter("datetime", invalid).parameter("locationId", "1850147")).getText();
                var after = LocalDateTime.now(tokyo).format(format);
                assertTrue(text.contains(before) || text.contains(after), invalid);
                assertFalse(text.contains("2024-02-29T10:00"), invalid);
            }
        }
    }

    @Test
    void nowIsTheWallClockAtTheLocation() {
        try (var m = new MockConversation(new AppSite())) {
            var format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            for (var entry : List.of(List.of("/now/1850147", "Asia/Tokyo"), List.of("/now/2643743", "Europe/London"),
                                     List.of("/now", "Europe/London"), List.of("/now/1", "Europe/London"))) {
                var zone = ZoneId.of(entry.get(1));
                var before = LocalDateTime.now(zone).format(format);
                var text = m.doRequest(entry.get(0)).getText();
                var after = LocalDateTime.now(zone).format(format);
                assertTrue(text.equals(before) || text.equals(after), entry + " " + text);
            }
        }
    }

    @Test
    void aSecureSiteOnlySendsTheLocationCookieSecurely() {
        try (var m = new MockConversation(new AppSite()).scheme("https")) {
            m.doRequest("/wheel/image/202403011000/1850147");
            assertTrue(m.getCookie("locationId").getSecure());
        }
    }

    @Test
    void responsesRefuseFramingAndSniffing() {
        try (var m = new MockConversation(new AppSite())) {
            for (var url : List.of("/wheel", "/wheel/image/202403011000/1850147", "/learn", "/robots.txt")) {
                var response = m.doRequest(url);
                assertEquals("nosniff", response.getHeader("X-Content-Type-Options"), url);
                assertEquals("frame-ancestors 'none'", response.getHeader("Content-Security-Policy"), url);
                assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"), url);
            }
        }
    }

    @Test
    void imagesRememberTheVisitorLocation() {
        try (var m = new MockConversation(new AppSite())) {
            m.doRequest("/wheel/image/202403011000/1850147");
            var cookie = m.getCookie("locationId");
            assertEquals("1850147", cookie.getValue());
            assertEquals("/", cookie.getPath());
            assertEquals(365 * 24 * 60 * 60, cookie.getMaxAge());
            assertEquals("Lax", cookie.getAttribute("SameSite"));
            assertTrue(cookie.isHttpOnly());
            assertFalse(cookie.getSecure());

            var response = m.doRequest("/wheel/image/birth/197506211430/2643743");
            assertTrue(response.getNewCookieNames().isEmpty(), response.getNewCookieNames().toString());
            assertEquals("1850147", m.getCookieValue("locationId"));

            m.doRequest("/range/table/202403011000/6058560");
            assertEquals("6058560", m.getCookieValue("locationId"));
            m.doRequest("/wheel/image/birth-transit/197506211430/1850147/202403011000/2643743");
            assertEquals("2643743", m.getCookieValue("locationId"));

            response = m.doRequest("/wheel/image/202403011000");
            assertTrue(response.getNewCookieNames().isEmpty(), response.getNewCookieNames().toString());
        }
    }

    @Test
    void pagesStartAtTheTimeOfTheRememberedLocation() {
        try (var m = new MockConversation(new AppSite())) {
            m.cookie("locationId", "1850147");
            var format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            var before = LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(format);
            var text = m.doRequest("/wheel").getText();
            var after = LocalDateTime.now(ZoneId.of("Asia/Tokyo")).format(format);
            assertTrue(text.contains(before) || text.contains(after), text);
        }
    }

    @Test
    void portComesFromConfiguration() {
        assertEquals(8080, AppSite.port(new HierarchicalProperties()));
        assertEquals(9123, AppSite.port(new HierarchicalProperties().put("gatesofhumanity.port", " 9123 ")));
        assertEquals(9124, AppSite.port(new HierarchicalProperties().put("GATES_PORT", "9124")));
        assertEquals(9123, AppSite.port(new HierarchicalProperties().put("gatesofhumanity.port", "9123").put("GATES_PORT", "9124")));
        assertThrows(IllegalArgumentException.class, () -> AppSite.port(new HierarchicalProperties().put("gatesofhumanity.port", "70000")));
        assertThrows(IllegalArgumentException.class, () -> AppSite.port(new HierarchicalProperties().put("gatesofhumanity.port", "http")));
    }

    // the deployed site, set up the way the RIFE2 filter sets it up
    @Test
    void theDeployedSiteRunsBehindTheProxyWithoutErrorPages() {
        restoringRifeConfig(() -> {
            var filter = new RifeFilter();
            filter.init(behindProxy(), new AppSiteDeployed());
            try {
                assertEquals("https://charts.example.com", RifeConfig.engine().getProxyRootUrl());
                assertFalse(RifeConfig.engine().getGlobalNoCacheHeaders());
                assertFalse(RifeConfig.engine().getPrettyEngineExceptions());
            } finally {
                filter.destroy();
            }
        });
    }

    @Test
    void warsRunTheDeployedSite()
    throws Exception {
        var web_xml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"));
        assertTrue(web_xml.contains("<param-value>" + AppSiteDeployed.class.getName() + "</param-value>"), web_xml);
    }

    @Test
    void deploymentsDontShowStackTraces() {
        var pretty = RifeConfig.engine().getPrettyEngineExceptions();
        try {
            RifeConfig.engine().setPrettyEngineExceptions(true);
            try (var m = new MockConversation(new AppSiteDeployed())) {
                assertEquals(200, m.doRequest("/wheel").getStatus());
                assertFalse(RifeConfig.engine().getPrettyEngineExceptions());
            }
        } finally {
            RifeConfig.engine().setPrettyEngineExceptions(pretty);
        }
    }

    @Test
    void theGatesPageNamesEveryGateInASentence() {
        try (var m = new MockConversation(new AppSite())) {
            var text = m.doRequest("/learn/gates").getParsedHtml().getDocument().text();
            assertTrue(text.contains("Gate 7, the Role of the Self in Interaction, is hexagram 7 of the I Ching, where it's called the Army."), text);
            assertTrue(text.contains("Gate 31, Influence, is hexagram 31 of the I Ching, where it's called Influence too."), text);
            assertTrue(text.contains("This gate is in the G Center, at one end of the channels of Awakening (10-20), Exploration (10-34) and Perfected Form (10-57)."), text);
            assertTrue(text.contains("at one end of the channel of the Alpha (7-31)."), text);
            // the hexagram drawn next to its names, The Creative all solid lines and The Receptive all broken ones
            var html = m.doRequest("/learn/gates").getText();
            var creative = "<rect x=\"0\" y=\"%d\" width=\"30\" height=\"3\"/>";
            var receptive = "<rect x=\"0\" y=\"%d\" width=\"13\" height=\"3\"/><rect x=\"17\" y=\"%1$d\" width=\"13\" height=\"3\"/>";
            var lines = new StringBuilder();
            for (var y = 0; y <= 25; y += 5) {
                lines.append(creative.formatted(y));
            }
            assertTrue(html.contains("aria-hidden=\"true\">" + lines + "</svg> <span>Gate 1, Self-Expression,"), html);
            lines.setLength(0);
            for (var y = 0; y <= 25; y += 5) {
                lines.append(receptive.formatted(y));
            }
            assertTrue(html.contains("aria-hidden=\"true\">" + lines + "</svg> <span>Gate 2, the Direction of the Self,"), html);
            // Difficulty at the Beginning is water over thunder, read from the top: broken, solid, broken, broken, broken, solid
            var ordering = List.of(receptive, creative, receptive, receptive, receptive, creative);
            lines.setLength(0);
            for (var i = 0; i < ordering.size(); ++i) {
                lines.append(ordering.get(i).formatted(5 * i));
            }
            assertTrue(html.contains("aria-hidden=\"true\">" + lines + "</svg> <span>Gate 3, Ordering,"), html);
        }
    }

    @Test
    void gatesAndPlanetsOpenTheirPlanetsInThePanel() {
        try (var m = new MockConversation(new AppSite())) {
            // Steve Jobs has his Personality Sun in gate 55 and Chiron in 41, which doesn't activate it
            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959").getText();
            assertTrue(svg.contains("data-explore=\"activation.1.SUN wheel.gates\""), svg);
            assertTrue(svg.contains("data-explore=\"activation.2.MOON wheel.gates\""), svg);
            assertFalse(Pattern.compile("data-explore=\"[^\"]*CHIRON[^\"]*wheel\\.gates\"").matcher(svg).find(), svg);
            assertTrue(svg.contains("data-explore=\"activation.1.SUN wheel.planets\""), svg);
            assertTrue(svg.contains("data-explore=\"wheel.gates\""), svg);

            // in a birth transit chart the transits are the first row, the birth the third and fourth
            svg = m.doRequest("/wheel/image/birth-transit/195502241915/5391959/202403011000/2643743").getText();
            assertTrue(svg.contains("data-explore=\"activation.1.MOON activation.4.VENUS activation.4.SATURN wheel.gates\""), svg);
            assertTrue(svg.contains("data-explore=\"activation.3.SUN activation.4.SUN wheel.rows\""), svg);
            // the symbol of a row is only painted in strokes, a rectangle around it takes the taps
            assertTrue(svg.contains("<rect class=\"explore-hit\" x=\"40\" y=\"1112\" width=\"48\" height=\"34\" data-explore=\"activation.3.SUN activation.4.SUN wheel.rows\"/>"), svg);
        }
    }

    // the example of "Line Fixing in Human Design" by Ryan Hanna, Jovian Archive, June 2026:
    // the Personality Venus in Gate 39 fixes the Design Sun in 55.2 in exaltation
    @Test
    void birthChartsShowFixingAcrossPersonalityAndDesign() {
        try (var m = new MockConversation(new AppSite())) {
            var svg = m.doRequest("/wheel/image/birth/197505201900/2643743").getText();
            assertTrue(svg.contains("<text x=\"64\" y=\"1060\" class=\"planet-gate design\" data-explore=\"activation.2.SUN wheel.rows\">55.2</text>"), svg);
            assertTrue(svg.matches("(?s).*<text x=\"448\" y=\"1000\" class=\"planet-gate\" data-explore=\"activation.1.VENUS wheel.rows\">39\\.\\d</text>.*"));
            assertTrue(svg.contains("<use href=\"#exalt2\" x=\"84\" y=\"1046\"/>"));
        }
    }

    @Test
    void namesKeepTheImageValidXml() {
        try (var m = new MockConversation(new AppSite())) {
            var invalid = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", "Ste\u0001v\uFFFEe")).getText();
            assertTrue(invalid.contains(">Steve</text>"), invalid);
            assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(invalid))));
            // whitespace that isn't a space still separates the words
            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", "Anna\tMaria\u000BLou")).getText();
            assertTrue(svg.contains(">Anna Maria Lou</text>"), svg);
            // a long name is cut between characters, never inside one
            var emoji = "a".repeat(26) + "\uD83D\uDE00bc";
            svg = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", emoji)).getText();
            assertTrue(svg.contains(">" + "a".repeat(26) + "\uD83D\uDE00\u2026</text>"), svg);
        }
    }

    @Test
    void birthChartsShowTheirAnalysis() {
        try (var m = new MockConversation(new AppSite())) {
            // Steve Jobs, born in San Francisco
            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959").getText();
            assertTrue(svg.contains("y=\"1244\">Generator</text>"), svg);
            assertTrue(svg.contains("y=\"1272\">To Respond</text>"));
            assertTrue(svg.contains("y=\"1300\">Emotional</text>"));
            assertTrue(svg.contains("y=\"1328\">6/<tspan class=\"design\">3</tspan> Role Model / Martyr</text>"));
            assertTrue(svg.contains("y=\"1244\">Split Definition (Wide)</text>"));
            assertTrue(svg.contains("y=\"1272\">Frustration</text>"));
            assertTrue(svg.contains("y=\"1300\">Satisfaction</text>"));
            assertTrue(svg.contains("y=\"1328\">Open and Enveloping</text>"));
            // 9-52, 17-62 and 30-41 define the Sacral, Root, Ajna, Throat and Solar Plexus
            var centers = Pattern.compile("class=\"(\\S+) hd-center\" data-explore=\"center\\.(\\w+) ").matcher(svg).results()
                .map(r -> r.group(2) + " " + r.group(1)).sorted().toList();
            assertEquals(List.of("AJNA gate-on-green", "G center-off", "HEAD center-off", "HEART center-off", "ROOT gate-on-brown",
                "SACRAL gate-on-red", "SOLAR gate-on-brown", "SPLEEN center-off", "THROAT gate-on-brown"), centers);
            // the Design arrows on the left in red, the Personality ones on the right, facing as on a bodygraph
            assertTrue(svg.contains("<text class=\"property-value design\" x=\"245\" y=\"1356\">Appetite \u2192</text>"));
            assertTrue(svg.contains("<text class=\"property-value design\" x=\"245\" y=\"1384\">Shores \u2192</text>"));
            assertTrue(svg.contains("<text class=\"property-value\" x=\"675\" y=\"1356\">Innocence \u2192</text>"));
            assertTrue(svg.contains("<text class=\"property-value\" x=\"675\" y=\"1384\">\u2190 Wanting</text>"));
            assertTrue(svg.contains("<text class=\"property-name\" x=\"660\" y=\"1384\">Perspective</text>"));
            assertTrue(svg.contains("y=\"1420\">Left Angle Cross of Spirit (55/59 | <tspan class=\"design\">9/16</tspan>)</text>"));
            assertTrue(svg.contains("y=\"1448\">9-52 Concentration \u00b7 17-62 Acceptance \u00b7 30-41 Recognition</text>"));
            assertTrue(svg.contains("<line class=\"properties-divider\" x1=\"80\" y1=\"1218\" x2=\"880\" y2=\"1218\"/>"));
            // one centered column below the title, the properties below its last line and the site below them
            assertTrue(svg.contains("<text class=\"title-footer\" x=\"480\" y=\"1110\">Human Design Birth Chart</text>"));
            assertTrue(svg.contains("<text class=\"date\" x=\"480\" y=\"1178\">Thu, February 24, 1955 @ 7:15 PM PST</text>"));
            assertTrue(svg.contains("<text class=\"date design\" x=\"480\" y=\"1262\">Design: Tue, November 30, 1954 @ 11:54 AM UTC</text>"));
            assertTrue(svg.contains("translate(0 62)"));
            assertTrue(svg.contains("<a href=\"http://localhost/\" target=\"_top\"><text class=\"url-footer\" x=\"480\" y=\"1556\">http://localhost</text>"), svg);
            assertTrue(svg.contains("height=\"1570\""));

            // Stephen King, born in Portland, Maine, with today's transits
            svg = m.doRequest("/wheel/image/birth-transit/194709210130/4975802/202403011000/2643743").getText();
            assertTrue(svg.contains(">Manifesting Generator</text>"), svg);
            assertTrue(svg.contains(">Sacral</text>"));
            assertTrue(svg.contains(">6/<tspan class=\"design\">2</tspan> Role Model / Hermit</text>"));
            assertTrue(svg.contains(">Single Definition</text>"));
            assertTrue(svg.contains(">Determination</text>"));
            assertTrue(svg.contains(">Left Angle Cross of the Plane (6/36 | <tspan class=\"design\">15/10</tspan>)</text>"));
            // the channels that the transits complete, which the body graph shows with those of the birth
            assertTrue(Pattern.compile("<g data-explore=\"transits\">\\s*<text class=\"property-name\" x=\"230\" y=\"\\d+\">Completed now</text>").matcher(svg).find(), svg);
            // the transits and the birth side by side
            assertTrue(svg.contains("<text class=\"legend-first\" x=\"416\" y=\"1138\">Transits</text>"));
            assertTrue(svg.contains("<text class=\"legend-second\" x=\"552\" y=\"1138\">Birth</text>"));
            assertEquals("#a4a4d4", channelColor(svg, "design"));
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"250\" y=\"1178\">Transits</text>"));
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"710\" y=\"1178\">Birth</text>"));
            assertTrue(svg.contains("<text class=\"date design\" x=\"710\" y=\"1290\">"));
            assertTrue(svg.contains("translate(0 90)"));
            assertTrue(svg.contains(">Authority</text>"));

            svg = m.doRequest("/wheel/image/202403011000/2643743").getText();
            assertFalse(svg.contains(">Authority</text>"));
            assertFalse(svg.contains("Incarnation Cross"));
            assertFalse(svg.contains("Determination"));
        }
    }

    @Test
    void chartsAreTitledByWhatTheyShow() {
        try (var m = new MockConversation(new AppSite())) {
            var titles = new LinkedHashMap<String, String>();
            titles.put("/wheel/image/202403011000/2643743", "Human Design Transit Chart");
            titles.put("/wheel/image/birth/195502241915/5391959", "Human Design Birth Chart");
            titles.put("/wheel/image/birth-transit/194709210130/4975802/202403011000/2643743", "Human Design Birth Transit Chart");
            titles.put("/wheel/image/connection/194709210130/4975802/195502241915/5391959", "Human Design Connection Chart");
            titles.put("/wheel/image/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/2643743", "Human Design Connection Transit Chart");
            titles.forEach((url, title) -> assertTrue(m.doRequest(url).getText().contains("<title>" + title + "</title>"), url));

            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", "Steve <Jobs>")).getText();
            assertTrue(svg.contains("<title>Steve &lt;Jobs&gt;, Human Design Birth Chart</title>"), svg);
        }
    }

    @Test
    void pagesDescribeTheirChart() {
        try (var m = new MockConversation(new AppSite())) {
            var pages = PAGE_PATHS;
            assertEquals(Set.copyOf(PageDescription.all()), pages.keySet());
            pages.forEach((page, path) -> {
                var html = m.doRequest(path + "?datetime=2024-03-01T10:00").getText();
                assertTrue(html.contains("<span id=\"page-heading\">" + page.heading() + "</span>"), path);
                // a tab says which page of which site it has, a link preview has the name of the site apart
                var tab = page == PageDescription.ABOUT ? "About Gates of Humanity" : page.title() + " | Gates of Humanity";
                assertTrue(html.contains("<title>" + tab + "</title>"), path);
                assertTrue(html.contains("<meta property=\"og:title\" content=\"" + page.title() + "\"/>"), path);
                assertTrue(html.contains("<meta property=\"og:site_name\" content=\"Gates of Humanity\"/>"), path);
                assertTrue(html.contains("<meta name=\"description\" content=\"" + page.description() + "\">"), path);
                assertTrue(html.contains("<link rel=\"canonical\" href=\"http://localhost" + path + "\">"), path);
                assertTrue(html.contains("<meta property=\"og:url\" content=\"http://localhost" + path + "\"/>"), path);
                assertTrue(page.description().length() <= 160, page.description());
            });
            assertEquals("This Page Doesn't Exist | Gates of Humanity", Jsoup.parse(m.doRequest("/nonexistent").getText()).title());
            // only the day has its own range table
            assertEquals(404, m.doRequest("/range/x").getStatus());
        }
    }

    private static List<String> exploreItems(String html) {
        // the row of a planet is part of its label, which the panel only shows in the cards
        return Pattern.compile("<span class=\"explore-label\">(.*?)</span> <span class=\"explore-value\">([^<]+)</span>")
            .matcher(html).results().map(r -> r.group(1).replaceAll("<[^>]+>", "") + ": " + r.group(2)).toList();
    }

    @Test
    void rangeTablesAreExploredAsTables() {
        try (var m = new MockConversation(new AppSite())) {
            var html = m.doRequest("/range/explore/d/202403011000/2643743").getText();
            assertTrue(html.contains("<h2>Explore this table</h2>"), html);
            assertTrue(html.contains("<p class=\"explore-intro\">Tapping or clicking a part of the table opens its description.</p>"), html);
            assertTrue(html.contains("<h3 class=\"explore-guide-title\">Transits by Day</h3>\n" +
                                     "    <p class=\"explore-guide-text\">Transits by Day is a table of the gate and line of every planet over a single day, with a row for each hour." +
                                     " <a href=\"http://localhost/learn/charts#transits-by-day\">More about this table</a></p>"), html);
            assertTrue(html.contains("<h3 class=\"explore-guide-title\">Reading the table</h3>"), html);
            // what the rows, columns and cells show is general, so it's only under "How to read"
            var guide = html.substring(html.indexOf("<details class=\"explore-guide\">"));
            assertTrue(guide.startsWith("<details class=\"explore-guide\">\n    <summary>How to read this table</summary>"), html);
            assertTrue(guide.contains("data-part=\"range.cells\""), html);
            assertFalse(html.contains("<h3>"), html);
        }
    }

    @Test
    void connectionTransitsShowTwoPeopleWithTheTransits() {
        try (var m = new MockConversation(new AppSite())) {
            var path = "/connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743";
            var names = new MockRequest().parameter("name", "Steve Jobs").parameter("secondName", "Steve Wozniak");
            var svg = m.doRequest("/wheel/image" + path, names).getText();
            assertEquals("black", channelColor(svg, "on"));
            assertEquals("#2f6fd6", channelColor(svg, "design"));
            assertEquals("#2e9e4f", channelColor(svg, "third"));
            // the transits on a line of their own, then the two people and the gates they share
            assertTrue(svg.contains("<rect class=\"legend-on\" x=\"440\" y=\"1128\""), svg);
            assertTrue(svg.contains("<text class=\"legend-second\" x=\"464\" y=\"1138\">Transits</text>"));
            assertTrue(svg.contains("<text class=\"legend-first\" x=\"416\" y=\"1166\">Steve Jobs</text>"));
            assertTrue(svg.contains("<rect class=\"legend-design\" x=\"424\""));
            assertTrue(svg.contains("<rect fill=\"url(#channel-design-third-vert)\""));
            assertTrue(svg.contains("<rect class=\"legend-third\" x=\"528\""));
            // the stripes of every two of the three, and of all three
            for (var combo : List.of("both", "on-third", "design-third", "all")) {
                assertTrue(svg.contains("<pattern id=\"channel-" + combo + "-vert\""), combo);
            }
            // a mark inside the ring for every gate of each person, the transits are on the wheel
            var marks = Pattern.compile("class=\"stripe-(design|third)\" transform=\"rotate").matcher(svg).results().map(r -> r.group(1)).toList();
            var jobs = Analysis.gates(Charts.birth(LocalDateTime.of(1955, 2, 25, 3, 15).atZone(ZoneOffset.UTC)));
            var woz = Analysis.gates(Charts.birth(LocalDateTime.of(1950, 8, 11, 16, 45).atZone(ZoneOffset.UTC)));
            assertEquals(jobs.size(), marks.stream().filter("design"::equals).count());
            assertEquals(woz.size(), marks.stream().filter("third"::equals).count());
            // the rows of the transits and of both people, named after them
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"480\" y=\"1074\">Steve Jobs</text>"));
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"480\" y=\"1074\">Steve Wozniak</text>"));
            assertTrue(svg.contains("<g transform=\"translate(0 134)\" data-explore=\"wheel.rows\">"), svg);
            assertTrue(svg.contains("data-explore=\"activation.6.CHIRON wheel.rows\""));
            assertTrue(svg.contains("translate(0 238)"));
            // the transits above the two people, who are side by side
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"480\" y=\"1206\">Transits</text>"), svg);
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"250\" y=\"1330\">Steve Jobs</text>"), svg);
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"710\" y=\"1330\">Steve Wozniak</text>"), svg);
            assertTrue(svg.contains(">Completed now</text><text class=\"property-value\" x=\"245\""), svg);
            assertTrue(svg.contains(">3-60 Mutation \u00b7 24-61 Awareness \u00b7 25-51 Initiation</text>"), svg);
            assertTrue(svg.contains(">Defined centers</text><text class=\"property-value\" x=\"245\" y=\"1300\">9 of 9</text>"), svg);

            var explore = m.doRequest("/wheel/explore" + path, names).getText();
            assertTrue(explore.contains("<a href=\"http://localhost/learn/charts#connection-transit-chart\">More about this chart</a>"), explore);
            var items = exploreItems(explore);
            // the centers and channels that the body graph shows, with the transits
            assertTrue(items.containsAll(List.of("Defined centers: 9 of 9", "Channels: 14 channels defined, 4 of them completed now")), items.toString());
            assertTrue(items.containsAll(List.of("Steve Jobs: Generator, Emotional Authority, 6/3 Profile", "Steve Wozniak: Projector, Sounding Board Authority, 6/2 Profile",
                "Electromagnetic: 5 channels", "Completed now: 4 channels", "37-40 Community: With the transits")), items.toString());
            assertTrue(explore.contains("data-part=\"activation.6.SUN\""), explore);
            assertTrue(m.doRequest("/wheel/explore" + path, new MockRequest().parameter("name", "<b>").parameter("secondName", "Woz")).getText()
                .contains("<span class=\"explore-label-row\">&lt;b&gt;, Personality </span>Sun"));
            assertTrue(explore.contains(Descriptions.of("about.transits.connection")), explore);

            // nothing to explore until both people are known, and errors for either of them
            assertEquals("", m.doRequest("/wheel/explore/connection-transit").getText());
            assertTrue(m.doRequest("/wheel/image/connection-transit/195502241915/5391959/195008110945/999999999/202403011000/2643743").getText()
                .contains("This city isn&apos;t known"));
            assertTrue(m.doRequest("/wheel/image/connection-transit/195502241915/5391959/179008110945/5391959/202403011000/2643743").getText()
                .contains("Birth charts can be made from"));
        }
    }

    // the text of the card of an item, without its markup
    private static String card(String html, String part) {
        var start = html.indexOf("data-part=\"" + part + "\"");
        return html.substring(start, html.indexOf("</details>", start)).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    @Test
    void theCardsOfThePlanetsExplainWhatTheRowsShow() {
        try (var m = new MockConversation(new AppSite())) {
            var transit = m.doRequest("/wheel/explore/202403011000/2643743").getText();
            // in the order of the rows, the Nodes after the Moon
            var planets = exploreItems(transit).stream().filter(item -> item.startsWith("Transit ")).map(item -> item.substring(0, item.indexOf(':'))).toList();
            assertEquals(List.of("Transit Sun", "Transit Earth", "Transit Moon", "Transit North Node", "Transit South Node", "Transit Mercury", "Transit Venus",
                "Transit Mars", "Transit Jupiter", "Transit Saturn", "Transit Uranus", "Transit Neptune", "Transit Pluto", "Transit Chiron"), planets);
            // a transit is there for everyone, its planets give their theme in a few words, the Learn page has the rest
            var mercury = card(transit, "activation.1.MERCURY");
            assertTrue(mercury.matches(".*" + Pattern.quote(Descriptions.of("transit.planet")) +
                " Mercury is in gate 63 from [^.]+ until [^.]+\\. It's in line \\d from [^.]+ until [^.]+\\. Mercury is about communication\\. Gate 63.*"), mercury);
            // Chiron is shown but brings nothing
            var chiron = card(transit, "activation.1.CHIRON");
            assertTrue(chiron.contains(Descriptions.of("planet.CHIRON") + " " + Descriptions.of("planet.CHIRON.more")), chiron);
            assertFalse(chiron.contains(Descriptions.of("transit.planet")), chiron);
            assertFalse(chiron.contains("is in gate"), chiron);
            assertTrue(card(transit, "channels").contains(Descriptions.of("about.channels.transit")));
            // both triangles, each explained, with a link to them
            var earth = card(transit, "activation.1.EARTH");
            assertTrue(earth.contains(Descriptions.of("fixing.exalted") + " " + Descriptions.of("fixing.detriment") + " All 64 gates in Learn Earth in Learn Exaltation and detriment in Learn"), earth);
            assertTrue(transit.contains("<a href=\"http://localhost/learn/the-chart#exaltation-and-detriment\">Exaltation and detriment in Learn</a>"), transit);

            var birth = m.doRequest("/wheel/explore/birth/195502241915/5391959").getText();
            assertFalse(card(birth, "activation.1.MERCURY").contains(Descriptions.of("transit.planet")));
            assertFalse(card(birth, "activation.1.MERCURY").contains("is in gate"));
            assertFalse(card(birth, "activation.1.MOON").contains(Descriptions.of("fixing.detriment")));
            // the color of a Variable as well as its arrow
            var determination = card(birth, "variable.DETERMINATION");
            assertTrue(determination.contains(Descriptions.of("variable.DETERMINATION.color.1") + " " + Descriptions.of("variable.DETERMINATION.color.1.right") + " " +
                Descriptions.of("variable.DETERMINATION.right")), determination);
            assertFalse(determination.contains(Descriptions.of("variable.DETERMINATION.color.1.left")), determination);
            assertTrue(card(birth, "type").contains("All the Types in Learn"));
            // the circuit of a channel, with a link to it
            var abstraction = card(birth, "channel.30-41");
            assertTrue(abstraction.contains("Circuit " + Descriptions.of("circuit.ABSTRACT") + " All 36 channels in Learn Abstract circuit in Learn"), abstraction);
            assertTrue(birth.contains("<a href=\"http://localhost/learn/channels#abstract-circuit\">Abstract circuit in Learn</a>"), birth);
            var variables = m.doRequest("/learn/variables").getText();
            assertTrue(variables.contains(Descriptions.of("variable.PERSPECTIVE.color.6")));
            assertTrue(variables.contains(Descriptions.of("variable.ENVIRONMENT.color.6") + " " + Descriptions.of("variable.ENVIRONMENT.color.6.left") + " " +
                Descriptions.of("variable.ENVIRONMENT.color.6.right")), variables);
        }
    }

    @Test
    void theCardsOfTheTransitsSayHowLongEachPlanetStays() {
        try (var m = new MockConversation(new AppSite())) {
            // ten in the morning in San Francisco, the times on its calendar
            var html = m.doRequest("/wheel/explore/202403011000/5391959").getText();
            var zone = ZoneId.of("America/Los_Angeles");
            var moment = Instant.parse("2024-03-01T18:00:00Z");
            var format = DateTimeFormatter.ofPattern("E, MMMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);
            for (var planet : List.of(Planet.MOON, Planet.SUN, Planet.PLUTO)) {
                var gate = Upcoming.gateStay(planet, moment);
                var line = Upcoming.lineStay(planet, moment);
                var position = Charts.position(planet, moment);
                var name = planet == Planet.PLUTO ? "Pluto" : planet == Planet.SUN ? "The Sun" : "The Moon";
                var card = card(html, "activation.1." + planet.name());
                assertTrue(card.contains(name + " is in gate " + position.gateNumber() + " from " + format.format(gate.start().atZone(zone)) +
                    " until " + format.format(gate.end().atZone(zone)) + ". It's in line " + position.gateLine() + " from " +
                    format.format(line.start().atZone(zone)) + " until " + format.format(line.end().atZone(zone)) + "."), card);
            }
            // the birth transit has the same for its transits
            var birth_transit = m.doRequest("/wheel/explore/birth-transit/195502241915/5391959/202403011000/5391959").getText();
            assertTrue(card(birth_transit, "activation.1.MOON").contains("The Moon is in gate"), birth_transit);
            assertFalse(card(birth_transit, "activation.3.MOON").contains("is in gate"), birth_transit);
        }
    }

    @Test
    void comingUpStopsWhereTheTablesOfThePlanetsEnd() {
        try (var m = new MockConversation(new AppSite())) {
            // the Sun is still in its last gate when the tables end, a birth with the other end of
            // one of its channels sees it complete that channel without leaving
            var end = Ephemeris.end();
            var last_gate = new Position(Planet.SUN, Ephemeris.longitude(Body.SUN, end)).gateNumber();
            var enters = end;
            while (new Position(Planet.SUN, Ephemeris.longitude(Body.SUN, enters)).gateNumber() == last_gate) {
                enters = enters.minus(Duration.ofHours(1));
            }
            var channel = Analysis.channels(last_gate).getFirst();
            var other = channel.gate1() == last_gate ? channel.gate2() : channel.gate1();
            var birth = LocalDateTime.of(1960, 1, 1, 12, 0);
            while (!Analysis.gates(Charts.birth(birth.atZone(ZoneOffset.UTC))).contains(other) ||
                   Analysis.definedChannels(Analysis.gates(Charts.birth(birth.atZone(ZoneOffset.UTC)))).contains(channel)) {
                birth = birth.plusDays(1);
            }
            var format = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
            var html = m.doRequest("/wheel/explore/birth-transit/" + birth.format(format) + "/2643743/" +
                                   enters.atZone(ZoneOffset.UTC).format(format) + "/2643743").getText();
            assertTrue(card(html, "upcoming.SUN").contains("is still there when the tables of the planets end on Tue, December 17, 2199"), card(html, "upcoming.SUN"));
            // less than a year is left to look ahead
            assertTrue(card(html, "upcoming.PLUTO").contains("Pluto None before December 17, 2199"), card(html, "upcoming.PLUTO"));
            assertFalse(html.contains("None in the next year"), html);
        }
    }

    @Test
    void birthChartsCanBeExplored() {
        try (var m = new MockConversation(new AppSite())) {
            var response = m.doRequest("/wheel/explore/birth/195502241915/5391959");
            assertEquals(200, response.getStatus());
            var html = response.getText();
            assertEquals(List.of("Type: Generator", "Authority: Emotional", "Profile: 6/3 Role Model / Martyr",
                "Definition: Split Definition (Wide)", "Incarnation Cross: Left Angle Cross of Spirit",
                "Head: Open", "Ajna: Defined", "Throat: Defined", "G Center: Open", "Heart: Open", "Spleen: Open",
                "Sacral: Defined", "Solar Plexus: Defined", "Root: Defined", "Channels: 3 channels defined",
                "Determination: Appetite &rarr;", "Environment: Shores &rarr;", "Motivation: Innocence &rarr;",
                "Perspective: &larr; Wanting"), exploreItems(html).subList(0, 19));
            // the Sun moves into the next line three minutes after the birth
            assertEquals(List.of("Type: At least 3 hours before and after", "Authority: 1 hour 45 min earlier, at least 3 hours later",
                "Profile: 2 hours 56 min earlier, 2 min later", "Definition: At least 3 hours earlier, 2 hours 57 min later",
                "Incarnation Cross: At least 3 hours earlier, 2 min later", "Channels: 1 hour 45 min earlier, 2 min later",
                "Variables: 1 hour 4 min earlier, 2 min later", "Gates and lines: 12 min earlier, 2 min later"), exploreItems(html).subList(19, 27));
            assertTrue(card(html, "birthtime.PROFILE").contains("The Profile, 6/3, stays the same for a birth from 4:19 PM to 7:17 PM. " +
                "With a birth at 4:18 PM, the Profile would be 6/2. With a birth at 7:18 PM, the Profile would be 1/3. Reading a chart in Learn"), card(html, "birthtime.PROFILE"));
            assertTrue(card(html, "birthtime.ACTIVATIONS").contains("With a birth at 7:02 PM, the Personality Moon would be in 17.4. " +
                "With a birth at 7:18 PM, the Personality Sun would be in 37.1 and the Personality Earth would be in 40.1."), card(html, "birthtime.ACTIVATIONS"));
            assertTrue(card(html, "birthtime.CROSS").contains("With an earlier birth, it stays the same for at least 3 hours. With a birth at 7:18 PM, the Incarnation Cross would be the Right Angle Cross of Planning."), card(html, "birthtime.CROSS"));
            assertTrue(card(html, "birthtime.CHANNELS").contains("With a birth at 5:29 PM, the channel of Mutation (3-60) would be defined too and the channel of Recognition (30-41) wouldn't be defined."), card(html, "birthtime.CHANNELS"));
            assertTrue(card(html, "birthtime.TYPE").contains("The Type, Generator, stays the same for any birth within 3 hours before and after, from 4:15 PM to 10:15 PM. Reading a chart in Learn"), card(html, "birthtime.TYPE"));
            assertTrue(html.contains("data-part=\"birthtime.ACTIVATIONS\" data-related=\"activation.1.MOON activation.1.SUN activation.1.EARTH\""), html);
            // a bar of each range on the six hours around the birth
            assertTrue(html.contains("<span>3 hours earlier</span><span>7:15 PM</span><span>3 hours later</span>"), html);
            assertEquals(List.of("0.00 100.00", "20.83 79.17", "1.11 49.44", "0.00 99.17", "0.00 50.56", "20.83 29.72", "32.22 18.33", "46.67 3.89"),
                Pattern.compile("left: ([\\d.]+)%; width: ([\\d.]+)%").matcher(html).results().map(r -> r.group(1) + " " + r.group(2)).toList());
            // the tables of the planets cut the time looked at short
            var first = Ephemeris.firstChart().truncatedTo(ChronoUnit.MINUTES).plus(Duration.ofMinutes(31)).atZone(ZoneId.of("Europe/London"));
            var early = m.doRequest("/wheel/explore/birth/" + DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(first) + "/2643743").getText();
            // the first whole minute of the tables is the one after the minute they begin in
            var minutes = 30;
            assertTrue(card(early, "birthtime.TYPE").contains(" With an earlier birth, it stays the same until the tables of the planets begin, " + minutes + " min earlier. " +
                "With a later birth, it stays the same for at least 3 hours. "), card(early, "birthtime.TYPE"));
            assertTrue(exploreItems(early).contains("Type: At least " + minutes + " min earlier, at least 3 hours later"), exploreItems(early).toString());
            // a time on another day says which
            var late = m.doRequest("/wheel/explore/birth/195502242355/5391959").getText();
            assertTrue(card(late, "birthtime.TYPE").contains(" from 8:55 PM to February 25, 2:55 AM."), card(late, "birthtime.TYPE"));
            // the planets of the Personality and of the Design, Chiron among them
            var planets = exploreItems(html).subList(27, exploreItems(html).size());
            assertEquals(28, planets.size(), planets.toString());
            assertEquals("Personality Sun: 55.6 Spirit", planets.getFirst());
            assertEquals("Design Sun: 9.3&#9660; Focus", planets.get(14));
            assertEquals("Design Chiron: 61.5 Mystery", planets.getLast());
            assertTrue(html.contains("Gate 55, Spirit, is hexagram 55 of the I Ching, where it's called Abundance."), html);
            assertTrue(html.contains("This gate is in the Solar Plexus, at one end of the channel of Emoting (39-55)."), html);
            assertTrue(html.contains("Line 6 sees the theme from a distance"), html);
            assertTrue(html.contains("data-part=\"activation.2.SUN\""), html);
            // the panel shows the row in the title of its section, the card of a tap in the label
            assertTrue(html.contains("<span class=\"explore-label\"><span class=\"explore-label-row\">Design </span>Sun</span>"), html);
            // the Personality line, then the Design line
            assertTrue(html.contains("Line 6, the Role Model"), html);
            assertTrue(html.indexOf("Line 3, the Martyr") > html.indexOf("Line 6, the Role Model"), html);
            assertTrue(html.contains("<h3>Variables</h3>"));
            // each arrow is described in its own direction, Appetite faces right and Wanting left
            assertTrue(html.contains("so the brain is passive and receptive"), html);
            assertTrue(html.contains("so the view is focused"), html);
            // under "How to read", what the chart is for first, with a link to the rest of its description in the guide to the charts
            assertTrue(html.startsWith("<div id=\"chart-summary\""), html);
            assertTrue(html.contains("</div>\n<h2>Explore this chart</h2>\n" +
                                       "<p class=\"explore-intro\">Tapping or clicking a part of the chart, or an item below, opens its description.</p>\n" +
                                       "<details class=\"explore-guide\">\n    <summary>How to read this chart</summary>\n" +
                                       "    <h3 class=\"explore-guide-title\">Birth Chart</h3>\n" +
                                       "    <p class=\"explore-guide-text\">The Birth Chart shows how a person is built, calculated from the date, the time and the place of birth." +
                                       " <a href=\"http://localhost/learn/charts#birth-chart\">More about this chart</a></p>\n"), html);
            // the general introductions and the parts of every chart are under "How to read", before what's in this chart
            var guide_end = html.indexOf("<details class=\"explore-about\">\n    <summary><h3>Overview</h3>");
            var order = List.of("<h3 class=\"explore-guide-title\">Human Design</h3>", "<p class=\"explore-guide-text\">Human Design describes how",
                "<h3 class=\"explore-guide-title\">Reading the chart</h3>", "<p class=\"explore-guide-text\">The chart starts from astrology.",
                "<p class=\"explore-guide-text\">Those gates light up the body graph", "<h3 class=\"explore-guide-title\">Parts of the chart</h3>",
                "data-part=\"wheel.zodiac\"", "data-part=\"wheel.rows\"").stream().map(html::indexOf).toList();
            assertTrue(order.getFirst() > 0 && order.equals(order.stream().sorted().toList()) && order.getLast() < guide_end, order.toString());
            assertTrue(html.substring(0, guide_end).strip().endsWith("</details>\n</div>\n</details>"), html);
            // a section with a general introduction keeps it behind its title
            assertTrue(html.contains("<details class=\"explore-about\">\n    <summary><h3>Centers</h3><span class=\"explore-about-icon\" aria-hidden=\"true\">?</span></summary>\n    \n" +
                                     "<p class=\"explore-section-intro\">The body graph has nine centers, and four of them are motors"), html);
            assertTrue(html.contains("<h3>Channels</h3>"), html);
            // what a part is in general, before what it is in this chart
            assertTrue(html.indexOf("The Type comes from") < html.indexOf("Generators have a defined Sacral"), html);
            // Overview, Centers, Channels, Variables, Birth time, the planets in general and the Personality, and the Design
            assertEquals(8, html.split("<p class=\"explore-section-intro\">").length - 1);

            // the birth chart of a birth transit chart, once it has one
            var birth_transit = exploreItems(m.doRequest("/wheel/explore/birth-transit/195502241915/5391959/202403011000/2643743").getText());
            // as the body graph shows it, with the centers and channels that the transits define too
            var with_transits = new ArrayList<>(exploreItems(html).subList(0, 19));
            for (var center : List.of("Head", "G Center", "Heart", "Spleen")) {
                with_transits.set(with_transits.indexOf(center + ": Open"), center + ": Defined now");
            }
            with_transits.set(with_transits.indexOf("Channels: 3 channels defined"), "Channels: 8 channels defined, 5 of them completed now");
            assertEquals(with_transits, birth_transit.subList(0, 19));
            assertTrue(birth_transit.contains("Completed now: 5 channels"), birth_transit.toString());
            // the next channel that each planet completes with the birth chart, on the calendar of the transit city
            assertEquals(List.of("Sun: April 10, 2024, 42-53 Maturation", "Earth: March 12, 2024, 6-59 Mating", "Moon: March 2, 2024, 10-34 Exploration",
                "North Node: None in the next year", "South Node: May 10, 2024, 16-48 the Wavelength", "Mercury: March 22, 2024, 42-53 Maturation",
                "Venus: April 22, 2024, 42-53 Maturation", "Mars: May 27, 2024, 42-53 Maturation", "Jupiter: April 8, 2024, 23-43 Structuring",
                "Saturn: None in the next year", "Uranus: June 6, 2024, 1-8 Inspiration", "Neptune: None in the next year", "Pluto: May 24, 2024, 3-60 Mutation"),
                birth_transit.subList(19 + 1 + 5, 19 + 1 + 5 + 13));
            var birth_transit_html = m.doRequest("/wheel/explore/birth-transit/195502241915/5391959/202403011000/2643743").getText();
            var moon = card(birth_transit_html, "upcoming.MOON");
            assertTrue(moon.contains("The Moon moves into gate 34, Power, on Sat, March 2, 2024 at 2:11 PM GMT, completing the channel of Exploration (10-34) " +
                                     "with the birth chart, and stays there until Sun, March 3, 2024 at 12:51 AM GMT."), moon);
            assertTrue(birth_transit_html.contains("onclick=\"return showDate('2024-03-02T14:11', image_url);\">Show the transits of that moment</button>"), birth_transit_html);
            // the Sun, the Earth, the Moon and the Nodes, but Mercury
            var mercury = card(birth_transit_html, "upcoming.MERCURY");
            assertTrue(mercury.contains(" Mercury moves into gate 42") && !mercury.contains("The Mercury"), mercury);
            assertEquals(List.of("Saturn Return: November 28, 1984, age 29", "Uranus Opposition: February 14, 1994, age 38", "Chiron Return: March 30, 2005, age 50"),
                birth_transit.subList(19 + 1 + 5 + 13, 19 + 1 + 5 + 13 + 3));
            // the transits are the first row of planets, then the Personality and the Design of the birth
            assertEquals(19 + 1 + 5 + 13 + 3 + 3 * 14, birth_transit.size(), birth_transit.toString());
            assertEquals("Personality Sun: 55.6 Spirit", birth_transit.get(19 + 1 + 5 + 13 + 3 + 14));
            assertEquals("Transit Sun: 37.6 Friendship", birth_transit.get(19 + 1 + 5 + 13 + 3));
            // the wheel of a birth transit chart only shows the transits once the birth chart is known
            assertEquals("", m.doRequest("/wheel/explore/birth-transit").getText());
            // the image explains the errors
            assertEquals("", m.doRequest("/wheel/explore/birth/180001101200/2643743").getText());
            assertEquals("", m.doRequest("/wheel/explore/birth/195502241915/1").getText());
            var invalid = m.doRequest("/wheel/explore/birth/197513011430/2643743");
            assertEquals(400, invalid.getStatus());
            assertEquals("", invalid.getText());
        }
    }

    @Test
    void everyPartOfEveryChartThatCanBeTappedIsExplained() {
        try (var m = new MockConversation(new AppSite())) {
            for (var chart : List.of("birth/195502241915/5391959", "202403011000/2643743",
                                     "birth-transit/195502241915/5391959/202403011000/2643743",
                                     "connection/195502241915/5391959/195008110945/5391959",
                                     "connection/195502241915/5391959",
                                     "connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743")) {
                var svg = m.doRequest("/wheel/image/" + chart).getText();
                var tapped = Pattern.compile("data-explore=\"([^\"]+)\"").matcher(svg).results().map(r -> r.group(1)).collect(Collectors.toSet());
                var html = m.doRequest("/wheel/explore/" + chart).getText();
                var explained = Pattern.compile("data-part=\"([^\"]+)\"").matcher(html).results().map(r -> r.group(1)).collect(Collectors.toSet());
                // the rings that a tap finds from where it falls, in main.js
                tapped.addAll(List.of("wheel.zodiac", "wheel.planets", "wheel.gates", "wheel.bodygraph"));
                // the summary that phones show below the wheel
                var summary = Jsoup.parse(html).select("#chart-summary [data-explore]").eachAttr("data-explore");
                assertTrue(summary.size() >= 15, chart + ": " + summary);
                tapped.addAll(summary);
                // a tap opens the first of its parts that the panel explains
                for (var parts : tapped) {
                    assertTrue(Arrays.stream(parts.split(" ")).anyMatch(explained::contains), chart + ": " + parts + " " + explained);
                }
                assertTrue(tapped.size() >= 40, chart + ": " + tapped);
            }
        }
    }

    private static final Map<String, Map<String, String>> SUMMARIZED = Map.of(
        "birth/195502241915/5391959", Map.of(),
        "birth/194709210130/4975802", Map.of("name", "Stephen <King>"),
        "202403011000/2643743", Map.of(),
        "birth-transit/195502241915/5391959/202403011000/2643743", Map.of(),
        "birth-transit/202403011000/2643743/202403051200", Map.of(),
        "connection/195502241915/5391959/195008110945/5391959", Map.of("name", "Steve Jobs"),
        "connection/195502241915/5391959", Map.of(),
        "connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743", Map.of("name", "Steve Jobs", "secondName", "Steve Wozniak"));

    private static MockRequest named(Map<String, String> names) {
        var request = new MockRequest();
        names.forEach(request::parameter);
        return request;
    }

    // the triangles after a gate and line of the drawing, by the names of their symbols
    private static String marks(org.jsoup.nodes.Element value) {
        var marks = new ArrayList<String>();
        for (var mark = value.nextElementSibling(); mark != null && mark.tagName().equals("use"); mark = mark.nextElementSibling()) {
            if (mark.attr("href").matches("#(exalt|detr).*")) {
                marks.add(mark.attr("href"));
            }
        }
        return String.join("", marks);
    }

    // the properties by their names, with the lines of one that takes several joined as they are written
    private static Map<String, String> properties(Stream<List<String>> lines) {
        var properties = new java.util.TreeMap<String, String>();
        var label = new String[]{""};
        lines.forEach(line -> {
            label[0] = line.getFirst().isEmpty() ? label[0] : line.getFirst();
            properties.merge(label[0], line.getLast(), (before, next) -> before + " \u00b7 " + next);
        });
        return properties;
    }

    @Test
    void theSummaryForPhonesSaysWhatTheDrawingSaysBelowTheWheel() {
        try (var m = new MockConversation(new AppSite())) {
            for (var chart : SUMMARIZED.entrySet()) {
                var path = chart.getKey();
                var svg = Jsoup.parse(m.doRequest("/wheel/image/" + path, named(chart.getValue())).getText(), "", org.jsoup.parser.Parser.xmlParser());
                var summary = Jsoup.parse(m.doRequest("/wheel/explore/" + path, named(chart.getValue())).getText()).selectFirst("#chart-summary");
                assertNotNull(summary, path);

                // what the summary says instead is in the parts of the drawing that phones leave out, also for who has
                // the page read aloud, and nothing of the wheel is
                var below = svg.select("svg > g[data-explore=wheel.rows], svg > g.below-wheel");
                assertEquals(svg.select("text.planet-gate[data-explore], text.title-footer, text.name-footer, text.date, text.coordinates, text.property-name, text.property-value").size(),
                    below.select("text.planet-gate[data-explore], text.title-footer, text.name-footer, text.date, text.coordinates, text.property-name, text.property-value").size(), path);
                assertEquals(1, svg.select("svg > g.below-wheel").size(), path);
                assertEquals(0, below.select("[class^=gate], [class*=hd-], [data-explore^=wheel.planets], [data-explore*=wheel.gates]").size(), path);
                assertTrue(svg.select("text").size() > below.select("text").size() + 64, path);

                // the gate and line of every planet of every row, with its marks
                var drawn_planets = new java.util.TreeMap<String, String>();
                for (var value : svg.select("text.planet-gate[data-explore]")) {
                    if (!value.text().isEmpty()) {
                        drawn_planets.put(value.attr("data-explore").split(" ")[0], value.text() + marks(value));
                    }
                }
                var planets = new java.util.TreeMap<String, String>();
                for (var planet : summary.select(".summary-planet")) {
                    var parts = planet.attr("data-explore").split(" ");
                    var values = planet.select("> span");
                    for (var row = 0; row < 2; ++row) {
                        if (!values.get(row).text().isEmpty()) {
                            planets.put(parts[row], values.get(row).text() + String.join("", values.get(row).select(".summary-mark use").eachAttr("href")));
                        }
                    }
                    assertEquals("#planet-" + Planet.valueOf(parts[0].substring(parts[0].lastIndexOf('.') + 1)).id_, planet.selectFirst("> svg use").attr("href"), path);
                }
                assertFalse(planets.isEmpty(), path);
                assertEquals(drawn_planets, planets, path);

                // the names above the rows, then the title with the moments and places
                var texts = svg.select("text");
                var title = texts.indexOf(svg.selectFirst("text.title-footer"));
                assertEquals(texts.subList(0, title).stream().filter(text -> text.hasClass("name-footer")).map(org.jsoup.nodes.Element::text).toList(),
                             summary.select(".summary-chart .name-footer").eachText(), path);
                assertEquals(texts.subList(title, texts.size()).stream().filter(text -> Stream.of("legend-", "property-", "url-").noneMatch(text.className()::startsWith))
                                 .map(org.jsoup.nodes.Element::text).toList(),
                             summary.select("> p:not(.summary-legend), .summary-detail p").eachText(), path);

                // the legend, in the colors of the charts
                assertEquals(svg.select("text[class^=legend-]").eachText(), summary.select(".summary-legend span").eachText(), path);
                assertEquals(svg.select("rect[class^=legend-], rect[fill^=url(#channel-][y]").stream()
                                 .map(swatch -> swatch.hasAttr("class") ? swatch.className() : swatch.attr("fill").replaceAll("url\\(#channel-(.*)-vert\\)", "legend-$1")).toList(),
                             summary.select(".summary-legend i").eachAttr("class"), path);
                var style = svg.selectFirst("style").wholeText();
                for (var side : List.of("on", "design", "third")) {
                    var color = Pattern.compile("rect\\.legend-" + side + " \\{\\s*fill: ([^;]+);").matcher(style);
                    assertTrue(color.find(), side);
                    assertTrue(summary.attr("style").contains("--chart-" + side + ": " + color.group(1)), path + ": " + summary.attr("style"));
                }

                // the properties, on the lines that the drawing gives them
                var drawn_properties = properties(svg.select("text.property-name").stream().flatMap(name ->
                    name.parent().select("text.property-value").stream().map(value -> List.of(name.text(), value.text()))));
                var summarized = properties(summary.select(".summary-properties > div").stream().flatMap(property -> {
                    var lines = property.select(".summary-line");
                    return (lines.isEmpty() ? property.select("dd") : lines).stream().map(line -> List.of(property.selectFirst("dt").text(), line.text()));
                }));
                assertEquals(drawn_properties, summarized, path);
            }
        }
    }

    @Test
    void theSummaryForPhonesAsksForNothingThatTheDrawingDoesNot() {
        // both are filled by the same code, which the engine stops when it sets a value that one of
        // them lacks, so a value that only the summary has is one that nothing sets
        var drawing = Set.of(rife.template.TemplateFactory.SVG.get("wheel").getAvailableValueIds());
        var summary = new java.util.TreeSet<>(List.of(rife.template.TemplateFactory.HTML.get("wheel_summary").getAvailableValueIds()));
        assertTrue(summary.containsAll(List.of("sun-gate1", "chiron-gate6", "pluto-d6-now", "line-item-text", "analysis-type", "legend-both")), summary.toString());
        summary.removeAll(drawing);
        assertEquals(Set.of(), summary);
    }

    @Test
    void rangeTablesCanBeExplored() {
        try (var m = new MockConversation(new AppSite())) {
            for (var range : List.of("202403011000/2643743", "d/202403011000/2643743")) {
                var table = m.doRequest("/range/table/" + range).getText();
                var tapped = Pattern.compile("data-explore=\"([^\"]+)\"").matcher(table).results().map(r -> r.group(1)).collect(Collectors.toSet());
                var html = m.doRequest("/range/explore/" + range).getText();
                var explained = Pattern.compile("data-part=\"([^\"]+)\"").matcher(html).results().map(r -> r.group(1)).collect(Collectors.toSet());
                assertEquals(Set.of("range.dates", "range.planets", "range.cells"), tapped);
                assertEquals(tapped, explained);
                assertTrue(html.contains("<span class=\"explore-label\">" + (range.startsWith("d/") ? "Hours" : "Days") + "</span>"), html);
            }
            assertEquals("", m.doRequest("/range/explore/219912011200/2643743").getText());
        }
    }

    @Test
    void transitAndConnectionChartsCanBeExplored() {
        try (var m = new MockConversation(new AppSite())) {
            var transit = m.doRequest("/wheel/explore/202403011000/2643743").getText();
            var gates = Analysis.gates(Charts.transit(ZonedDateTime.parse("2024-03-01T10:00:00Z")));
            var channels = Analysis.definedChannels(gates);
            var items = exploreItems(transit);
            assertTrue(items.contains("Channels: " + channels.size() + " channel" + (channels.size() == 1 ? "" : "s") + " defined"), items.toString());
            for (var channel : channels) {
                assertTrue(transit.contains("data-part=\"channel." + channel.key() + "\""), channel.toString());
            }
            assertTrue(items.stream().anyMatch(item -> item.startsWith("Defined centers: ")), items.toString());
            assertTrue(transit.contains("in black. When both ends of a channel are activated"), transit);

            // the first date of the path is the second person's
            var connection = exploreItems(m.doRequest("/wheel/explore/connection/195502241915/5391959/195008110945/5391959",
                new MockRequest().parameter("name", "Steve Wozniak").parameter("secondName", "Steve Jobs")).getText());
            assertTrue(connection.contains("Steve Jobs: Generator, Emotional Authority, 6/3 Profile"), connection.toString());
            // the paragraphs of a person are named, with a link to each of them in Learn
            var person = m.doRequest("/wheel/explore/connection/195502241915/5391959/195008110945/5391959").getText();
            assertTrue(person.contains("<p><span class=\"explore-paragraph-label\">Authority</span> " + Descriptions.of("authority.EMOTIONAL") + "</p>"), person);
            // a triangle that only the other person fixes links to what outlined triangles are
            assertTrue(card(person, "activation.4.MERCURY").contains("Outlined triangles in Learn"), card(person, "activation.4.MERCURY"));
            assertTrue(person.contains("<a href=\"http://localhost/learn/the-chart#outlined-triangles\">Outlined triangles in Learn</a>"), person);
            var person_card = card(person, "person.2");
            assertTrue(person_card.contains("All the Types in Learn All the Authorities in Learn All the Profiles and Crosses in Learn"), person_card);
            assertTrue(connection.contains("Electromagnetic: 5 channels"), connection.toString());
            // the lines that only the other person fixes, outlined in the rows and explained in their card
            var connection_html = m.doRequest("/wheel/explore/connection/195502241915/5391959/195008110945/5391959").getText();
            assertTrue(connection.contains("Steve Jobs, Design Mercury: 43.6&#9651; Insight"), connection.toString());
            assertTrue(connection_html.contains(Descriptions.of("fixing.other")), connection_html);
            var connection_svg = m.doRequest("/wheel/image/connection/195502241915/5391959/195008110945/5391959").getText();
            assertTrue(connection_svg.contains("<use href=\"#exalt4-now\" x=\"404\" y=\"1150\"/>"), connection_svg);
            // a birth chart fixes its own lines
            assertFalse(Pattern.compile("href=\"#(exalt|detr)\\d-now\"").matcher(m.doRequest("/wheel/image/birth/195502241915/5391959").getText()).find());
            // named after the person with the whole channel, as below the chart
            assertTrue(connection.containsAll(List.of("30-41 Recognition: Dominance (Steve Jobs)", "47-64 Abstraction: Dominance (Steve Wozniak)",
                "9-52 Concentration: Compromise (Steve Jobs)", "23-43 Structuring: Compromise (Steve Wozniak)")), connection.toString());
        }
    }

    @Test
    void pagesReadTheSameInAnAppAsInABrowser() throws IOException {
        var web = Pattern.compile("\\b(browsers?|web ?sites?|sites?)\\b", Pattern.CASE_INSENSITIVE);
        // an app that shows the pages says that it keeps the saved people, rather than a browser
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.keptIn", "this app");
        properties.put("gatesofhumanity.shareRootUrl", "https://charts.example.com");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var people = Jsoup.parse(m.doRequest("/people").getText());
            assertTrue(people.selectFirst("p.learn-lead").text().contains("They stay in this app, they're never stored anywhere else."));
            assertTrue(people.select("head script:not([src])").first().data().contains("kept_in = 'this app';"));
            // and shares where a chart is on the web, its own address means nothing elsewhere
            assertTrue(Jsoup.parse(m.doRequest("/wheel/birth").getText()).select("head script:not([src])").first().data().contains("share_root = 'https://charts.example.com';"));
            assertTrue(m.doRequest("/about").getText().contains("The people you save stay in this app, they're never stored anywhere else."));
            // as does a drawing, which is saved and sent on
            assertTrue(m.doRequest("/wheel/image/birth/195502241915/5391959").getText()
                .contains("<a href=\"https://charts.example.com\" target=\"_top\"><text class=\"url-footer\" x=\"480\" y=\"1556\">https://charts.example.com</text>"));
            for (var path : PAGE_PATHS.values()) {
                var said = web.matcher(Jsoup.parse(m.doRequest(path).getText()).body().text());
                assertFalse(said.find(), () -> path + " says " + said.group());
            }
            var missing = web.matcher(Jsoup.parse(m.doRequest("/nonexistent").getText()).body().text());
            assertFalse(missing.find(), () -> "the missing page says " + missing.group());
        }
        try (var explore = AppSite.class.getResourceAsStream("/explore.tsv")) {
            for (var line : new String(explore.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.startsWith("#")).toList()) {
                var said = web.matcher(line);
                assertFalse(said.find(), () -> line);
            }
        }
    }

    @Test
    void thePagesOfChartsWelcomeAFirstVisit() {
        var charts = Set.of("/wheel", "/wheel/birth", "/wheel/birth-transit", "/wheel/connection", "/wheel/connection-transit", "/range", "/range/d");
        try (var m = new MockConversation(new AppSite())) {
            for (var path : charts) {
                var welcome = Jsoup.parse(m.doRequest(path).getText()).selectFirst("body > dialog#welcome[aria-labelledby=welcome-name]");
                assertEquals("Gates of Humanity", welcome.selectFirst("h2#welcome-name").text(), path);
                assertEquals("http://localhost/images/emblem.svg", welcome.selectFirst("img[alt='']").attr("src"), path);
                assertEquals(List.of("Charts and transits", "Tap or click to explore", "Kept in this browser"), welcome.select("li h3").eachText(), path);
                assertTrue(welcome.select("li p").last().text().startsWith("The people you save stay in this browser, they're never stored anywhere else."), path);
                assertEquals("http://localhost/learn", welcome.selectFirst("li a").attr("href"), path);
                // closes without a script, and with Enter
                assertEquals("Start", welcome.selectFirst("form[method=dialog] button[type=submit][autofocus]").text(), path);
            }
            // the pages of text are read by who looked for them
            for (var path : PAGE_PATHS.values()) {
                var page = Jsoup.parse(m.doRequest(path).getText());
                assertEquals(charts.contains(path), page.selectFirst("#welcome") != null, path);
                // every page has the way to the welcome again, at its foot: the first of the charts shows it
                var again = page.selectFirst("#site-footer nav a#welcome-again");
                assertEquals("Show the welcome again", again.text(), path);
                assertEquals("http://localhost/wheel/birth#welcome", again.attr("href"), path);
            }
            assertTrue(Files.isRegularFile(Path.of("src/main/webapp/images/emblem.svg")));
        }
    }

    @Test
    void everyChartPageHasTheButtonsThatTheMenusOfTheAppsPress() throws IOException {
        var pressed = Pattern.compile("static let (load|save) = \"([^\"]+)\"").matcher(Files.readString(Path.of("ios/app/Pages.swift")));
        var buttons = new ArrayList<String>();
        while (pressed.find()) {
            buttons.add(pressed.group(2));
        }
        assertEquals(2, buttons.size());
        try (var m = new MockConversation(new AppSite())) {
            for (var path : List.of("/wheel", "/wheel/birth", "/wheel/birth-transit", "/wheel/connection", "/wheel/connection-transit")) {
                var page = Jsoup.parse(m.doRequest(path).getText());
                for (var button : buttons) {
                    assertNotNull(page.selectFirst(button), path + " " + button);
                }
            }
        }
    }

    @Test
    void aLinkOfTheMenuIsItsWholeRow() throws IOException {
        var css = Files.readString(Path.of("src/main/webapp/css/main.css"));
        var menu = Pattern.compile("\n#menu \\{\n(.*?)\n}", Pattern.DOTALL).matcher(css);
        assertTrue(menu.find());
        var side = Pattern.compile("\n    padding: \\S+ (\\S+) \\S+;").matcher(menu.group(1));
        assertTrue(side.find());
        var link = Pattern.compile("\n#menu a \\{\n(.*?)\n}", Pattern.DOTALL).matcher(css);
        assertTrue(link.find());
        // as wide as the menu, with the room around its text inside it rather than around it
        assertEquals("    display: block;\n    margin: 0 -" + side.group(1) + ";\n    padding: 10px " + side.group(1) + ";", link.group(1));
        assertFalse(Pattern.compile("\n#menu li \\{[^}]*padding").matcher(css).find());

        try (var m = new MockConversation(new AppSite())) {
            for (var path : PAGE_PATHS.values()) {
                var items = Jsoup.parse(m.doRequest(path).getText()).select("#menu > li");
                assertFalse(items.isEmpty(), path);
                for (var item : items) {
                    assertEquals(1, item.childNodeSize(), path);
                    assertEquals("a", item.child(0).tagName(), path);
                }
            }
        }
    }

    @Test
    void anAppShowsItsPagesAsWideAsTheScreen() throws IOException {

        // the width that the stylesheet frames the pages in, on screens with room to spare
        assertTrue(Files.readString(Path.of("src/main/webapp/css/main.css")).contains("#content, #title-bar {\n        max-width: var(--column-width);"));
        var paths = new ArrayList<>(PAGE_PATHS.values());
        paths.add("/nonexistent");
        try (var m = new MockConversation(new AppSite())) {
            for (var path : paths) {
                assertEquals(":root { --column-width: 900px; }", Jsoup.parse(m.doRequest(path).getText()).selectFirst("head style").data(), path);
            }
        }
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.columnWidth", "none");
        try (var m = new MockConversation(new AppSite(), properties)) {
            for (var path : paths) {
                assertEquals(":root { --column-width: none; }", Jsoup.parse(m.doRequest(path).getText()).selectFirst("head style").data(), path);
            }
        }
    }

    @Test
    void menusLinkToEveryPage() {
        try (var m = new MockConversation(new AppSite())) {
            var menu_links = PageDescription.MENU.stream().map(p -> "http://localhost" + PAGE_PATHS.get(p)).toList();
            for (var path : PAGE_PATHS.values()) {
                var html = m.doRequest(path).getText();
                var menu = html.substring(html.indexOf("<ul id=\"menu\">"), html.indexOf("</ul>", html.indexOf("<ul id=\"menu\">")));
                var links = Pattern.compile("<li(?: class=\"[^\"]*\")?><a href=\"([^\"]+)\">").matcher(menu).results().map(r -> r.group(1)).toList();
                assertEquals(menu_links, links, path);
                assertTrue(menu.contains(">Transits by Month</a>") && menu.contains(">Transits by Day</a>"), menu);
                // the charts of people, then the transits, then the saved people and the pages about it all
                var labels = Pattern.compile("<a href=\"[^\"]+\">([^<]+)</a>").matcher(menu).results().map(r -> r.group(1)).toList();
                assertEquals(List.of("Birth Chart", "Connection Chart", "Transit Chart", "Birth Transit Chart", "Connection Transit Chart",
                    "Transits by Month", "Transits by Day", "People", "Learn", "About"), labels, path);
                var divided = Pattern.compile("<li class=\"menu-divided[^\"]*\"><a href=\"[^\"]+\">([^<]+)</a>").matcher(menu).results().map(r -> r.group(1)).toList();
                assertEquals(List.of("Transit Chart", "People"), divided, path);
                assertTrue(html.contains("<footer id=\"site-footer\">"), path);
                assertTrue(html.contains("<a href=\"https://uwyn.com\">Geert Bevin at Uwyn</a>"), path);
            }
        }
    }

    @Test
    void learnPagesShowEveryDescription() {
        try (var m = new MockConversation(new AppSite())) {
            var text = new StringBuilder();
            for (var topic : LearnTopic.values()) {
                var response = m.doRequest("/learn/" + topic.slug_);
                assertEquals(200, response.getStatus(), topic.slug_);
                text.append(response.getParsedHtml().getDocument().text()).append('\n');
            }
            // the variants of the chart and the range tables are only explained next to them
            for (var description : Descriptions.all().entrySet()) {
                var key = description.getKey();
                if (key.startsWith("range.") || key.equals("about.range") || key.startsWith("explore.tap.") || key.equals("about.cycles.buttons") || key.equals("fixing.exalted") || key.equals("fixing.detriment") || key.matches("wheel\\.(bodygraph|rows)\\..+")) {
                    continue;
                }
                assertTrue(text.indexOf(description.getValue()) >= 0, key);
            }

            var channels = m.doRequest("/learn/channels").getText();
            assertEquals(36, Pattern.compile("<section class=\"learn-card\" id=\"\\d+-\\d+-").matcher(channels).results().count());
            assertTrue(channels.contains("<h2>34-57 Power</h2>"), channels);
            assertTrue(channels.contains("id=\"abstract-circuit\""), channels);
            assertTrue(channels.contains("11-56 Curiosity, 13-33 the Prodigal, 29-46 Discovery"), channels);
            var power = channels.substring(channels.indexOf("<h2>34-57 Power</h2>"));
            assertTrue(power.substring(0, power.indexOf("</section>")).contains("Integration Channel, Individual group"), power);

            var index = m.doRequest("/learn").getText();
            assertFalse(index.contains(Descriptions.of("explore.tap.chart")), index);
            for (var topic : LearnTopic.values()) {
                assertTrue(index.contains("<a class=\"learn-topic\" href=\"http://localhost/learn/" + topic.slug_ + "\">"), topic.slug_);
                // every topic page links to all the others
                var page = m.doRequest("/learn/" + topic.slug_).getText();
                assertEquals(LearnTopic.values().length - 1, Pattern.compile("<a href=\"http://localhost/learn/[a-z-]+\">").matcher(page).results().count(), topic.slug_);
            }

            var guide = m.doRequest("/learn/charts").getText();
            for (var page : List.of("wheel/birth", "wheel", "wheel/birth-transit", "wheel/connection", "wheel/connection-transit")) {
                assertTrue(guide.contains("<a href=\"http://localhost/" + page + "\">Open the "), page);
            }
            assertTrue(guide.contains("<a href=\"http://localhost/range\">Open Transits by Month</a>"), guide);
            // what a chart is, which its panel shows on its own, and then what it's for
            assertTrue(guide.contains("<p>" + Descriptions.of("guide.range_day") + " " + Descriptions.of("guide.range_day.use") + "</p>"), guide);
            var planets = m.doRequest("/learn/planets").getText();
            assertTrue(planets.contains("<p>" + Descriptions.of("planet.MERCURY") + " " + Descriptions.of("planet.MERCURY.more") + "</p>"), planets);
            assertTrue(guide.contains("<a href=\"http://localhost/range/d\">Open Transits by Day</a>"), guide);
            // in the order of the menu
            var opened = List.of("Open the Birth Chart", "Open the Connection Chart", "Open the Transit Chart", "Open the Birth Transit Chart",
                "Open the Connection Transit Chart", "Open Transits by Month", "Open Transits by Day").stream().map(guide::indexOf).toList();
            assertTrue(opened.getFirst() > 0 && opened.equals(opened.stream().sorted().toList()), opened.toString());
        }
    }

    @Test
    void cyclesSetTheTransitsOfABirth() {
        try (var m = new MockConversation(new AppSite())) {
            // Steve Jobs, born in San Francisco, on the clocks of London and Tokyo, at the first minute
            // after Saturn gets back at 21:33:23 UTC
            assertEquals("1984-11-28T21:34", m.doRequest("/cycle/saturn-return/195502241915/5391959/2643743").getText());
            assertEquals("1984-11-29T06:34", m.doRequest("/cycle/saturn-return/195502241915/5391959/1850147").getText());
            assertEquals("1984-11-28T21:34", m.doRequest("/cycle/saturn-return/195502241915/5391959").getText());
            assertEquals("1994-02-14T08:36", m.doRequest("/cycle/uranus-opposition/195502241915/5391959/2643743").getText());
            assertEquals("2005-03-30T08:33", m.doRequest("/cycle/chiron-return/195502241915/5391959/2643743").getText());
            for (var missing : List.of("/cycle/saturn-return/195502301915/5391959", "/cycle/saturn-return/195502241915/999999999",
                                       "/cycle/saturn-return/218502241915/5391959", "/cycle/saturn-return/179001011200/2643743",
                                       // a birth whose design moment is before the tables, so its chart can't be drawn
                                       "/cycle/saturn-return/180002011200/2643743",
                                       "/cycle/mars-return/195502241915/5391959")) {
                assertEquals(404, m.doRequest(missing).getStatus(), missing);
            }

            var page = m.doRequest("/wheel/birth-transit").getText();
            for (var cycle : List.of("saturn-return", "uranus-opposition", "chiron-return")) {
                assertTrue(page.contains("onclick=\"return showCycle('" + cycle + "', 'http://localhost/wheel/image/birth-transit/');\""), cycle);
            }
            assertFalse(m.doRequest("/wheel/birth").getText().contains("showCycle"));

            var panel = m.doRequest("/wheel/explore/birth-transit/195502241915/5391959/202403011000/1850147").getText();
            assertTrue(panel.contains("<span class=\"explore-label\">Saturn Return</span> <span class=\"explore-value\">November 29, 1984, age 29</span>"), panel);
            assertTrue(panel.contains("<span class=\"explore-label\">Uranus Opposition</span> <span class=\"explore-value\">February 14, 1994, age 38</span>"), panel);
            assertTrue(panel.contains("<span class=\"explore-label\">Chiron Return</span> <span class=\"explore-value\">March 30, 2005, age 50</span>"), panel);
        }
    }

    @Test
    void exploreItemsNameTheirRelatedParts() {
        try (var m = new MockConversation(new AppSite())) {
            var birth = related(m.doRequest("/wheel/explore/birth/195502241915/5391959").getText());
            assertEquals("center.SOLAR channel.39-55", birth.get("activation.1.SUN"));
            assertEquals("center.HEAD channel.24-61 activation.2.CHIRON", birth.get("activation.1.VENUS"));
            assertEquals("center.SOLAR center.ROOT activation.1.CHIRON activation.2.MOON activation.2.MARS", birth.get("channel.30-41"));
            assertEquals("channel.4-63 channel.24-61 channel.47-64 activation.1.VENUS activation.2.CHIRON", birth.get("center.HEAD"));
            assertEquals("center.SOLAR", birth.get("authority"));
            assertEquals("activation.1.SUN activation.2.SUN", birth.get("profile"));
            assertEquals("activation.1.SUN activation.1.EARTH activation.2.SUN activation.2.EARTH", birth.get("cross"));
            assertEquals("activation.2.NORTH_NODE", birth.get("variable.ENVIRONMENT"));
            assertEquals("", birth.get("wheel.gates"));

            // the birth chart is in the rows below the transits
            var birth_transit = related(m.doRequest("/wheel/explore/birth-transit/195502241915/5391959/202403011000/5391959").getText());
            assertEquals("activation.3.SUN activation.4.SUN", birth_transit.get("profile"));
            assertEquals("channel.4-63 channel.24-61 channel.47-64 activation.3.VENUS activation.4.CHIRON activation.1.SUN activation.1.MERCURY activation.1.EARTH", birth_transit.get("center.HEAD"));
        }
    }

    private static Map<String, String> related(String html) {
        var related = new LinkedHashMap<String, String>();
        var matcher = Pattern.compile("<details data-part=\"([^\"]+)\" data-related=\"([^\"]*)\"").matcher(html);
        while (matcher.find()) {
            related.put(matcher.group(1), matcher.group(2));
        }
        return related;
    }

    @Test
    void pagesAnswerHeadRequests() {
        try (var m = new MockConversation(new AppSite())) {
            for (var path : List.of("/learn", "/wheel/image/202403011000/1850147", "/robots.txt")) {
                var get = m.doRequest(path);
                var head = m.doRequest(path, new MockRequest().method(RequestMethod.HEAD));
                assertEquals(200, head.getStatus(), path);
                assertEquals(get.getContentType(), head.getContentType(), path);
                assertEquals("", head.getText(), path);
            }
        }
    }

    @Test
    void aSharedChartDoesntChangeWhereTheVisitorIs() {
        try (var m = new MockConversation(new AppSite())) {
            assertEquals(List.of("locationId"), List.copyOf(m.doRequest("/wheel/image/202403011000/1850147").getNewCookieNames()));
        }
        try (var m = new MockConversation(new AppSite())) {
            assertTrue(m.doRequest("/wheel/image/202403011000/1850147", new MockRequest().parameter("shared", "1")).getNewCookieNames().isEmpty());
        }
    }

    @Test
    void everyExploreItemLinksToItsSectionOfALearnPage() {
        try (var m = new MockConversation(new AppSite())) {
            var links = new HashSet<String>();
            for (var explore : List.of("/wheel/explore/birth/195502241915/5391959", "/wheel/explore/202403011000/2643743",
                                       "/wheel/explore/birth-transit/195502241915/5391959/202403011000/2643743",
                                       "/wheel/explore/connection/195502241915/5391959/195008110945/5391959",
                                       "/wheel/explore/connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743",
                                       "/wheel/explore/connection/195502241915/5391959",
                                       "/range/explore/202403011000/2643743", "/range/explore/d/202403011000/2643743")) {
                var html = m.doRequest(explore).getText();
                // every section has an introduction behind the question mark of its title
                assertFalse(html.contains("\n<h3>"), explore);
                assertTrue(html.contains("<details class=\"explore-about\">") || explore.startsWith("/range"), explore);
                var items = html.split("<details data-part=").length - 1;
                var more = Pattern.compile("<p class=\"explore-more\"><a href=\"([^\"]+)\">").matcher(html).results().map(r -> r.group(1)).toList();
                // and a second link for the planets in a line with a triangle, and for the circuit of a channel, and three for a person
                var triangles = Pattern.compile("<span class=\"explore-value\">\\d+\\.\\d(&#96(?:50|51|60|61);)+").matcher(html).results().count();
                var people = html.split("<details data-part=\"person\\.").length - 1;
                var planets = html.split("<details data-part=\"activation\\.").length - 1;
                var channels = html.split("<details data-part=\"channel\\.").length - 1;
                // the planets that complete nothing coming up have nothing to learn more about
                var none = html.split("None in the next year</span>").length - 1;
                assertEquals(items + triangles + 2 * people + planets + channels - none, more.size(), explore);
                links.addAll(more);
                var purpose = Pattern.compile("<a href=\"([^\"]+)\">More about this (?:chart|table)</a>").matcher(html).results().map(r -> r.group(1)).toList();
                assertEquals(1, purpose.size(), explore);
                links.addAll(purpose);
            }
            for (var link : links) {
                var anchor = link.indexOf('#');
                var page = m.doRequest((anchor < 0 ? link : link.substring(0, anchor)).replace("http://localhost", ""));
                assertEquals(200, page.getStatus(), link);
                if (anchor >= 0) {
                    assertTrue(page.getText().contains("id=\"" + link.substring(anchor + 1) + "\""), link);
                }
            }
        }
    }

    @Test
    void learnPagesInviteToTheirChartsAndNameTheirSources() {
        try (var m = new MockConversation(new AppSite())) {
            assertTrue(m.doRequest("/learn/types").getText().contains("<a href=\"http://localhost/wheel/birth\">See them in a Birth Chart</a>"));
            var transits = m.doRequest("/learn/transits").getText();
            assertTrue(transits.contains("<a href=\"http://localhost/wheel\">Open the Transit Chart</a>"), transits);
            assertTrue(transits.contains("<a href=\"http://localhost/wheel/birth-transit\">Open the Birth Transit Chart</a>"), transits);
            assertTrue(m.doRequest("/learn/connections").getText().contains("<a href=\"http://localhost/wheel/connection\">Open the Connection Chart</a>"));
            assertFalse(m.doRequest("/learn/charts").getText().contains("learn-invite"));

            var index = m.doRequest("/learn").getText();
            assertTrue(index.contains("<a href=\"https://jovianarchive.com\">Jovian Archive</a>"), index);
            assertTrue(index.contains("<a href=\"https://www.ihdschool.com\">International Human Design School</a>"), index);
            assertFalse(m.doRequest("/learn/types").getText().contains("learn-sources"));
        }
    }

    @Test
    void learnPagesShowWhereTheyAre() {
        try (var m = new MockConversation(new AppSite())) {
            for (var topic : LearnTopic.values()) {
                var html = m.doRequest("/learn/" + topic.slug_).getText();
                assertTrue(html.contains("<nav class=\"learn-breadcrumbs\" aria-label=\"Breadcrumbs\"><a href=\"http://localhost/learn\">Learn about Human Design</a>"), topic.slug_);
                assertTrue(html.contains("<span aria-current=\"page\">" + topic.page().heading() + "</span>"), topic.slug_);
                var json = html.substring(html.indexOf("<script type=\"application/ld+json\">") + 35, html.indexOf("</script>", html.indexOf("application/ld+json")));
                var crumbs = Json.parse(json);
                assertTrue(crumbs.toString().contains("http://localhost/learn/" + topic.slug_), json);
                assertTrue(html.contains("<a class=\"learn-all\" href=\"http://localhost/learn\">All topics</a>"), topic.slug_);
            }
            assertFalse(m.doRequest("/learn").getText().contains("learn-breadcrumbs"));
        }
    }

    @Test
    void theMenuShowsTheCurrentPage() {
        try (var m = new MockConversation(new AppSite())) {
            var pages = new LinkedHashMap<String, String>();
            pages.put("/wheel/birth", "/wheel/birth");
            pages.put("/range/d", "/range/d");
            pages.put("/people", "/people");
            pages.put("/learn", "/learn");
            pages.put("/learn/centers", "/learn");
            pages.put("/about", "/about");
            pages.forEach((url, expected) -> {
                var html = m.doRequest(url).getText();
                var menu = html.substring(html.indexOf("<ul id=\"menu\">"), html.indexOf("</ul>", html.indexOf("<ul id=\"menu\">")));
                var current = Pattern.compile("<li class=\"[^\"]*current\"><a href=\"([^\"]+)\">").matcher(menu).results().map(r -> r.group(1)).toList();
                assertEquals(List.of("http://localhost" + expected), current, url);
            });
        }
    }

    @Test
    void theAboutPageSaysWhoMadeTheSite() {
        try (var m = new MockConversation(new AppSite())) {
            var text = m.doRequest("/about").getParsedHtml().getDocument().text();
            assertTrue(text.contains("I'm Geert Bevin, and at Uwyn I make"), text);
            assertTrue(text.contains("I'm not a Human Design practitioner"), text);
            assertTrue(text.contains("put RIFE2, my own web framework, through its paces, and in particular its template engine"), text);
            assertFalse(text.contains("\u2014"), text);
        }
    }

    @Test
    void theAboutPageLinksToTheSourceOnTheWebAndInAnApp() {
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.shareRootUrl", "https://charts.example.com");
        for (var site : List.of(new HierarchicalProperties(), properties)) {
            try (var m = new MockConversation(new AppSite(), site)) {
                var card = Jsoup.parse(m.doRequest("/about").getText()).selectFirst("article#learn > div.learn-card");
                var source = card.select("a[href=https://github.com/gbevin/GatesOfHumanity]");
                assertEquals(List.of("GitHub"), source.eachText());
                assertTrue(source.parents().first().text().startsWith("The code of these pages and of the apps is open source, under the Apache License"), card.text());
            }
        }
    }

    @Test
    void whatPresentsTheChartsNamesTheWheelInItsOwnWords() throws IOException {
        // Jovian Archive claims the name Rave Mandala: the texts that explain a chart say what the wheel is called,
        // the bar, the descriptions for search engines and the link preview don't
        try (var m = new MockConversation(new AppSite())) {
            for (var path : PAGE_PATHS.values()) {
                var document = Jsoup.parse(m.doRequest(path).getText());
                assertEquals("Wheel of the 64 Gates, I Ching and Astrology", document.selectFirst("#title-bar #tagline").text().replace('\u00a0', ' '), path);
                assertFalse(document.head().html().contains("Rave"), path);
            }
        }
        var preview = Files.readString(Path.of("Tools/images/og-image.html"));
        assertTrue(preview.contains("Wheel of the 64&nbsp;Gates,<br>I&nbsp;Ching and Astrology"));
        assertFalse(preview.contains("Rave"));
    }

    @Test
    void theAboutPageCreditsWhatTheChartsAreMadeWith() throws IOException {
        var notice = Files.readString(Path.of("NOTICE.txt")).replaceAll("\\s+", " ");
        try (var m = new MockConversation(new AppSite())) {
            var credits = Jsoup.parse(m.doRequest("/about").getText()).selectFirst("article#learn section.learn-sources");
            assertEquals("Credits", credits.selectFirst("h2").text());
            var items = credits.select("ul > li");
            assertEquals(5, items.size());
            // GeoNames asks to be named with its license where its data is used
            assertEquals("The city names, coordinates and time zones are from GeoNames, licensed under the Creative Commons Attribution 4.0 License." +
                         " The data was reduced to what the city search needs.", items.get(0).text());
            assertEquals(List.of("https://www.geonames.org", "https://creativecommons.org/licenses/by/4.0/"), items.get(0).select("a").eachAttr("href"));
            for (var name : List.of("JPL Horizons", "The Definitive Book of Human Design", "RIFE2", "ASM", "ANTLR 4", "Awesomplete", "normalize.css")) {
                assertTrue(credits.text().contains(name), name);
                assertTrue(notice.contains(name), name);
            }
            // everything that's credited is in the notice of the sources, with the same address
            var links = credits.select("a").eachAttr("href");
            assertEquals(8, links.size());
            for (var link : links) {
                assertTrue(notice.contains(link), link);
            }
        }
        // a program that has more inside it than the site adds its own credit
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.credit", "Made with <more> & more.");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var html = m.doRequest("/about").getText();
            assertTrue(html.contains("<li>Made with &lt;more&gt; &amp; more.</li>"), html);
            var items = Jsoup.parse(html).select("section.learn-sources ul > li");
            assertEquals(6, items.size());
            assertEquals("Made with <more> & more.", items.get(5).text());
        }
        // and one for what draws its widgets, when it has them
        properties.put("gatesofhumanity.widgetsCredit", "Drawn with something.");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var items = Jsoup.parse(m.doRequest("/about").getText()).select("section.learn-sources ul > li");
            assertEquals(List.of("Made with <more> & more.", "Drawn with something."), items.eachText().subList(5, 7));
            assertEquals(7, items.size());
        }
        properties.remove("gatesofhumanity.credit");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var items = Jsoup.parse(m.doRequest("/about").getText()).select("section.learn-sources ul > li");
            assertEquals(6, items.size());
            assertEquals("Drawn with something.", items.get(5).text());
        }
        assertTrue(notice.contains("SwiftDraw (https://github.com/swhitty/SwiftDraw)"));
    }

    @Test
    void thePagesOnTheWebOfferTheAppAndAnAppDoesnt() {
        var store = "https://apps.apple.com/app/id6834835560";
        try (var m = new MockConversation(new AppSite())) {
            for (var path : PAGE_PATHS.values()) {
                var page = Jsoup.parse(m.doRequest(path).getText());
                var links = page.select("#site-footer nav a");
                assertEquals(List.of("Learn about Human Design", "About these charts", "Get the app", "Show the welcome again"), links.eachText(), path);
                assertEquals(store, links.get(2).attr("href"), path);
                // and Safari on an iPhone or an iPad offers it above the page
                assertEquals(List.of("app-id=6834835560"), page.select("head > meta[name=apple-itunes-app]").eachAttr("content"), path);
            }
            var card = Jsoup.parse(m.doRequest("/about").getText()).selectFirst("article#learn > div#apps.learn-card");
            assertEquals("The apps", card.selectFirst("h2").text());
            assertTrue(card.text().contains("an app for iPhone, iPad, Apple Watch, Mac and Apple Vision Pro"), card.text());
            assertEquals(List.of(store), card.select("a").eachAttr("href"));
            assertEquals("embed", card.nextElementSibling().id());
        }
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.shareRootUrl", "https://charts.example.com");
        try (var m = new MockConversation(new AppSite(), properties)) {
            for (var path : PAGE_PATHS.values()) {
                var html = m.doRequest(path).getText();
                assertEquals(List.of("Learn about Human Design", "About these charts", "Show the welcome again"),
                    Jsoup.parse(html).select("#site-footer nav a").eachText(), path);
                assertFalse(html.contains("apps.apple.com"), path);
                assertFalse(html.contains("app-store-url"), path);
                assertFalse(html.contains("apple-itunes-app"), path);
                assertFalse(html.contains("app-id"), path);
            }
            assertNull(Jsoup.parse(m.doRequest("/about").getText()).selectFirst("#apps"));
        }
    }

    @Test
    void theAboutPageOnTheWebOffersTheTransitsForOtherPages() {
        try (var m = new MockConversation(new AppSite())) {
            var card = Jsoup.parse(m.doRequest("/about").getText()).selectFirst("article#learn > div#embed.learn-card");
            assertEquals("The transits on your own site", card.selectFirst("h2").text());
            var images = card.select("figure.embed > img");
            assertEquals(List.of("http://localhost/embed/transits/medium", "http://localhost/embed/transits/small"), images.eachAttr("src"));
            assertEquals(List.of("338", "158"), images.eachAttr("width"));
            assertEquals(List.of("158", "158"), images.eachAttr("height"));
            // what's shown to copy is the image as the page itself has it, inside a link to the chart of the transits
            var codes = card.select("figure.embed > pre > code");
            assertEquals(2, codes.size());
            for (var i = 0; i < codes.size(); ++i) {
                var copied = Jsoup.parseBodyFragment(codes.get(i).text()).body();
                assertEquals(1, copied.children().size());
                var link = copied.child(0);
                assertEquals("a", link.tagName());
                assertEquals("http://localhost/wheel", link.attr("href"));
                assertEquals(1, link.children().size());
                var image = link.child(0);
                for (var attribute : List.of("src", "width", "height", "alt")) {
                    assertEquals(images.get(i).attr(attribute), image.attr(attribute), attribute);
                }
                assertEquals("The Human Design transits of this moment", image.attr("alt"));
                // and the image is there to be had
                assertEquals(200, m.doRequest(image.attr("src").replace("http://localhost", "")).getStatus());
            }
        }
        // an app has the pages inside it and no address that a page elsewhere could ask
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.shareRootUrl", "https://charts.example.com");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var about = Jsoup.parse(m.doRequest("/about").getText());
            assertTrue(about.select("#embed, figure.embed").isEmpty());
            assertEquals(1, about.select("article#learn > div.learn-card").size());
        }
    }

    @Test
    void theAboutPageSaysWhichVersionAProgramIs() {
        // the site alone has no version to tell
        try (var m = new MockConversation(new AppSite())) {
            assertTrue(Jsoup.parse(m.doRequest("/about").getText()).select(".learn-version").isEmpty());
        }
        var properties = new HierarchicalProperties();
        properties.put("gatesofhumanity.version", "1.2<3");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var html = m.doRequest("/about").getText();
            assertTrue(html.contains("<p class=\"learn-version\">Version 1.2&lt;3</p>"), html);
            assertEquals(1, Jsoup.parse(html).select("article#learn > .learn-version").size());
        }
        properties.put("gatesofhumanity.build", "4&5");
        try (var m = new MockConversation(new AppSite(), properties)) {
            var html = m.doRequest("/about").getText();
            assertTrue(html.contains("<p class=\"learn-version\">Version 1.2&lt;3, build 4&amp;5</p>"), html);
        }
        // a build number alone says nothing
        properties.remove("gatesofhumanity.version");
        try (var m = new MockConversation(new AppSite(), properties)) {
            assertTrue(Jsoup.parse(m.doRequest("/about").getText()).select(".learn-version").isEmpty());
        }
    }

    @Test
    void thePeoplePageIsFilledInByTheBrowser() {
        try (var m = new MockConversation(new AppSite())) {
            var document = Jsoup.parse(m.doRequest("/people").getText());
            assertEquals("Saved People | Gates of Humanity", document.title());
            // the bar of a chart has the name as part of its heading, the bar of a page of text has it beside it
            assertEquals("Gates of Humanity", document.selectFirst("p#page-name #site-name").text());
            assertEquals("Gates of Humanity", Jsoup.parse(m.doRequest("/wheel/birth").getText()).selectFirst("h1#page-name #site-name").text());
            var list = document.selectFirst("article#learn div#people.entries");
            assertEquals("", list.html());
            assertTrue(document.selectFirst("p.learn-lead").text().contains("They stay in this browser, they're never stored anywhere else."));
            assertTrue(m.doRequest("/about").getText().contains("The people you save stay in this browser, they're never stored anywhere else."));
            var scripts = document.select("head script:not([src])").stream().map(script -> script.data()).collect(Collectors.joining("\n"));
            assertTrue(scripts.contains("people: 'http://localhost/people'"), scripts);
            assertTrue(scripts.contains("kept_in = 'this browser';"), scripts);
            assertTrue(scripts.contains("share_root = '';"), scripts);
            // the charts whose first person is the one that the browser remembers, under the names of their pages
            assertTrue(scripts.contains("people_charts = [{label: 'Birth Chart', url: 'http://localhost/wheel/birth'}, " +
                "{label: 'Birth Transit Chart', url: 'http://localhost/wheel/birth-transit'}, " +
                "{label: 'Connection Chart', url: 'http://localhost/wheel/connection'}, ];"), scripts);
            assertTrue(scripts.contains("preparePersonForm();\n            showPeople();"), scripts);

            // the form that adds someone is closed at first, and every field of it has its name
            var form = document.selectFirst("article#learn form#person-form[hidden]");
            var opener = document.selectFirst("button#person-add[type=button][aria-controls=person-form]");
            assertEquals("Add a person false", opener.text() + " " + opener.attr("aria-expanded"));
            assertTrue(opener.elementSiblingIndex() < form.elementSiblingIndex());
            var fields = form.select("input:not([type=hidden])");
            assertEquals(List.of("person-name", "person-datetime", "person-location", "person-group"), fields.eachAttr("id"));
            assertEquals(List.of("Name", "Birth date and time", "Birth city", "Group"), fields.stream().map(input -> form.selectFirst("label[for=" + input.id() + "]").text()).toList());
            assertEquals(List.of("text", "datetime-local", "text", "text"), fields.eachAttr("type"));
            assertEquals(1, form.select("input#person-location-id[type=hidden]").size());
            assertEquals("Type its first letters, then choose the city from the list.", form.selectFirst("#" + form.selectFirst("#person-location").attr("aria-describedby")).text());
            // the groups are listed by the script, as the cities are, rather than by the browser
            assertEquals(0, form.select("datalist, [list]").size());
            // with the same dates as the pages of the charts
            var chart_date = Jsoup.parse(m.doRequest("/wheel/birth").getText()).selectFirst("input#datetime");
            assertEquals(chart_date.attr("min") + " " + chart_date.attr("max"), form.selectFirst("#person-datetime").attr("min") + " " + form.selectFirst("#person-datetime").attr("max"));
            assertEquals(List.of("person-cancel button", "person-submit submit"), form.select("button").stream().map(button -> button.id() + " " + button.attr("type")).toList());
            assertTrue(form.selectFirst("#person-submit").hasAttr("disabled"));
            assertTrue(form.selectFirst("p#person-notice[role=status]").hasAttr("hidden"));
            // the pages with a chart choose someone in their window and link here for the rest
            for (var page : CHART_PAGES) {
                var chart = Jsoup.parse(m.doRequest(PAGE_PATHS.get(page)).getText());
                assertEquals(1, chart.select("#load-entries > div.popup-content.entries").size(), page.heading());
                assertTrue(chart.select("head script:not([src])").first().data().contains("people: 'http://localhost/people'"), page.heading());
            }
        }
    }

    @Test
    void crawlersFindEveryPageAndSkipTheImages() {
        try (var m = new MockConversation(new AppSite())) {
            var robots = m.doRequest("/robots.txt").getText();
            assertTrue(robots.contains("Disallow: /wheel/image/\nDisallow: /wheel/explore/\nDisallow: /range/table/\nDisallow: /range/explore/\n"), robots);
            assertTrue(robots.contains("Sitemap: http://localhost/sitemap.xml"), robots);

            var sitemap = m.doRequest("/sitemap.xml").getText();
            for (var entry : PAGE_PATHS.entrySet()) {
                var path = entry.getValue();
                var page = m.doRequest(path);
                assertEquals(200, page.getStatus(), path);
                // the saved people are in the browser of the visitor, their page has nothing to find
                var unlisted = entry.getKey() == PageDescription.PEOPLE;
                assertEquals(!unlisted, sitemap.contains("<loc>http://localhost" + path + "</loc>"), sitemap);
                assertEquals(unlisted ? "noindex" : "", Jsoup.parse(page.getText()).select("meta[name=robots]").attr("content"), path);
            }
            var response = m.doRequest("/sitemap.xml");
            assertTrue(response.getContentType().startsWith("application/xml"), response.getContentType());
            assertTrue(m.doRequest("/robots.txt").getContentType().startsWith("text/plain"));
            assertEquals(PageDescription.all().size() - 1, sitemap.split("<loc>").length - 1);
        }
    }

    @Test
    void birthChartsCarryTheirName() {
        try (var m = new MockConversation(new AppSite())) {
            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", " Steve <Jobs> ")).getText();
            assertTrue(svg.contains("<text class=\"title-footer\" x=\"480\" y=\"1110\">Steve &lt;Jobs&gt;</text>"), svg);
            assertTrue(svg.contains("<text class=\"date\" x=\"480\" y=\"1138\">Human Design Birth Chart</text>"));

            svg = m.doRequest("/wheel/image/birth/195502241915/5391959", new MockRequest().parameter("name", "A name that is much too long for the footer")).getText();
            assertTrue(svg.contains(">A name that is much too lon\u2026</text>"), svg);

            svg = m.doRequest("/wheel/image/birth-transit/194709210130/4975802/202403011000/2643743", new MockRequest().parameter("name", "Stephen King")).getText();
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"710\" y=\"1178\">Stephen King</text>"));
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"480\" y=\"1074\">Stephen King</text>"));
            assertTrue(svg.contains("<text class=\"title-footer\" x=\"480\" y=\"1110\">Human Design Birth Transit Chart</text>"));

            svg = m.doRequest("/wheel/image/birth/195502241915/5391959").getText();
            assertTrue(svg.contains("<text class=\"title-footer\" x=\"480\" y=\"1110\">Human Design Birth Chart</text>"));
            // no subtitle without a name, the legend follows the title
            assertTrue(svg.contains("<text class=\"legend-first\" x=\"416\" y=\"1138\">Personality</text>"), svg);
            assertTrue(svg.contains("<text class=\"legend-both\" x=\"478\" y=\"1138\">Both</text>"));
            assertTrue(svg.contains("<text class=\"legend-second\" x=\"552\" y=\"1138\">Design</text>"));
            // the transits alone are in a single color
            assertFalse(m.doRequest("/wheel/image/202403011000/2643743").getText().contains("legend-first\" x"));

            svg = m.doRequest("/wheel/image/202403011000/2643743", new MockRequest().parameter("name", "Ignored")).getText();
            assertFalse(svg.contains("Ignored"));
        }
    }

    @Test
    void pagesOpenAtAGivenDate() {
        try (var m = new MockConversation(new AppSite())) {
            for (var page : CHART_PAGES.stream().map(PAGE_PATHS::get).toList()) {
                var response = m.doRequest(page, new MockRequest().parameter("datetime", "1955-02-24T19:15"));
                assertEquals(200, response.getStatus(), page);
                assertTrue(response.getText().contains("value=\"1955-02-24T19:15\""), page);
            }
            // a page opened at a date keeps it whatever city the browser remembers
            assertTrue(m.doRequest("/range", new MockRequest().parameter("datetime", "1955-02-24T19:15")).getText()
                .contains("showFirstChart('http://localhost/range/table/', '')"));
            m.cookie("locationId", "1850147");
            assertTrue(m.doRequest("/range").getText().contains("showFirstChart('http://localhost/range/table/', '1850147')"));
        }
    }

    // without the script, the date and birth details never end up in a URL
    @Test
    void dateFormsArePosted() {
        try (var m = new MockConversation(new AppSite())) {
            for (var page : CHART_PAGES.stream().map(PAGE_PATHS::get).toList()) {
                var html = m.doRequest(page).getText();
                assertTrue(html.contains("method=\"post\" onsubmit=\"showDate("), page);
                // the script sets up the city search, a class would set up a second one
                assertFalse(html.contains("class=\"awesomplete\""), page);
                assertTrue(html.contains("type=\"datetime-local\" min=\"1800-01-01T00:00\" max=\"2199-12-31T23:59\""), page);
            }
        }
    }

    @Test
    void pagesDrawTheirChartFromTheirImageRoute() {
        try (var m = new MockConversation(new AppSite())) {
            var images = new LinkedHashMap<String, String>();
            images.put("/wheel", "http://localhost/wheel/image");
            images.put("/wheel/birth", "http://localhost/wheel/image/birth");
            images.put("/wheel/birth-transit", "http://localhost/wheel/image/birth-transit");
            images.put("/wheel/connection", "http://localhost/wheel/image/connection");
            images.put("/wheel/connection-transit", "http://localhost/wheel/image/connection-transit");
            images.put("/range", "http://localhost/range/table");
            images.put("/range/d", "http://localhost/range/table/d");
            images.forEach((page, image) -> assertEquals(image, m.doRequest(page).getTemplate().getValue("image-url"), page));
        }
    }

    @Test
    void theRangeDurationIsNeverEchoedIntoThePage() {
        try (var m = new MockConversation(new AppSite())) {
            var text = m.doRequest("/range", new MockRequest().parameter("duration", "x');alert(1)//")).getText();
            assertFalse(text.contains("alert(1)"));
            assertTrue(text.contains("showFirstChart('http://localhost/range/table/', '2643743')"));
        }
    }

    @Test
    void connectionChartsNameBothPeople() {
        try (var m = new MockConversation(new AppSite())) {
            m.cookie("locationId", "1850147");
            // Steve Jobs and Steve Wozniak, born in San Francisco and San Jose
            var response = m.doRequest("/wheel/image/connection/195008110945/5392171/195502241915/5391959",
                new MockRequest().parameter("name", "Steve Jobs").parameter("secondName", "Steve Wozniak"));
            assertEquals(200, response.getStatus());
            var svg = response.getText();
            assertEquals("#2f6fd6", channelColor(svg, "on"));
            assertEquals("#2e9e4f", channelColor(svg, "design"));
            assertTrue(svg.contains(">Steve Jobs</text><text class=\"property-value\" x=\"245\" y=\"1244\">Generator \u00b7 Emotional \u00b7 6/3</text>"), svg);
            assertTrue(svg.contains(">Steve Wozniak</text><text class=\"property-value\" x=\"245\" y=\"1272\">"));
            assertTrue(svg.contains(">Electromagnetic</text>"));
            assertTrue(svg.contains("Human Design Connection Chart"));
            assertTrue(svg.contains("<text class=\"legend-first\" x=\"416\" y=\"1138\">Steve Jobs</text>"), svg);
            assertTrue(svg.contains("<text class=\"legend-both\" x=\"478\" y=\"1138\">Both</text>"));
            assertTrue(svg.contains("<text class=\"legend-second\" x=\"552\" y=\"1138\">Steve Wozniak</text>"));
            assertTrue(svg.contains("<text class=\"name-footer\" x=\"480\" y=\"1074\">Steve Wozniak</text>"));
            assertTrue(svg.contains("<g transform=\"translate(0 30)\" data-explore=\"wheel.rows\">"), svg);
            assertTrue(svg.contains("translate(0 134)"));
            // a birth place isn't where the visitor is
            assertTrue(response.getNewCookieNames().isEmpty());

            // without a second chart, only the first is drawn
            assertEquals(200, m.doRequest("/wheel/image/connection/195502241915/5391959").getStatus());
            var page = m.doRequest("/wheel/connection").getText();
            assertTrue(page.contains("saveDateLocation('second', this)"), page);
            assertTrue(page.contains("'#second-location', 'partner'"));
            assertTrue(m.doRequest("/wheel").getText().contains("/wheel/connection\">Connection Chart</a>"));
        }
    }

    @Test
    void noAnswerLeavesATemplateTagUnfilled() {
        try (var m = new MockConversation(new AppSite())) {
            var urls = new ArrayList<>(PAGE_PATHS.values());
            urls.addAll(List.of("/nonexistent", "/robots.txt", "/sitemap.xml", "/wheel/image", "/wheel/image/202403011000/1850147",
                "/wheel/image/birth/197506211430/2643743?name=Test", "/wheel/image/birth-transit", "/wheel/image/connection", "/wheel/image/connection-transit",
                "/wheel/image/birth-transit/197506211430/2643743/202403011000/1850147", "/wheel/image/connection/195502241915/5391959",
                "/wheel/image/connection/194709210130/4975802/195502241915/5391959",
                "/wheel/image/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147",
                "/wheel/image/197513011430", "/wheel/image/180001011200", "/wheel/image/202403011000/1",
                "/wheel/explore/202403011000/1850147", "/wheel/explore/birth/197506211430/2643743",
                "/wheel/explore/birth-transit/197506211430/2643743/202403011000/1850147",
                "/wheel/explore/connection/194709210130/4975802/195502241915/5391959",
                "/wheel/explore/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147",
                "/range/table/202403011000/2643743", "/range/table/d/202403011000", "/range/explore/202403011000/1850147", "/range/explore/d/202403011000/1850147"));
            var unfilled = new ArrayList<String>();
            for (var url : urls) {
                Pattern.compile("<!--/?[vbi] ?[^>]*-->|\\{\\{/?[vbi] ?[^}]*}}").matcher(m.doRequest(url).getText()).results()
                    .forEach(tag -> unfilled.add(url + " " + tag.group()));
            }
            assertEquals(List.of(), unfilled);
        }
    }

    // the pages with a chart or a table, the others are pages of text
    private static final List<PageDescription> CHART_PAGES = List.of(PageDescription.TRANSIT, PageDescription.BIRTH, PageDescription.BIRTH_TRANSIT,
        PageDescription.CONNECTION, PageDescription.CONNECTION_TRANSIT, PageDescription.RANGE_MONTH, PageDescription.RANGE_DAY);

    @Test
    void everyFieldAndButtonOfAChartPageHasAName() {
        try (var m = new MockConversation(new AppSite())) {
            for (var page : CHART_PAGES) {
                var path = PAGE_PATHS.get(page);
                var controls = Jsoup.parse(m.doRequest(path).getText()).selectFirst("#controls");
                var fields = controls.select("input:not([type=hidden])");
                assertTrue(fields.size() >= 2, path);
                var names = fields.eachAttr("aria-label");
                assertEquals(fields.size(), names.size(), path);
                assertEquals(names.size(), new HashSet<>(names).size(), path + " " + names);
                // the steps in time are shortened to a letter on phones
                assertEquals(List.of("A day earlier", "A day later", "An hour earlier", "An hour later", "A minute earlier", "A minute later"),
                    controls.select("#change-time button[id]").eachAttr("aria-label"), path);
                for (var button : controls.select("button")) {
                    assertFalse((button.text() + button.attr("aria-label")).isBlank(), path + " " + button);
                    // only Show sends the form
                    assertEquals(button.id().equals("action-show") ? "" : "button", button.attr("type"), path + " " + button);
                }
                assertTrue(controls.select("[autocomplete]").eachAttr("autocomplete").stream().allMatch("off"::equals), path);
            }
        }
    }

    @Test
    void chartPagesCanShowTheirChartFullScreen() {
        try (var m = new MockConversation(new AppSite())) {
            for (var page : CHART_PAGES) {
                var path = PAGE_PATHS.get(page);
                var document = Jsoup.parse(m.doRequest(path).getText());
                // hidden until there's a drawing, next to it
                var button = document.selectFirst("#svg-container > button#chart-zoom[type=button][hidden]");
                assertEquals("Show the chart full screen", button.attr("aria-label"), path);
                var viewer = document.selectFirst("div#chart-viewer[role=dialog][aria-modal=true][hidden]");
                assertFalse(viewer.attr("aria-label").isBlank(), path);
                assertEquals(1, viewer.select("div#chart-viewer-stage").size(), path);
                assertEquals(List.of("chart-viewer-out", "chart-viewer-in", "chart-viewer-close"), viewer.select("#chart-viewer-controls button[type=button]").eachAttr("id"), path);
                assertEquals(List.of("Smaller", "Larger", "Close"), viewer.select("#chart-viewer-controls button").eachAttr("aria-label"), path);
            }
        }
    }

    @Test
    void everyPageHasOneHeadingForItsTitleAndCanBeZoomed() {
        try (var m = new MockConversation(new AppSite())) {
            for (var entry : PAGE_PATHS.entrySet()) {
                var path = entry.getValue();
                var document = Jsoup.parse(m.doRequest(path).getText());
                var headings = document.select("h1");
                assertEquals(1, headings.size(), path);
                if (CHART_PAGES.contains(entry.getKey())) {
                    assertEquals("Gates of Humanity " + entry.getKey().heading(), headings.text(), path);
                } else {
                    assertEquals(entry.getKey().title(), headings.text(), path);
                    assertEquals("Gates of Humanity " + entry.getKey().heading(), document.select("p#page-name").text(), path);
                }
                assertEquals("width=device-width, initial-scale=1", document.select("meta[name=viewport]").attr("content"), path);

                var menu = document.selectFirst("nav#menu-toggle");
                assertEquals("false", menu.selectFirst("button#menu-button[aria-controls=menu]").attr("aria-expanded"), path);
                assertEquals(PageDescription.MENU.size(), menu.select("ul#menu a").size(), path);
            }
        }
    }

    private static double rotation(String svg, String before) {
        var matcher = Pattern.compile(before + "rotate\\(([0-9.]+) 480 480\\)").matcher(svg);
        assertTrue(matcher.find(), before);
        return Double.parseDouble(matcher.group(1));
    }

    @Test
    void planetsAreDrawnInsideTheirGatesAndSigns() {
        try (var m = new MockConversation(new AppSite())) {
            // Tokyo is 9 hours ahead of UTC
            var svg = m.doRequest("/wheel/image/202403011000/1850147").getText();
            var signs = Pattern.compile("rotate\\(([0-9.]+) 480 480\\) translate\\(480 24\\)").matcher(svg).results()
                .map(sign -> Double.parseDouble(sign.group(1))).toList();
            // the signs in the order the wheel draws them, and in the order of the zodiac
            var drawn = List.of("sagittarius", "scorpio", "libra", "virgo", "leo", "cancer", "gemini", "taurus", "aries", "pisces", "aquarius", "capricorn");
            var zodiac = List.of("aries", "taurus", "gemini", "cancer", "leo", "virgo", "libra", "scorpio", "sagittarius", "capricorn", "aquarius", "pisces");
            assertEquals(12, signs.size());
            for (var p : Charts.transit(ZonedDateTime.of(2024, 3, 1, 1, 0, 0, 0, ZoneOffset.UTC)).personality().positions()) {
                var planet = "activation.1." + p.planet().name();
                var angle = rotation(svg, "<g data-explore=\"" + planet + " wheel.planets\" transform=\"");
                var sign = signs.get(drawn.indexOf(zodiac.get((int) (p.longitude() / 30.0))));
                // the symbol of a sign is in the middle of its 30 degrees
                assertTrue(Math.abs(angle - sign) <= 15.0 || Math.abs(angle - sign) >= 345.0, planet + " " + angle + " " + sign);
                if (p.planet() != Planet.CHIRON) {
                    var gate = rotation(svg, "data-explore=\"[^\"]*" + planet + " [^\"]*wheel\\.gates\" transform=\"");
                    var within = ((angle - gate) % 360.0 + 360.0) % 360.0;
                    assertTrue(within < 5.625, planet + " " + angle + " " + gate);
                }
            }
        }
    }

    @Test
    void channelsAreLitByTheirOwnGates() {
        try (var m = new MockConversation(new AppSite())) {
            var svg = m.doRequest("/wheel/image/birth/195502241915/5391959").getText();
            var active = Analysis.gates(Charts.birth(ZonedDateTime.of(1955, 2, 25, 3, 15, 0, 0, ZoneOffset.UTC)));
            var lit = new LinkedHashMap<String, Integer>();
            // the two halves of a channel, each from the gate at its end, the Integration channels share theirs
            Pattern.compile("class=\"(hd-channel-[^\"]*)\" data-explore=\"channel\\.(\\d+-\\d+) channels\"").matcher(svg).results()
                .forEach(half -> lit.merge(half.group(2), half.group(1).contains("hd-channel-off") ? 0 : 1, Integer::sum));
            assertTrue(lit.size() >= 30, lit.toString());
            lit.forEach((channel, halves) -> {
                var gates = Arrays.stream(channel.split("-")).map(Integer::valueOf).toList();
                assertEquals(gates.stream().filter(active::contains).count(), halves.longValue(), channel);
            });
            assertEquals(2, lit.get("30-41"));
        }
    }

    @Test
    void coordinatesAreSouthAndEastOfTheEquatorAndGreenwichToo() {
        try (var m = new MockConversation(new AppSite())) {
            // Tokyo, São Paulo and Sydney
            assertTrue(m.doRequest("/range/table/202403011000/1850147").getText().contains("<p>35.6895\u00b0 N, 139.69171\u00b0 E</p>"));
            assertTrue(m.doRequest("/range/table/202403011000/3448439").getText().contains("<p>23.5475\u00b0 S, 46.63611\u00b0 W</p>"));
            assertTrue(m.doRequest("/wheel/image/202403011000/2147714").getText().contains(">33.86785\u00b0 S, 151.20732\u00b0 E</text>"));
        }
    }

    @Test
    void everyFileThatAPageLoadsExists() {
        try (var m = new MockConversation(new AppSite())) {
            for (var path : PAGE_PATHS.values()) {
                var document = Jsoup.parse(m.doRequest(path).getText());
                var files = new ArrayList<>(document.select("script[src]").eachAttr("src"));
                files.addAll(document.select("link[rel=stylesheet], link[rel=icon], link[rel=apple-touch-icon], link[rel=manifest]").eachAttr("href"));
                files.add(document.select("meta[property=og:image]").attr("content"));
                assertTrue(files.size() >= 9, path + " " + files);
                for (var file : files) {
                    assertTrue(file.startsWith("http://localhost/"), path + " " + file);
                    var name = file.substring("http://localhost/".length()).replaceAll("\\?.*", "");
                    assertTrue(Files.isRegularFile(Path.of("src/main/webapp", name)), path + " " + file);
                }
            }
        }
    }

    @Test
    void aBrowserCanInstallTheSiteFromItsManifest() throws Exception {
        try (var m = new MockConversation(new AppSite())) {
            var document = Jsoup.parse(m.doRequest("/wheel/birth").getText());
            assertEquals("http://localhost/site.webmanifest", document.select("link[rel=manifest]").attr("href"));
            // a file that the container serves, which RIFE2 has to leave to it
            assertTrue(RifeConfig.engine().getPassThroughSuffixes().contains("webmanifest"));
            var root = Path.of("src/main/webapp");
            var manifest = Json.parseObject(Files.readString(root.resolve("site.webmanifest")));
            assertEquals("Gates of Humanity", manifest.getString("name"));
            assertEquals("standalone", manifest.getString("display"));
            // addresses in it are from where it is, so that the site can be deployed under any root
            assertEquals("./", manifest.getString("scope"));
            assertEquals(200, m.doRequest("/" + manifest.getString("start_url")).getStatus());
            assertEquals(document.select("meta[name=theme-color]").attr("content"), manifest.getString("theme_color"));
            var kinds = new ArrayList<String>();
            for (var listed : (JsonArray) manifest.get("icons")) {
                var icon = (rife.json.JsonObject) listed;
                var picture = javax.imageio.ImageIO.read(root.resolve(icon.getString("src")).toFile());
                assertEquals(icon.getString("sizes"), picture.getWidth() + "x" + picture.getHeight(), icon.getString("src"));
                kinds.add(icon.getString("sizes") + " " + icon.getString("purpose", "any"));
            }
            // what Chrome asks for before it offers to install, and one that Android may cut to its own shape
            assertEquals(List.of("192x192 any", "512x512 any", "512x512 maskable"), kinds);
        }
    }

    @Test
    void addressesWithoutAPageGetAPageOfTheSite() {
        try (var m = new MockConversation(new AppSite())) {
            for (var url : List.of("/nonexistent", "/learn/typo", "/wheel/birth/extra", "/about/more")) {
                var response = m.doRequest(url);
                assertEquals(404, response.getStatus(), url);
                var html = response.getText();
                assertTrue(html.contains("<h1 class=\"learn-title\">This Page Doesn't Exist</h1>"), html);
                assertTrue(html.contains("<a href=\"http://localhost/wheel/birth\">Birth Chart</a>"), url);
                // no item of the menu is the current one
                assertFalse(html.contains("current\">"), url);
                assertTrue(m.doRequest("/learn/types").getText().contains("<li class=\"current\"><a href=\"http://localhost/learn\">"));
            }
            assertEquals(404, m.doRequest("/wheel", new MockRequest().method(RequestMethod.POST)).getStatus());
        }
    }

    @Test
    void errorsOfTheDeployedSiteGetAPageOfTheSite() {
        restoringRifeConfig(() -> {
            var logged = RifeConfig.engine().getLogEngineExceptions();
            RifeConfig.engine().setLogEngineExceptions(false);
            var site = new AppSiteDeployed() {
                public final rife.engine.Route broken = get("/broken", c -> {
                    throw new IllegalStateException("broken on purpose");
                });
            };
            try (var m = new MockConversation(site)) {
                var response = m.doRequest("/broken");
                assertEquals(500, response.getStatus());
                var html = response.getText();
                assertTrue(html.contains("<h1 class=\"learn-title\">This Page Couldn't Be Shown</h1>"), html);
                assertTrue(html.contains("An error kept this page from being shown. Trying again in a moment usually helps."), html);
                assertFalse(html.contains("broken on purpose"), html);
            } finally {
                RifeConfig.engine().setLogEngineExceptions(logged);
            }
        });
    }

    @Test
    void theChartAndItsPanelNameTheTwoPeopleAlike() {
        try (var m = new MockConversation(new AppSite())) {
            for (var path : List.of("/connection/195008110945/5392171/195502241915/5391959",
                                    "/connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743")) {
                var image = m.doRequest("/wheel/image" + path).getText();
                assertTrue(image.contains(">First person</text>") && image.contains(">Second person</text>"), image);
                var panel = m.doRequest("/wheel/explore" + path).getText();
                assertTrue(panel.contains("<h3>First person, Personality planets</h3>") && panel.contains("<h3>Second person, Design planets</h3>"), panel);

                // a long name is shortened in both
                var names = new MockRequest().parameter("name", "A".repeat(40)).parameter("secondName", "B".repeat(40));
                for (var route : List.of("/wheel/image", "/wheel/explore")) {
                    var named = m.doRequest(route + path, names).getText();
                    assertTrue(named.contains("A".repeat(27)) && named.contains("B".repeat(27)), route + path);
                    assertFalse(named.contains("A".repeat(28)) || named.contains("B".repeat(28)), route + path);
                }
            }
        }
    }

    @Test
    void connectionRowsStayWithinTheChartWithLongNames() {
        try (var m = new MockConversation(new AppSite())) {
            var names = new MockRequest().parameter("name", "Bartholomew Featherstonehaugh").parameter("secondName", "Wilhelmina Vandersloot-Marsh");
            for (var path : List.of("/connection/195008110945/5392171/195502241915/5391959",
                                    "/connection-transit/195502241915/5391959/195008110945/5391959/202403011000/2643743")) {
                var svg = m.doRequest("/wheel/image" + path, names).getText();
                var rows = Pattern.compile("<text class=\"property-value\" x=\"245\" y=\"\\d+\">([^<]*)</text>").matcher(svg).results().map(r -> r.group(1)).toList();
                assertTrue(rows.stream().anyMatch(row -> row.contains("(Wilhelmina")), path);
                for (var row : rows) {
                    assertTrue(row.length() <= 76, row);
                }
            }
        }
    }
}

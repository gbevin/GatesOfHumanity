package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.TransitTimeline;
import gatesofhumanity.models.Upcoming;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.test.MockConversation;
import rife.test.MockRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static gatesofhumanity.elements.Drawings.*;
import static org.junit.jupiter.api.Assertions.*;

public class PersonWidgetTest {
    private static final List<String> FAMILIES = List.of("systemSmall", "systemMedium", "systemLarge");
    // someone who doesn't exist, born in Brussels on the fourteenth of March 1988 at 7:25, an hour ahead of UTC
    private static final Instant BORN = Instant.parse("1988-03-14T06:25:00Z");
    private static final String PERSON = "/widget/person/198803140625/";
    private static final Set<Integer> OWN = Analysis.gates(Charts.birth(BORN.atZone(ZoneOffset.UTC)));
    private static final Set<Integer> INTEGRATION = Set.of(10, 20, 34, 57);

    private static Document drawing(MockConversation m, String family, Instant at, String name) {
        var request = new MockRequest();
        if (name != null) {
            request.parameter("name", name);
        }
        var response = m.doRequest(PERSON + family + moment(at), request);
        assertEquals(200, response.getStatus(), family);
        assertEquals("image/svg+xml", response.getContentType().split(";")[0], family);
        return org.jsoup.Jsoup.parse(response.getText(), org.jsoup.parser.Parser.xmlParser());
    }

    private static List<String> said(Document drawing) {
        return drawing.select("a > text, a > g:not([transform*=scale]) > text").eachText();
    }

    private static Set<Integer> transit(Instant at) {
        return Analysis.gates(Charts.transit(at.atZone(ZoneOffset.UTC)));
    }

    // what the planets add at a moment to what the person has: channels and centers
    private static List<Analysis.NamedChannel> added(Instant at) {
        var all = new HashSet<>(OWN);
        all.addAll(transit(at));
        var own = Analysis.definedChannels(OWN);
        return Analysis.definedChannels(all).stream().filter(channel -> !own.contains(channel)).toList();
    }

    @Test
    void aPersonHasADrawingForEachMomentAPlanetMovesToAnotherGate() {
        var moments = TransitTimeline.ofGates(Upcoming.planets(), FROM, Widget.AHEAD);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var response = m.doRequest(PERSON + family + moment(FROM) + "/ahead", new MockRequest().parameter("name", "Nora B&C"));
                assertEquals(200, response.getStatus(), family);
                var timeline = Json.parseObject(response.getText());
                assertEquals("2026-11-16T17:00:00Z", timeline.getString("reload"), family);
                assertEquals("2026-11-18T17:00:00Z", timeline.getString("until"), family);
                var entries = (JsonArray) timeline.get("entries");
                assertEquals(moments.size(), entries.size(), family);
                for (var i = 0; i < moments.size(); ++i) {
                    var entry = (JsonObject) entries.get(i);
                    assertEquals(moments.get(i).start().toString(), entry.getString("date"), family);
                    // the address of a drawing has the person in it, and the name to show
                    assertEquals(PERSON + family + moment(moments.get(i).start()) + "?name=Nora+B%26C", entry.getString("drawing"), family);
                }
            }
            // without a name the drawings have none
            var unnamed = Json.parseObject(m.doRequest(PERSON + "systemSmall" + moment(FROM) + "/ahead").getText());
            assertEquals(PERSON + "systemSmall" + moment(FROM), ((JsonObject) ((JsonArray) unnamed.get("entries")).getFirst()).getString("drawing"));
            // and asked without a moment they begin now
            var now = Json.parseObject(m.doRequest(PERSON + "systemSmall", new MockRequest().parameter("name", "Nora")).getText());
            assertTrue(((JsonObject) ((JsonArray) now.get("entries")).getFirst()).getString("drawing").matches(PERSON + "systemSmall/\\d{12}\\?name=Nora"));
        }
    }

    @Test
    void theDrawingsAreWhatTheAppCanDrawAndTakeApart() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var request = new MockRequest().parameter("name", "Nora <B> & \"C\"");
                var svg = m.doRequest(PERSON + family + moment(FROM), request).getText();
                // a tap opens the chart of the person with the transits, which the page shows for who was saved under the name
                assertDrawn(svg, family, 1, "http://localhost/wheel/birth-transit?person=Nora%20%3CB%3E%20%26%20%22C%22");
                assertDrawn(m.doRequest(PERSON + family + moment(FROM)).getText(), family, 1, "http://localhost/wheel/birth-transit");
            }
        }
    }

    @Test
    void theGatesOfThePersonAndOfThePlanetsAreToldApart() {
        var transit = transit(FROM);
        var all = new HashSet<>(OWN);
        all.addAll(transit);
        // the person has gates of their own, the planets others, and some both
        assertTrue(OWN.stream().anyMatch(gate -> !transit.contains(gate)) && transit.stream().anyMatch(gate -> !OWN.contains(gate)) && OWN.stream().anyMatch(transit::contains));
        var own_centers = Analysis.definedCenters(OWN);
        var centers = Analysis.definedCenters(all);
        assertTrue(centers.size() > own_centers.size() && !own_centers.isEmpty());
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var svg = drawing(m, family, FROM, "Nora");
                assertEquals(new TreeSet<>(all), svg.select("text.small-on").eachText().stream().map(Integer::parseInt).collect(Collectors.toCollection(TreeSet::new)), family);
                // each half of a channel in the color of who has its gate
                for (var channel : Analysis.allChannels()) {
                    if (INTEGRATION.contains(channel.gate1()) || INTEGRATION.contains(channel.gate2())) {
                        continue;
                    }
                    var expected = new ArrayList<String>();
                    for (var gate : List.of(channel.gate1(), channel.gate2())) {
                        expected.add(OWN.contains(gate) ? transit.contains(gate) ? "hd-channel-both" : "hd-channel-on" : transit.contains(gate) ? "hd-channel-design" : "hd-channel-off");
                    }
                    var halves = svg.select("[data-explore=channel." + channel.key() + " channels]:not(.hd-channel-contour)");
                    var found = halves.stream().map(half -> half.classNames().stream().filter(name -> name.matches("hd-channel-(on|off|design|both)")).findFirst().orElseThrow()).sorted().toList();
                    assertEquals(expected.stream().sorted().toList(), found, family + " " + channel);
                }
                // a center of the person in its color, one that the planets add with a line of their color around it
                for (var center : Center.values()) {
                    var shape = svg.selectFirst("path.hd-center[data-explore=center." + center.name() + " centers]");
                    assertEquals(!centers.contains(center), shape.hasClass("center-off"), family + " " + center);
                    assertEquals(centers.contains(center) && !own_centers.contains(center), shape.hasClass("center-added"), family + " " + center);
                }
                // with the unlit channels darker than in a body graph of the planets alone, under the soft color of theirs
                assertTrue(svg.selectFirst("a > g[transform*=scale]").hasClass("with-own"), family);
                var style = svg.selectFirst("style").outerHtml();
                // an open center has the color of a channel that isn't lit, as it has in a chart
                assertTrue(style.contains(".with-own .hd-channel-off, .with-own .hd-channel-off-stroke, .with-own .center-off { fill: #55559a; }"), family);
                assertEquals(2, style.split("center-off", -1).length - 1, family);
                assertTrue(style.contains(".hd-channel-design, .hd-channel-design-stroke { fill: #a4a4d4; }") && style.contains(".center-added { stroke: #a4a4d4;"), family);
            }
        }
    }

    // each center of a body graph with whether it's drawn as a defined one
    private static TreeMap<String, Boolean> centers(Document svg) {
        var centers = new TreeMap<String, Boolean>();
        for (var center : svg.select("path.hd-center")) {
            centers.put(center.attr("data-explore"), !center.hasClass("center-off"));
        }
        assertEquals(9, centers.size());
        return centers;
    }

    @Test
    void theCentersAreThoseOfTheChartThatTheWidgetOpens() {
        // the chart with the transits of the same person at the same moment, which is on the clock of a place
        var brussels = ZoneId.of("Europe/Brussels");
        var open = 0;
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var step = 0; step < 6; ++step) {
                var at = FROM.plus(Duration.ofDays(47L * step)).plus(Duration.ofMinutes(613L * step));
                var chart = m.doRequest("/wheel/image/birth-transit/" + HdImage.MOMENT_FORMAT.format(BORN.atZone(brussels)) + "/2800866/"
                    + HdImage.MOMENT_FORMAT.format(at.atZone(brussels)) + "/2800866");
                assertEquals(200, chart.getStatus(), at.toString());
                var drawn = centers(Jsoup.parse(chart.getText(), Parser.xmlParser()));
                for (var family : FAMILIES) {
                    assertEquals(drawn, centers(drawing(m, family, at, "Nora")), family + " at " + at);
                }
                open += (int) drawn.values().stream().filter(defined -> !defined).count();
            }
        }
        // there were open centers to leave open
        assertTrue(open >= 3, open + " open centers");
    }

    @Test
    void theChannelsThatThePlanetsAddAreNamedWithUntilWhenTheyLast() {
        var moment = TransitTimeline.atGates(Upcoming.planets(), FROM);
        var added = added(FROM);
        assertEquals(List.of("10-20 Awakening", "23-43 Structuring", "25-51 Initiation", "28-38 Struggle", "30-41 Recognition"), added.stream().map(Object::toString).toList());
        // none of them is the person's alone, and the two of the planets alone are among them
        assertTrue(added.stream().noneMatch(channel -> OWN.contains(channel.gate1()) && OWN.contains(channel.gate2())));
        var centers = new HashSet<>(Analysis.definedCenters(new HashSet<>() {{ addAll(OWN); addAll(transit(FROM)); }}));
        centers.removeAll(Analysis.definedCenters(OWN));
        assertEquals(5, centers.size());
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var small = drawing(m, "systemSmall", FROM, "Nora");
            assertEquals(List.of("NORA", "10-20", "23-43", "25-51", "28-38", "+1"), said(small));
            assertEquals("The Transits of Nora", small.selectFirst("title").text());
            var medium = drawing(m, "systemMedium", FROM, "Nora");
            var expected = new ArrayList<String>(List.of("NORA", "UNTIL", "5 centers added"));
            var times = new ArrayList<String>();
            for (var channel : added.subList(0, 4)) {
                var until = BodygraphWidget.until(moment, channel, OWN);
                expected.addAll(List.of(channel.key(), channel.name(), Widget.Ahead.of(FROM, until).text()));
                times.add(until.toString());
            }
            expected.add("and 1 more");
            assertEquals(expected, said(medium));
            assertEquals(times, medium.select("text[data-time]").eachAttr("data-time"));
            // a gate of the person is never left: the channel lasts as long as the planet stays in the other one
            var awakening = added.getFirst();
            var planets = OWN.contains(awakening.gate1()) ? awakening.gate2() : awakening.gate1();
            assertTrue(OWN.contains(awakening.gate1()) != OWN.contains(awakening.gate2()));
            assertEquals(moment.places().stream().filter(place -> place.gate() == planets).map(TransitTimeline.Place::until).max(Instant::compareTo).orElseThrow().toString(), times.getFirst());
            var large = said(drawing(m, "systemLarge", FROM, "Nora"));
            assertEquals(List.of("NORA", "5 centers added", "10-20", "Awakening"), large.subList(0, 4));
            assertEquals("and 1 more", large.getLast());
        }
    }

    // how many centers the planets add at a moment to those of the person
    private static int centersAdded(Instant at) {
        var all = new HashSet<>(OWN);
        all.addAll(transit(at));
        var centers = new HashSet<>(Analysis.definedCenters(all));
        centers.removeAll(Analysis.definedCenters(OWN));
        return centers.size();
    }

    private static Instant dayThatAdds(int centers) {
        for (var day = 0; day < 3000; ++day) {
            var at = FROM.plus(java.time.Duration.ofDays(day));
            if (centersAdded(at) == centers) {
                return at;
            }
        }
        throw new AssertionError("no such day in eight years");
    }

    @Test
    void aTapOnTheWatchOpensARowForEveryChannelThatThePlanetsAdd() {
        var moment = TransitTimeline.atGates(Upcoming.planets(), FROM);
        var added = added(FROM);
        assertEquals(5, added.size());
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // all five, where the largest widget has places for four, and without the name: the watch has it for a title
            for (var name : new String[] {null, "Nora"}) {
                var request = new MockRequest();
                if (name != null) {
                    request.parameter("name", name);
                }
                var response = m.doRequest(PERSON + "watch" + moment(FROM), request);
                assertEquals(200, response.getStatus());
                assertFalse(response.getText().contains("Nora"));
                var told = Json.parseObject(response.getText());
                assertEquals(List.of("Channels added now", "5 centers added"), List.of(told.getString("heading"), told.getString("note")));
                var about = (JsonObject) ((JsonArray) told.get("about")).getFirst();
                assertEquals(List.of(Descriptions.of("about.transits.birth"), Descriptions.of("about.transits.center")), about.get("texts"));
                var rows = (JsonArray) told.get("rows");
                var expected = new ArrayList<String>();
                for (var channel : added) {
                    expected.add(channel.key() + " " + channel.name() + " " + BodygraphWidget.until(moment, channel, OWN));
                }
                assertEquals(expected, rows.stream().map(row -> ((JsonObject) row).getString("value") + " " + ((JsonObject) row).getString("name") + " " + ((JsonObject) row).getString("until")).toList());
            }
            // without a channel that the planets add, in the words of a person's widget
            assertEquals("added", new PersonWidget().are());
            assertEquals("No channel is added now", new PersonWidget().none());
            assertEquals(List.of("No center", "1 center", "2 centers"), List.of(new PersonWidget().centers(0), new PersonWidget().centers(1), new PersonWidget().centers(2)));
        }
    }

    @Test
    void theCentersThatThePlanetsAddAreCounted() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals("5 centers added", said(drawing(m, "systemMedium", FROM, "Nora")).get(2));
            // a channel of the planets to a center that the person has adds one alone
            var one = dayThatAdds(1);
            assertEquals("1 center added", said(drawing(m, "systemMedium", one, "Nora")).get(2));
            assertEquals(1, drawing(m, "systemLarge", one, "Nora").select(".center-added").size());
            var none = dayThatAdds(0);
            assertEquals("No center added", said(drawing(m, "systemLarge", none, "Nora")).get(1));
            assertTrue(drawing(m, "systemLarge", none, "Nora").select(".center-added").isEmpty());
            if (added(none).isEmpty()) {
                assertEquals("No channel is added now", said(drawing(m, "systemMedium", none, "Nora")).getLast());
            }
        }
    }

    @Test
    void theNameIsShownAsItFits() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals("NORA <B> & \"C\"", said(drawing(m, "systemLarge", FROM, " Nora <B> & \"C\" ")).getFirst());
            // sixteen letters of a heading
            assertEquals("MAXIMILIANE ALE…", said(drawing(m, "systemMedium", FROM, "Maximiliane Alexandra")).getFirst());
            assertEquals("The Transits of Maximiliane Alexandra", drawing(m, "systemMedium", FROM, "Maximiliane Alexandra").selectFirst("title").text());
            // without a name the drawing says what it shows
            var unnamed = drawing(m, "systemLarge", FROM, null);
            assertEquals(List.of("CHANNELS NOW", "5 centers added"), said(unnamed).subList(0, 2));
            assertEquals("The Body Graph Now", unnamed.selectFirst("title").text());
            assertEquals("10-20", said(drawing(m, "systemSmall", FROM, null)).getFirst());
        }
    }

    @Test
    void aNameWithWhatXmlRefusesIsStillADrawing() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var request = new MockRequest();
            request.parameter("name", "Ste\u0001ve\uFFFE\nB");
            var svg = m.doRequest(PERSON + "systemLarge" + moment(FROM), request).getText();
            // what the app draws it with refuses what isn't well-formed
            assertDoesNotThrow(() -> javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg))));
            assertEquals("STEVE B", said(org.jsoup.Jsoup.parse(svg, org.jsoup.parser.Parser.xmlParser())).getFirst());
        }
    }

    // where the body graph of a drawing ends: the lowest point of the shape that it's drawn on, an arc of
    // 348 below a chord of 568 at 680, moved and made smaller as the drawing has it
    private static double graphBottom(Document svg) {
        var placed = java.util.regex.Pattern.compile("translate\\((\\S+) (\\S+)\\) scale\\((\\S+)\\)").matcher(svg.selectFirst("a > g[transform*=scale]").attr("transform"));
        assertTrue(placed.matches());
        return Double.parseDouble(placed.group(2)) + (680 + 348 - Math.sqrt(348 * 348 - 284 * 284)) * Double.parseDouble(placed.group(3));
    }

    @Test
    void theSmallestSizeHasALineForTheNameUnderTheBodyGraph() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var named : List.of(List.of("Nora", "NORA", "9"), List.of("Marie-Christine V", "MARIE-CHRISTINE V", "8"),
                                     // in the smallest letters there's room for twenty, and a longer name is cut there
                                     List.of("Anne-Sophie Van Damme", "ANNE-SOPHIE VAN DAM…", "7"))) {
                var svg = drawing(m, "systemSmall", FROM, named.get(0));
                var name = svg.selectFirst("text.figure.quiet");
                assertEquals(named.get(1), name.text());
                assertEquals(named.get(2), name.attr("font-size"));
                // no letter reaches the body graph above it, nor what tells that there's more at the other end of the line
                var top = Double.parseDouble(name.attr("y")) - Double.parseDouble(name.attr("font-size"));
                assertTrue(graphBottom(svg) <= top, graphBottom(svg) + " over " + top);
                // capitals of a bold Helvetica are about two thirds of their size wide, a few of them more
                var end = Double.parseDouble(name.attr("x")) + name.text().length() * 0.72 * Double.parseDouble(name.attr("font-size"));
                assertTrue(end < 128, named.get(1) + " ends at " + end);
                assertEquals("146", svg.selectFirst("text[text-anchor=end].quiet").attr("x"));
            }
            // without a name the body graph has the room of that line too, as the one of the moment
            var unnamed = drawing(m, "systemSmall", FROM, null);
            assertNull(unnamed.selectFirst("text.figure.quiet"));
            assertTrue(graphBottom(unnamed) > 149, String.valueOf(graphBottom(unnamed)));
            assertEquals(graphBottom(unnamed), graphBottom(Drawings.drawing(m, "/widget/bodygraph/systemSmall" + moment(FROM))));
        }
    }

    @Test
    void aBirthOnTheClockOfAPlaceIsAMomentInUtc() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // Brussels is an hour ahead of UTC in winter and two in summer
            var winter = m.doRequest("/widget/born/198803140725/2800866");
            assertEquals(200, winter.getStatus());
            assertEquals("text/plain", winter.getContentType().split(";")[0]);
            assertEquals("198803140625", winter.getText());
            assertEquals("201606211105", m.doRequest("/widget/born/201606211305/2800866").getText());
            // Toronto is four hours behind in May, and a place that isn't known is London
            assertEquals("197205182345", m.doRequest("/widget/born/197205181945/6167865").getText());
            assertEquals("197205181845", m.doRequest("/widget/born/197205181945/1").getText());
            assertEquals(400, m.doRequest("/widget/born/198813140725/2800866").getStatus());
            var before = Ephemeris.firstChart().atZone(ZoneOffset.UTC).minusYears(1);
            assertEquals(400, m.doRequest("/widget/born/" + HdImage.MOMENT_FORMAT.format(before) + "/2800866").getStatus());
            assertEquals(404, m.doRequest("/widget/born/198803140725").getStatus());
        }
    }

    @Test
    void whatCantBeAnsweredIsRefused() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals(400, m.doRequest("/widget/person/198813140625/systemLarge" + moment(FROM)).getStatus());
            assertEquals(400, m.doRequest("/widget/person/198803140625/systemLarge/202613011700").getStatus());
            // a birth needs the tables to reach back to the design before it
            var early = Ephemeris.firstChart().atZone(ZoneOffset.UTC).minusDays(1);
            assertEquals(400, m.doRequest("/widget/person/" + HdImage.MOMENT_FORMAT.format(early) + "/systemLarge" + moment(FROM)).getStatus());
            assertEquals(404, m.doRequest("/widget/person/198803140625/accessoryCircular" + moment(FROM)).getStatus());
            assertEquals(404, m.doRequest("/widget/person/systemLarge" + moment(FROM)).getStatus());
            assertEquals(404, m.doRequest("/widget/person/198803140625").getStatus());
        }
        try (var m = new MockConversation(new AppSite())) {
            assertEquals(404, m.doRequest("/widget/person/198803140625/systemLarge" + moment(FROM)).getStatus());
            assertEquals(404, m.doRequest("/widget/born/198803140725/2800866").getStatus());
        }
    }
}

package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.Entities;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.GateNames;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.TransitTimeline;
import gatesofhumanity.models.Upcoming;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.test.MockConversation;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static gatesofhumanity.elements.Drawings.*;
import static org.junit.jupiter.api.Assertions.*;

public class TransitsWidgetTest {
    private static final List<String> FAMILIES = List.of("systemSmall", "systemMedium", "accessoryRectangular", "accessoryInline", "accessoryCircular");
    private static final String MOMENT = moment(FROM);

    private static String place(TransitTimeline.Moment moment, Planet planet) {
        var place = moment.places().stream().filter(each -> each.planet() == planet).findFirst().orElseThrow();
        return place.gate() + "." + place.line();
    }

    @Test
    void everySizeOfWidgetHasADrawingForEachMomentAPlanetMovesOn() {
        var moments = TransitTimeline.of(TransitsWidget.PLANETS, FROM, Widget.AHEAD);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var response = m.doRequest("/widget/transits/" + family + MOMENT + "/ahead");
                assertEquals(200, response.getStatus(), family);
                assertEquals("application/json", response.getContentType().split(";")[0], family);
                var timeline = Json.parseObject(response.getText());
                assertEquals("2026-11-16T17:00:00Z", timeline.getString("reload"), family);
                assertEquals("2026-11-18T17:00:00Z", timeline.getString("until"), family);
                var entries = (JsonArray) timeline.get("entries");
                assertEquals(moments.size(), entries.size(), family);
                for (var i = 0; i < moments.size(); ++i) {
                    var entry = (JsonObject) entries.get(i);
                    var moment = moments.get(i);
                    assertEquals(moment.start().toString(), entry.getString("date"), family);
                    var path = "/widget/transits/" + family + moment(moment.start());
                    assertEquals(path, entry.getString("drawing"), family);
                    // the Sun's line is in every size, with the gate and line that it has at that moment
                    var shown = String.join("|", texts(m, path));
                    assertTrue(shown.contains(place(moment, Planet.SUN)), family + " at " + moment.start() + " shows " + shown);
                }
            }
        }
    }

    @Test
    void aDrawingIsWhatTheAppCanDrawAndTakeApart() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                // the second moment has a name on two lines
                for (var moment : List.of(MOMENT, "/202608080000")) {
                    var path = "/widget/transits/" + family + moment;
                    assertDrawn(svg(m, path), path, family.startsWith("system") || family.equals("accessoryCircular") ? 1 : 0);
                }
            }
        }
    }

    @Test
    void theSmallWidgetIsAboutTheSun() {
        var moment = TransitTimeline.at(TransitsWidget.PLANETS, FROM);
        var sun = moment.places().getFirst();
        assertEquals(Planet.SUN, sun.planet());
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var path = "/widget/transits/systemSmall" + MOMENT;
            var texts = texts(m, path);
            assertEquals(List.of("SUN", sun.gate() + "." + sun.line(), GateNames.of(sun.gate()).name(), "until " + WRITTEN.format(sun.until()),
                "Earth " + place(moment, Planet.EARTH), "Moon " + place(moment, Planet.MOON)), texts);
            // at 233.34 degrees, 4.47 degrees into gate 43, which begins 51 gates of 5.625 degrees after 302
            assertEquals("43.5", texts.get(1));
            assertEquals("Insight", texts.get(2));
            assertEquals("until Sun 22:13", texts.get(3));
            var until = drawing(m, path).select("text[data-time]");
            assertEquals(List.of(sun.until().toString()), until.eachAttr("data-time"));
            assertEquals(List.of("until {EEEjmm}"), until.eachAttr("data-text"));
        }
    }

    @Test
    void aTapOnTheWatchOpensARowForEveryPlanetWithWhatExplainsIt() {
        var moments = TransitTimeline.of(Upcoming.planets(), FROM, Widget.AHEAD);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // it changes each time any planet moves to another line, which is more often than the three of the widgets do
            var dates = new ArrayList<String>();
            for (var entry : (JsonArray) Json.parseObject(m.doRequest("/widget/transits/watch" + MOMENT + "/ahead").getText()).get("entries")) {
                dates.add(((JsonObject) entry).getString("date"));
                assertTrue(((JsonObject) entry).getString("drawing").startsWith("/widget/transits/watch/"));
            }
            assertEquals(moments.stream().map(moment -> moment.start().toString()).toList(), dates);
            assertTrue(moments.size() > TransitTimeline.of(TransitsWidget.PLANETS, FROM, Widget.AHEAD).size());

            var moment = moments.getFirst();
            var response = m.doRequest("/widget/transits/watch" + moment(moment.start()));
            assertEquals(200, response.getStatus());
            assertEquals("application/json", response.getContentType().split(";")[0]);
            var told = Json.parseObject(response.getText());
            assertEquals("Transits now", told.getString("heading"));
            var about = (JsonObject) ((JsonArray) told.get("about")).getFirst();
            assertEquals("Transits", about.getString("title"));
            assertEquals(List.of(Descriptions.of("about.transits"), Descriptions.of("about.planets")), about.get("texts"));
            // a row for each of the thirteen planets, in the order of the rows below the wheel
            var rows = (JsonArray) told.get("rows");
            assertEquals(List.of("Sun", "Earth", "Moon", "North Node", "South Node", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"),
                rows.stream().map(row -> ((JsonObject) row).getString("label")).toList());
            var skeletons = new java.util.HashSet<String>();
            for (var at = 0; at < rows.size(); ++at) {
                var row = (JsonObject) rows.get(at);
                var place = moment.places().get(at);
                assertEquals(place.gate() + "." + place.line(), row.getString("value"));
                assertEquals(capitalized(GateNames.of(place.gate()).name()), row.getString("name"));
                assertEquals(place.until().toString(), row.getString("until"));
                assertEquals(Widget.Ahead.of(moment.start(), place.until()).skeleton(), "{" + row.getString("untilAs") + "}");
                skeletons.add(row.getString("untilAs"));
                // what the planet, its gate and its line are about, in the words of the Explore panel
                var parts = (JsonArray) row.get("about");
                assertEquals(List.of(row.getString("label"), "Gate " + place.gate() + ", " + GateNames.of(place.gate()).name(), "Line " + place.line()),
                    parts.stream().map(part -> ((JsonObject) part).getString("title")).toList());
                var planet = "planet." + place.planet().name();
                assertEquals(List.of(Descriptions.of(planet), Descriptions.of(planet + ".more"), Descriptions.of("transit.planet")), ((JsonObject) parts.get(0)).get("texts"));
                assertEquals(ExplorePanel.gateTexts(place.gate()), ((JsonObject) parts.get(1)).get("texts"));
                assertEquals(List.of(Descriptions.of("gate.line." + place.line())), ((JsonObject) parts.get(2)).get("texts"));
            }
            // a slow planet stays until a date, where the Moon moves on within the day
            assertTrue(skeletons.contains("EEEjmm") && skeletons.size() > 1, skeletons.toString());
            // the gate is described as the Explore panel does, with where it is in the body graph
            var sun = ExplorePanel.gateTexts(43);
            assertEquals(3, sun.size());
            assertEquals("Gate 43, Insight, is hexagram 43 of the I Ching, where it's called Breakthrough.", sun.getFirst());
            assertEquals(Descriptions.of("gate.43"), sun.get(1));
            assertTrue(sun.get(2).startsWith("This gate is in the Ajna, at one end of the channel of "), sun.get(2));
        }
    }

    @Test
    void theMediumWidgetHasARowForEachPlanetWithWhenItMovesOn() {
        var moments = TransitTimeline.of(TransitsWidget.PLANETS, FROM, Widget.AHEAD);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var moment : List.of(moments.getFirst(), moments.get(1), moments.getLast())) {
                var svg = drawing(m, "/widget/transits/systemMedium" + moment(moment.start()));
                var expected = new ArrayList<String>(List.of("TRANSITS NOW", "UNTIL"));
                for (var place : moment.places()) {
                    expected.add(capitalized(place.planet().id_));
                    expected.add(place.gate() + "." + place.line());
                    expected.addAll(Fitted.of(capitalized(GateNames.of(place.gate()).name()), 13, 15, 10, 2).lines());
                    expected.add(WRITTEN.format(place.until()));
                }
                assertEquals(expected, svg.select("text").eachText());
                assertEquals(moment.places().stream().map(place -> place.until().toString()).toList(), svg.select("text[data-time]").eachAttr("data-time"));
                assertEquals(List.of("{EEEjmm}", "{EEEjmm}", "{EEEjmm}"), svg.select("text[data-time]").eachAttr("data-text"));
            }
        }
    }

    // the lines of a hexagram from the bottom up, a whole one as a rectangle and a broken one as two, and which are lit
    private static List<String> hexagram(Elements rectangles) {
        var lines = new TreeMap<Integer, String>(Comparator.reverseOrder());
        for (var rectangle : rectangles) {
            lines.merge(Integer.parseInt(rectangle.attr("y")), rectangle.className() + " " + rectangle.attr("width"), (one, other) -> one + "," + other);
        }
        assertEquals(List.of(25, 20, 15, 10, 5, 0), List.copyOf(lines.keySet()));
        return List.copyOf(lines.values());
    }

    @Test
    void aGateHasItsHexagramWithTheLineOfThePlanetLit() {
        var whole = "unlit 30";
        var broken = "unlit 13,unlit 13";
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // the Sun in 43.5: Breakthrough has one broken line, at the top, and the fifth is lit
            assertEquals(List.of(whole, whole, whole, whole, "lit 30", broken), hexagram(drawing(m, "/widget/transits/systemSmall" + MOMENT).select("rect:not([id])")));
            // the Sun in 7.3: the Army has one whole line, the second, and the third is lit
            assertEquals(List.of(broken, whole, "lit 13,lit 13", broken, broken, broken), hexagram(drawing(m, "/widget/transits/systemSmall/202608080000").select("rect:not([id])")));
            // each row of the medium size has the hexagram of its own gate, as the gates have them
            var moment = TransitTimeline.at(TransitsWidget.PLANETS, FROM);
            var rows = drawing(m, "/widget/transits/systemMedium" + MOMENT).select("a > g");
            assertEquals(3, rows.size());
            for (var i = 0; i < rows.size(); ++i) {
                var place = moment.places().get(i);
                var iching = Entities.GATES.get(place.gate()).iching();
                var expected = new ArrayList<String>();
                for (var line = 1; line <= 6; ++line) {
                    var lit = line == place.line() ? "lit" : "unlit";
                    expected.add(iching.yang(line) ? lit + " 30" : lit + " 13," + lit + " 13");
                }
                assertEquals(expected, hexagram(rows.get(i).select("rect")), place.toString());
            }
        }
    }

    @Test
    void aLongNameGoesOnTwoLinesInSmallerLetters() {
        // the Sun is in gate 7, the Role of the Self in Interaction, from the sixth to the twelfth of August
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var small = drawing(m, "/widget/transits/systemSmall/202608080000");
            assertEquals(List.of("SUN", "7.3", "The Role of the", "Self in Interaction", "until Sat 14:19", "Earth 13.3", "Moon 16.6"), small.select("text").eachText());
            assertEquals(2, small.select("text[font-size=11.5]").size());
            var medium = drawing(m, "/widget/transits/systemMedium/202608080000");
            assertEquals(List.of("Sun", "7.3", "The Role of the", "Self in Interaction", "Sat 14:19"), medium.select("text").eachText().subList(2, 7));
            assertEquals(2, medium.select("text[font-size=10]").size());
            // the Lock Screen has one line for it
            assertEquals("Sun 7.3 The Role of the S…", texts(m, "/widget/transits/accessoryRectangular/202608080000").getFirst());
        }
    }

    @Test
    void aNameThatBeginsWithAnArticleBeginsWithACapital() {
        // the Sun is in gate 2, the Direction of the Self, from the fourth to the ninth of May
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals(List.of("The Direction", "of the Self"), texts(m, "/widget/transits/systemSmall/202605060000").subList(2, 4));
        }
    }

    @Test
    void withoutAMomentTheDrawingsBeginThisMinute() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var before = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            var timeline = Json.parseObject(m.doRequest("/widget/transits/accessoryInline").getText());
            var after = Instant.now().truncatedTo(ChronoUnit.MINUTES);
            var first = Instant.parse(((JsonObject) ((JsonArray) timeline.get("entries")).getFirst()).getString("date"));
            assertTrue(first.equals(before) || first.equals(after), first.toString());
            assertEquals(first.plus(Widget.ASK_AGAIN).toString(), timeline.getString("reload"));
        }
    }

    @Test
    void whatCantBeAnsweredIsRefused() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // the thirteenth month
            assertEquals(400, m.doRequest("/widget/transits/systemSmall/202613011700").getStatus());
            assertEquals(400, m.doRequest("/widget/transits/systemSmall/202613011700/ahead").getStatus());
            var after = Ephemeris.end().atZone(ZoneOffset.UTC).plusYears(1);
            assertEquals(400, m.doRequest("/widget/transits/systemSmall/" + HdImage.MOMENT_FORMAT.format(after)).getStatus());
            assertEquals(404, m.doRequest("/widget/transits/systemHuge" + MOMENT).getStatus());
            assertEquals(404, m.doRequest("/widget/transits/systemSmall/2026").getStatus());
            assertEquals(404, m.doRequest("/widget/transits/systemSmall" + MOMENT + "/behind").getStatus());
            assertEquals(404, m.doRequest("/widget/transits").getStatus());
        }
    }

    @Test
    void theSiteOnTheWebHasNoWidgets() {
        try (var m = new MockConversation(new AppSite())) {
            assertEquals(404, m.doRequest("/widget/transits/systemSmall" + MOMENT).getStatus());
            assertEquals(404, m.doRequest("/widget/transits/systemSmall").getStatus());
        }
    }
}

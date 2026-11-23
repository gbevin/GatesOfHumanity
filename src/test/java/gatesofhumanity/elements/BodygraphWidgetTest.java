package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.Circuit;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.TransitTimeline;
import gatesofhumanity.models.Upcoming;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.test.MockConversation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static gatesofhumanity.elements.Drawings.*;
import static org.junit.jupiter.api.Assertions.*;

public class BodygraphWidgetTest {
    private static final List<String> FAMILIES = List.of("systemSmall", "systemMedium", "systemLarge");

    private static Document drawing(MockConversation m, String family, Instant at) {
        return Drawings.drawing(m, "/widget/bodygraph/" + family + moment(at));
    }

    // what a drawing says beside its body graph, which has the numbers of the gates
    private static List<String> said(Document drawing) {
        return drawing.select("a > text, a > g:not([transform*=scale]) > text").eachText();
    }

    private static Set<Integer> gates(Instant at) {
        return Analysis.gates(Charts.transit(at.atZone(ZoneOffset.UTC)));
    }

    private static List<Analysis.NamedChannel> channels(Instant at) {
        return Analysis.definedChannels(gates(at));
    }

    // the first whole day from a moment on at which the channels of the planets are as asked
    private static Instant dayWith(Predicate<List<Analysis.NamedChannel>> channels) {
        for (var day = 0; day < 3000; ++day) {
            var at = FROM.plus(Duration.ofDays(day));
            if (channels.test(channels(at))) {
                return at;
            }
        }
        throw new AssertionError("no such day in eight years");
    }

    @Test
    void theBodyGraphChangesWhenAPlanetMovesToAnotherGate() {
        var moments = TransitTimeline.ofGates(Upcoming.planets(), FROM, Widget.AHEAD);
        // the Moon alone crosses seven gates in three days, and no line of a gate is a change
        assertTrue(moments.size() >= 7 && moments.size() < TransitTimeline.of(Upcoming.planets(), FROM, Widget.AHEAD).size() / 3, moments.size() + " moments");
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var response = m.doRequest("/widget/bodygraph/" + family + moment(FROM) + "/ahead");
                assertEquals(200, response.getStatus(), family);
                var timeline = Json.parseObject(response.getText());
                assertEquals("2026-11-16T17:00:00Z", timeline.getString("reload"), family);
                assertEquals("2026-11-18T17:00:00Z", timeline.getString("until"), family);
                var entries = (JsonArray) timeline.get("entries");
                assertEquals(moments.size(), entries.size(), family);
                for (var i = 0; i < moments.size(); ++i) {
                    var entry = (JsonObject) entries.get(i);
                    assertEquals(moments.get(i).start().toString(), entry.getString("date"), family);
                    assertEquals("/widget/bodygraph/" + family + moment(moments.get(i).start()), entry.getString("drawing"), family);
                }
            }
            // each drawing has the gates of its own moment
            for (var each : List.of(moments.get(1), moments.getLast())) {
                var lit = drawing(m, "systemLarge", each.start()).select("text.small-on").eachText().stream().map(Integer::parseInt).collect(Collectors.toCollection(TreeSet::new));
                assertEquals(new TreeSet<>(gates(each.start())), lit);
                assertNotEquals(new TreeSet<>(gates(FROM)), lit);
            }
        }
    }

    @Test
    void theDrawingsAreWhatTheAppCanDrawAndTakeApart() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                for (var at : List.of(FROM, dayWith(List::isEmpty), dayWith(channels -> channels.size() > 4))) {
                    var path = "/widget/bodygraph/" + family + moment(at);
                    assertDrawn(svg(m, path), path, 1);
                }
            }
        }
    }

    @Test
    void theGatesOfThePlanetsLightTheirChannelsAndCenters() {
        var gates = gates(FROM);
        // the Sun in 43 and the Earth across from it in 23 are the two ends of one channel
        assertTrue(gates.containsAll(List.of(43, 23, 30, 41)), gates.toString());
        var centers = Analysis.definedCenters(gates);
        assertEquals(Set.of(Center.AJNA, Center.THROAT, Center.SOLAR, Center.ROOT), centers);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var family : FAMILIES) {
                var svg = drawing(m, family, FROM);
                // on the shape that a chart has it on, which is drawn first
                assertEquals("cone", svg.selectFirst("a > g[transform*=scale]").child(0).className(), family);
                // every gate has its number in the drawing, lit or not, which the smaller sizes don't show
                assertEquals(64, svg.select("text.small-on, text.small-off").size(), family);
                assertEquals(new TreeSet<>(gates), svg.select("text.small-on").eachText().stream().map(Integer::parseInt).collect(Collectors.toCollection(TreeSet::new)), family);
                assertEquals(family.equals("systemLarge"), !svg.selectFirst("style").outerHtml().contains(".small-on, .small-off { display: none; }"), family);
                // both halves of a channel that's whole, one of a channel with one gate
                assertEquals(2, svg.select(".hd-channel-on[data-explore=channel.23-43 channels]").size(), family);
                assertEquals(0, svg.select(".hd-channel-off[data-explore=channel.23-43 channels]").size(), family);
                assertEquals(1, svg.select(".hd-channel-on[data-explore=channel.17-62 channels]").size(), family);
                assertEquals(1, svg.select(".hd-channel-off[data-explore=channel.17-62 channels]").size(), family);
                assertEquals(2, svg.select(".hd-channel-off[data-explore=channel.1-8 channels]").size(), family);
                for (var center : Center.values()) {
                    var shape = svg.selectFirst("path.hd-center[data-explore=center." + center.name() + " centers]");
                    assertEquals(!centers.contains(center), shape.hasClass("center-off"), family + " " + center);
                    assertEquals(centers.contains(center), shape.className().contains("gate-on-"), family + " " + center);
                }
                // an open center has the color of a channel that isn't lit, as it has in a chart, and no other
                var style = svg.selectFirst("style").outerHtml();
                assertTrue(style.contains(".hd-channel-off, .hd-channel-off-stroke, .center-off { fill: #6464aa; }"), family);
                assertEquals(2, style.split("center-off", -1).length - 1, family);
                // nothing is there without the planets, so nothing is added to it
                assertTrue(svg.select(".center-added, .hd-channel-design, .hd-channel-both, .with-own").isEmpty(), family);
                assertEquals("http://localhost/wheel", svg.selectFirst("a").attr("href"), family);
            }
        }
    }

    @Test
    void aCenterIsColoredExactlyWhenAWholeChannelEndsInIt() {
        var colored = 0;
        var open = 0;
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var step = 0; step < 12; ++step) {
                var at = FROM.plus(Duration.ofDays(61L * step)).plus(Duration.ofMinutes(431L * step));
                for (var family : FAMILIES) {
                    // told from the drawing alone: the gates whose numbers are lit, and the channels that have both of theirs
                    var svg = drawing(m, family, at);
                    var lit = svg.select("text.small-on").eachText().stream().map(Integer::parseInt).collect(Collectors.toSet());
                    var defined = Analysis.allChannels().stream().filter(channel -> lit.contains(channel.gate1()) && lit.contains(channel.gate2()))
                        .flatMap(channel -> Analysis.centers(channel).stream()).collect(Collectors.toSet());
                    for (var center : Center.values()) {
                        var shape = svg.selectFirst("path.hd-center[data-explore=center." + center.name() + " centers]");
                        assertEquals(defined.contains(center), shape.className().contains("gate-on-"), family + " " + center + " at " + at);
                        assertEquals(!defined.contains(center), shape.hasClass("center-off"), family + " " + center + " at " + at);
                    }
                    colored += defined.size();
                    open += 9 - defined.size();
                }
            }
        }
        // the planets alone leave most centers open, and both were there to tell apart
        assertTrue(colored >= 36 && open > colored, colored + " colored and " + open + " open");
    }

    @Test
    void thePieceThatTheIntegrationChannelsShareIsLitWhenBothItsEndsAre() {
        var middle = "[data-explore=channel.10-34 channel.10-57 channel.20-34 channel.20-57 channels]";
        // gate 20 alone at its upper end
        var gates = gates(FROM);
        assertTrue(gates.contains(20) && !gates.contains(34) && !gates.contains(57), gates.toString());
        var both = FROM;
        while (!((gates(both).contains(10) || gates(both).contains(20)) && (gates(both).contains(34) || gates(both).contains(57)))) {
            both = both.plus(Duration.ofDays(1));
        }
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals("hd-channel-20-57 hd-channel-off-stroke", drawing(m, "systemLarge", FROM).selectFirst(middle).className());
            assertEquals("hd-channel-20-57 hd-channel-on-stroke", drawing(m, "systemLarge", both).selectFirst(middle).className());
        }
    }

    @Test
    void theChannelsAreNamedWithUntilWhenTheyLast() {
        var moment = TransitTimeline.atGates(Upcoming.planets(), FROM);
        var channels = channels(FROM);
        assertEquals(List.of("23-43 Structuring", "30-41 Recognition"), channels.stream().map(Object::toString).toList());
        // the Sun and the Earth leave their gates together, the day after, and both gates of the other channel are held for nine days more
        var first = Upcoming.gateStay(Planet.SUN, FROM).end();
        assertEquals(first, BodygraphWidget.until(moment, channels.get(0), Set.of()));
        assertEquals("Mon 20:33", WRITTEN.format(first));
        var second = BodygraphWidget.until(moment, channels.get(1), Set.of());
        assertEquals(Instant.parse("2026-11-24T03:13:00Z"), second);
        for (var gate : List.of(30, 41)) {
            var longest = moment.places().stream().filter(place -> place.gate() == gate).map(TransitTimeline.Place::until).max(Instant::compareTo).orElseThrow();
            assertFalse(longest.isBefore(second), "gate " + gate);
        }
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // the small size is the body graph, with the channels by their gates in its corners
            assertEquals(List.of("23-43", "30-41"), said(drawing(m, "systemSmall", FROM)));
            var medium = drawing(m, "systemMedium", FROM);
            assertEquals(List.of("CHANNELS NOW", "UNTIL", "4 centers defined", "23-43", "Structuring", "Mon 20:33", "30-41", "Recognition", "Nov 24"), said(medium));
            // a day of the week says which day within a week, a date beyond it
            var until = medium.select("text[data-time]");
            assertEquals(List.of("{EEEjmm}", "{MMMd}"), until.eachAttr("data-text"));
            assertEquals(List.of(first.toString(), second.toString()), until.eachAttr("data-time"));
            var large = drawing(m, "systemLarge", FROM);
            assertEquals(List.of("CHANNELS NOW", "4 centers defined", "23-43", "Structuring", "until Mon 20:33", "30-41", "Recognition", "until Nov 24"), said(large));
            assertEquals(List.of("until {EEEjmm}", "until {MMMd}"), large.select("text[data-time]").eachAttr("data-text"));
        }
    }

    @Test
    void aMomentAheadIsWrittenByHowFarItIs() {
        assertEquals(new Widget.Ahead("{EEEjmm}", "Sat 16:59"), Widget.Ahead.of(FROM, Instant.parse("2026-11-21T16:59:00Z")));
        assertEquals(new Widget.Ahead("{MMMd}", "Nov 21"), Widget.Ahead.of(FROM, Instant.parse("2026-11-21T17:00:00Z")));
        assertEquals(new Widget.Ahead("{MMMd}", "Sep 11"), Widget.Ahead.of(FROM, Instant.parse("2027-09-11T16:59:00Z")));
        assertEquals(new Widget.Ahead("{yMMM}", "Sep 2027"), Widget.Ahead.of(FROM, Instant.parse("2027-09-11T17:00:00Z")));
    }

    @Test
    void aChannelLastsUntilOneOfItsGatesIsLeft() {
        var early = Instant.parse("2026-01-01T00:00:00Z");
        var later = Instant.parse("2026-01-02T00:00:00Z");
        var last = Instant.parse("2026-01-03T00:00:00Z");
        var channel = Analysis.definedChannels(Set.of(1, 8)).getFirst();
        // two planets in gate 1, of which the second stays longer than the one in gate 8
        var moment = new TransitTimeline.Moment(early, List.of(new TransitTimeline.Place(Planet.MOON, 1, 1, early),
            new TransitTimeline.Place(Planet.SUN, 1, 1, last), new TransitTimeline.Place(Planet.MARS, 8, 1, later), new TransitTimeline.Place(Planet.PLUTO, 2, 1, early)));
        assertEquals(later, BodygraphWidget.until(moment, channel, Set.of()));
        moment = new TransitTimeline.Moment(early, List.of(new TransitTimeline.Place(Planet.MOON, 1, 1, early),
            new TransitTimeline.Place(Planet.SUN, 1, 1, later), new TransitTimeline.Place(Planet.MARS, 8, 1, last)));
        assertEquals(later, BodygraphWidget.until(moment, channel, Set.of()));
        // a gate that's there without the planets is never left
        assertEquals(last, BodygraphWidget.until(moment, channel, Set.of(1)));
        assertEquals(later, BodygraphWidget.until(moment, channel, Set.of(8)));
    }

    @Test
    void eachChannelHasAPlaceOfItsOwnInADrawing() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // a day with four channels fills every place of every size
            var four = dayWith(channels -> channels.size() == 4);
            var keys = channels(four).stream().map(Analysis.NamedChannel::key).toList();
            var small = drawing(m, "systemSmall", four).select("a > text");
            assertEquals(keys, small.eachText());
            // the corners at the top, left and right, and below those
            assertEquals(List.of("12", "146", "12", "146"), small.eachAttr("x"));
            assertEquals(List.of("24", "24", "40", "40"), small.eachAttr("y"));
            var rows = drawing(m, "systemMedium", four).select("a > g:not([transform*=scale])");
            assertEquals(List.of("translate(0, 54)", "translate(0, 77)", "translate(0, 100)", "translate(0, 123)"), rows.eachAttr("transform"));
            assertEquals(keys, rows.select(".gold").eachText());
            var corners = drawing(m, "systemLarge", four).select("a > g:not([transform*=scale])");
            assertEquals(List.of("translate(18, 58)", "translate(320, 58)", "translate(18, 112)", "translate(320, 112)"), corners.eachAttr("transform"));
            // those at the right end where the drawing does
            assertEquals(List.of(false, true, false, true), corners.stream().map(corner -> corner.hasClass("end")).toList());
            assertTrue(drawing(m, "systemLarge", four).selectFirst("style").outerHtml().contains(".end text { text-anchor: end; }"));
            assertEquals(keys, corners.select(".gold").eachText());
            // the lower ones in smaller letters
            assertEquals(List.of("19", "19", "15", "15"), corners.select(".gold").eachAttr("font-size"));
        }
    }

    @Test
    void whatASizeHasNoPlaceForIsCounted() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var many = dayWith(channels -> channels.size() > 4);
            var channels = channels(many);
            var keys = channels.stream().limit(4).map(Analysis.NamedChannel::key).toList();
            var more = channels.size() - 4;
            var small = said(drawing(m, "systemSmall", many));
            assertEquals(keys, small.subList(0, 4));
            assertEquals(List.of("+" + more), small.subList(4, small.size()));
            for (var family : List.of("systemMedium", "systemLarge")) {
                var drawing = drawing(m, family, many);
                assertEquals(keys, drawing.select("a > g:not([transform*=scale]) .gold").eachText(), family);
                assertEquals("and " + more + " more", drawing.select("a > text").last().text(), family);
            }
        }
    }

    @Test
    void aTapOnTheWatchOpensARowForEveryChannelWithWhatExplainsIt() {
        // more channels than the largest widget has places for
        var at = dayWith(channels -> channels.size() > 4);
        var moment = TransitTimeline.atGates(Upcoming.planets(), at);
        var channels = channels(at);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var response = m.doRequest("/widget/bodygraph/watch" + moment(at));
            assertEquals(200, response.getStatus());
            assertEquals("application/json", response.getContentType().split(";")[0]);
            var told = Json.parseObject(response.getText());
            assertEquals("Channels defined now", told.getString("heading"));
            assertEquals(Analysis.definedCenters(gates(at)).size() + " centers defined", told.getString("note"));
            assertNull(told.get("none"));
            var about = (JsonObject) ((JsonArray) told.get("about")).getFirst();
            assertEquals("Channels", about.getString("title"));
            assertEquals(List.of(Descriptions.of("about.transits"), Descriptions.of("about.channels.transit"), Descriptions.of("about.transits.centers")), about.get("texts"));
            var rows = (JsonArray) told.get("rows");
            assertEquals(channels.size(), rows.size());
            for (var place = 0; place < rows.size(); ++place) {
                var row = (JsonObject) rows.get(place);
                var channel = channels.get(place);
                var until = BodygraphWidget.until(moment, channel, Set.of());
                assertNull(row.get("label"));
                assertEquals(List.of(channel.key(), capitalized(channel.name()), until.toString(), Widget.Ahead.of(at, until).skeleton()),
                    List.of(row.getString("value"), row.getString("name"), row.getString("until"), "{" + row.getString("untilAs") + "}"));
                // what the channel, its circuit and its two gates are about, in the words of the Explore panel
                var parts = (JsonArray) row.get("about");
                var circuit = Circuit.of(channel);
                assertEquals(List.of(channel.toString(), circuit.label_, ExplorePanel.gateTitle(channel.gate1()), ExplorePanel.gateTitle(channel.gate2())),
                    parts.stream().map(part -> ((JsonObject) part).getString("title")).toList());
                assertEquals(List.of(ExplorePanel.connects(channel), Descriptions.of("channel." + channel.key())), ((JsonObject) parts.get(0)).get("texts"));
                assertEquals(List.of(Descriptions.of("circuit." + circuit.name())), ((JsonObject) parts.get(1)).get("texts"));
                assertEquals(ExplorePanel.gateTexts(channel.gate2()), ((JsonObject) parts.get(3)).get("texts"));
            }

            // without a channel it says so
            var none = Json.parseObject(m.doRequest("/widget/bodygraph/watch" + moment(dayWith(List::isEmpty))).getText());
            assertEquals(List.of("No center defined", "No channel is defined now", 0), List.of(none.getString("note"), none.getString("none"), ((JsonArray) none.get("rows")).size()));

            // it changes when the body graph does
            assertEquals(((JsonArray) Json.parseObject(m.doRequest("/widget/bodygraph/systemSmall" + moment(FROM) + "/ahead").getText()).get("entries")).size(),
                ((JsonArray) Json.parseObject(m.doRequest("/widget/bodygraph/watch" + moment(FROM) + "/ahead").getText()).get("entries")).size());
        }
    }

    @Test
    void withoutAChannelNoCenterIsDefinedEither() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var none = dayWith(List::isEmpty);
            assertEquals(List.of(), said(drawing(m, "systemSmall", none)));
            assertEquals(List.of("CHANNELS NOW", "UNTIL", "No center defined", "No channel is defined now"), said(drawing(m, "systemMedium", none)));
            var large = drawing(m, "systemLarge", none);
            assertEquals(List.of("CHANNELS NOW", "No center defined", "No channel is defined now"), said(large));
            assertTrue(large.select("path.hd-center").stream().allMatch(center -> center.hasClass("center-off")));
        }
    }

    @Test
    void aLongNameIsInSmallerLettersWhereItsBesideWhenItEnds() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            // Transformation, Transitoriness, Perfected Form and the Wavelength have fourteen letters
            var long_name = dayWith(channels -> !channels.isEmpty() && channels.size() <= 4 && channels.stream().anyMatch(channel -> channel.name().length() == 14));
            var names = drawing(m, "systemMedium", long_name).select("a > g:not([transform*=scale]) > text.name");
            for (var name : names) {
                assertEquals(name.text().length() <= 11 ? "12" : name.text().length() == 12 ? "11" : name.text().length() == 13 ? "10" : "9", name.attr("font-size"), name.text());
            }
            assertTrue(names.eachAttr("font-size").contains("9"), names.eachText().toString());
            assertEquals(channels(long_name).stream().map(channel -> capitalized(channel.name())).toList(), names.eachText());
        }
    }

    @Test
    void whatCantBeAnsweredIsRefused() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            assertEquals(400, m.doRequest("/widget/bodygraph/systemLarge/202613011700").getStatus());
            assertEquals(404, m.doRequest("/widget/bodygraph/accessoryCircular" + moment(FROM)).getStatus());
            assertEquals(404, m.doRequest("/widget/bodygraph").getStatus());
        }
        try (var m = new MockConversation(new AppSite())) {
            assertEquals(404, m.doRequest("/widget/bodygraph/systemLarge" + moment(FROM)).getStatus());
        }
    }
}

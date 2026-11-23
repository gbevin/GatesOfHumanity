package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DescriptionsTest {
    // every part of a chart the Explore panel can show has a description, and no description is left over
    @Test
    void everyPartOfAChartIsDescribed() {
        var keys = new HashSet<String>();
        for (var type : Analysis.Type.values()) {
            keys.add("type." + type.name());
        }
        for (var authority : Analysis.Authority.values()) {
            keys.add("authority." + authority.name());
        }
        for (var definition : Analysis.Definition.values()) {
            keys.add("definition." + definition.name());
        }
        for (var angle : Analysis.Angle.values()) {
            keys.add("angle." + angle.name());
        }
        for (var line = 1; line <= 6; ++line) {
            keys.add("line." + line);
        }
        for (var center : Center.values()) {
            keys.add("center." + center.name() + ".defined");
            keys.add("center." + center.name() + ".open");
        }
        for (var kind : Variables.Kind.values()) {
            keys.add("variable." + kind.name());
            keys.add("variable." + kind.name() + ".left");
            keys.add("variable." + kind.name() + ".right");
        }
        for (var part : new String[]{"zodiac", "planets", "gates", "bodygraph", "rows"}) {
            keys.add("wheel." + part);
        }
        for (var variant : new String[]{"transit", "birth_transit", "connection", "connection_transit"}) {
            keys.add("wheel.bodygraph." + variant);
            keys.add("wheel.rows." + variant);
        }
        for (var channel : Analysis.channels()) {
            keys.add("channel." + Analysis.name(channel).key());
        }
        for (var kind : new String[]{"electromagnetic", "companionship", "dominance", "compromise"}) {
            keys.add("connection." + kind);
        }
        keys.addAll(List.of("about.channels", "about.transits", "about.transits.centers", "about.transits.birth", "about.transits.center", "about.upcoming", "about.birthtime", "about.transits.connection", "about.transits.connection.centers", "about.connection", "about.connection.centers"));
        keys.addAll(List.of("about.cycles", "about.cycles.buttons"));
        for (var cycle : Cycle.values()) {
            keys.add("cycle." + cycle.name());
        }
        keys.addAll(List.of("about.range", "range.dates", "range.planets", "range.cells"));
        keys.add("about.guide");
        for (var guide : new String[]{"birth", "transit", "birth_transit", "connection", "connection_transit", "range_month", "range_day"}) {
            keys.add("guide." + guide);
            keys.add("guide." + guide + ".use");
        }
        keys.add("intro");
        keys.add("explore.tap.chart");
        for (var gate = 1; gate <= 64; ++gate) {
            keys.add("gate." + gate);
        }
        for (var planet : Planet.values()) {
            if (planet != Planet.NONE) {
                keys.add("planet." + planet.name());
                keys.add("planet." + planet.name() + ".more");
            }
        }
        keys.add("about.gates");
        for (var line = 1; line <= 6; ++line) {
            keys.add("gate.line." + line);
        }
        keys.add("about.planets");
        keys.add("explore.tap.table");
        keys.add("intro.chart");
        keys.add("intro.bodygraph");
        keys.add("fixing.other");
        keys.addAll(List.of("about.overview", "about.person", "about.planets.personality", "about.planets.design"));
        keys.add("about.circuits");
        for (var circuit : Circuit.values()) {
            keys.add("circuit." + circuit.name());
        }
        keys.addAll(List.of("about.fixing", "fixing.exalted", "fixing.detriment", "about.channels.transit", "transit.planet"));
        for (var kind : Variables.Kind.values()) {
            for (var color = 1; color <= 6; ++color) {
                keys.add("variable." + kind.name() + ".color." + color);
                if (kind.hasSides()) {
                    keys.add("variable." + kind.name() + ".color." + color + ".left");
                    keys.add("variable." + kind.name() + ".color." + color + ".right");
                }
            }
        }
        for (var part : new String[]{"type", "authority", "profile", "definition", "cross", "centers", "variables"}) {
            keys.add("about." + part);
        }
        assertEquals(keys, Descriptions.all().keySet());
    }

    @Test
    void descriptionsAreSingleParagraphsOfPlainText() {
        for (var text : Descriptions.all().values()) {
            assertFalse(text.isBlank());
            assertFalse(text.contains("\u2014"), text);
            assertFalse(text.contains("<"), text);
            // commas, not semicolons, in short copy
            assertFalse(text.contains(";"), text);
            // charts are often of other people, so the descriptions don't speak to the person of the chart
            assertFalse(Pattern.compile("\\b(you|your|you're|yourself|one's|oneself)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find(), text);
        }
    }
}

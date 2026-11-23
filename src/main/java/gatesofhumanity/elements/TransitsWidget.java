package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.models.GateNames;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.TransitTimeline;
import gatesofhumanity.models.Upcoming;
import rife.engine.Context;
import rife.engine.Route;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.template.Template;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * The widget of the transits: the lines that the Sun, the Earth and the Moon are in, with the
 * hexagrams of their gates, and a new drawing each time one of them moves to another line.
 * What a tap on the transits of the watch opens has a row for every planet, each with what the
 * planet, its gate and its line are about, and changes each time any of them moves on.
 */
public class TransitsWidget extends Widget {
    public static final String FAMILY_PATTERN = "systemSmall|systemMedium|accessoryRectangular|accessoryInline|accessoryCircular|" + WATCH;
    static final List<Planet> PLANETS = List.of(Planet.SUN, Planet.EARTH, Planet.MOON);

    static List<Planet> planets(String family) {
        return WATCH.equals(family) ? Upcoming.planets() : PLANETS;
    }

    @Override
    Route route() {
        return site.transitsWidget;
    }

    @Override
    List<Instant> changes(Instant from) {
        return TransitTimeline.of(planets(family), from, AHEAD).stream().map(TransitTimeline.Moment::start).toList();
    }

    @Override
    Template drawing(Context c, Instant at) {
        return drawing(c, site, family, TransitTimeline.at(PLANETS, at), false);
    }

    @Override
    JsonObject told(Instant at) {
        var rows = new JsonArray();
        for (var place : TransitTimeline.at(planets(family), at).places()) {
            var planet = ExplorePanel.planetName(place.planet());
            var key = "planet." + place.planet().name();
            rows.add(row(planet, place.gate() + "." + place.line(), capitalized(GateNames.of(place.gate()).name()), at, place.until(), List.of(
                part(planet, descriptions(key, key + ".more", "transit.planet")),
                part(ExplorePanel.gateTitle(place.gate()), ExplorePanel.gateTexts(place.gate())),
                part("Line " + place.line(), descriptions("gate.line." + place.line())))));
        }
        return new JsonObject().set("heading", "Transits now").set("about", List.of(part("Transits", descriptions("about.transits", "about.planets")))).set("rows", rows);
    }

    /**
     * The drawing of a moment for a size of widget. Elsewhere than in the app nothing writes
     * its moments again, so they say that they're in UTC, and the drawing says where it's from.
     */
    static Template drawing(Context c, AppSite site, String family, TransitTimeline.Moment moment, boolean elsewhere) {
        var t = c.templateSvg("widget_transits_" + family);
        var url = c.urlFor(site.wheel).toString();
        t.setValueEncoded("url", url);
        if (elsewhere) {
            set(t, "from", URI.create(url).getHost());
            for (var said : List.of("zone", "credit")) {
                if (t.hasBlock(said)) {
                    t.setBlock(said, said);
                }
            }
        }
        for (var place : moment.places()) {
            var planet = place.planet().id_;
            var name = capitalized(GateNames.of(place.gate()).name());
            var where = place.gate() + "." + place.line();
            set(t, planet + "_place", where);
            // the hexagram of the gate, with the line that the planet is in lit
            for (var hexagram : List.of("hexagram", planet + "_hexagram")) {
                if (t.hasValueId(hexagram)) {
                    Hexagram.print(t, hexagram, place.gate(), place.line());
                }
            }
            if (!t.hasValueId(planet)) {
                continue;
            }
            // a row of the planet, in the block for the lines that its name takes there
            set(t, "planet", capitalized(planet));
            set(t, "place", where);
            set(t, "until", place.until());
            set(t, "until_written", WRITTEN.format(place.until()));
            var fitted = Fitted.of(name, number(t, "name_letters"), number(t, "name_size"), number(t, "name_smallest"), number(t, "name_lines"));
            set(t, "size", fitted.written());
            for (var line = 1; line <= fitted.lines().size(); ++line) {
                set(t, "name_" + line, fitted.lines().get(line - 1));
            }
            var lines = "_" + fitted.lines().size();
            if (t.hasBlock("named" + lines)) {
                t.setBlock("named", "named" + lines);
            }
            t.setBlock(planet, t.hasBlock("row" + lines) ? "row" + lines : "row");
        }
        return t;
    }
}

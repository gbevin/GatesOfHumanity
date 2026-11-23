package gatesofhumanity.elements;

import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Circuit;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.TransitTimeline;
import gatesofhumanity.models.Upcoming;
import rife.engine.Context;
import rife.engine.Route;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.template.Template;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The widget of the body graph of a moment: the gates that the planets are in, the channels
 * and the centers that those define together, drawn as the charts draw a body graph, with the
 * channels by name and until when each one lasts. A new drawing each time a planet moves to
 * another gate. What a tap on the body graph of the watch opens has a row for every channel,
 * each with what the channel and its two gates are about.
 */
public class BodygraphWidget extends Widget {
    public static final String FAMILY_PATTERN = "systemSmall|systemMedium|systemLarge|" + WATCH;

    @Override
    Route route() {
        return site.bodygraphWidget;
    }

    @Override
    List<Instant> changes(Instant from) {
        return TransitTimeline.ofGates(Upcoming.planets(), from, AHEAD).stream().map(TransitTimeline.Moment::start).toList();
    }

    /**
     * The gates that the body graph has without the planets of the moment: none, it's theirs.
     */
    Set<Integer> own() {
        return Set.of();
    }

    /**
     * What a tap on the widget opens.
     */
    String opens(Context c) {
        return c.urlFor(site.wheel).toString();
    }

    /**
     * How many centers the planets define, in words.
     */
    String centers(int centers) {
        // a channel defines the two centers that it joins, so there's never one alone
        return centers == 0 ? "No center" : centers + " centers";
    }

    /**
     * What the centers and the channels are said to be, and what's said without a channel.
     */
    String are() {
        return "defined";
    }

    String none() {
        return "No channel is " + are() + " now";
    }

    /**
     * What explains the channels and the centers as a whole, by the keys of the Explore panel.
     */
    List<String> about() {
        return descriptions("about.transits", "about.channels.transit", "about.transits.centers");
    }

    /**
     * Says in the words of the widget how many centers the planets define.
     */
    void word(Template t, int centers) {
        set(t, "centers", centers(centers));
        set(t, "centers_are", are());
        set(t, "none", none());
    }

    /**
     * What the planets of a moment add to the gates that are there without them: the channels
     * that they define or complete, and how many centers those define.
     */
    private record Added(TransitTimeline.Moment moment, Set<Integer> own, Set<Integer> transit, List<Analysis.NamedChannel> channels, Set<Center> centers) {
        static Added at(Instant at, Set<Integer> own) {
            var moment = TransitTimeline.atGates(Upcoming.planets(), at);
            var transit = moment.places().stream().map(TransitTimeline.Place::gate).collect(Collectors.toSet());
            var all = new HashSet<>(own);
            all.addAll(transit);
            var centers = new HashSet<>(Analysis.definedCenters(all));
            centers.removeAll(Analysis.definedCenters(own));
            var there = Analysis.definedChannels(own);
            return new Added(moment, own, transit, Analysis.definedChannels(all).stream().filter(channel -> !there.contains(channel)).toList(), centers);
        }
    }

    @Override
    JsonObject told(Instant at) {
        var added = Added.at(at, own());
        var rows = new JsonArray();
        for (var channel : added.channels()) {
            var circuit = Circuit.of(channel);
            rows.add(row(null, channel.key(), capitalized(channel.name()), at, until(added.moment(), channel, added.own()), List.of(
                part(channel.toString(), List.of(ExplorePanel.connects(channel), Descriptions.of("channel." + channel.key()))),
                part(circuit.label_, descriptions("circuit." + circuit.name())),
                part(ExplorePanel.gateTitle(channel.gate1()), ExplorePanel.gateTexts(channel.gate1())),
                part(ExplorePanel.gateTitle(channel.gate2()), ExplorePanel.gateTexts(channel.gate2())))));
        }
        var told = new JsonObject().set("heading", "Channels " + are() + " now").set("note", centers(added.centers().size()) + " " + are())
            .set("about", List.of(part("Channels", about()))).set("rows", rows);
        return rows.isEmpty() ? told.set("none", none()) : told;
    }

    @Override
    Template drawing(Context c, Instant at) {
        var added = Added.at(at, own());
        var moment = added.moment();
        var own = added.own();
        var t = c.templateSvg("widget_bodygraph_" + family);
        t.setValueEncoded("url", opens(c));

        WheelImage.lightBodygraph(t, new WheelImage.Sides(own.isEmpty() ? List.of(added.transit()) : List.of(own, added.transit())));
        if (!own.isEmpty()) {
            // the gates that are there without the planets are told apart from theirs
            t.setValue("graph", "with-own");
            for (var center : added.centers()) {
                t.setValue("hd-center-" + center.name().toLowerCase(Locale.ROOT), WheelImage.centerColor(center) + " center-added");
            }
        }
        word(t, added.centers().size());
        var channels = added.channels();
        if (channels.isEmpty() && t.hasBlock("no_channels")) {
            t.setBlock("channels", "no_channels");
        }
        // each channel in the place that the size has for it, as many as it has places
        var place = 0;
        while (place < channels.size() && t.hasBlock("channel_" + (place + 1))) {
            var channel = channels.get(place++);
            var until = until(moment, channel, own);
            var ahead = Ahead.of(at, until);
            var name = capitalized(channel.name());
            set(t, "channel", channel.key());
            set(t, "until", until);
            set(t, "until_as", ahead.skeleton());
            set(t, "until_written", ahead.text());
            if (t.hasValueId("name_letters")) {
                var fitted = Fitted.of(name, number(t, "name_letters"), number(t, "name_size"), number(t, "name_smallest"), 1);
                name = fitted.lines().getFirst();
                set(t, "size", fitted.written());
            }
            set(t, "name", name);
            for (var row : List.of("row", "row_small")) {
                if (t.hasBlock(row)) {
                    t.setBlock(row, row);
                }
            }
            t.appendBlock("channels", "channel_" + place);
        }
        if (place < channels.size()) {
            set(t, "count", channels.size() - place);
            t.appendBlock("channels", "more");
        }
        return t;
    }

    /**
     * A channel lasts until the first of its two gates has no planet in it anymore, of those
     * that it doesn't have without the planets.
     */
    static Instant until(TransitTimeline.Moment moment, Analysis.NamedChannel channel, Set<Integer> own) {
        return Stream.of(channel.gate1(), channel.gate2()).filter(gate -> !own.contains(gate))
            .map(gate -> moment.places().stream().filter(place -> place.gate() == gate).map(TransitTimeline.Place::until).max(Comparator.naturalOrder()).orElseThrow())
            .min(Comparator.naturalOrder()).orElseThrow();
    }
}

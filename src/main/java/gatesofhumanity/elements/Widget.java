package gatesofhumanity.elements;

import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.ephemeris.Ephemeris;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.Route;
import rife.engine.annotations.ActiveSite;
import rife.engine.annotations.Parameter;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.template.Template;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a widget of the app shows: a drawing in SVG for a moment, from the template of one size
 * of widget. Asked without a moment, it answers the moments from now on at which the drawing
 * changes, each with the address of its drawing, so that the app shows every drawing at its
 * moment without asking again. One size isn't a drawing: what a tap on a screen of the watch
 * opens is rows to read, each with what explains it in the words of the Explore panel, in JSON. It also says when to ask again, and until when these are all
 * the changes: what the drawing is after that isn't known from them.
 */
public abstract class Widget implements Element {
    public static final String AHEAD_PATTERN = "ahead";
    static final String WATCH = "watch";
    // far enough that a widget still has what's true when iOS asks again much later than it was told to
    static final Duration AHEAD = Duration.ofDays(3);
    static final Duration ASK_AGAIN = Duration.ofDays(1);
    // a moment as a browser shows it, where the app writes it as its device does
    static final DateTimeFormatter WRITTEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter WRITTEN_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter WRITTEN_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    /**
     * How a moment ahead is written: its day of the week and its time while that says which
     * day it is, then its date, and its month and year when it's most of a year away. The
     * skeleton is the one of Unicode that the app writes it with, the text what a browser shows.
     */
    record Ahead(String skeleton, String text) {
        static Ahead of(Instant from, Instant moment) {
            var away = Duration.between(from, moment);
            if (away.compareTo(Duration.ofDays(6)) < 0) {
                return new Ahead("{EEEjmm}", WRITTEN.format(moment));
            }
            if (away.compareTo(Duration.ofDays(300)) < 0) {
                return new Ahead("{MMMd}", WRITTEN_DAY.format(moment));
            }
            return new Ahead("{yMMM}", WRITTEN_MONTH.format(moment));
        }
    }

    @ActiveSite AppSiteEmbedded site;

    @Parameter String family;
    // in UTC
    @Parameter String datetime;
    @Parameter String ahead;

    /**
     * The route of the widget.
     */
    abstract Route route();

    /**
     * What the addresses of its drawings begin with, before the size, and end with.
     */
    String address() {
        return route().path();
    }

    String query() {
        return "";
    }

    /**
     * The moments at which the drawing changes, from one on and the first at that moment.
     */
    abstract List<Instant> changes(Instant from);

    abstract Template drawing(Context c, Instant at);

    /**
     * What a tap on a screen of the watch opens at a moment: a heading, what explains the screen
     * as a whole, and its rows.
     */
    abstract JsonObject told(Instant at);

    /**
     * Something that's explained, under a title, in the texts of the Explore panel.
     */
    static JsonObject part(String title, List<String> texts) {
        return new JsonObject().set("title", title).set("texts", texts);
    }

    static List<String> descriptions(String... keys) {
        return Arrays.stream(keys).map(Descriptions::of).toList();
    }

    /**
     * A row: what it's about, in figures and by name, until when it lasts as the watch writes a
     * moment, and what explains it.
     */
    static JsonObject row(String label, String value, String name, Instant from, Instant until, List<JsonObject> about) {
        var row = new JsonObject().set("value", value).set("name", name).set("until", until.toString())
            .set("untilAs", Ahead.of(from, until).skeleton().replaceAll("[{}]", "")).set("about", about);
        return label == null ? row : row.set("label", label);
    }

    @Override
    public void process(Context c) {
        Instant moment;
        if (datetime == null) {
            moment = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        } else {
            var local = HdImage.moment(datetime);
            moment = local == null ? null : local.toInstant(ZoneOffset.UTC);
        }
        if (moment == null || !Ephemeris.covers(moment)) {
            c.setStatus(400);
            return;
        }
        if (datetime == null || ahead != null) {
            printTimeline(c, moment);
        } else if (WATCH.equals(family)) {
            c.setContentType("application/json");
            c.print(Json.toString(told(moment)));
        } else {
            c.print(drawing(c, moment));
        }
    }

    private void printTimeline(Context c, Instant from) {
        var entries = new JsonArray();
        for (var change : changes(from)) {
            var drawing = address() + "/" + family + "/" + HdImage.MOMENT_FORMAT.format(change.atZone(ZoneOffset.UTC)) + query();
            entries.add(new JsonObject().set("date", change.toString()).set("drawing", drawing));
        }
        c.setContentType("application/json");
        c.print(Json.toString(new JsonObject().set("reload", from.plus(ASK_AGAIN).toString()).set("until", from.plus(AHEAD).toString()).set("entries", entries)));
    }

    // a size of widget shows what it has the room for
    static void set(Template t, String id, Object value) {
        if (t.hasValueId(id)) {
            t.setValueEncoded(id, value);
        }
    }

    // what a template says of the room it has
    static int number(Template t, String id) {
        return Integer.parseInt(t.getDefaultValue(id));
    }

    static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

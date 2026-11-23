package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.*;
import rife.engine.Context;
import rife.template.Template;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static gatesofhumanity.elements.ExplorePanel.*;

/**
 * The Explore panel below a chart: an introduction to each part of it.
 */
public class WheelExplore extends WheelImage {
    private Context context_;
    // the rows of planets below the wheel, and their planets by the gate they're in, which the
    // other items relate to
    private final List<Row> rows_ = new ArrayList<>();
    private final Map<Integer, List<String>> planetsByGate_ = new HashMap<>();
    // the lines of each row, fixed in exaltation or detriment as the wheel shows them
    private List<ActivatedGates> fixings_ = List.of();
    private static final Duration UPCOMING_WITHIN = Duration.ofDays(365);
    private static final Duration BIRTH_TIME_WITHIN = Duration.ofHours(3);
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_CLOCK_FORMAT = DateTimeFormatter.ofPattern("MMMM d, h:mm a", Locale.ENGLISH);
    // a moment within a sentence, the image writes it with an @
    private static final DateTimeFormatter SENTENCE_FORMAT = DateTimeFormatter.ofPattern("E, MMMM d, yyyy 'at' h:mm a z", Locale.ENGLISH);

    // a row of planets below the wheel, of the transits when its planets are there for everyone
    private record Row(List<Position> positions, int number, String name, List<String> introductions, boolean transit) {
    }

    // the image that's loaded with the panel explains errors
    @Override
    protected void printError(Context c, String message) {
    }

    private More more(LearnTopic topic, String section) {
        return More.of(context_, site, topic, section);
    }

    @Override
    public void process(Context c) {
        context_ = c;
        if (!prepare(c)) {
            return;
        }
        var chart = ChartType.of(type);
        var t = ExplorePanel.start(c, site, "chart",
            "guide." + (chart == ChartType.TRANSIT ? "transit" : chart.parameter_.replace('-', '_')), chart.page_.heading(), "intro.chart", "intro.bodygraph");
        var footer = new Footer();
        var charts = prepareCharts(t, chart, footer);
        switch (chart) {
            case BIRTH -> {
                var birth = charts.first();
                fixings_ = activations(charts);
                addRow(birth.personality().positions(), 1, "Personality", List.of("about.planets", "about.planets.personality"));
                addRow(birth.design().positions(), 2, "Design", List.of("about.planets.design"));
                chartSection(t, "");
                birthSections(t, birth, 1, 2, Set.of(), List.of());
                birthTimeSection(t);
            }
            case BIRTH_TRANSIT -> {
                // the wheel only draws the transits once the birth chart is known
                if (charts.first() == null) {
                    return;
                }
                var transit = charts.first();
                var birth = charts.second();
                fixings_ = activations(charts);
                addTransitRow(transit.personality().positions());
                addRow(birth.personality().positions(), 3, "Personality", List.of("about.planets.personality"));
                addRow(birth.design().positions(), 4, "Design", List.of("about.planets.design"));
                chartSection(t, ".birth_transit");
                var completed = Analysis.completedByTransits(transit, List.of(birth));
                birthSections(t, birth, 3, 4, Analysis.definedCentersWithTransits(transit, List.of(birth)), completed);
                completedSection(t, completed, "Completed channels", "about.transits.birth");
                upcomingSection(t, birth);
                cyclesSection(t);
            }
            case CONNECTION_TRANSIT -> {
                // the wheel only draws the transits once both birth charts are known
                if (charts.first() == null) {
                    return;
                }
                var transit = charts.first();
                var first = charts.second();
                var second = charts.third();
                fixings_ = activations(charts);
                var first_name = firstPerson();
                var second_name = secondPerson();
                addTransitRow(transit.personality().positions());
                addRow(first.personality().positions(), 3, first_name + ", Personality", List.of("about.planets.personality"));
                addRow(first.design().positions(), 4, first_name + ", Design", List.of("about.planets.design"));
                addRow(second.personality().positions(), 5, second_name + ", Personality", List.of("about.planets.personality"));
                addRow(second.design().positions(), 6, second_name + ", Design", List.of("about.planets.design"));
                chartSection(t, ".connection_transit");
                personSection(t, "person.1", first_name, first);
                personSection(t, "person.2", second_name, second);
                var completed = Analysis.completedByTransits(transit, List.of(first, second));
                connectionSection(t, Connection.of(first, second), Analysis.definedCentersWithTransits(transit, List.of(first, second)), completed, first_name, second_name);
                completedSection(t, completed, "Completed for two people", "about.transits.connection");
            }
            case TRANSIT -> {
                var transit = charts.first();
                fixings_ = activations(charts);
                addTransitRow(transit.personality().positions());
                var gates = Analysis.gates(transit);
                chartSection(t, ".transit");
                startSection(t);
                item(t, "centers", "Defined centers", centerNames(Analysis.definedCenters(gates)), more(LearnTopic.TRANSITS, "Defined centers"), "about.transits", "about.transits.centers");
                endSection(t, "Transits", "about.transits");
                channelsSection(t, Analysis.definedChannels(gates), 0, "about.channels.transit");
            }
            case CONNECTION -> {
                var first = charts.first();
                var second = charts.second();
                fixings_ = activations(charts);
                var first_name = firstPerson();
                addRow(first.personality().positions(), 1, first_name + ", Personality", List.of("about.planets", "about.planets.personality"));
                addRow(first.design().positions(), 2, first_name + ", Design", List.of("about.planets.design"));
                chartSection(t, ".connection");
                personSection(t, "person.1", first_name, first);
                if (second != null) {
                    var second_name = secondPerson();
                    addRow(second.personality().positions(), 3, second_name + ", Personality", List.of("about.planets.personality"));
                    addRow(second.design().positions(), 4, second_name + ", Design", List.of("about.planets.design"));
                    personSection(t, "person.2", second_name, second);
                    connectionSection(t, Connection.of(first, second), null, List.of(), first_name, second_name);
                } else {
                    var analysis = Analysis.of(first);
                    startSection(t);
                    item(t, "centers", "Defined centers", centerNames(analysis.definedCenters()), more(LearnTopic.CENTERS, null), "about.centers");
                    endSection(t, "Centers", "about.centers");
                    channelsSection(t, analysis.definedChannels(), 0, "about.channels");
                }
            }
        }
        // last, once every item that relates to the planets is in the panel
        for (var row : rows_) {
            planetsSection(t, row);
        }
        // what the drawing says below the wheel, for the phones that show the wheel alone
        describe(t, chart, charts, fixings_, footer);
        t.setBlock("summary", "summary");
        c.print(t);
    }

    // the descriptions of the body graph and the rows depend on what the chart puts in them
    private void chartSection(Template t, String variant) {
        startSection(t);
        item(t, "wheel.zodiac", "Zodiac", "", more(LearnTopic.CHART, "Zodiac"), "wheel.zodiac");
        item(t, "wheel.planets", "Planets", "", more(LearnTopic.PLANETS, null), "wheel.planets");
        item(t, "wheel.gates", "Gates", "", more(LearnTopic.GATES, null), "wheel.gates");
        item(t, "wheel.bodygraph", "Body graph", "", more(LearnTopic.CHART, "Body graph"), "wheel.bodygraph" + variant);
        item(t, "wheel.rows", "Rows of planets", "", more(LearnTopic.CHART, "Rows of planets"), "wheel.rows" + variant);
        endGuide(t);
    }

    // the transits can define open centers, which the body graph shows defined
    private void birthSections(Template t, RaveChart chart, int personalityRow, int designRow, Set<Center> withTransits, List<Analysis.NamedChannel> completed) {
        var analysis = Analysis.of(chart);
        var type = analysis.type();
        var profile = analysis.profile();
        var cross = analysis.cross();
        startSection(t);
        item(t, "type", "Type", type.label_, more(LearnTopic.TYPES, type.label_), "about.type", "type." + type.name());
        var authority_center = analysis.authority().center_;
        item(t, "authority", authority_center == null ? List.of() : List.of(centerPart(authority_center)), "Authority", analysis.authority().label_, more(LearnTopic.AUTHORITIES, analysis.authority().label_), "about.authority", "authority." + analysis.authority().name());
        item(t, "profile", List.of(activation(personalityRow, Planet.SUN), activation(designRow, Planet.SUN)), "Profile", profile.name(), more(LearnTopic.PROFILES, null), "about.profile", "line." + profile.personalityLine(), "line." + profile.designLine());
        item(t, "definition", "Definition", analysis.definition().label_, more(LearnTopic.DEFINITION, analysis.definition().label_), "about.definition", "definition." + analysis.definition().name());
        item(t, "cross", List.of(activation(personalityRow, Planet.SUN), activation(personalityRow, Planet.EARTH), activation(designRow, Planet.SUN), activation(designRow, Planet.EARTH)), "Incarnation Cross", cross.title(), more(LearnTopic.PROFILES, cross.angle().label_), "about.cross", "angle." + cross.angle().name());
        endSection(t, "Overview", "about.overview");

        startSection(t);
        for (var center : Center.values()) {
            var defined = analysis.definedCenters().contains(center);
            var key = "center." + center.name() + (defined ? ".defined" : ".open");
            if (!defined && withTransits.contains(center)) {
                item(t, centerPart(center), centerRelated(center), centerName(center), "Defined now", more(LearnTopic.TRANSITS, "Completed channels"), "about.transits.center", key);
            } else {
                item(t, centerPart(center), centerRelated(center), centerName(center), defined ? "Defined" : "Open", more(LearnTopic.CENTERS, centerName(center)), key);
            }
        }
        endSection(t, "Centers", "about.centers");

        channelsSection(t, analysis.definedChannels(), completed.size(), "about.channels");

        startSection(t);
        var variables = Variables.of(chart);
        for (var variable : List.of(variables.determination(), variables.environment(), variables.motivation(), variables.perspective())) {
            var kind = variable.kind();
            item(t, "variable." + kind.name(), List.of(activation(kind.design_ ? designRow : personalityRow, kind.planet_)), variable.kind().label_, arrow(variable), more(LearnTopic.VARIABLES, variable.kind().label_),
                variableIntroductions(variable).toArray(String[]::new));
        }
        endSection(t, "Variables", "about.variables");
    }

    private static List<String> variableIntroductions(Variables.Variable variable) {
        var key = "variable." + variable.kind().name();
        var side = variable.left() ? ".left" : ".right";
        var keys = new ArrayList<>(List.of(key, key + ".color." + variable.color()));
        if (variable.kind().hasSides()) {
            keys.add(key + ".color." + variable.color() + side);
        }
        keys.add(key + side);
        return keys;
    }

    private void channelsSection(Template t, List<Analysis.NamedChannel> channels, int completed, String introduction) {
        startSection(t);
        item(t, "channels", "Channels", channelsCount(channels.size(), completed), more(LearnTopic.CHANNELS, null), introduction);
        for (var channel : channels) {
            channelItem(t, channel, "");
        }
        endSection(t, "Channels", introduction);
    }

    // a person in a connection chart, summed up as in the row below the chart
    private void personSection(Template t, String part, String name, RaveChart chart) {
        var analysis = Analysis.of(chart);
        var profile = analysis.profile();
        startSection(t);
        t.setValue("paragraphs", "");
        labeled(t, "Type", Descriptions.of("type." + analysis.type().name()));
        labeled(t, "Authority", Descriptions.of("authority." + analysis.authority().name()));
        labeled(t, "Personality line", Descriptions.of("line." + profile.personalityLine()));
        labeled(t, "Design line", Descriptions.of("line." + profile.designLine()));
        ExplorePanel.more(t, more(LearnTopic.TYPES, analysis.type().label_));
        ExplorePanel.more(t, more(LearnTopic.AUTHORITIES, analysis.authority().label_));
        ExplorePanel.more(t, more(LearnTopic.PROFILES, null));
        addItem(t, part, List.of(), name, analysis.type().label_ + ", " + analysis.authority().label_ + " Authority, " + profile.lines() + " Profile");
        endSection(t, name, "about.person");
    }

    // the defined centers as the body graph shows them, with the transits when it has them
    private void connectionSection(Template t, Connection connection, Set<Center> withTransits, List<Analysis.NamedChannel> completed, String firstName, String secondName) {
        startSection(t);
        if (withTransits == null) {
            item(t, "centers", "Defined centers", connection.definedCenters().size() + " of 9", more(LearnTopic.CONNECTIONS, "Defined centers"), "about.connection.centers");
        } else {
            item(t, "centers", "Defined centers", withTransits.size() + " of 9", more(LearnTopic.TRANSITS, "Defined centers of two people"), "about.transits.connection.centers");
        }
        var channels = connection.electromagnetic().size() + connection.companionship().size() + connection.dominance().size() + connection.compromise().size();
        item(t, "channels", "Channels", channelsCount(channels, completed.size()), more(LearnTopic.CHANNELS, null), "about.channels");
        item(t, "connection.electromagnetic", "Electromagnetic", count(connection.electromagnetic().size(), "channel"), more(LearnTopic.CONNECTIONS, "Electromagnetic"), "connection.electromagnetic");
        item(t, "connection.companionship", "Companionship", count(connection.companionship().size(), "channel"), more(LearnTopic.CONNECTIONS, "Companionship"), "connection.companionship");
        item(t, "connection.dominance", "Dominance", count(connection.dominance().size(), "channel"), more(LearnTopic.CONNECTIONS, "Dominance"), "connection.dominance");
        item(t, "connection.compromise", "Compromise", count(connection.compromise().size(), "channel"), more(LearnTopic.CONNECTIONS, "Compromise"), "connection.compromise");
        for (var channel : connection.electromagnetic()) {
            channelItem(t, channel, "Electromagnetic");
        }
        for (var channel : connection.companionship()) {
            channelItem(t, channel, "Companionship");
        }
        // named after the person with the whole channel, as below the chart
        for (var owned : connection.dominance()) {
            channelItem(t, owned.channel(), "Dominance (" + owned.ownerName(firstName, secondName) + ")");
        }
        for (var owned : connection.compromise()) {
            channelItem(t, owned.channel(), "Compromise (" + owned.ownerName(firstName, secondName) + ")");
        }
        endSection(t, "Connection", "about.connection");
    }

    // the planets of one row below the wheel, with the gate and line each one is in
    private void planetsSection(Template t, Row row) {
        startSection(t);
        // in the order of the rows below the wheel
        for (var position : row.positions().stream().sorted(Comparator.comparing(Position::planet)).toList()) {
            var gate = position.gateNumber();
            var line = position.gateLine();
            t.setValue("paragraphs", "");
            if (row.transit() && position.planet() != Planet.CHIRON) {
                paragraph(t, Descriptions.of("transit.planet"));
                paragraph(t, stay(position));
            }
            paragraph(t, planetTheme(position.planet()));
            gateParagraphs(t, gate);
            paragraph(t, Descriptions.of("gate.line." + line));
            var fixed = fixedLine(row.number(), position);
            if (fixed != null && fixed.isExalted()) {
                paragraph(t, Descriptions.of("fixing.exalted"));
            }
            if (fixed != null && fixed.isInDetriment()) {
                paragraph(t, Descriptions.of("fixing.detriment"));
            }
            if (fixed != null && (fixed.isExaltedOnlyByOther() || fixed.isInDetrimentOnlyByOther())) {
                paragraph(t, Descriptions.of("fixing.other"));
            }
            ExplorePanel.more(t, more(LearnTopic.GATES, gateTitle(gate)));
            ExplorePanel.more(t, More.of(context_, site, LearnTopic.PLANETS, planetName(position.planet()), planetName(position.planet()) + " in Learn"));
            if (fixed != null && (fixed.isExalted() || fixed.isInDetriment())) {
                var triangles = fixed.isExaltedOnlyByOther() || fixed.isInDetrimentOnlyByOther() ? "Outlined triangles" : "Exaltation and detriment";
                ExplorePanel.more(t, More.of(context_, site, LearnTopic.CHART, triangles, triangles + " in Learn"));
            }
            addItem(t, activation(row.number(), position.planet()), gateRelated(gate), row.name().isBlank() ? null : row.name(), planetName(position.planet()),
                gate + "." + line + triangles(fixed) + " " + GateNames.of(gate).name());
        }
        endSectionIntroducedBy(t, row.name() + " planets", row.introductions());
    }

    // the theme of a planet, which its section of the Learn page of the planets describes further, all of it for
    // Chiron, which isn't one of the planets of a chart
    private static String planetTheme(Planet planet) {
        var theme = Descriptions.of("planet." + planet.name());
        return planet == Planet.CHIRON ? theme + " " + Descriptions.of("planet.CHIRON.more") : theme;
    }

    // the line a planet activates, which Chiron never does
    private ActivatedLine fixedLine(int row, Position position) {
        var lines = fixings_.get(row - 1).activatedGateLines(position.gateNumber());
        return position.planet() == Planet.CHIRON || lines == null ? null : lines.activatedLine(position.gateLine());
    }

    // as the rows below the wheel draw them, outlined when only another chart fixes the line
    private static String triangles(ActivatedLine line) {
        if (line == null) {
            return "";
        }
        return (line.isExalted() ? line.isExaltedOnlyByOther() ? "\u25B3" : "\u25B2" : "") +
               (line.isInDetriment() ? line.isInDetrimentOnlyByOther() ? "\u25BD" : "\u25BC" : "");
    }

    private void channelItem(Template t, Analysis.NamedChannel channel, String value) {
        t.setValue("paragraphs", "");
        paragraph(t, connects(channel));
        paragraph(t, Descriptions.of("channel." + channel.key()));
        var circuit = Circuit.of(channel);
        labeled(t, "Circuit", Descriptions.of("circuit." + circuit.name()));
        ExplorePanel.more(t, more(LearnTopic.CHANNELS, channel.toString()));
        ExplorePanel.more(t, More.of(context_, site, LearnTopic.CHANNELS, circuit.label_, circuit.label_ + " in Learn"));
        addItem(t, channelPart(channel), channelRelated(channel), channel.toString(), value);
    }

    private void completedSection(Template t, List<Analysis.NamedChannel> completed, String learnSection, String description) {
        startSection(t);
        item(t, "transits", "Completed now", count(completed.size(), "channel"), more(LearnTopic.TRANSITS, learnSection), "about.transits", description);
        for (var channel : completed) {
            channelItem(t, channel, "With the transits");
        }
        endSection(t, "Transits", "about.transits");
    }

    // the next time each planet of the transits completes a channel with the birth chart, on the
    // calendar of the transit city
    private void upcomingSection(Template t, RaveChart birth) {
        startSection(t);
        var zone = momentLocation_.getZone();
        // the tables end before a year has passed near their end
        var tables_end = Ephemeris.end().atZone(zone);
        var cut = momentUtc_.toInstant().plus(UPCOMING_WITHIN).isAfter(Ephemeris.end());
        for (var planet : Upcoming.planets()) {
            var name = planetName(planet);
            var found = Upcoming.next(planet, birth, momentUtc_.toInstant(), UPCOMING_WITHIN);
            t.setValue("paragraphs", "");
            if (found.isEmpty()) {
                paragraph(t, named(planet) + " doesn't move into a gate that completes a channel with the birth chart " +
                             (cut ? "before the tables of the planets end on " + DAY_FORMAT.format(tables_end) + "." : "within the next year."));
                addItem(t, "upcoming." + planet.name(), List.of(activation(1, planet)), name, cut ? "None before " + DAY_FORMAT.format(tables_end) : "None in the next year");
                continue;
            }
            var completion = found.get();
            var channels = completion.channels().stream().map(channel -> channel.name() + " (" + channel.key() + ")").toList();
            var listed = channels.size() == 1 ? "the channel of " + channels.getFirst() :
                "the channels of " + String.join(", ", channels.subList(0, channels.size() - 1)) + " and " + channels.getLast();
            paragraph(t, named(planet) + " moves into gate " + completion.gate() + ", " + GateNames.of(completion.gate()).name() + ", on " + SENTENCE_FORMAT.format(completion.start().atZone(zone)) +
                         ", completing " + listed + " with the birth chart, and " + (completion.end().equals(Ephemeris.end()) ?
                             "is still there when the tables of the planets end on " : "stays there until ") + SENTENCE_FORMAT.format(completion.end().atZone(zone)) + ".");
            t.setValue("moment", HdPage.FIELD_FORMAT.format(completion.start().atZone(zone)));
            t.appendBlock("paragraphs", "moment-paragraph");
            ExplorePanel.more(t, more(LearnTopic.TRANSITS, "Coming up"));
            var related = new ArrayList<String>();
            related.add(activation(1, planet));
            completion.channels().forEach(channel -> related.add(channelPart(channel)));
            addItem(t, "upcoming." + planet.name(), related, name,
                DAY_FORMAT.format(completion.start().atZone(zone)) + ", " + String.join(", ", completion.channels().stream().map(Object::toString).toList()));
        }
        endSection(t, "Coming up", "about.upcoming");
    }

    // how long a planet of the transits stays in its gate and its line, on the calendar of the transit city
    private String stay(Position position) {
        var planet = position.planet();
        var moment = momentUtc_.toInstant();
        var gate = Upcoming.gateStay(planet, moment);
        var line = Upcoming.lineStay(planet, moment);
        return named(planet) + " is in gate " + position.gateNumber() + " " + from(gate) + " " + until(gate) +
               ". It's in line " + position.gateLine() + " " + from(line) + " " + until(line) + ".";
    }

    private String from(Upcoming.Stay stay) {
        return stay.start().equals(Ephemeris.start()) ?
            "from before the tables of the planets begin on " + DAY_FORMAT.format(stay.start().atZone(momentLocation_.getZone())) :
            "from " + SENTENCE_FORMAT.format(stay.start().atZone(momentLocation_.getZone()));
    }

    private String until(Upcoming.Stay stay) {
        return stay.end().equals(Ephemeris.end()) ?
            "until after the tables of the planets end on " + DAY_FORMAT.format(stay.end().atZone(momentLocation_.getZone())) :
            "until " + SENTENCE_FORMAT.format(stay.end().atZone(momentLocation_.getZone()));
    }

    // how far the birth time can be off with each part of the chart staying as it is
    private void birthTimeSection(Template t) {
        startSection(t);
        var birth = momentUtc_.toInstant();
        var ranges = BirthTime.ranges(birth, BIRTH_TIME_WITHIN);
        var current = BirthTime.Moment.of(birth);
        t.setValueEncoded("axis-earlier", hours(BIRTH_TIME_WITHIN) + " earlier");
        t.setValueEncoded("axis-birth", clock(birth));
        t.setValueEncoded("axis-later", hours(BIRTH_TIME_WITHIN) + " later");
        t.appendBlock("items", "timeline-axis");
        for (var entry : ranges.entrySet()) {
            var part = entry.getKey();
            var range = entry.getValue();
            t.setValue("paragraphs", "");
            var stays = stays(part, current);
            var related = new ArrayList<>(birthTimeParts(part));
            if (range.earlier(birth).equals(BIRTH_TIME_WITHIN) && range.later(birth).equals(BIRTH_TIME_WITHIN) && range.before() == null && range.after() == null) {
                paragraph(t, stays + " for any birth within " + hours(BIRTH_TIME_WITHIN) + " before and after, from " + clock(range.first()) + " to " + clock(range.last()) + ".");
                ExplorePanel.more(t, more(LearnTopic.CHART, "Birth time"));
                addItem(t, "birthtime." + part.name(), related, null, birthTimeLabel(part), "At least " + hours(BIRTH_TIME_WITHIN) + " before and after", timeline(t, range, birth));
                continue;
            }
            paragraph(t, stays + " for a birth from " + clock(range.first()) + " to " + clock(range.last()) + ".");
            for (var earlier : List.of(true, false)) {
                var other = earlier ? range.before() : range.after();
                if (other == null) {
                    paragraph(t, unchanged(earlier, earlier ? range.earlier(birth) : range.later(birth)));
                    continue;
                }
                var changes = changes(part, current, other);
                paragraph(t, "With a birth at " + clock(other.time()) + ", " + String.join(" and ", changes.keySet()) + ".");
                changes.values().forEach(related::addAll);
            }
            ExplorePanel.more(t, more(LearnTopic.CHART, "Birth time"));
            addItem(t, "birthtime." + part.name(), related, null, birthTimeLabel(part),
                Widget.capitalized(margin(range.earlier(birth), range.before() == null) + " earlier, " + margin(range.later(birth), range.after() == null) + " later"),
                timeline(t, range, birth));
        }
        endSection(t, "Birth time", "about.birthtime");
    }

    // a side without a change, which the tables of the planets can cut short
    private static String unchanged(boolean earlier, Duration checked) {
        var born = earlier ? "With an earlier birth" : "With a later birth";
        if (checked.equals(BIRTH_TIME_WITHIN)) {
            return born + ", it stays the same for at least " + hours(BIRTH_TIME_WITHIN) + ".";
        }
        var tables = "the tables of the planets " + (earlier ? "begin" : "end");
        return checked.isZero() ? Widget.capitalized(tables) + " at this birth." :
            born + ", it stays the same until " + tables + ", " + hours(checked) + " " + (earlier ? "earlier" : "later") + ".";
    }

    // a bar of the range on the time looked at, with the birth in the middle
    private static String timeline(Template t, BirthTime.Range range, Instant birth) {
        double whole = BIRTH_TIME_WITHIN.multipliedBy(2).toMinutes();
        var start = BIRTH_TIME_WITHIN.toMinutes() - range.earlier(birth).toMinutes();
        var length = range.earlier(birth).toMinutes() + range.later(birth).toMinutes();
        t.setValue("timeline-left", String.format(Locale.ROOT, "%.2f", 100 * start / whole));
        t.setValue("timeline-width", String.format(Locale.ROOT, "%.2f", 100 * length / whole));
        return t.getBlock("timeline");
    }

    // the parts of the chart that a part of the birth time is about
    private static List<String> birthTimeParts(BirthTime.Part part) {
        return switch (part) {
            case TYPE -> List.of("type");
            case AUTHORITY -> List.of("authority");
            case PROFILE -> List.of("profile");
            case DEFINITION -> List.of("definition");
            case CROSS -> List.of("cross");
            case CHANNELS -> List.of("channels");
            case VARIABLES -> Arrays.stream(Variables.Kind.values()).map(kind -> "variable." + kind.name()).toList();
            case ACTIVATIONS -> List.of();
        };
    }

    private static String birthTimeLabel(BirthTime.Part part) {
        return switch (part) {
            case TYPE -> "Type";
            case AUTHORITY -> "Authority";
            case PROFILE -> "Profile";
            case DEFINITION -> "Definition";
            case CROSS -> "Incarnation Cross";
            case CHANNELS -> "Channels";
            case VARIABLES -> "Variables";
            case ACTIVATIONS -> "Gates and lines";
        };
    }

    private static String stays(BirthTime.Part part, BirthTime.Moment moment) {
        var a = moment.analysis();
        return switch (part) {
            case TYPE -> "The Type, " + a.type().label_ + ", stays the same";
            case AUTHORITY -> "The Authority, " + a.authority().label_ + ", stays the same";
            case PROFILE -> "The Profile, " + a.profile().lines() + ", stays the same";
            case DEFINITION -> "The Definition, " + a.definition().label_ + ", stays the same";
            case CROSS -> "The Incarnation Cross, the " + a.cross().title() + ", stays the same";
            case CHANNELS -> "The defined channels stay the same";
            case VARIABLES -> "The colors and arrows of the Variables stay the same";
            case ACTIVATIONS -> "Every planet stays in its gate and line";
        };
    }

    // what a part would be at another moment, with the parts of the chart it's about
    private static Map<String, List<String>> changes(BirthTime.Part part, BirthTime.Moment current, BirthTime.Moment other) {
        var a = other.analysis();
        var changes = new LinkedHashMap<String, List<String>>();
        switch (part) {
            case TYPE -> changes.put("the Type would be " + a.type().label_, List.of("type"));
            case AUTHORITY -> changes.put("the Authority would be " + a.authority().label_, List.of("authority"));
            case PROFILE -> changes.put("the Profile would be " + a.profile().lines(), List.of("profile"));
            case DEFINITION -> changes.put("the Definition would be " + a.definition().label_, List.of("definition"));
            case CROSS -> changes.put("the Incarnation Cross would be the " + a.cross().title(), List.of("cross"));
            case CHANNELS -> {
                var now = current.analysis().definedChannels();
                for (var channel : a.definedChannels()) {
                    if (!now.contains(channel)) {
                        changes.put("the channel of " + channel.name() + " (" + channel.key() + ") would be defined too", List.of());
                    }
                }
                for (var channel : now) {
                    if (!a.definedChannels().contains(channel)) {
                        changes.put("the channel of " + channel.name() + " (" + channel.key() + ") wouldn't be defined", List.of(channelPart(channel)));
                    }
                }
            }
            case VARIABLES -> {
                for (var i = 0; i < other.variables().size(); ++i) {
                    var variable = other.variables().get(i);
                    var was = current.variables().get(i);
                    if (variable.color() != was.color() || variable.left() != was.left()) {
                        changes.put("the " + variable.kind().label_ + " would be " + arrow(variable), List.of("variable." + variable.kind().name()));
                    }
                }
            }
            case ACTIVATIONS -> {
                var rows = List.of(current.chart().personality(), current.chart().design());
                var others = List.of(other.chart().personality(), other.chart().design());
                for (var row = 0; row < rows.size(); ++row) {
                    for (var position : others.get(row).positions()) {
                        var was = Analysis.find(rows.get(row).positions(), position.planet());
                        if (position.planet() != Planet.CHIRON && (was.gateNumber() != position.gateNumber() || was.gateLine() != position.gateLine())) {
                            changes.put("the " + (row == 0 ? "Personality " : "Design ") + planetName(position.planet()) + " would be in " +
                                        position.gateNumber() + "." + position.gateLine(), List.of(activation(row + 1, position.planet())));
                        }
                    }
                }
            }
        }
        return changes;
    }

    // a time of birth on the calendar of the birth city, with its day when it's another one
    private String clock(Instant time) {
        var local = time.atZone(momentLocation_.getZone());
        return (local.toLocalDate().equals(momentLocation_.toLocalDate()) ? CLOCK_FORMAT : DAY_CLOCK_FORMAT).format(local);
    }

    private static String margin(Duration duration, boolean atLeast) {
        if (duration.isZero()) {
            return "not a minute";
        }
        return (atLeast ? "at least " : "") + hours(duration);
    }

    private static String hours(Duration duration) {
        var hours = duration.toHours();
        var minutes = duration.toMinutesPart();
        if (hours == 0) {
            return minutes + " min";
        }
        return hours + (hours == 1 ? " hour" : " hours") + (minutes == 0 ? "" : " " + minutes + " min");
    }


    // the Sun, the Earth, the Moon and the Nodes, but Mercury, as a sentence starts with them
    private static final Set<Planet> NAMED_WITH_THE = EnumSet.of(Planet.SUN, Planet.EARTH, Planet.MOON, Planet.NORTH_NODE, Planet.SOUTH_NODE);

    private static String named(Planet planet) {
        return NAMED_WITH_THE.contains(planet) ? "The " + planetName(planet) : planetName(planet);
    }

    // the turning points of the birth, on the calendar of the transit city
    private void cyclesSection(Template t) {
        startSection(t);
        var birth_zone = secondMomentLocation_.getZone();
        for (var cycle : Cycle.values()) {
            var value = cycle.moment(secondMomentUtc_.toInstant())
                .map(moment -> DAY_FORMAT.format(CycleTime.firstMinute(moment, momentLocation_.getZone())) + ", age " +
                               Period.between(secondMomentLocation_.toLocalDate(), CycleTime.firstMinute(moment, birth_zone).toLocalDate()).getYears())
                .orElse("After " + Ephemeris.end().atZone(ZoneOffset.UTC).getYear());
            item(t, "cycle." + cycle.name(), cycle.label_, value, more(LearnTopic.TRANSITS, cycle.label_), "cycle." + cycle.name());
        }
        endSectionIntroducedBy(t, "Cycles", List.of("about.cycles", "about.cycles.buttons"));
    }

    // the row numbers are the ones the wheel gives the rows, named after the row so that the card
    // of a tap says which one it is
    private void addRow(List<Position> positions, int number, String name, List<String> introductions) {
        addRow(new Row(positions, number, name, introductions, false));
    }

    private void addTransitRow(List<Position> positions) {
        addRow(new Row(positions, 1, "Transit", List.of("about.planets", "transit.planet"), true));
    }

    private void addRow(Row row) {
        var positions = row.positions();
        var number = row.number();
        rows_.add(row);
        for (var position : positions) {
            planetsByGate_.computeIfAbsent(position.gateNumber(), _ -> new ArrayList<>()).add(activation(number, position.planet()));
        }
    }

    private static String activation(int row, Planet planet) {
        return "activation." + row + "." + planet.name();
    }

    private static String centerPart(Center center) {
        return "center." + center.name();
    }

    private static String channelPart(Analysis.NamedChannel channel) {
        return "channel." + channel.key();
    }

    private List<String> planetsIn(int gate) {
        return planetsByGate_.getOrDefault(gate, List.of());
    }

    // a planet's gate is in a center, at one end of channels, and can hold other planets
    private List<String> gateRelated(int gate) {
        var related = new ArrayList<String>();
        related.add(centerPart(Analysis.center(gate)));
        Analysis.channels(gate).forEach(channel -> related.add(channelPart(channel)));
        related.addAll(planetsIn(gate));
        return related;
    }

    private List<String> channelRelated(Analysis.NamedChannel channel) {
        var related = new ArrayList<String>();
        Analysis.centers(channel).forEach(center -> related.add(centerPart(center)));
        related.addAll(planetsIn(channel.gate1()));
        related.addAll(planetsIn(channel.gate2()));
        return related;
    }

    private List<String> centerRelated(Center center) {
        var related = new ArrayList<String>();
        Analysis.allChannels().stream().filter(channel -> Analysis.centers(channel).contains(center)).forEach(channel -> related.add(channelPart(channel)));
        for (var gate = 1; gate <= 64; ++gate) {
            if (Analysis.center(gate) == center) {
                related.addAll(planetsIn(gate));
            }
        }
        return related;
    }

    // every channel that the body graph shows, with the ones the transits complete
    private static String channelsCount(int defined, int completed) {
        if (defined + completed == 0) {
            return "None defined";
        }
        return count(defined + completed, "channel") + " defined" + (completed == 0 ? "" : ", " + completed + " of them completed now");
    }

    private static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String centerNames(Set<Center> centers) {
        if (centers.isEmpty()) {
            return "None";
        }
        return String.join(", ", centers.stream().map(ExplorePanel::centerName).toList());
    }

}

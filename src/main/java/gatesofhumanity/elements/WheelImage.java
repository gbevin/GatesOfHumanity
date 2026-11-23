package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.*;
import rife.engine.Context;
import rife.engine.annotations.Parameter;
import rife.template.Template;

import java.time.*;
import java.util.*;
import java.util.function.Function;

import static gatesofhumanity.models.Entities.*;

public class WheelImage extends HdImage {
    // the colors of the two people in a connection, as The Definitive Book of Human Design draws them
    static final String CONNECTION_FIRST_COLOR = "#2f6fd6";
    static final String CONNECTION_SECOND_COLOR = "#2e9e4f";
    static final int CONNECTION_ROWS_OFFSET = 30;
    static final String BIRTH_TRANSIT_BIRTH_COLOR = "#a4a4d4";
    private static final String DESIGN_COLOR = "#ff3b1b";
    // the height of the second chart's rows of planets
    private static final int SECOND_CHART_ROWS = 104;
    static final int TITLE_LENGTH = 28;
    private static final int FOOTER_LINE_HEIGHT = 28;
    // the rows below the connection chart, the summary of each person first
    private static final int SUMMARY_ROWS = 3;
    private static final int CHANNELS_PER_LINE = 3;
    // what fits beside the labels at the width of the chart, also with wide letters
    static final int CHANNEL_LINE_LENGTH = 76;
    private static final String SEPARATOR = " \u00b7 ";
    private static final int CHANNEL_LINE_HEIGHT = 28;
    private static final int CHANNELS_Y = 1448;
    // the gates at either end of the middle segment that the Integration channels share
    private static final Set<Integer> INTEGRATION_UPPER = Set.of(10, 20);
    private static final Set<Integer> INTEGRATION_LOWER = Set.of(34, 57);

    @Parameter String type;

    @Parameter String secondDatetime;
    @Parameter String name;
    @Parameter String secondName;
    // a chart from a shared link, which isn't where the visitor is
    @Parameter String shared;
    @Parameter Integer secondLocationId;
    @Parameter String thirdDatetime;
    @Parameter Integer thirdLocationId;

    Location secondLocation_;
    ZonedDateTime secondMomentLocation_;
    ZonedDateTime secondMomentUtc_;
    Location thirdLocation_;
    ZonedDateTime thirdMomentLocation_;
    ZonedDateTime thirdMomentUtc_;

    protected boolean processOtherChartParameters(Context c) {
        if (secondLocationId != null) {
            secondLocation_ = CityDirectory.find(secondLocationId);
        }
        if (thirdLocationId != null) {
            thirdLocation_ = CityDirectory.find(thirdLocationId);
        }
        if (secondDatetime != null) {
            secondMomentLocation_ = chartMoment(c, secondDatetime, secondLocation_);
            if (secondMomentLocation_ == null) {
                return false;
            }
            secondMomentUtc_ = secondMomentLocation_.withZoneSameInstant(ZoneOffset.UTC);
        }
        if (thirdDatetime != null) {
            thirdMomentLocation_ = chartMoment(c, thirdDatetime, thirdLocation_);
            if (thirdMomentLocation_ == null) {
                return false;
            }
            thirdMomentUtc_ = thirdMomentLocation_.withZoneSameInstant(ZoneOffset.UTC);
        }
        return true;
    }

    // the moment of a chart in its city, or null once an error answers
    private ZonedDateTime chartMoment(Context c, String datetime, Location location) {
        if (location == null) {
            printUnknownLocation(c);
            return null;
        }
        var moment = parseMoment(c, datetime);
        return moment == null ? null : moment.atZone(ZoneId.of(location.timezone()));
    }

    @Override
    protected boolean remembersLocation() {
        var chart = ChartType.of(type);
        return shared == null && chart != ChartType.BIRTH && chart != ChartType.CONNECTION;
    }

    /**
     * Prepares the moments and places of the charts, or answers with an error and returns false
     * when they can't be drawn.
     */
    protected boolean prepare(Context c) {
        name = xmlText(name);
        secondName = xmlText(secondName);
        var chart = ChartType.of(type);
        var connection = chart == ChartType.CONNECTION;
        if (chart != ChartType.BIRTH && chart != ChartType.TRANSIT && !processOtherChartParameters(c)) {
            return false;
        }
        if (!processParameters(c)) {
            return false;
        }
        if ((chart == ChartType.BIRTH || connection) && !Ephemeris.coversChart(momentUtc_.toInstant()) ||
            secondMomentUtc_ != null && !Ephemeris.coversChart(secondMomentUtc_.toInstant()) ||
            thirdMomentUtc_ != null && !Ephemeris.coversChart(thirdMomentUtc_.toInstant())) {
            printOutOfRange(c, Ephemeris.firstChart(), "Birth charts");
            return false;
        }
        if (!Ephemeris.covers(momentUtc_.toInstant())) {
            printOutOfRange(c, Ephemeris.start(), "Transits");
            return false;
        }
        return true;
    }

    @Override
    public void process(Context c) {
        if (!prepare(c)) {
            return;
        }
        var chart = ChartType.of(type);
        var t = prepareTemplate(c, c.templateSvg("wheel"));
        var footer = new Footer();
        var drawn = draw(t, chart, footer);
        t.setValueEncoded("svg-title", footer.subtitle_ == null ? footer.title_ : footer.title_ + ", " + footer.subtitle_);
        describe(t, chart, drawn.charts(), drawn.activations(), footer);
        c.print(t);
    }

    /**
     * The charts of a drawing, and what each of their rows activates.
     */
    record Drawn(ChartSet charts, List<ActivatedGates> activations) {
    }

    /**
     * Draws the chart into the template of the wheel: its signs, the planets on it, the gates
     * that they light with the segments of the circle inside them, and the body graph with what
     * lights it. What else shows the chart reads it from the template that this filled, see
     * {@link WheelVolume}, so that it can't show anything that the drawing doesn't.
     */
    Drawn draw(Template t, ChartType chart, Footer footer) {
        var charts = prepareCharts(t, chart, footer);
        drawSigns(t);
        drawPlanets(t, charts.first());
        var activations = activations(charts);
        drawGates(t, activations, explorePlanetsByGate(charts));
        var sides = Sides.of(charts.count(), activations);
        drawStripes(t, sides.sides().size());
        if (charts.third() != null) {
            markPeople(t, sides);
        }
        lightBodygraph(t, sides);
        return new Drawn(charts, activations);
    }

    /**
     * Fills in what a chart says below its wheel: the rows of planets, the title, the legend, the
     * moments and places and the properties. The drawing and the summary that phones show in its
     * place are two templates with the same values and blocks, and this fills both, after
     * {@link #prepareCharts}, so neither can say something that the other doesn't.
     */
    void describe(Template t, ChartType chart, ChartSet charts, List<ActivatedGates> activations, Footer footer) {
        showGatesOfRows(t, charts);
        for (var i = 0; i < activations.size(); ++i) {
            drawExaltationsDetriments(t, activations.get(i), String.valueOf(i + 1));
        }
        var connection = chart == ChartType.CONNECTION;
        var translate = 0;
        if (charts.third() != null) {
            // the transits have no Design row, the first person's rows have one
            translate = 2 * SECOND_CHART_ROWS + CONNECTION_ROWS_OFFSET;
            t.setBlock("planet-gates-3-4");
            t.setBlock("planet-gates-5-6");
            place(t, "gates-5-6-offset", SECOND_CHART_ROWS + CONNECTION_ROWS_OFFSET);
            place(t, "footer-translate", translate);
        } else if (charts.second() != null) {
            translate = connection ? SECOND_CHART_ROWS + CONNECTION_ROWS_OFFSET : SECOND_CHART_ROWS;
            t.setBlock("planet-gates-3-4");
            place(t, "footer-translate", translate);
            if (connection) {
                place(t, "gates-3-4-offset", CONNECTION_ROWS_OFFSET);
            }
        }
        layoutFooter(t, footer, translate);
    }

    // where the drawing puts a part, which a template that lays itself out has no value for
    private static void place(Template t, String id, int position) {
        if (t.hasValueId(id)) {
            t.setValue(id, position);
        }
    }

    /**
     * The chart drawn on the wheel and the ones drawn inside it, any of which can be missing.
     */
    record ChartSet(RaveChart first, RaveChart second, RaveChart third) {
        ChartSet(RaveChart first, RaveChart second) {
            this(first, second, null);
        }

        int count() {
            return third != null ? 3 : second != null ? 2 : 1;
        }
    }

    /**
     * The colors of the sides that light the body graph of a chart: the personality and the
     * design of one chart, or each of its charts.
     */
    static List<String> sideColors(ChartType chart) {
        return switch (chart) {
            case BIRTH, TRANSIT -> List.of("black", DESIGN_COLOR, CONNECTION_SECOND_COLOR);
            case CONNECTION -> List.of(CONNECTION_FIRST_COLOR, CONNECTION_SECOND_COLOR, CONNECTION_SECOND_COLOR);
            case BIRTH_TRANSIT -> List.of("black", BIRTH_TRANSIT_BIRTH_COLOR, CONNECTION_SECOND_COLOR);
            case CONNECTION_TRANSIT -> List.of("black", CONNECTION_FIRST_COLOR, CONNECTION_SECOND_COLOR);
        };
    }

    ChartSet prepareCharts(Template t, ChartType chart, Footer footer) {
        // the colors of the first, second and third chart, written out in the styles of the
        // drawing since design programs don't know the variables of CSS
        var colors = sideColors(chart);
        t.setValue("channel-on-color", colors.get(0));
        t.setValue("channel-design-color", colors.get(1));
        t.setValue("channel-third-color", colors.get(2));
        var here = new Detail(null, momentLocation_, momentUtc_, location_, null);
        return switch (chart) {
            case BIRTH -> {
                var birth = Charts.birth(momentUtc_);
                footer.title_ = hasName(name) ? title(name, null) : "Human Design Birth Chart";
                footer.subtitle_ = hasName(name) ? "Human Design Birth Chart" : null;
                footer.legend_ = new Legend("Personality", "Design", false);
                footer.details_.add(here.withDesign(birth.design().time()));
                footer.analysis_ = Analysis.of(birth);
                footer.variables_ = Variables.of(birth);
                yield new ChartSet(birth, null);
            }
            case CONNECTION -> {
                footer.title_ = "Human Design Connection Chart";
                var first_chart = Charts.birth(momentUtc_);
                var first = firstPerson();
                if (secondLocation_ == null || secondMomentUtc_ == null) {
                    footer.details_.add(here.withLabel(hasName(name) ? first : null).withDesign(first_chart.design().time()));
                    yield new ChartSet(first_chart, null);
                }
                var second_chart = Charts.birth(secondMomentUtc_);
                var second = secondPerson();
                t.setValueEncoded("gates-3-4-label", second);
                footer.details_.add(here.withLabel(first).withDesign(first_chart.design().time()));
                footer.details_.add(new Detail(second, secondMomentLocation_, secondMomentUtc_, secondLocation_, second_chart.design().time()));
                footer.legend_ = new Legend(first, second, false);
                footer.connection_ = Connection.of(first_chart, second_chart);
                footer.analyses_ = List.of(Analysis.of(first_chart), Analysis.of(second_chart));
                footer.names_ = List.of(first, second);
                yield new ChartSet(first_chart, second_chart);
            }
            case BIRTH_TRANSIT -> {
                footer.title_ = "Human Design Birth Transit Chart";
                if (secondLocation_ == null || secondMomentUtc_ == null) {
                    footer.details_.add(here);
                    yield new ChartSet(null, null);
                }
                var birth_chart = Charts.birth(secondMomentUtc_);
                var birth = title(name, "Birth");
                t.setValueEncoded("gates-3-4-label", birth);
                footer.legend_ = new Legend("Transits", birth, false);
                footer.details_.add(here.withLabel("Transits"));
                footer.details_.add(new Detail(birth, secondMomentLocation_, secondMomentUtc_, secondLocation_, birth_chart.design().time()));
                footer.analysis_ = Analysis.of(birth_chart);
                footer.variables_ = Variables.of(birth_chart);
                var transit = Charts.transit(momentUtc_);
                footer.completed_ = Analysis.completedByTransits(transit, List.of(birth_chart));
                yield new ChartSet(transit, birth_chart);
            }
            case CONNECTION_TRANSIT -> {
                footer.title_ = "Human Design Connection Transit Chart";
                if (secondMomentUtc_ == null || thirdMomentUtc_ == null) {
                    footer.details_.add(here);
                    yield new ChartSet(null, null);
                }
                var transit = Charts.transit(momentUtc_);
                var first_chart = Charts.birth(secondMomentUtc_);
                var second_chart = Charts.birth(thirdMomentUtc_);
                var first = firstPerson();
                var second = secondPerson();
                t.setValueEncoded("gates-3-4-label", first);
                t.setValueEncoded("gates-5-6-label", second);
                footer.legend_ = new Legend(first, second, true);
                footer.details_.add(here.withLabel("Transits"));
                footer.details_.add(new Detail(first, secondMomentLocation_, secondMomentUtc_, secondLocation_, first_chart.design().time()));
                footer.details_.add(new Detail(second, thirdMomentLocation_, thirdMomentUtc_, thirdLocation_, second_chart.design().time()));
                footer.connection_ = Connection.of(first_chart, second_chart);
                footer.analyses_ = List.of(Analysis.of(first_chart), Analysis.of(second_chart));
                footer.names_ = List.of(first, second);
                footer.completed_ = Analysis.completedByTransits(transit, List.of(first_chart, second_chart));
                footer.centersWithTransits_ = Analysis.definedCentersWithTransits(transit, List.of(first_chart, second_chart));
                yield new ChartSet(transit, first_chart, second_chart);
            }
            case TRANSIT -> {
                footer.title_ = "Human Design Transit Chart";
                footer.details_.add(here);
                yield new ChartSet(Charts.transit(momentUtc_), null);
            }
        };
    }

    private static void drawSigns(Template t) {
        var sign_degree = 13.25;
        for (var sign : SIGNS) {
            t.setValue("degree", sign_degree);
            t.appendBlock("signs", "sign-" + sign);
            for (var tick = 1; tick <= 6; ++tick) {
                t.setValue("degree" + tick, sign_degree + 5.0 * (tick - 4));
            }
            t.appendBlock("astro-lines", "astro-line");
            sign_degree += 360.0 / 12.0;
        }
    }

    private static void drawPlanets(Template t, RaveChart chart) {
        if (chart == null) {
            return;
        }
        for (var p : chart.personality().positions()) {
            t.setValue("planet-explore", "activation.1." + p.planet().name() + " wheel.planets");
            t.setValue("longitude", p.degree());
            t.appendBlock("planets", "planet");
            t.appendBlock("planets", "planet-" + p.planet().id_);
        }
    }

    /**
     * The gates of the personality and design of the first chart, then of the second, in the
     * order of the rows below the wheel. Fills in the gates of each row, with their lines fixed
     * across the rows.
     */
    static List<ActivatedGates> activations(ChartSet charts) {
        var activations = new ArrayList<ActivatedGates>();
        for (var row : rows(charts)) {
            activations.add(activatedGates(row));
        }
        // planets fix the lines of the rows above theirs, the design those of the personality and
        // a later chart those of an earlier one. Each chart has two rows, and what another chart
        // fixes only holds for now
        for (var later = activations.size() - 1; later > 0; --later) {
            if (!activations.get(later).activatedGates().isEmpty()) {
                for (var earlier = later - 1; earlier >= 0; --earlier) {
                    activations.get(earlier).resonateWithinComposite(activations.get(later), earlier / 2 != later / 2);
                }
            }
        }
        return activations;
    }

    static void showGatesOfRows(Template t, ChartSet charts) {
        var rows = rows(charts);
        for (var row = 0; row < rows.size(); ++row) {
            for (var p : rows.get(row)) {
                t.setValue(p.planet().id_ + "-gate" + (row + 1), p.gateNumber() + "." + p.gateLine());
            }
        }
    }

    /**
     * The positions of the rows below the wheel: the personality and the design of the first
     * chart, then of the second and of the third, numbered from 1 in the template and the Explore panel.
     */
    static List<List<Position>> rows(ChartSet charts) {
        var rows = new ArrayList<List<Position>>();
        var drawn = charts.third() == null ? Arrays.asList(charts.first(), charts.second()) : List.of(charts.first(), charts.second(), charts.third());
        for (var chart : drawn) {
            rows.add(chart == null ? List.of() : chart.personality().positions());
            rows.add(chart == null ? List.of() : chart.design().positions());
        }
        return rows;
    }

    /**
     * The Explore panel items of the planets in each gate, which a tap on the gate opens, from
     * the first row below the wheel to the last. Chiron doesn't activate gates.
     */
    static Map<Integer, String> explorePlanetsByGate(ChartSet charts) {
        var keys = new HashMap<Integer, String>();
        var rows = rows(charts);
        for (var row = 0; row < rows.size(); ++row) {
            for (var position : rows.get(row)) {
                if (position.planet() != Planet.CHIRON) {
                    keys.merge(position.gateNumber(), "activation." + (row + 1) + "." + position.planet().name(), (a, b) -> a + " " + b);
                }
            }
        }
        return keys;
    }

    private static void drawGates(Template t, List<ActivatedGates> activations, Map<Integer, String> explorePlanets) {
        var line_angle = 360.0 / (64.0 * 6.0);
        for (var i = 0; i < 64; ++i) {
            var degree = 360.0 * i / 64.0;
            t.setValue("degree-main", degree);
            t.setValue("degree-gate", degree + 360.0 / 128.0);

            var gate_number = GATES_ORDER.get(i);
            var gate = GATES.get(gate_number);
            var on_wheel = activations.getFirst().isGateActivated(gate_number);
            t.setValue("gate-class-iching", on_wheel ? "gate-on" : "gate-off");
            t.setValue("gate", gate_number);
            t.setValue("gate-explore", explorePlanets.containsKey(gate_number) ? explorePlanets.get(gate_number) + " wheel.gates" : "wheel.gates");
            if (on_wheel) {
                t.setValue("background-class", "gate-on-" + gate.color().id_);
                t.appendBlock("gate-backgrounds", "gate-background");
            }

            t.setValue("class", "iching" + (i % 2));
            for (var line = 1; line <= 6; ++line) {
                t.setValue("degree" + line, degree + line_angle / 2.0 + (6 - line) * line_angle);
                t.setBlock("iching" + line, "iching" + line + (gate.iching().yang(line) ? "-on" : "-off"));
            }

            t.appendBlock("hd-segments", "hd-segment");
            t.appendBlock("gate-hits", "gate-hit");
        }
    }

    /**
     * The gates of the sides that the channels are colored by: the personality and the design of
     * one chart, or each chart.
     */
    record Sides(List<Set<Integer>> sides) {
        // the colors of the sides, in the style names of the template
        private static final List<String> NAMES = List.of("on", "design", "third");

        static Sides of(int charts, List<ActivatedGates> activations) {
            var sides = new ArrayList<Set<Integer>>();
            if (charts == 1) {
                sides.add(new HashSet<>(activations.get(0).activatedGates()));
                sides.add(new HashSet<>(activations.get(1).activatedGates()));
            } else {
                for (var chart = 0; chart < charts; ++chart) {
                    var gates = new HashSet<>(activations.get(2 * chart).activatedGates());
                    gates.addAll(activations.get(2 * chart + 1).activatedGates());
                    sides.add(gates);
                }
            }
            return new Sides(sides);
        }

        Set<Integer> all() {
            var all = new HashSet<Integer>();
            sides.forEach(all::addAll);
            return all;
        }

        /**
         * The style of the sides that activate any of the gates, null when none does.
         */
        String style(Collection<Integer> gates) {
            var present = new ArrayList<Integer>();
            for (var side = 0; side < sides.size(); ++side) {
                if (!Collections.disjoint(sides.get(side), gates)) {
                    present.add(side);
                }
            }
            return present.isEmpty() ? null : combination(present);
        }

        // two sides of a single chart or of two charts are "both", as the legend says
        static String combination(List<Integer> sides) {
            if (sides.equals(List.of(0, 1))) {
                return "both";
            }
            if (sides.size() == 3) {
                return "all";
            }
            return String.join("-", sides.stream().map(NAMES::get).toList());
        }

        static String name(int side) {
            return NAMES.get(side);
        }
    }

    // the angles of the stripes, along each channel of the body graph
    private static final Map<String, Integer> STRIPE_ANGLES = new LinkedHashMap<>();

    static {
        STRIPE_ANGLES.put("vert", 0);
        STRIPE_ANGLES.put("16-48", 34);
        STRIPE_ANGLES.put("20-57", 30);
        STRIPE_ANGLES.put("10", 90);
        STRIPE_ANGLES.put("34", 120);
        STRIPE_ANGLES.put("35-36", -34);
        STRIPE_ANGLES.put("12-22", -30);
        STRIPE_ANGLES.put("45-21", -23);
        STRIPE_ANGLES.put("25-51", -36);
        STRIPE_ANGLES.put("40-37", -50);
        STRIPE_ANGLES.put("26-44", 78);
        STRIPE_ANGLES.put("50-27", 104);
        STRIPE_ANGLES.put("6-59", -104);
        STRIPE_ANGLES.put("bottom-left", 126);
        STRIPE_ANGLES.put("bottom-right", -126);
    }

    /**
     * The striped fills of the gates that several sides activate, along every channel.
     */
    private static void drawStripes(Template t, int sides) {
        var combinations = new ArrayList<List<Integer>>();
        combinations.add(List.of(0, 1));
        if (sides == 3) {
            combinations.add(List.of(0, 2));
            combinations.add(List.of(1, 2));
            combinations.add(List.of(0, 1, 2));
        }
        for (var combination : combinations) {
            t.setValue("combo", Sides.combination(combination));
            t.setValue("stripes-height", 3 * combination.size());
            t.setValue("stripes", "");
            for (var i = 0; i < combination.size(); ++i) {
                t.setValue("stripe-y", 3 * i);
                t.setValue("side", Sides.name(combination.get(i)));
                t.appendBlock("stripes", "stripe");
            }
            STRIPE_ANGLES.forEach((id, angle) -> {
                t.setValue("angle-id", id);
                t.setValue("angle", angle);
                t.appendBlock("stripe-patterns", "stripe-pattern");
                t.appendBlock("stripe-styles", "stripe-style");
                if (id.equals("20-57")) {
                    t.appendBlock("stripe-styles", "stripe-stroke-style");
                }
            });
        }
    }

    // a mark of each person inside the ring of gates, since the wheel only shows the transits
    private static void markPeople(Template t, Sides sides) {
        for (var i = 0; i < 64; ++i) {
            var gate = GATES_ORDER.get(i);
            t.setValue("degree-main", 360.0 * i / 64.0);
            for (var side = 1; side <= 2; ++side) {
                if (sides.sides().get(side).contains(gate)) {
                    t.appendBlock("person-marks", "person-mark-" + Sides.name(side));
                }
            }
        }
    }

    /**
     * Lights the body graph of bodygraph.svg with the gates of the sides: their halves of the
     * channels, the centers that they define together, and their numbers.
     */
    static void lightBodygraph(Template t, Sides sides) {
        lightChannels(t, sides);
        t.setValue("hd-channel-20-57", integrationMiddleStroke(sides));
        var gates = sides.all();
        lightCenters(t, gates);
        for (var gate = 1; gate <= 64; ++gate) {
            t.setValue("gate-class-" + gate, gates.contains(gate) ? "small-on" : "small-off");
        }
    }

    private static void lightChannels(Template t, Sides sides) {
        for (var gate = 1; gate <= 64; ++gate) {
            var style = sides.style(List.of(gate));
            t.setValue("hd-channel-" + gate, "hd-channel-" + (style == null ? "off" : style));
        }
    }

    /**
     * The segment between the junction of gates 10 and 20 and the junction of gates 34 and 57
     * belongs to every channel joining one of each, so to 10-34, 10-57, 20-34 and 20-57.
     */
    static String integrationMiddleStroke(Sides sides) {
        var all = sides.all();
        if (Collections.disjoint(all, INTEGRATION_UPPER) || Collections.disjoint(all, INTEGRATION_LOWER)) {
            return "hd-channel-off-stroke";
        }
        var ends = new HashSet<>(INTEGRATION_UPPER);
        ends.addAll(INTEGRATION_LOWER);
        return "hd-channel-" + sides.style(ends) + "-stroke";
    }

    private static void lightCenters(Template t, Set<Integer> gates) {
        var defined = Analysis.definedCenters(gates);
        for (var center : Center.values()) {
            t.setValue("hd-center-" + center.name().toLowerCase(Locale.ROOT), defined.contains(center) ? centerColor(center) : "center-off");
        }
    }

    static String centerColor(Center center) {
        return switch (center) {
            case HEAD, G -> "gate-on-yellow";
            case AJNA -> "gate-on-green";
            case HEART, SACRAL -> "gate-on-red";
            case THROAT, SPLEEN, SOLAR, ROOT -> "gate-on-brown";
        };
    }

    private record Detail(String label, ZonedDateTime local, ZonedDateTime utc, Location place, ZonedDateTime design) {
        Detail withLabel(String label) {
            return new Detail(label, local, utc, place, design);
        }

        Detail withDesign(ZonedDateTime design) {
            return new Detail(label, local, utc, place, design);
        }
    }

    // what the colors of the two charts stand for: a swatch in the color of the gates of each and a
    // striped one for the gates of both, with the transits of a connection on a line above
    private record Legend(String first, String second, boolean transits) {
    }

    // what the footer shows, the analysis of one chart or the connection of two
    static final class Footer {
        String title_;
        String subtitle_;
        Legend legend_;
        final List<Detail> details_ = new ArrayList<>();
        Analysis analysis_;
        Variables variables_;
        Connection connection_;
        List<Analysis> analyses_;
        List<String> names_;
        // the channels that the transits complete with a connection
        List<Analysis.NamedChannel> completed_;
        Set<Center> centersWithTransits_;
    }

    private void footerLine(Template t, String lines, int x, int y, String style, String text) {
        place(t, "line-x", x);
        place(t, "line-y", y);
        t.setValue("line-class", style);
        t.setValueEncoded("line-text", text);
        t.appendBlock(lines, "footer-line");
    }

    /**
     * The title, the moments and places of the charts side by side, their properties and the
     * site, below the planet rows.
     */
    private void layoutFooter(Template t, Footer footer, int translate) {
        var y = 1110;
        footerLine(t, "footer-lines", 480, y, "title-footer", footer.title_);
        if (footer.subtitle_ != null) {
            footerLine(t, "footer-lines", 480, y += FOOTER_LINE_HEIGHT, "date", footer.subtitle_);
        }
        if (footer.legend_ != null) {
            if (footer.legend_.transits()) {
                y += FOOTER_LINE_HEIGHT;
                place(t, "legend-y", y);
                place(t, "legend-swatch-y", y - 10);
                t.appendBlock("footer-lines", "footer-legend-transits");
            }
            var first_side = footer.legend_.transits() ? 1 : 0;
            y += FOOTER_LINE_HEIGHT;
            place(t, "legend-y", y);
            place(t, "legend-swatch-y", y - 10);
            t.setValueEncoded("legend-first", footer.legend_.first());
            t.setValueEncoded("legend-second", footer.legend_.second());
            t.setValue("legend-first-side", Sides.name(first_side));
            t.setValue("legend-second-side", Sides.name(first_side + 1));
            t.setValue("legend-both", Sides.combination(List.of(first_side, first_side + 1)));
            t.appendBlock("footer-lines", "footer-legend");
        }
        var top = y + 40;
        var last = top;
        var details = footer.details_;
        // three don't fit side by side, the first one goes above the other two
        if (details.size() == 3) {
            last = detailLines(t, details.getFirst(), 480, top);
            top = last + 40;
            details = details.subList(1, 3);
        }
        var columns = details.size();
        for (var i = 0; i < columns; ++i) {
            last = Math.max(last, detailLines(t, details.get(i), columns == 1 ? 480 : 250 + 460 * i, top));
        }

        // the properties are laid out below a last detail line at 1200
        var offset = last - 1200;
        var bottom = last;
        if (footer.analysis_ != null) {
            bottom = showAnalysis(t, footer.analysis_, footer.variables_, footer.completed_, offset) + offset;
        } else if (footer.connection_ != null) {
            bottom = showConnection(t, footer.connection_, footer.completed_, footer.centersWithTransits_, footer.analyses_.get(0), footer.analyses_.get(1),
                footer.names_.get(0), footer.names_.get(1), offset) + offset;
        }
        place(t, "site-y", bottom + 46);
        place(t, "height", translate + bottom + 46 + 14);
    }

    /**
     * @return the y of the last line
     */
    private int detailLines(Template t, Detail detail, int x, int top) {
        var line = top;
        t.setValue("detail-lines", "");
        if (detail.label() != null) {
            footerLine(t, "detail-lines", x, line, "name-footer", detail.label());
            line += FOOTER_LINE_HEIGHT;
        }
        footerLine(t, "detail-lines", x, line, "date", localMoment(detail.local()));
        footerLine(t, "detail-lines", x, line += FOOTER_LINE_HEIGHT, "date", utcMoment(detail.utc()));
        footerLine(t, "detail-lines", x, line += FOOTER_LINE_HEIGHT, "coordinates", coordinates(detail.place()));
        if (detail.design() != null) {
            footerLine(t, "detail-lines", x, line += FOOTER_LINE_HEIGHT, "date design", "Design: " + utcMoment(detail.design()));
        }
        t.appendBlock("footer-lines", "footer-detail");
        return line;
    }

    // the image is XML, which doesn't allow most control characters, nor U+FFFE and U+FFFF
    static String xmlText(String text) {
        return text == null ? null : text.replaceAll("[\\t\\n\\x{0B}\\f\\r\\x{1C}-\\x{1F}\\x{85}]", " ")
            .replaceAll("[^\\x{20}-\\x{7E}\\x{A0}-\\x{D7FF}\\x{E000}-\\x{FFFD}\\x{10000}-\\x{10FFFF}]", "");
    }

    static boolean hasName(String name) {
        return name != null && !name.isBlank();
    }

    private static String title(String name, String fallback) {
        if (!hasName(name)) {
            return fallback;
        }
        var title = name.strip();
        if (title.codePointCount(0, title.length()) <= TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_LENGTH - 1)).strip() + "\u2026";
    }

    // the two people of a connection, as the chart and its panel call them
    String firstPerson() {
        return title(name, "First person");
    }

    String secondPerson() {
        return title(secondName, "Second person");
    }

    private static String summary(Analysis analysis) {
        return analysis.type().label_ + SEPARATOR + analysis.authority().label_ + SEPARATOR + analysis.profile().lines();
    }

    // up to three channels on a line, fewer when the names of their owners make them long
    private static List<List<String>> lines(List<String> items) {
        var lines = new ArrayList<List<String>>();
        var line = new ArrayList<String>();
        var length = 0;
        for (var item : items) {
            if (!line.isEmpty() && (line.size() == CHANNELS_PER_LINE || length + SEPARATOR.length() + item.length() > CHANNEL_LINE_LENGTH)) {
                lines.add(List.copyOf(line));
                line.clear();
                length = 0;
            }
            length += (line.isEmpty() ? 0 : SEPARATOR.length()) + item.length();
            line.add(item);
        }
        if (!line.isEmpty()) {
            lines.add(List.copyOf(line));
        }
        if (lines.isEmpty()) {
            lines.add(List.of("None"));
        }
        return lines;
    }

    // the items of a line one by one, so that a template can keep each of them whole
    private static void lineItems(Template t, List<String> line) {
        t.setValue("line-items", "");
        for (var i = 0; i < line.size(); ++i) {
            t.setValueEncoded("line-item-text", line.get(i));
            t.setValueEncoded("line-item-separator", i < line.size() - 1 ? SEPARATOR : "");
            t.appendBlock("line-items", "line-item");
        }
    }

    /**
     * @return the y of the last row
     */
    private static int showConnection(Template t, Connection connection, List<Analysis.NamedChannel> completed, Set<Center> centersWithTransits,
                                      Analysis first, Analysis second, String firstName, String secondName, int offset) {
        // the part is the key of the Explore panel item that a tap on the row opens
        record Row(String part, String label, List<String> items) {
        }
        var rows = new ArrayList<Row>();
        rows.add(new Row("person.1", firstName, List.of(summary(first))));
        rows.add(new Row("person.2", secondName, List.of(summary(second))));
        rows.add(new Row("centers", "Defined centers", List.of((centersWithTransits == null ? connection.definedCenters() : centersWithTransits).size() + " of 9")));
        Function<Connection.Owned, String> owned = o -> o.channel() + " (" + o.ownerName(firstName, secondName) + ")";
        var groups = new LinkedHashMap<String, List<String>>();
        groups.put("Electromagnetic", connection.electromagnetic().stream().map(Object::toString).toList());
        groups.put("Companionship", connection.companionship().stream().map(Object::toString).toList());
        groups.put("Dominance", connection.dominance().stream().map(owned).toList());
        groups.put("Compromise", connection.compromise().stream().map(owned).toList());
        groups.forEach((label, items) -> {
            var group_lines = lines(items);
            for (var i = 0; i < group_lines.size(); ++i) {
                rows.add(new Row("connection." + label.toLowerCase(Locale.ROOT), i == 0 ? label : "", group_lines.get(i)));
            }
        });
        if (completed != null) {
            var completed_lines = lines(completed.stream().map(Object::toString).toList());
            for (var i = 0; i < completed_lines.size(); ++i) {
                rows.add(new Row("transits", i == 0 ? "Completed now" : "", completed_lines.get(i)));
            }
        }

        var y = 1244;
        for (var i = 0; i < rows.size(); ++i) {
            // a little space before the channels
            if (i == SUMMARY_ROWS) {
                y += 8;
            }
            place(t, "row-y", y);
            place(t, "row-hit-y", y - 20);
            t.setValue("row-part", rows.get(i).part());
            t.setValueEncoded("row-label", rows.get(i).label());
            lineItems(t, rows.get(i).items());
            t.appendBlock("connection-rows", "connection-row");
            if (i < rows.size() - 1) {
                y += CHANNEL_LINE_HEIGHT;
            }
        }
        place(t, "connection-offset", offset);
        t.setBlock("connection", "connection-lines");
        return y;
    }

    /**
     * @return the y of the last row
     */
    private static int showAnalysis(Template t, Analysis analysis, Variables variables, List<Analysis.NamedChannel> completed, int offset) {
        place(t, "analysis-offset", offset);
        t.setValueEncoded("analysis-type", analysis.type().label_);
        t.setValueEncoded("analysis-strategy", analysis.type().strategy_);
        t.setValueEncoded("analysis-authority", analysis.authority().label_);
        var profile = analysis.profile();
        t.setValue("analysis-profile-personality", profile.personalityLine());
        t.setValue("analysis-profile-design", profile.designLine());
        t.setValueEncoded("analysis-profile-names", profile.lineNames());
        t.setValueEncoded("analysis-definition", analysis.definition().label_);
        t.setValueEncoded("analysis-not-self", analysis.type().notSelf_);
        t.setValueEncoded("analysis-signature", analysis.type().signature_);
        t.setValueEncoded("analysis-aura", analysis.type().aura_);
        var cross = analysis.cross();
        t.setValueEncoded("analysis-cross-name", cross.title());
        t.setValue("analysis-cross-personality", cross.personalitySun() + "/" + cross.personalityEarth());
        t.setValue("analysis-cross-design", cross.designSun() + "/" + cross.designEarth());
        t.setValueEncoded("analysis-determination", arrow(variables.determination()));
        t.setValueEncoded("analysis-environment", arrow(variables.environment()));
        t.setValueEncoded("analysis-motivation", arrow(variables.motivation()));
        t.setValueEncoded("analysis-perspective", arrow(variables.perspective()));

        var lines = lines(analysis.definedChannels().stream().map(Object::toString).toList());
        for (var i = 0; i < lines.size(); ++i) {
            place(t, "channel-line-y", CHANNELS_Y + CHANNEL_LINE_HEIGHT * i);
            place(t, "channel-line-hit-y", CHANNELS_Y + CHANNEL_LINE_HEIGHT * i - 20);
            lineItems(t, lines.get(i));
            t.appendBlock("analysis-channels", "analysis-channel-line");
        }
        var last = CHANNELS_Y + CHANNEL_LINE_HEIGHT * (lines.size() - 1);
        // the channels that the transits complete with a birth chart, which the body graph shows too
        if (completed != null) {
            var completed_lines = lines(completed.stream().map(Object::toString).toList());
            place(t, "completed-y", last + CHANNEL_LINE_HEIGHT);
            t.setValue("analysis-completed-rows", "");
            for (var completed_line : completed_lines) {
                last += CHANNEL_LINE_HEIGHT;
                place(t, "channel-line-y", last);
                place(t, "channel-line-hit-y", last - 20);
                lineItems(t, completed_line);
                t.appendBlock("analysis-completed-rows", "analysis-channel-line");
            }
            t.setBlock("analysis-completed", "analysis-completed-lines");
        }
        t.setBlock("analysis", "analysis-lines");
        return last;
    }

    // the arrow on the side that the variable faces, as on a bodygraph
    static String arrow(Variables.Variable variable) {
        return variable.left() ? "\u2190 " + variable.colorName() : variable.colorName() + " \u2192";
    }

    private static void drawExaltationsDetriments(Template t, ActivatedGates activeGates, String symbolValueSuffix) {
        for (var activated_lines : activeGates.allActivatedGateLines()) {
            for (var activated_line : activated_lines.allActivatedLines()) {
                for (var planet : activated_line.planets()) {
                    // outlined when only another chart fixes the line
                    if (activated_line.isExalted()) {
                        t.setValue(planet.id_ + "-e" + symbolValueSuffix + "-now", activated_line.isExaltedOnlyByOther() ? "-now" : "");
                        t.setBlock(planet.id_ + "-e" + symbolValueSuffix);
                    }
                    if (activated_line.isInDetriment()) {
                        t.setValue(planet.id_ + "-d" + symbolValueSuffix + "-now", activated_line.isInDetrimentOnlyByOther() ? "-now" : "");
                        t.setBlock(planet.id_ + "-d" + symbolValueSuffix);
                    }
                }
            }
        }
    }

    /**
     * The gates of one row of planets, whose activations the row shows.
     */
    private static ActivatedGates activatedGates(List<Position> positions) {
        var activated = new ActivatedGates();
        for (var p : positions) {
            // Chiron is shown but doesn't activate gates
            if (p.planet() != Planet.CHIRON) {
                activated.activateGateLine(p);
            }
        }
        activated.fixResonatingPlanets();
        return activated;
    }
}

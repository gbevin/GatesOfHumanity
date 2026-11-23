package gatesofhumanity.elements;

import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.Position;
import rife.engine.Context;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.template.Template;
import rife.template.TemplateFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static gatesofhumanity.models.Entities.GATES_ORDER;

/**
 * A chart as shapes in a room, for an app that has room around its window: the body graph
 * standing in the middle of the wheel, which lies tipped around it as the drawing has it, with
 * the signs outside, the gates inside and the planets over the band between them. A gate of
 * the wheel that's lit has a segment of the circle inside it, towards the body graph, as the
 * drawing has, and a planet that's chosen a line from there to its gate in the body graph.
 * <p>
 * It's the chart that the wheel image draws at the same address, and it's made from that
 * drawing: the wheel image fills its template, and this reads from it which gates, centers
 * and channels are lit, where the planets are and in which color everything is, see
 * {@link Drawing}, and takes the shapes of the body graph from the drawing's own. What it adds
 * is what a drawing doesn't have: how thick each shape is and where it stands in the room,
 * for an app to build as they come.
 * <p>
 * The room is around its middle, with x to the right, y up and z towards whoever looks at it,
 * and the wheel is two across. A shape is one of six kinds:
 * <ul>
 * <li>a slab: an outline of x and y that's as thick as its depth, turned and then put where
 * it's at
 * <li>a rod, from one place to another
 * <li>a ball
 * <li>a text, which is turned too, or faces whoever looks at it when it says so
 * <li>a halo: an outline of x and y that faces whoever looks at it from behind where it's at,
 * this far behind it, in its color, with an edge so soft that it fades into the room; nothing
 * of it is further from its middle than its radius, and it may reach out of the room
 * <li>a light, which shines on everything from where it's from, this strong beside the others
 * </ul>
 * What's turned is turned around x, then y, then z, in degrees. A shape can be matte and can
 * glow. Nothing of the chart is seen through, only what's behind it is. A shape that's quiet
 * isn't there until what's chosen names the part that it names first, or is one of the shapes
 * that it's there with. Each has an id that stays the same from one moment to the next, so
 * that an app can move what it built, and the part of the Explore panel that explains it.
 */
public class WheelVolume extends WheelImage {
    // What's behind the chart from wherever it's looked at, in the color that the page has behind
    // its drawing and in the shape that the drawing has around the body graph: something that
    // keeps turning to be behind all of the chart. It lets a little of the room through, and its
    // edge fades into the room. Being behind all of it, it's further away than the body graph,
    // so it's larger than on the drawing, to be around the body graph for whoever looks.
    static final double HALO_BEHIND = 1.0;
    static final double HALO_LARGER = 1.6;
    static final double HALO_SOFT = 0.2;
    static final double HALO_OPACITY = 0.98;
    private static final String PAPER = "#c6c6de";
    // nothing of its picture is further from its middle than this
    static final double HALO_REACH = Body.SURROUND.stream().mapToDouble(point -> Math.hypot(point[0], point[1])).max().orElse(1.0) * HALO_LARGER + 2.0 * HALO_SOFT;
    // The room is what the chart itself takes, so that what an app moves and sizes it by is at
    // the chart. What's behind the chart reaches out of it.
    static final double[] ROOM = {2.0, 1.8, 2.0};
    // how far the corners of a center and of a gate of the wheel are rounded off
    private static final double CENTER_ROUNDED = 0.028;
    private static final double GATE_ROUNDED = 0.01;
    // The light on the chart, each from where it comes: one from above at the front and to
    // the left, a weaker one from the right that softens its shadows, and one from behind and
    // above that sets the edges off.
    private static final double[][] LIGHTS = {{-0.6, 0.9, 1.0, 1.0}, {1.0, 0.2, 0.7, 0.45}, {0.2, 0.8, -1.0, 0.6}};
    private static final String WHITE = "#ffffff";
    // The edge of a center, which is what it's seen by on something pale: a dark slab behind
    // its own that's this much larger all round, and this much less deep at either face.
    static final double EDGE = 0.01;
    static final double EDGE_BACK = 0.004;
    static final String EDGE_COLOR = "#202052";
    // the colors of the site: its dark blue, its gold, and the two of the ring of signs
    private static final String DARK = "#1b1b4a";
    private static final String GOLD = "#e3a83c";
    private static final String INK = "#202052";
    private static final String TILE = "#ececf6";
    private static final List<String> SIGN_COLORS = List.of("#9494c9", "#8484ba");
    private static final String SIGNS = "\u2648\u2649\u264a\u264b\u264c\u264d\u264e\u264f\u2650\u2651\u2652\u2653";
    // drawn as a letter rather than as a picture
    private static final String AS_TEXT = "\ufe0e";
    // How far the wheel is tipped towards whoever looks: lying flat it would be a line from its side.
    static final double TIPPED = 28.0;
    // from its middle outward, the wheel has the gates, the band that the planets are over, and the signs
    private static final double GATES_INNER = 0.7;
    // where the band that the gates are on ends, inside them
    static final double WHEEL_INNER = GATES_INNER - 0.015;
    // how far the segment of a gate that something is in goes inward, and how thick it is
    static final double RAY_INNER = 0.5;
    static final double RAY_DEPTH = 0.006;
    private static final double GATES_OUTER = 0.84;
    private static final double PLANETS = 0.885;
    private static final double SIGNS_INNER = 0.93;
    private static final double SIGNS_OUTER = 0.995;
    private static final double BAND_DEPTH = 0.02;
    // how far the planets are over the wheel, each a ball with its name beyond it
    static final double PLANET_HEIGHT = 0.11;
    static final double PLANET_RADIUS = 0.03;
    static final double PLANET_NAME = 0.05;
    // how far the balls stay apart, so that planets that are close together are told apart
    private static final double PLANET_GAP = 0.01;
    // Deep enough, with the dark edge around it, for the channels at both of their depths:
    // a channel goes into a center whole.
    static final double CENTER_DEPTH = 0.056;
    // A gate is the box that the drawing has for it, a little smaller so that two that the
    // drawing has side by side don't touch, with its number on it. One that's lit goes through
    // its center and stands out of both of its faces by this much. The others stand out less,
    // and aren't there at all where they're in the color of their center.
    static final double GATE_BETWEEN = 0.004;
    static final double GATE_ROUND = 0.008;
    static final double GATE_PROUD = 0.01;
    static final double GATE_OFF_PROUD = 0.003;
    static final double GATE_NUMBER = 0.033;
    private static final double NUMBER_OVER = 0.002;
    // how large the numbers of the wheel are, which lie on their gates
    static final double WHEEL_NUMBER = 0.042;
    // A channel is as wide as the drawing has it. Where the drawing paints a channel over
    // another one, or right beside it, the two are at different depths here, this far before
    // and behind the middle of the body graph, so that neither is in the other.
    static final double CHANNEL_RADIUS = 0.01;
    static final double CHANNEL_BETWEEN = 0.004;
    static final double CHANNEL_LAYER = CHANNEL_RADIUS + CHANNEL_BETWEEN / 2.0;
    // a cord rather than a thread, thinner than a channel
    static final double LINE_RADIUS = 0.005;
    // a channel that several sides light has their colors in turn along it, as wide as the stripes of the drawing
    private static final double BAND_LENGTH = 0.0075;
    // the angle of the wheel that's away from whoever looks, where the drawing has its top
    private static final double FAR_LONGITUDE = 268.25;
    // what the body graph stands on
    private static final double FLOOR = -0.8625;

    /**
     * The places of the body graph, out of its drawing: where each gate is, the outline of each
     * center, the two ends of the line that the Integration channels share, and what of the
     * Explore panel explains the half of a channel at each gate.
     */
    static final class Body {
        static final Map<Integer, double[]> GATE_PLACES = new HashMap<>();
        // how wide and how high the box of a gate is
        static final double[] GATE_SIZE = new double[2];
        static final Map<Center, List<double[]>> CENTER_OUTLINES = new EnumMap<>(Center.class);
        static final Map<String, String> EXPLAINED = new HashMap<>();
        static final String SHARED = "shared";
        // what the Integration channels are together, where channels are told apart
        static final String INTEGRATION = "integration";
        // Which of its two depths a channel is at, by its gates as "1-8": -1 behind, 1 before,
        // and none for a channel that's clear of every other one.
        static final Map<String, Integer> CHANNEL_LAYERS = new HashMap<>();
        // what the drawing of the wheel has around the body graph: straight down from its top to
        // either side, and round below, around the middle of the body graph
        static final List<double[]> SURROUND = new ArrayList<>();
        static final double[] SHARED_UPPER;
        static final double[] SHARED_LOWER;
        // where the middle of what's around the body graph is
        static final double[] SURROUND_MIDDLE;

        private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

        static {
            var t = TemplateFactory.SVG.get("bodygraph");
            for (var gate = 1; gate <= 64; ++gate) {
                t.setValue("gate-class-" + gate, "gate-" + gate);
                t.setValue("hd-channel-" + gate, "of-" + gate);
            }
            t.setValue("hd-channel-20-57", "of-" + SHARED);
            for (var center : Center.values()) {
                t.setValue("hd-center-" + center.name().toLowerCase(Locale.ROOT), "center-" + center.name());
            }
            var drawn = t.getContent();

            var gates = Pattern.compile("<rect class=\"gate-(\\d+)\" x=\"([\\d.]+)\"\\s+y=\"([\\d.]+)\" width=\"([\\d.]+)\" height=\"([\\d.]+)\"").matcher(drawn);
            while (gates.find()) {
                GATE_SIZE[0] = Double.parseDouble(gates.group(4)) / 400.0;
                GATE_SIZE[1] = Double.parseDouble(gates.group(5)) / 400.0;
                GATE_PLACES.put(Integer.parseInt(gates.group(1)), place(
                    Double.parseDouble(gates.group(2)) + Double.parseDouble(gates.group(4)) / 2.0,
                    Double.parseDouble(gates.group(3)) + Double.parseDouble(gates.group(5)) / 2.0));
            }

            var centers = Pattern.compile("<path d=\"([^\"]+)\"\\s+class=\"center-(\\w+) hd-center\"[^>]*?(?:transform=\"rotate\\(([^)]+)\\)\")?\\s*/>").matcher(drawn);
            while (centers.find()) {
                var outline = points(centers.group(1));
                if (centers.group(3) != null) {
                    var turn = numbers(centers.group(3));
                    outline = outline.stream().map(point -> turned(point, turn.get(0), turn.get(1), turn.get(2))).toList();
                }
                CENTER_OUTLINES.put(Center.valueOf(centers.group(2)), outline.stream().map(point -> place(point[0], point[1])).toList());
            }

            double[] upper = null;
            double[] lower = null;
            var halves = Pattern.compile("<(?:path d=\"([^\"]+)\"|rect[^>]*?) class=\"[^\"]*\\bof-(\\w+)\" data-explore=\"([^\"]+)\"").matcher(drawn);
            var painted = new HashMap<String, Integer>();
            while (halves.find()) {
                painted.putIfAbsent(halves.group(2), painted.size());
                EXPLAINED.put(halves.group(2), halves.group(3));
                if (SHARED.equals(halves.group(2))) {
                    // the line ends at its two short sides
                    var outline = points(halves.group(1));
                    var sides = new ArrayList<double[][]>();
                    for (var i = 0; i < 4; ++i) {
                        sides.add(new double[][]{outline.get(i), outline.get((i + 1) % 4)});
                    }
                    sides.sort(Comparator.comparingDouble(side -> Math.hypot(side[0][0] - side[1][0], side[0][1] - side[1][1])));
                    var ends = sides.subList(0, 2).stream()
                        .map(side -> new double[]{(side[0][0] + side[1][0]) / 2.0, (side[0][1] + side[1][1]) / 2.0})
                        .sorted(Comparator.comparingDouble(end -> end[1])).toList();
                    upper = place(ends.get(0)[0], ends.get(0)[1]);
                    lower = place(ends.get(1)[0], ends.get(1)[1]);
                }
            }
            SHARED_UPPER = upper;
            SHARED_LOWER = lower;
            layer(painted);

            var around = Pattern.compile("<path d=\"M ([\\d.]+) ([\\d.]+) L ([\\d.]+) ([\\d.]+) A ([\\d.]+) [\\d.]+ 0 0 0 ([\\d.]+) ([\\d.]+) Z\" fill=\"url\\(#grad1\\)\"")
                .matcher(TemplateFactory.SVG.get("wheel").getContent());
            if (!around.find()) {
                throw new IllegalStateException("The drawing of the wheel has nothing around its body graph");
            }
            var top = new double[]{Double.parseDouble(around.group(1)), Double.parseDouble(around.group(2))};
            var left = new double[]{Double.parseDouble(around.group(3)), Double.parseDouble(around.group(4))};
            var radius = Double.parseDouble(around.group(5));
            var right = new double[]{Double.parseDouble(around.group(6)), Double.parseDouble(around.group(7))};
            // the middle of the circle that it's round along, above the line between its sides
            var half = (right[0] - left[0]) / 2.0;
            var middle = new double[]{left[0] + half, left[1] - Math.sqrt(radius * radius - half * half)};
            var from = Math.atan2(left[1] - middle[1], left[0] - middle[0]);
            var to = Math.atan2(right[1] - middle[1], right[0] - middle[0]);
            // about the middle of the body graph, so that it stays around it when it's larger
            var round = new ArrayList<double[]>();
            round.add(top);
            for (var step = 0; step <= 24; ++step) {
                var angle = from + (to - from) * step / 24.0;
                round.add(new double[]{middle[0] + radius * Math.cos(angle), middle[1] + radius * Math.sin(angle)});
            }
            var lowest = round.stream().mapToDouble(point -> point[1]).max().orElse(top[1]);
            var centered = place(top[0], (top[1] + lowest) / 2.0);
            for (var point : round) {
                var placed = place(point[0], point[1]);
                SURROUND.add(new double[]{placed[0] - centered[0], placed[1] - centered[1]});
            }
            SURROUND_MIDDLE = centered;
        }

        private Body() {
        }

        /**
         * Gives the channels that are in each other's way one of the two depths each, so that
         * no two of them are at the same one, and the one that the drawing paints later is
         * before the other wherever that can be.
         */
        private static void layer(Map<String, Integer> painted) {
            // each channel as the lines that it runs along, and when the drawing paints it
            var lines = new HashMap<String, List<double[][]>>();
            var order = new HashMap<String, Integer>();
            var integration = List.of(10, 20, 34, 57);
            for (var channel : Analysis.allChannels()) {
                if (integration.contains(channel.gate1()) && integration.contains(channel.gate2())) {
                    continue;
                }
                var name = channel.gate1() + "-" + channel.gate2();
                var line = new ArrayList<double[][]>();
                line.add(new double[][]{GATE_PLACES.get(channel.gate1()), GATE_PLACES.get(channel.gate2())});
                lines.put(name, line);
                order.put(name, Math.min(painted.get(String.valueOf(channel.gate1())), painted.get(String.valueOf(channel.gate2()))));
            }
            var together = new ArrayList<double[][]>();
            for (var gate : integration) {
                together.add(new double[][]{GATE_PLACES.get(gate), gate == 10 || gate == 20 ? SHARED_UPPER : SHARED_LOWER});
            }
            together.add(new double[][]{SHARED_UPPER, SHARED_LOWER});
            lines.put(INTEGRATION, together);
            order.put(INTEGRATION, painted.get(SHARED));

            // which are in each other's way: closer than two channels are wide, with what's between them
            var names = lines.keySet().stream().sorted().toList();
            var near = new HashMap<String, List<String>>();
            for (var one : names) {
                for (var other : names) {
                    if (one.compareTo(other) < 0 && lines.get(one).stream().anyMatch(line -> lines.get(other).stream().anyMatch(
                            crossed -> apart(line[0], line[1], crossed[0], crossed[1]) < 2.0 * CHANNEL_RADIUS + CHANNEL_BETWEEN))) {
                        near.computeIfAbsent(one, name -> new ArrayList<>()).add(other);
                        near.computeIfAbsent(other, name -> new ArrayList<>()).add(one);
                    }
                }
            }
            // each group of them takes turns, from one of them on, at one depth and at the other
            for (var start : names) {
                if (!near.containsKey(start) || CHANNEL_LAYERS.containsKey(start)) {
                    continue;
                }
                var group = new ArrayList<String>();
                CHANNEL_LAYERS.put(start, 1);
                group.add(start);
                for (var next = 0; next < group.size(); ++next) {
                    var from = group.get(next);
                    for (var other : near.get(from)) {
                        if (!CHANNEL_LAYERS.containsKey(other)) {
                            CHANNEL_LAYERS.put(other, -CHANNEL_LAYERS.get(from));
                            group.add(other);
                        } else if (CHANNEL_LAYERS.get(other).equals(CHANNEL_LAYERS.get(from))) {
                            throw new IllegalStateException("The channels " + from + " and " + other + " of the drawing are in each other's way at either depth");
                        }
                    }
                }
                // the other way round when that has more of them as the drawing paints them
                var as_painted = 0;
                for (var one : group) {
                    for (var other : near.get(one)) {
                        as_painted += Integer.compare(order.get(one), order.get(other)) == Integer.compare(CHANNEL_LAYERS.get(one), CHANNEL_LAYERS.get(other)) ? 1 : -1;
                    }
                }
                if (as_painted < 0) {
                    group.forEach(name -> CHANNEL_LAYERS.put(name, -CHANNEL_LAYERS.get(name)));
                }
            }
        }

        // how far apart two lines are in the drawing, nothing when they cross
        private static double apart(double[] from, double[] to, double[] other_from, double[] other_to) {
            if (side(from, to, other_from) * side(from, to, other_to) < 0.0 && side(other_from, other_to, from) * side(other_from, other_to, to) < 0.0) {
                return 0.0;
            }
            return Math.min(Math.min(away(from, other_from, other_to), away(to, other_from, other_to)), Math.min(away(other_from, from, to), away(other_to, from, to)));
        }

        private static double side(double[] from, double[] to, double[] point) {
            return (to[0] - from[0]) * (point[1] - from[1]) - (to[1] - from[1]) * (point[0] - from[0]);
        }

        private static double away(double[] point, double[] from, double[] to) {
            var along = Math.max(0.0, Math.min(1.0, ((point[0] - from[0]) * (to[0] - from[0]) + (point[1] - from[1]) * (to[1] - from[1])) /
                                                    (Math.pow(to[0] - from[0], 2) + Math.pow(to[1] - from[1], 2))));
            return Math.hypot(point[0] - from[0] - along * (to[0] - from[0]), point[1] - from[1] - along * (to[1] - from[1]));
        }

        private static List<Double> numbers(String text) {
            var numbers = new ArrayList<Double>();
            var matcher = NUMBER.matcher(text);
            while (matcher.find()) {
                numbers.add(Double.parseDouble(matcher.group()));
            }
            return numbers;
        }

        private static List<double[]> points(String path) {
            var numbers = numbers(path);
            var points = new ArrayList<double[]>();
            for (var i = 0; i + 1 < numbers.size(); i += 2) {
                points.add(new double[]{numbers.get(i), numbers.get(i + 1)});
            }
            return points;
        }

        private static double[] turned(double[] point, double degrees, double x, double y) {
            var angle = Math.toRadians(degrees);
            var dx = point[0] - x;
            var dy = point[1] - y;
            return new double[]{x + dx * Math.cos(angle) - dy * Math.sin(angle), y + dx * Math.sin(angle) + dy * Math.cos(angle)};
        }

        // The drawing has the body graph around 480 across and 465 down, and 670 high, which the
        // wheel leaves the room for in its middle.
        private static double[] place(double x, double y) {
            return new double[]{(x - 480.0) / 400.0, (465.0 - y) / 400.0};
        }
    }

    /**
     * What the drawing of a chart says, read from its template once the wheel image has filled
     * it: which gates are lit and how, which centers are defined, who lights each half of a
     * channel, which gates of the wheel are lit, where the planets are, and every color, from
     * its own styles. Nothing here decides any of that.
     */
    static final class Drawing {
        private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
        private static final Pattern FILL = Pattern.compile("(?:^|[;\\s])fill:\\s*([^;]+);");
        private static final Pattern RGB = Pattern.compile("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
        // a number as a template writes it, which is with an exponent when it's a small one
        private static final String NUMBER = "(-?[\\d.]+(?:[eE]-?\\d+)?)";
        private static final Pattern LIT = Pattern.compile("class=\"([^\"]+)\" opacity=\"([\\d.]+)\"[^>]*?rotate\\(" + NUMBER + " ");
        private static final Pattern PLANET = Pattern.compile("data-explore=\"(activation\\.\\d+\\.(\\w+) wheel\\.planets)\" transform=\"rotate\\(" + NUMBER + " ");
        // a mark is an arc with the clock, from one place of the drawing to another
        private static final Pattern MARK = Pattern.compile(
            "d=\"M ([\\d.]+) ([\\d.]+) A [\\d.]+ [\\d.]+ 0 0 1 ([\\d.]+) ([\\d.]+)[^\"]*\" class=\"(stripe-\\w+)\" transform=\"rotate\\(" + NUMBER + " ");

        /** A planet that the drawing has on the wheel: what explains it, which it is, and how far round the wheel. */
        record OnWheel(String part, Planet planet, double longitude) {
        }

        /** A mark that the drawing has inside a gate of the wheel: its color, and from where to where round the wheel. */
        record Mark(String color, double from, double to) {
        }

        private final Template drawn_;
        private final Map<String, String> fills_ = new HashMap<>();

        Drawing(Template drawn) {
            drawn_ = drawn;
            var content = drawn.getContent();
            var styles = RULE.matcher(content.substring(content.indexOf("<style"), content.indexOf("</style>")));
            while (styles.find()) {
                var fill = FILL.matcher(styles.group(2));
                if (fill.find()) {
                    for (var selector : styles.group(1).split(",")) {
                        fills_.put(selector.strip(), fill.group(1).strip());
                    }
                }
            }
        }

        /** The color that the styles of the drawing fill an element of a class with. */
        String fill(String element, String name) {
            var fill = fills_.containsKey(element + "." + name) ? fills_.get(element + "." + name) : fills_.get("." + name);
            if (fill == null) {
                throw new IllegalStateException("The drawing has no fill for " + element + "." + name);
            }
            return color(fill);
        }

        /** A color of the styles, written as an app takes it. */
        static String color(String written) {
            var rgb = RGB.matcher(written);
            if (rgb.matches()) {
                return String.format("#%02x%02x%02x", Integer.parseInt(rgb.group(1)), Integer.parseInt(rgb.group(2)), Integer.parseInt(rgb.group(3)));
            }
            if (written.matches("#[0-9a-fA-F]{6}")) {
                return written.toLowerCase(Locale.ROOT);
            }
            if (written.matches("#[0-9a-fA-F]{3}")) {
                return ("#" + written.charAt(1) + written.charAt(1) + written.charAt(2) + written.charAt(2) + written.charAt(3) + written.charAt(3)).toLowerCase(Locale.ROOT);
            }
            return switch (written) {
                case "white" -> "#ffffff";
                case "black" -> "#000000";
                default -> throw new IllegalStateException("The drawing has a color that isn't known here: " + written);
            };
        }

        /** A color as it shows over white when it's only this strong. */
        static String washed(String color, double strength) {
            var washed = new StringBuilder("#");
            for (var part = 1; part < 7; part += 2) {
                washed.append(String.format("%02x", (int) Math.round(Integer.parseInt(color.substring(part, part + 2), 16) * strength + 255.0 * (1.0 - strength))));
            }
            return washed.toString();
        }

        private String value(String id) {
            return drawn_.isValueSet(id) ? drawn_.getValue(id) : "";
        }

        /** Whether a gate of the body graph is lit. */
        boolean gateLit(int gate) {
            return value("gate-class-" + gate).endsWith("-on");
        }

        /** The color of the box of a gate of the body graph. */
        String gateBox(int gate) {
            return fill("rect", value("gate-class-" + gate));
        }

        /** The color of the number of a gate of the body graph. */
        String gateNumber(int gate) {
            return fill("text", value("gate-class-" + gate));
        }

        /** Whether a center is defined. */
        boolean centerDefined(Center center) {
            return !value("hd-center-" + center.name().toLowerCase(Locale.ROOT)).endsWith("-off");
        }

        /** The color of a center. */
        String center(Center center) {
            return fill("path", value("hd-center-" + center.name().toLowerCase(Locale.ROOT)));
        }

        /** The color of a channel that nothing lights. */
        String channelOff() {
            return fill("path", "hd-channel-off");
        }

        /**
         * The colors that light the half of a channel at a gate, or the line that the Integration
         * channels share when it's named by its two gates: none when nothing lights it, and
         * several when several sides do, which the drawing stripes it with.
         */
        List<String> channel(String half) {
            var style = value("hd-channel-" + half).replaceFirst("^hd-channel-", "").replaceFirst("-stroke$", "");
            var sides = switch (style) {
                case "", "off" -> List.<String>of();
                case "both" -> List.of("on", "design");
                case "all" -> List.of("on", "design", "third");
                default -> List.of(style.split("-"));
            };
            return sides.stream().map(side -> fill("rect", "stripe-" + side)).toList();
        }

        /** The color of the first of the sides, whose planets are on the wheel. */
        String first() {
            return fill("rect", "stripe-on");
        }

        /** How far round the wheel the drawing has something, as a longitude of the wheel here. */
        private static double longitude(String turned) {
            return -91.75 - Double.parseDouble(turned);
        }

        /** The gate of the wheel that the drawing has this far round. */
        private static int gate(String turned) {
            return GATES_ORDER.get((int) Math.floor(Double.parseDouble(turned) / Position.GATE_DEGREES + 1e-6) % 64);
        }

        /** The gates of the wheel that are lit, each with the wash of its color that the drawing gives it. */
        Map<Integer, String> wheelLit() {
            var lit = new HashMap<Integer, String>();
            var found = LIT.matcher(value("gate-backgrounds"));
            while (found.find()) {
                lit.put(gate(found.group(3)), washed(fill("path", found.group(1)), Double.parseDouble(found.group(2))));
            }
            return lit;
        }

        /** The color of the number of a gate of the wheel. */
        String wheelNumber(boolean lit) {
            return fill("text", lit ? "gate-on" : "gate-off");
        }

        /** The planets on the wheel. */
        List<OnWheel> planets() {
            return planets(value("planets"));
        }

        /** The gate that the drawing says a planet on the wheel is in, in the first of its rows: where a planet is exactly between two gates doesn't say. */
        int gate(Planet planet) {
            var activation = value(planet.id_ + "-gate1");
            return Integer.parseInt(activation.substring(0, activation.indexOf('.')));
        }

        /** The planets that a drawing has on its wheel. */
        static List<OnWheel> planets(String drawn) {
            var planets = new ArrayList<OnWheel>();
            var found = PLANET.matcher(drawn);
            while (found.find()) {
                planets.add(new OnWheel(found.group(1), Planet.valueOf(found.group(2)), longitude(found.group(3))));
            }
            return planets;
        }

        /** The gates of the wheel that are marked for someone, each with its marks where the drawing has them, in its order. */
        Map<Integer, List<Mark>> marks() {
            var marks = new HashMap<Integer, List<Mark>>();
            var found = MARK.matcher(value("person-marks"));
            while (found.find()) {
                // how far round the wheel each end of the arc is before it's turned to its gate, from the top of the drawing with the clock
                var starts = Math.toDegrees(Math.atan2(Double.parseDouble(found.group(1)) - 480.0, 480.0 - Double.parseDouble(found.group(2))));
                var ends = Math.toDegrees(Math.atan2(Double.parseDouble(found.group(3)) - 480.0, 480.0 - Double.parseDouble(found.group(4))));
                var turned = Double.parseDouble(found.group(6));
                marks.computeIfAbsent(gate(found.group(6)), gate -> new ArrayList<>())
                    .add(new Mark(fill("path", found.group(5)), -91.75 - turned - ends, -91.75 - turned - starts));
            }
            return marks;
        }
    }

    static String glyph(Planet planet) {
        return switch (planet) {
            case SUN -> "\u2609";
            case EARTH -> "\u2295";
            case MOON -> "\u263d";
            case NORTH_NODE -> "\u260a";
            case SOUTH_NODE -> "\u260b";
            case MERCURY -> "\u263f";
            case VENUS -> "\u2640";
            case MARS -> "\u2642";
            case JUPITER -> "\u2643";
            case SATURN -> "\u2644";
            case URANUS -> "\u2645";
            case NEPTUNE -> "\u2646";
            case PLUTO -> "\u2647";
            case CHIRON -> "\u26b7";
            case NONE -> "";
        } + AS_TEXT;
    }

    /**
     * Where a longitude is on the wheel as a slab of it has its outline: it goes round against
     * the clock, as on the drawing, with the top of the drawing up.
     */
    static double[] around(double longitude, double radius) {
        var angle = Math.toRadians(longitude - FAR_LONGITUDE + 90.0);
        return new double[]{radius * Math.cos(angle), radius * Math.sin(angle)};
    }

    /**
     * Where that is in the room, this far over the wheel: the top of the drawing is away from
     * whoever looks and higher, since the wheel is tipped towards them.
     */
    static double[] over(double longitude, double radius, double height) {
        var flat = around(longitude, radius);
        var tipped = Math.toRadians(TIPPED);
        return new double[]{flat[0], height * Math.cos(tipped) + flat[1] * Math.sin(tipped), height * Math.sin(tipped) - flat[1] * Math.cos(tipped)};
    }

    /**
     * Where each of these longitudes is shown so that none is closer to another than this many
     * degrees: those that are move apart by as little as it takes, around where they were
     * together, and the others stay where they are.
     */
    static List<Double> spread(List<Double> longitudes, double apart) {
        var count = longitudes.size();
        var order = new ArrayList<Integer>();
        for (var i = 0; i < count; ++i) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(longitudes::get));
        var shown = new double[count];
        for (var i = 0; i < count; ++i) {
            shown[i] = longitudes.get(order.get(i));
        }
        // each pass takes the ones that are too close apart by half of what's lacking, the last
        // and the first across the start of the circle too
        for (var pass = 0; pass < 200 && count > 1; ++pass) {
            var moved = false;
            for (var i = 0; i < count; ++i) {
                var next = (i + 1) % count;
                var gap = shown[next] - shown[i] + (next == 0 ? 360.0 : 0.0);
                if (gap < apart - 1e-9) {
                    shown[i] -= (apart - gap) / 2.0;
                    shown[next] += (apart - gap) / 2.0;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        var spread = new ArrayList<Double>(Collections.nCopies(count, 0.0));
        for (var i = 0; i < count; ++i) {
            spread.set(order.get(i), shown[i]);
        }
        return spread;
    }

    /**
     * An outline whose every side is this much further out. Its corners are those of a shape
     * that bulges nowhere inward, as the centers are.
     */
    static List<double[]> grown(List<double[]> outline, double by) {
        var corners = outline.size();
        var area = 0.0;
        for (var corner = 0; corner < corners; ++corner) {
            var at = outline.get(corner);
            var after = outline.get((corner + 1) % corners);
            area += at[0] * after[1] - after[0] * at[1];
        }
        // outward is to the right of a side that goes round against the clock
        var round = area > 0.0 ? 1.0 : -1.0;
        var grown = new ArrayList<double[]>();
        for (var corner = 0; corner < corners; ++corner) {
            var before = outline.get((corner + corners - 1) % corners);
            var at = outline.get(corner);
            var after = outline.get((corner + 1) % corners);
            var back = Math.hypot(at[0] - before[0], at[1] - before[1]);
            var on = Math.hypot(after[0] - at[0], after[1] - at[1]);
            var first = new double[]{round * (at[1] - before[1]) / back, -round * (at[0] - before[0]) / back};
            var second = new double[]{round * (after[1] - at[1]) / on, -round * (after[0] - at[0]) / on};
            // where the two sides meet once each is that much further out
            var along = by / (1.0 + first[0] * second[0] + first[1] * second[1]);
            grown.add(new double[]{at[0] + (first[0] + second[0]) * along, at[1] + (first[1] + second[1]) * along});
        }
        return grown;
    }

    private static double rounded(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static JsonArray place(double... values) {
        var place = new JsonArray();
        for (var value : values) {
            place.add(rounded(value));
        }
        return place;
    }

    /**
     * An outline with its corners rounded off over this far, or less where its sides are short:
     * what it's the outline of stays what it was, without its points.
     */
    static List<double[]> softened(List<double[]> outline, double rounded) {
        var soft = new ArrayList<double[]>();
        var corners = outline.size();
        for (var corner = 0; corner < corners; ++corner) {
            var before = outline.get((corner + corners - 1) % corners);
            var at = outline.get(corner);
            var after = outline.get((corner + 1) % corners);
            var back = Math.hypot(before[0] - at[0], before[1] - at[1]);
            var on = Math.hypot(after[0] - at[0], after[1] - at[1]);
            var cut = Math.min(rounded, Math.min(back, on) / 3.0);
            if (cut <= 0.0) {
                soft.add(at);
                continue;
            }
            var from = new double[]{at[0] + (before[0] - at[0]) * cut / back, at[1] + (before[1] - at[1]) * cut / back};
            var to = new double[]{at[0] + (after[0] - at[0]) * cut / on, at[1] + (after[1] - at[1]) * cut / on};
            // a curve from one side to the other that the corner pulls towards itself
            for (var step = 0; step <= 4; ++step) {
                var along = step / 4.0;
                var first = (1.0 - along) * (1.0 - along);
                var middle = 2.0 * (1.0 - along) * along;
                var last = along * along;
                soft.add(new double[]{first * from[0] + middle * at[0] + last * to[0], first * from[1] + middle * at[1] + last * to[1]});
            }
        }
        return soft;
    }

    private static JsonObject shape(String kind, String id, String color, String part) {
        var shape = new JsonObject().set("kind", kind).set("id", id).set("color", color);
        return part == null ? shape : shape.set("part", part);
    }

    private static JsonObject rod(String id, double[] from, double[] to, double radius, String color, String part) {
        return shape("rod", id, color, part).set("from", place(from)).set("to", place(to)).set("radius", radius);
    }

    // what nothing lights: as the drawing has it, without a shine
    private static JsonObject unlit(JsonObject shape) {
        return shape.set("look", "matte");
    }

    // black takes no light of its own
    private static JsonObject glowing(JsonObject shape, String color, double glow) {
        return color.equals("#000000") ? shape : shape.set("glow", glow);
    }

    /**
     * A part of the wheel between two longitudes and two distances from its middle, this thick
     * and with its middle this far over the wheel. Its outline follows the wheel round.
     */
    private static JsonObject sector(String id, String color, String part, double from, double to, double inner, double outer, double depth, double height, double rounded) {
        var steps = Math.max(1, (int) Math.ceil((to - from) / 5.0));
        var corners = new ArrayList<double[]>();
        for (var step = 0; step <= steps; ++step) {
            corners.add(around(from + (to - from) * step / steps, inner));
        }
        for (var step = steps; step >= 0; --step) {
            corners.add(around(from + (to - from) * step / steps, outer));
        }
        var outline = new JsonArray();
        for (var point : rounded > 0.0 ? softened(corners, rounded) : corners) {
            outline.add(place(point));
        }
        var tipped = Math.toRadians(TIPPED);
        return shape("slab", id, color, part).set("outline", outline).set("depth", depth)
            .set("at", place(0.0, height * Math.cos(tipped), height * Math.sin(tipped))).set("turned", place(TIPPED - 90.0, 0.0, 0.0));
    }

    // a stretch of a channel at its depth, in the colors that light it: one rod, or their colors in turn
    private static void stretch(JsonArray shapes, String id, double[] from, double[] to, double depth, List<String> colors, String off, String part) {
        var length = Math.hypot(to[0] - from[0], to[1] - from[1]);
        var bands = colors.size() < 2 ? 1 : Math.max(colors.size(), (int) Math.round(length / BAND_LENGTH));
        for (var band = 0; band < bands; ++band) {
            var start = new double[]{from[0] + (to[0] - from[0]) * band / bands, from[1] + (to[1] - from[1]) * band / bands, depth};
            var end = new double[]{from[0] + (to[0] - from[0]) * (band + 1) / bands, from[1] + (to[1] - from[1]) * (band + 1) / bands, depth};
            var color = colors.isEmpty() ? off : colors.get(band % colors.size());
            var rod = rod(bands == 1 ? id : id + "." + band, start, end, CHANNEL_RADIUS, color, part);
            shapes.add(colors.isEmpty() ? unlit(rod) : glowing(rod, color, 0.3));
        }
    }

    private static void bodygraph(JsonArray shapes, Drawing drawing) {
        for (var center : Center.values()) {
            var corners = softened(Body.CENTER_OUTLINES.get(center), CENTER_ROUNDED);
            var outline = new JsonArray();
            for (var point : corners) {
                outline.add(place(point));
            }
            var slab = shape("slab", "center." + center.name(), drawing.center(center), "center." + center.name() + " centers")
                .set("outline", outline).set("depth", CENTER_DEPTH).set("at", place(0.0, 0.0, 0.0));
            shapes.add(drawing.centerDefined(center) ? slab.set("glow", 0.25) : unlit(slab));
            // each has a dark edge, which is what it's seen by on something pale
            var edge = new JsonArray();
            for (var point : softened(grown(Body.CENTER_OUTLINES.get(center), EDGE), CENTER_ROUNDED + EDGE)) {
                edge.add(place(point));
            }
            shapes.add(unlit(shape("slab", "center." + center.name() + ".edge", EDGE_COLOR, "center." + center.name() + " centers")
                .set("outline", edge).set("depth", CENTER_DEPTH - 2.0 * EDGE_BACK).set("at", place(0.0, 0.0, 0.0))));
        }

        var off = drawing.channelOff();
        var integration = List.of(10, 20, 34, 57);
        for (var channel : Analysis.allChannels()) {
            if (integration.contains(channel.gate1()) && integration.contains(channel.gate2())) {
                continue;
            }
            var first = Body.GATE_PLACES.get(channel.gate1());
            var second = Body.GATE_PLACES.get(channel.gate2());
            var middle = new double[]{(first[0] + second[0]) / 2.0, (first[1] + second[1]) / 2.0};
            var id = "channel." + channel.gate1() + "-" + channel.gate2();
            var depth = CHANNEL_LAYER * Body.CHANNEL_LAYERS.getOrDefault(channel.gate1() + "-" + channel.gate2(), 0);
            for (var gate : List.of(channel.gate1(), channel.gate2())) {
                stretch(shapes, id + "." + gate, Body.GATE_PLACES.get(gate), middle, depth, drawing.channel(String.valueOf(gate)), off, Body.EXPLAINED.get(String.valueOf(gate)));
            }
        }
        // the Integration channels share the line between where the gates 10 and 20 meet and
        // where 34 and 57 do
        var depth = CHANNEL_LAYER * Body.CHANNEL_LAYERS.getOrDefault(Body.INTEGRATION, 0);
        for (var gate : integration) {
            var end = gate == 10 || gate == 20 ? Body.SHARED_UPPER : Body.SHARED_LOWER;
            stretch(shapes, "channel.integration." + gate, Body.GATE_PLACES.get(gate), end, depth, drawing.channel(String.valueOf(gate)), off, Body.EXPLAINED.get(String.valueOf(gate)));
        }
        var shared = drawing.channel("20-57");
        stretch(shapes, "channel.integration.shared", Body.SHARED_UPPER, Body.SHARED_LOWER, depth, shared, off, Body.EXPLAINED.get(Body.SHARED));
        // where three of them meet, they're joined round
        for (var joint : List.of(Body.SHARED_UPPER, Body.SHARED_LOWER)) {
            var color = shared.isEmpty() ? off : shared.getFirst();
            var ball = shape("ball", "channel.integration.joint." + (joint == Body.SHARED_UPPER ? "upper" : "lower"), color, Body.EXPLAINED.get(Body.SHARED))
                .set("at", place(joint[0], joint[1], depth)).set("radius", CHANNEL_RADIUS);
            shapes.add(shared.isEmpty() ? unlit(ball) : glowing(ball, color, 0.3));
        }

        // Every gate is the box of the drawing with its number on both of its faces, read from
        // behind the body graph too, in the colors that the drawing gives the two.
        var box = softened(List.of(
            new double[]{-(Body.GATE_SIZE[0] - GATE_BETWEEN) / 2.0, -(Body.GATE_SIZE[1] - GATE_BETWEEN) / 2.0},
            new double[]{(Body.GATE_SIZE[0] - GATE_BETWEEN) / 2.0, -(Body.GATE_SIZE[1] - GATE_BETWEEN) / 2.0},
            new double[]{(Body.GATE_SIZE[0] - GATE_BETWEEN) / 2.0, (Body.GATE_SIZE[1] - GATE_BETWEEN) / 2.0},
            new double[]{-(Body.GATE_SIZE[0] - GATE_BETWEEN) / 2.0, (Body.GATE_SIZE[1] - GATE_BETWEEN) / 2.0}), GATE_ROUND);
        for (var gate = 1; gate <= 64; ++gate) {
            var at = Body.GATE_PLACES.get(gate);
            var lit = drawing.gateLit(gate);
            var part = Body.EXPLAINED.get(String.valueOf(gate));
            var over = CENTER_DEPTH / 2.0;
            // a box in the color of its center is that center
            if (!drawing.gateBox(gate).equals(drawing.center(Analysis.center(gate)))) {
                over += lit ? GATE_PROUD : GATE_OFF_PROUD;
                var outline = new JsonArray();
                for (var point : box) {
                    outline.add(place(point));
                }
                shapes.add(unlit(shape("slab", "body." + gate, drawing.gateBox(gate), part).set("outline", outline).set("depth", rounded(2.0 * over)).set("at", place(at[0], at[1], 0.0))));
            }
            shapes.add(shape("text", "body." + gate + ".number", drawing.gateNumber(gate), part).set("text", String.valueOf(gate)).set("size", GATE_NUMBER)
                .set("at", place(at[0], at[1], over + NUMBER_OVER)));
            shapes.add(shape("text", "body." + gate + ".number.behind", drawing.gateNumber(gate), part).set("text", String.valueOf(gate)).set("size", GATE_NUMBER)
                .set("at", place(at[0], at[1], -over - NUMBER_OVER)).set("turned", place(0.0, 180.0, 0.0)));
        }

        // what it stands on
        shapes.add(disc("stand", DARK, 0.25, 0.025, FLOOR - 0.0125));
        shapes.add(disc("stand.rim", GOLD, 0.27, 0.008, FLOOR - 0.029));
    }

    // a round slab that lies flat, its middle this high
    private static JsonObject disc(String id, String color, double radius, double depth, double height) {
        var outline = new JsonArray();
        for (var corner = 0; corner < 32; ++corner) {
            var angle = Math.toRadians(360.0 / 32 * corner);
            outline.add(place(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return shape("slab", id, color, null).set("outline", outline).set("depth", depth).set("at", place(0.0, height, 0.0)).set("turned", place(-90.0, 0.0, 0.0));
    }

    // a text that lies on the wheel, this far over it, its top away from whoever looks
    private static JsonObject lying(String id, String color, String part, String text, double size, double longitude, double radius, double height) {
        return shape("text", id, color, part).set("text", text).set("size", size)
            .set("at", place(over(longitude, radius, height + NUMBER_OVER))).set("turned", place(TIPPED - 90.0, 0.0, 0.0));
    }

    private static void wheel(JsonArray shapes, Drawing drawing, Map<Integer, String> planets) {
        // the band that everything of the wheel is on, which shows between the gates and the signs
        for (var part = 0; part < 8; ++part) {
            shapes.add(sector("wheel.band." + part, DARK, null, 45.0 * part, 45.0 * (part + 1), WHEEL_INNER, SIGNS_OUTER + 0.005, BAND_DEPTH, -BAND_DEPTH / 2.0, 0.0));
        }
        for (var sign = 0; sign < 12; ++sign) {
            shapes.add(sector("wheel.sign." + sign, SIGN_COLORS.get(sign % 2), "wheel.zodiac", 30.0 * sign, 30.0 * (sign + 1), SIGNS_INNER, SIGNS_OUTER, 0.012, 0.006, 0.0).set("look", "matte"));
            // its sign lies on it, so it's where the sign is from wherever it's looked at
            shapes.add(lying("wheel.sign." + sign + ".name", WHITE, "wheel.zodiac", SIGNS.charAt(sign) + AS_TEXT, 0.05, 30.0 * sign + 15.0, (SIGNS_INNER + SIGNS_OUTER) / 2.0, 0.012));
        }
        var lit = drawing.wheelLit();
        var marks = drawing.marks();
        for (var i = 0; i < 64; ++i) {
            var gate = GATES_ORDER.get(i);
            // the drawing has the gates with the clock from its top, which is against the longitude
            var start = -91.75 - Position.GATE_DEGREES * (i + 1);
            var middle = start + Position.GATE_DEGREES / 2.0;
            var part = planets.containsKey(gate) ? planets.get(gate) + " wheel.gates" : "wheel.gates";
            var on = lit.containsKey(gate);
            // a gate that's lit stands out of the others, in the wash of its color that the drawing gives it
            var depth = on ? 0.034 : 0.012;
            var tile = sector("wheel." + gate, on ? lit.get(gate) : TILE, part, start + 0.25, start + Position.GATE_DEGREES - 0.25,
                GATES_INNER, GATES_OUTER, depth, depth / 2.0, GATE_ROUNDED);
            shapes.add(on ? tile.set("glow", 0.2) : tile.set("look", "matte"));
            if (on) {
                // As the drawing has it, a segment of the circle goes from a gate that's lit
                // towards the body graph, in the same wash of its color: thin, level with the
                // middle of the band, and ending before anything of the body graph.
                shapes.add(unlit(sector("wheel." + gate + ".ray", lit.get(gate), part, start + 0.25, start + Position.GATE_DEGREES - 0.25,
                    RAY_INNER, WHEEL_INNER, RAY_DEPTH, -BAND_DEPTH / 2.0, GATE_ROUNDED)));
            }
            // its number lies on it, so it's on its own gate from wherever it's looked at
            shapes.add(lying("wheel." + gate + ".number", drawing.wheelNumber(on), null, String.valueOf(gate), WHEEL_NUMBER, middle, (GATES_INNER + GATES_OUTER) / 2.0, depth));
            // who else has the gate is marked on the edge of the band inside it, each where the drawing has their mark
            var marked = marks.getOrDefault(gate, List.of());
            for (var mark = 0; mark < marked.size(); ++mark) {
                shapes.add(unlit(sector("wheel." + gate + ".mark." + (mark + 1), marked.get(mark).color(), null, marked.get(mark).from(), marked.get(mark).to(),
                    WHEEL_INNER + 0.002, GATES_INNER - 0.002, 0.008, 0.004, 0.0)));
            }
        }
    }

    /**
     * The planets that the drawing has on the wheel, each over where it is.
     */
    static void planets(JsonArray shapes, Drawing drawing) {
        var planets = drawing.planets();
        var color = drawing.first();
        // planets that are close together stand apart, each still held over where it is
        var apart = Math.toDegrees((Math.max(2.0 * PLANET_RADIUS, PLANET_NAME) + PLANET_GAP) / PLANETS);
        var shown = spread(planets.stream().map(Drawing.OnWheel::longitude).toList(), apart);
        for (var i = 0; i < planets.size(); ++i) {
            var planet = planets.get(i);
            var id = "planet.1." + planet.planet().name();
            var at = over(shown.get(i), PLANETS, PLANET_HEIGHT);
            shapes.add(glowing(shape("ball", id, color, planet.part()).set("at", place(at)).set("radius", PLANET_RADIUS), color, 0.35));
            // what holds it over where it is on the wheel
            shapes.add(rod(id + ".stem", over(planet.longitude(), PLANETS, 0.0), at, 0.0022, GOLD, planet.part()));
            shapes.add(shape("text", id + ".name", INK, planet.part()).set("text", glyph(planet.planet())).set("size", PLANET_NAME).set("faces", true)
                .set("at", place(over(shown.get(i), PLANETS, PLANET_HEIGHT + PLANET_RADIUS + 0.04))));
            // Chiron activates no gate
            if (planet.planet() != Planet.CHIRON) {
                // From where the segment of its gate ends, at where the planet is, to its gate
                // in the body graph. All of them at once are a tangle over the body graph, so
                // each is only there for its planet, for the gate of the wheel that the planet
                // is in, and for its gate in the body graph.
                var in = drawing.gate(planet.planet());
                var gate = Body.GATE_PLACES.get(in);
                var with = new JsonArray();
                with.add("body." + in);
                with.add("body." + in + ".number");
                with.add("body." + in + ".number.behind");
                shapes.add(rod(id + ".line", over(planet.longitude(), RAY_INNER, -BAND_DEPTH / 2.0), new double[]{gate[0], gate[1], 0.0}, LINE_RADIUS, color, planet.part())
                    .set("quiet", true).set("with", with).set("glow", 0.6));
            }
        }
    }

    // the image that's loaded with the shapes explains errors
    @Override
    protected void printError(Context c, String message) {
    }

    @Override
    public void process(Context c) {
        if (!prepare(c)) {
            return;
        }
        // The chart is drawn as the wheel image draws it, and what's in the room is read from
        // that drawing: its shapes are the body graph's, and what's lit and in which color is
        // what the drawing has.
        var footer = new Footer();
        var drawn = c.templateSvg("wheel");
        var charts = draw(drawn, ChartType.of(type), footer).charts();
        // the gate of each planet, for its line to it
        showGatesOfRows(drawn, charts);
        var drawing = new Drawing(drawn);

        var shapes = new JsonArray();
        var surround = new JsonArray();
        for (var point : Body.SURROUND) {
            surround.add(place(point[0] * HALO_LARGER, point[1] * HALO_LARGER));
        }
        shapes.add(shape("halo", "backdrop", PAPER, null).set("at", place(Body.SURROUND_MIDDLE[0], Body.SURROUND_MIDDLE[1], 0.0)).set("outline", surround).set("soft", HALO_SOFT)
            .set("radius", rounded(HALO_REACH)).set("behind", HALO_BEHIND).set("opacity", HALO_OPACITY));
        for (var light = 0; light < LIGHTS.length; ++light) {
            shapes.add(shape("light", "light." + light, WHITE, null).set("from", place(LIGHTS[light][0], LIGHTS[light][1], LIGHTS[light][2])).set("strength", LIGHTS[light][3]));
        }
        bodygraph(shapes, drawing);
        wheel(shapes, drawing, explorePlanetsByGate(charts));
        planets(shapes, drawing);
        c.setContentType("application/json");
        c.print(Json.toString(new JsonObject().set("title", footer.title_).set("room", place(ROOM)).set("shapes", shapes)));
    }
}

package gatesofhumanity.elements;

import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.Position;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;
import rife.template.TemplateFactory;
import rife.test.MockConversation;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static gatesofhumanity.models.Entities.GATES;
import static org.junit.jupiter.api.Assertions.*;

public class WheelVolumeTest {
    // London, where the clock is an hour ahead of UTC in June
    private static final String BIRTH = "/birth/197506211430/2643743";
    private static final ZonedDateTime BORN = ZonedDateTime.of(1975, 6, 21, 13, 30, 0, 0, ZoneOffset.UTC);
    private static final String OTHER = "198803120815/2643743";
    private static final ZonedDateTime OTHER_BORN = ZonedDateTime.of(1988, 3, 12, 8, 15, 0, 0, ZoneOffset.UTC);
    private static final Pattern COLOR = Pattern.compile("#[0-9a-f]{6}");
    private static final String PERSONALITY = "#000000";
    private static final String DESIGN = "#ff3b1b";

    private static Map<String, JsonObject> shapes(MockConversation m, String chart) {
        var response = m.doRequest("/wheel/volume" + chart);
        assertEquals(200, response.getStatus(), chart);
        assertEquals("application/json", response.getContentType().split(";")[0], chart);
        var told = Json.parseObject(response.getText());
        assertEquals(List.of(2.0, 1.8, 2.0), numbers(told.get("room")));
        var shapes = new LinkedHashMap<String, JsonObject>();
        for (var each : (JsonArray) told.get("shapes")) {
            var shape = (JsonObject) each;
            assertNull(shapes.put(shape.getString("id"), shape), "two shapes are called " + shape.getString("id"));
        }
        return shapes;
    }

    private static List<Double> numbers(Object written) {
        return ((JsonArray) written).stream().map(number -> ((Number) number).doubleValue()).toList();
    }

    private static List<List<Double>> outline(JsonObject slab) {
        return ((JsonArray) slab.get("outline")).stream().map(WheelVolumeTest::numbers).toList();
    }

    // The charts of every kind, at their addresses.
    private static final List<String> CHARTS = List.of(BIRTH, "/202403011000/1850147", "/connection/" + OTHER + BIRTH.substring("/birth".length()),
        "/birth-transit" + BIRTH.substring("/birth".length()) + "/202403011000/1850147",
        "/connection-transit/194709210130/4975802/195502241915/5391959/202403011000/1850147");
    // what the styles of the drawing fill its classes with
    private static final Map<String, String> CENTER_COLORS = Map.of("center-off", "#ffffff", "gate-on-yellow", "#ffe000", "gate-on-green", "#75ce28",
        "gate-on-brown", "#d6992d", "gate-on-red", "#ff5b3b");
    // and the same at half their strength over white, as a gate of the wheel that's lit shows
    private static final Map<String, String> WASHED = Map.of("gate-on-yellow", "#fff080", "gate-on-green", "#bae794", "gate-on-brown", "#ebcc96", "gate-on-red", "#ffad9d");

    // the drawing of a chart, as a browser reads it
    private static org.jsoup.nodes.Document drawing(MockConversation m, String chart) {
        var response = m.doRequest("/wheel/image" + chart);
        assertEquals(200, response.getStatus(), chart);
        return Jsoup.parse(response.getText(), "", org.jsoup.parser.Parser.xmlParser());
    }

    // the color that the styles of the drawing give the stripes of a side
    private static String stripe(org.jsoup.nodes.Document drawing, String side) {
        var found = Pattern.compile("\\.stripe-" + side + "\\s*\\{\\s*fill:\\s*([^;]+);").matcher(drawing.selectFirst("style").outerHtml());
        assertTrue(found.find(), side);
        return found.group(1).equals("black") ? "#000000" : found.group(1);
    }

    // how far round the wheel the drawing turned something, from its top with the clock
    private static double turned(org.jsoup.nodes.Element element) {
        var found = Pattern.compile("rotate\\((-?[\\d.]+) ").matcher(element.attr("transform"));
        assertTrue(found.find(), element.outerHtml());
        return Double.parseDouble(found.group(1));
    }

    // the gate of the wheel that's this far round it
    private static int gateAt(double turned) {
        return gatesofhumanity.models.Entities.GATES_ORDER.get((int) Math.floor(turned / 5.625 + 1e-6) % 64);
    }

    // whether a point is inside an outline, by how often a line from it to the right crosses the outline

    // how far a place is from the line through the middle of the wheel that it lies around
    private static double fromAxis(List<Double> place) {
        var tipped = Math.toRadians(WheelVolume.TIPPED);
        var along = place.get(1) * Math.cos(tipped) + place.get(2) * Math.sin(tipped);
        return Math.sqrt(place.get(0) * place.get(0) + place.get(1) * place.get(1) + place.get(2) * place.get(2) - along * along);
    }

    /**
     * No ball of a planet is in another one, nor in the name of another planet, and no two
     * names are in each other: a name is as large as it's written, around where it's at.
     */
    private static void assertApart(Map<String, JsonObject> shapes) {
        var balls = shapes.entrySet().stream().filter(shape -> shape.getKey().matches("planet\\.\\d+\\.[A-Z_]+")).toList();
        assertTrue(balls.size() >= 14);
        for (var ball : balls) {
            var name = shapes.get(ball.getKey() + ".name");
            for (var other : balls) {
                if (other == ball) {
                    continue;
                }
                var radius = ((Number) ball.getValue().get("radius")).doubleValue();
                var between = between(numbers(ball.getValue().get("at")), numbers(other.getValue().get("at")));
                assertTrue(between >= radius + ((Number) other.getValue().get("radius")).doubleValue(), ball.getKey() + " in " + other.getKey() + ": " + between);
                var other_name = shapes.get(other.getKey() + ".name");
                var half = ((Number) other_name.get("size")).doubleValue() / 2.0;
                between = between(numbers(ball.getValue().get("at")), numbers(other_name.get("at")));
                assertTrue(between >= radius + half, ball.getKey() + " in the name of " + other.getKey() + ": " + between);
                between = between(numbers(name.get("at")), numbers(other_name.get("at")));
                assertTrue(between >= 2.0 * half, "the name of " + ball.getKey() + " in the one of " + other.getKey() + ": " + between);
            }
        }
    }

    private static double between(List<Double> one, List<Double> other) {
        return Math.sqrt(Math.pow(one.get(0) - other.get(0), 2) + Math.pow(one.get(1) - other.get(1), 2) + Math.pow(one.get(2) - other.get(2), 2));
    }

    // how far apart two lines in the room are at their closest
    private static double apart(List<Double> from, List<Double> to, List<Double> other_from, List<Double> other_to) {
        var closest = Double.MAX_VALUE;
        // as near as a walk along the one in steps comes to the other, which is exact enough for lines this short
        for (var step = 0; step <= 40; ++step) {
            var along = step / 40.0;
            var point = List.of(from.get(0) + (to.get(0) - from.get(0)) * along, from.get(1) + (to.get(1) - from.get(1)) * along, from.get(2) + (to.get(2) - from.get(2)) * along);
            var dx = other_to.get(0) - other_from.get(0);
            var dy = other_to.get(1) - other_from.get(1);
            var dz = other_to.get(2) - other_from.get(2);
            var length = dx * dx + dy * dy + dz * dz;
            var on = length == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((point.get(0) - other_from.get(0)) * dx + (point.get(1) - other_from.get(1)) * dy + (point.get(2) - other_from.get(2)) * dz) / length));
            closest = Math.min(closest, between(point, List.of(other_from.get(0) + dx * on, other_from.get(1) + dy * on, other_from.get(2) + dz * on)));
        }
        return closest;
    }

    private static boolean inside(List<List<Double>> outline, double x, double y) {
        var inside = false;
        for (var i = 0; i < outline.size(); ++i) {
            var from = outline.get(i);
            var to = outline.get((i + 1) % outline.size());
            if (from.get(1) > y != to.get(1) > y && x < from.get(0) + (to.get(0) - from.get(0)) * (y - from.get(1)) / (to.get(1) - from.get(1))) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static void assertPlace(double[] expected, Object written, String what) {
        var place = numbers(written);
        assertEquals(expected.length, place.size(), what);
        for (var i = 0; i < expected.length; ++i) {
            assertEquals(expected[i], place.get(i), 0.00006, what);
        }
    }

    private static final List<Double> ON_THE_WHEEL = List.of(WheelVolume.TIPPED - 90.0, 0.0, 0.0);

    private static List<String> colorsAlong(Map<String, JsonObject> shapes, String id) {
        if (shapes.containsKey(id)) {
            return List.of(shapes.get(id).getString("color"));
        }
        var colors = new ArrayList<String>();
        for (var band = 0; shapes.containsKey(id + "." + band); ++band) {
            colors.add(shapes.get(id + "." + band).getString("color"));
        }
        return colors;
    }

    @Test
    void theChartInTheRoomIsLitAsItsDrawingIs() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var chart : CHARTS) {
                var drawing = drawing(m, chart);
                var shapes = shapes(m, chart);

                // each center in the color that the drawing fills it with
                var centers = drawing.select("path.hd-center");
                assertEquals(9, centers.size(), chart);
                var defined = new HashSet<String>();
                for (var center : centers) {
                    var name = center.attr("data-explore").split(" ")[0];
                    var filled = center.classNames().stream().filter(CENTER_COLORS::containsKey).findFirst().orElseThrow();
                    assertEquals(CENTER_COLORS.get(filled), shapes.get(name).getString("color"), chart + " " + name);
                    assertEquals(filled.equals("center-off"), "matte".equals(shapes.get(name).get("look")), chart + " " + name);
                    if (!filled.equals("center-off")) {
                        defined.add(name.substring("center.".length()));
                    }
                }

                // Every gate of the body graph has its number where the drawing has its box. One
                // that the drawing lights is a black box with a white number, whoever lights it,
                // and the others have a dark number, on a white box where their center has a color.
                var boxes = drawing.select("rect.small-on, rect.small-off");
                assertEquals(64, boxes.size(), chart);
                for (var box : boxes) {
                    var gate = Integer.parseInt(box.nextElementSibling().text());
                    var lit = box.hasClass("small-on");
                    assertEquals(lit, box.nextElementSibling().hasClass("small-on"), chart + " gate " + gate);
                    var at = new double[]{(Double.parseDouble(box.attr("x")) + Double.parseDouble(box.attr("width")) / 2.0 - 480.0) / 400.0,
                        (465.0 - Double.parseDouble(box.attr("y")) - Double.parseDouble(box.attr("height")) / 2.0) / 400.0};
                    var number = shapes.get("body." + gate + ".number");
                    assertEquals(String.valueOf(gate), number.getString("text"), chart);
                    assertEquals(lit ? "#ffffff" : "#404040", number.getString("color"), chart + " gate " + gate);
                    assertEquals(number.getString("color"), shapes.get("body." + gate + ".number.behind").getString("color"), chart + " gate " + gate);
                    assertEquals(at[0], numbers(number.get("at")).get(0), 6e-5, chart + " gate " + gate);
                    assertEquals(at[1], numbers(number.get("at")).get(1), 6e-5, chart + " gate " + gate);
                    var shown = shapes.get("body." + gate);
                    var on_color = defined.contains(Analysis.center(gate).name());
                    if (lit) {
                        assertEquals("#000000", shown.getString("color"), chart + " gate " + gate);
                        assertEquals(WheelVolume.CENTER_DEPTH + 0.02, ((Number) shown.get("depth")).doubleValue(), 1e-9, chart + " gate " + gate);
                    } else if (on_color) {
                        assertEquals("#ffffff", shown.getString("color"), chart + " gate " + gate);
                        assertEquals(WheelVolume.CENTER_DEPTH + 0.006, ((Number) shown.get("depth")).doubleValue(), 1e-9, chart + " gate " + gate);
                    } else {
                        // white on white is its center
                        assertNull(shown, chart + " gate " + gate);
                    }
                }

                // The wheel is lit where the drawing has a segment of the circle, in the wash of
                // the color that the drawing gives it, with a segment of its own inside the gate.
                var lit = new java.util.HashMap<Integer, String>();
                for (var segment : drawing.select("path[opacity=0.5][class^=gate-on-]")) {
                    lit.put(gateAt(turned(segment)), WASHED.get(segment.className()));
                }
                assertFalse(lit.isEmpty(), chart);
                for (var gate = 1; gate <= 64; ++gate) {
                    var tile = shapes.get("wheel." + gate);
                    var ray = shapes.get("wheel." + gate + ".ray");
                    var number = shapes.get("wheel." + gate + ".number");
                    assertEquals(lit.getOrDefault(gate, "#ececf6"), tile.getString("color"), chart + " gate " + gate);
                    assertEquals(lit.containsKey(gate), tile.get("glow") != null, chart + " gate " + gate);
                    assertEquals(lit.containsKey(gate), ((Number) tile.get("depth")).doubleValue() > 0.03, chart + " gate " + gate);
                    assertEquals(lit.containsKey(gate) ? "#000000" : "#bebebe", number.getString("color"), chart + " gate " + gate);
                    if (lit.containsKey(gate)) {
                        assertEquals(lit.get(gate), ray.getString("color"), chart + " gate " + gate);
                        assertEquals(tile.getString("part"), ray.getString("part"), chart + " gate " + gate);
                    } else {
                        assertNull(ray, chart + " gate " + gate);
                    }
                }

                // the planets that the drawing has on the wheel, where it has them, in the color of the first side
                var planets = drawing.select("g[data-explore$=wheel.planets]");
                assertEquals(14, planets.size(), chart);
                assertEquals(14L, shapes.keySet().stream().filter(id -> id.matches("planet\\.\\d+\\.[A-Z_]+")).count(), chart);
                for (var planet : planets) {
                    var part = planet.attr("data-explore");
                    var id = "planet.1." + part.split(" ")[0].substring("activation.1.".length());
                    assertEquals(part, shapes.get(id).getString("part"), chart);
                    assertEquals(stripe(drawing, "on"), shapes.get(id).getString("color"), chart + " " + id);
                    assertPlace(WheelVolume.over(-91.75 - turned(planet), 0.885, 0.0), shapes.get(id + ".stem").get("from"), chart + " " + id);
                    // its line goes to the gate of the body graph that it's in on the wheel
                    var line = shapes.get(id + ".line");
                    if (id.endsWith(".CHIRON")) {
                        assertNull(line, chart);
                    } else {
                        var in = gateAt(turned(planet));
                        assertTrue(lit.containsKey(in), chart + " " + id);
                        assertPlace(new double[]{WheelVolume.Body.GATE_PLACES.get(in)[0], WheelVolume.Body.GATE_PLACES.get(in)[1], 0.0}, line.get("to"), chart + " " + id);
                        assertEquals(List.of("body." + in, "body." + in + ".number", "body." + in + ".number.behind"),
                            ((JsonArray) line.get("with")).stream().map(String::valueOf).toList(), chart + " " + id);
                    }
                }

                // who the drawing marks inside a gate of the wheel is marked there, in their color
                var marks = new java.util.HashMap<Integer, List<String>>();
                var arcs = new java.util.HashMap<Integer, List<double[]>>();
                for (var mark : drawing.select("path.stripe-design[transform], path.stripe-third[transform]")) {
                    marks.computeIfAbsent(gateAt(turned(mark)), gate -> new ArrayList<>()).add(stripe(drawing, mark.className().substring("stripe-".length())));
                    // where the drawing has it: from the first place of its path to where its arc ends, with the clock from the top
                    var path = Pattern.compile("M ([\\d.]+) ([\\d.]+) A [\\d.]+ [\\d.]+ 0 0 1 ([\\d.]+) ([\\d.]+)").matcher(mark.attr("d"));
                    assertTrue(path.find(), mark.outerHtml());
                    arcs.computeIfAbsent(gateAt(turned(mark)), gate -> new ArrayList<>()).add(new double[]{
                        turned(mark) + Math.toDegrees(Math.atan2(Double.parseDouble(path.group(1)) - 480.0, 480.0 - Double.parseDouble(path.group(2)))),
                        turned(mark) + Math.toDegrees(Math.atan2(Double.parseDouble(path.group(3)) - 480.0, 480.0 - Double.parseDouble(path.group(4))))});
                }
                assertEquals(chart.startsWith("/connection-transit"), !marks.isEmpty(), chart);
                assertEquals(chart.startsWith("/connection-transit"), marks.values().stream().anyMatch(marked -> marked.size() == 2), chart);
                for (var gate = 1; gate <= 64; ++gate) {
                    var marked = marks.getOrDefault(gate, List.of());
                    for (var mark = 0; mark < 2; ++mark) {
                        var shown = shapes.get("wheel." + gate + ".mark." + (mark + 1));
                        assertEquals(mark < marked.size() ? marked.get(mark) : null, shown == null ? null : shown.getString("color"), chart + " gate " + gate);
                        if (shown != null) {
                            // at the side of its gate where the drawing has it: every corner of it between
                            // the two ends of the drawing's arc, and its middle at theirs
                            var arc = arcs.get(gate).get(mark);
                            var round = outline(shown).stream().mapToDouble(corner ->
                                ((-91.75 - (Math.toDegrees(Math.atan2(corner.get(1), corner.get(0))) + 178.25)) % 360.0 + 360.0) % 360.0).toArray();
                            for (var corner : round) {
                                assertTrue(corner >= arc[0] - 0.05 && corner <= arc[1] + 0.05, chart + " gate " + gate + " mark " + (mark + 1) + ": " + corner + " in " + arc[0] + " to " + arc[1]);
                            }
                            assertEquals((arc[0] + arc[1]) / 2.0, (java.util.Arrays.stream(round).min().orElseThrow() + java.util.Arrays.stream(round).max().orElseThrow()) / 2.0, 0.05, chart + " gate " + gate);
                        }
                    }
                    if (marked.size() == 2) {
                        // side by side, on the edge of the band inside the gate
                        var first = outline(shapes.get("wheel." + gate + ".mark.1"));
                        var second = outline(shapes.get("wheel." + gate + ".mark.2"));
                        var tile = outline(shapes.get("wheel." + gate));
                        for (var corner : second) {
                            assertFalse(inside(first, corner.get(0), corner.get(1)), chart + " gate " + gate);
                            assertTrue(Math.hypot(corner.get(0), corner.get(1)) > 0.685 && Math.hypot(corner.get(0), corner.get(1)) < 0.7, chart + " gate " + gate);
                        }
                        var middle = new double[]{second.stream().mapToDouble(point -> point.get(0)).average().orElseThrow(), second.stream().mapToDouble(point -> point.get(1)).average().orElseThrow()};
                        assertFalse(inside(first, middle[0], middle[1]), chart + " gate " + gate);
                        assertTrue(inside(tile, middle[0] * 0.77 / Math.hypot(middle[0], middle[1]), middle[1] * 0.77 / Math.hypot(middle[0], middle[1])), chart + " gate " + gate);
                    }
                }

                // a channel that nothing lights is white, and the others are in the colors of the sides
                var sides = new HashSet<>(List.of("#ffffff", stripe(drawing, "on"), stripe(drawing, "design"), stripe(drawing, "third")));
                for (var shape : shapes.entrySet()) {
                    if (shape.getKey().startsWith("channel.")) {
                        assertTrue(sides.contains(shape.getValue().getString("color")), chart + " " + shape.getKey());
                    }
                }
            }
        }
    }

    @Test
    void aBirthChartIsItsBodyGraphInsideTheWheelWithThePlanetsOverTheirGates() {
        var birth = Charts.birth(BORN);
        var personality = Analysis.gates(new gatesofhumanity.models.RaveChart(birth.personality(), gatesofhumanity.models.PositionData.NONE));
        var design = Analysis.gates(new gatesofhumanity.models.RaveChart(birth.design(), gatesofhumanity.models.PositionData.NONE));
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var shapes = shapes(m, BIRTH);
            for (var shape : shapes.values()) {
                var what = shape.toString();
                assertTrue(Set.of("slab", "rod", "ball", "text", "halo", "light").contains(shape.getString("kind")), what);
                assertTrue(COLOR.matcher(shape.getString("color")).matches(), what);
                var places = new ArrayList<List<Double>>();
                for (var key : List.of("at", "from", "to")) {
                    if (shape.get(key) != null) {
                        places.add(numbers(shape.get(key)));
                    }
                }
                assertFalse(places.isEmpty(), what);
                for (var place : places) {
                    assertEquals(3, place.size(), what);
                    // a light is from somewhere rather than at a place, and everything else is in
                    // the room, nearer to its middle than what's behind the chart is behind it
                    assertTrue(shape.getString("kind").equals("light") ||
                               Math.sqrt(place.get(0) * place.get(0) + place.get(1) * place.get(1) + place.get(2) * place.get(2)) < WheelVolume.HALO_BEHIND &&
                               Math.abs(place.get(0)) <= 1.0 && Math.abs(place.get(1)) <= 0.9 && Math.abs(place.get(2)) <= 1.0, what);
                }
                // a text either faces whoever looks or lies on something, turned or not, and nothing floats before what it names
                assertNull(shape.get("forward"), what);
            }
            // What's behind the chart is the first shape, so that everything else is on it: pale
            // as a page is behind its drawing, with a little of the room through it and an edge
            // that fades into the room, and in the shape that the drawing has around the body
            // graph, pointed at its top and round below, larger since it's further away.
            var backdrop = shapes.get("backdrop");
            assertEquals("backdrop", shapes.keySet().iterator().next());
            assertEquals(List.of("halo", "#c6c6de"), List.of(backdrop.getString("kind"), backdrop.getString("color")));
            assertEquals(0.98, ((Number) backdrop.get("opacity")).doubleValue());
            assertEquals(1.0, ((Number) backdrop.get("behind")).doubleValue());
            var around = outline(backdrop);
            var soft = ((Number) backdrop.get("soft")).doubleValue();
            var radius = ((Number) backdrop.get("radius")).doubleValue();
            assertTrue(soft > 0.1, String.valueOf(soft));
            assertEquals(0.0, around.getFirst().get(0), 1e-4);
            assertEquals(around.stream().mapToDouble(point -> point.get(1)).max().orElseThrow(), around.getFirst().get(1));
            assertTrue(around.stream().allMatch(point -> Math.hypot(point.get(0), point.get(1)) + soft < radius), radius + " " + around);
            // three lights, the strongest from above at the front
            var lights = shapes.values().stream().filter(shape -> shape.getString("kind").equals("light")).toList();
            assertEquals(3, lights.size());
            assertTrue(numbers(lights.get(0).get("from")).get(1) > 0.0 && numbers(lights.get(0).get("from")).get(2) > 0.0);
            assertEquals(1.0, ((Number) lights.get(0).get("strength")).doubleValue());
            // nothing else lets the room through
            assertEquals(1L, shapes.values().stream().filter(shape -> shape.get("opacity") != null).count());

            // The centers stand where the drawing has them, their corners rounded off, each on a
            // dark slab that's larger all round and that the center stands out of: its edge.
            // The edge is deeper than a channel is thick, so a channel goes into a center whole.
            for (var center : Center.values()) {
                var slab = shapes.get("center." + center.name());
                assertEquals("slab", slab.getString("kind"));
                assertNull(slab.get("turned"));
                assertEquals("center." + center.name() + " centers", slab.getString("part"));
                assertEquals(0.056, ((Number) slab.get("depth")).doubleValue(), center.name());
                var edge = shapes.get("center." + center.name() + ".edge");
                assertEquals(List.of("slab", WheelVolume.EDGE_COLOR, "matte"), List.of(edge.getString("kind"), edge.getString("color"), edge.get("look")), center.name());
                assertEquals(slab.getString("part"), edge.getString("part"), center.name());
                assertEquals(0.048, ((Number) edge.get("depth")).doubleValue(), 1e-9, center.name());
                var framed = outline(edge);
                var corners = outline(slab);
                var sharp = WheelVolume.Body.CENTER_OUTLINES.get(center).stream().map(point -> List.of(point[0], point[1])).toList();
                assertEquals(5 * sharp.size(), corners.size(), center.name());
                assertEquals(corners.size(), framed.size(), center.name());
                for (var corner : corners) {
                    assertTrue(inside(framed, corner.get(0), corner.get(1)), center.name() + " " + corner);
                    var nearest = framed.stream().mapToDouble(point -> Math.hypot(point.get(0) - corner.get(0), point.get(1) - corner.get(1))).min().orElseThrow();
                    assertTrue(nearest > 0.007 && nearest < 0.02, center.name() + " " + nearest);
                    // inside of what the drawing has, to within what's written of a place
                    var towards = List.of(corner.get(0) * 0.999 + 0.001 * sharp.stream().mapToDouble(point -> point.get(0)).average().orElseThrow(),
                        corner.get(1) * 0.999 + 0.001 * sharp.stream().mapToDouble(point -> point.get(1)).average().orElseThrow());
                    assertTrue(inside(sharp, towards.get(0), towards.get(1)), center.name() + " " + corner);
                }
            }
            // A channel is as wide as the drawing has it, and at one depth all along. All of it
            // is within the depth of the edge of a center, so it goes into a center whole.
            var rods = shapes.entrySet().stream().filter(shape -> shape.getKey().startsWith("channel.") && shape.getValue().getString("kind").equals("rod")).toList();
            for (var rod : rods) {
                assertEquals(0.01, ((Number) rod.getValue().get("radius")).doubleValue(), rod.getKey());
                var depth = numbers(rod.getValue().get("from")).get(2);
                assertEquals(depth, numbers(rod.getValue().get("to")).get(2), rod.getKey());
                assertTrue(Math.abs(depth) + 0.01 < 0.048 / 2.0, rod.getKey());
            }
            // Where the drawing paints a channel over another one, or right beside it, the two
            // are at different depths, so no channel is in another one: there's room between any
            // two of them. The channel between 26 and 44 crosses four, behind them as the drawing has it.
            var before = WheelVolume.Body.CHANNEL_LAYERS.entrySet().stream().filter(layer -> layer.getValue() == 1).map(Map.Entry::getKey).sorted().toList();
            var behind_them = WheelVolume.Body.CHANNEL_LAYERS.entrySet().stream().filter(layer -> layer.getValue() == -1).map(Map.Entry::getKey).sorted().toList();
            assertEquals(List.of("14-2", "21-45", "29-46", "36-35", "5-15", "integration"), before);
            assertEquals(List.of("22-12", "44-26", "48-16"), behind_them);
            for (var rod : rods) {
                var channel = rod.getKey().split("\\.")[1];
                assertEquals(0.012 * WheelVolume.Body.CHANNEL_LAYERS.getOrDefault(channel, 0), numbers(rod.getValue().get("from")).get(2), 1e-9, rod.getKey());
            }
            assertTrue(rods.stream().anyMatch(rod -> numbers(rod.getValue().get("from")).get(2) == 0.0));
            for (var one : rods) {
                for (var other : rods) {
                    var channel = one.getKey().split("\\.")[1];
                    if (one.getKey().compareTo(other.getKey()) < 0 && !channel.equals(other.getKey().split("\\.")[1])) {
                        var between = apart(numbers(one.getValue().get("from")), numbers(one.getValue().get("to")), numbers(other.getValue().get("from")), numbers(other.getValue().get("to")));
                        assertTrue(between >= 0.02 + 0.003, one.getKey() + " in " + other.getKey() + ": " + between);
                    }
                }
            }
            // where three of the Integration channels meet, they're joined round
            var shared = rods.stream().filter(rod -> rod.getKey().startsWith("channel.integration.shared")).map(Map.Entry::getValue).toList();
            for (var joint : List.of("upper", "lower")) {
                var ball = shapes.get("channel.integration.joint." + joint);
                assertEquals(0.01, ((Number) ball.get("radius")).doubleValue());
                assertEquals(joint.equals("upper") ? numbers(shared.getFirst().get("from")) : numbers(shared.getLast().get("to")), numbers(ball.get("at")));
                assertEquals(shared.getFirst().getString("color"), ball.getString("color"));
            }

            // A gate is the box that the drawing has for it, inside its center and clear of every
            // other gate, with its number on both of its faces: on the gate from wherever it's
            // looked at, and read from behind the body graph too.
            assertEquals(List.of(0.05, 0.035), List.of(WheelVolume.Body.GATE_SIZE[0], WheelVolume.Body.GATE_SIZE[1]));
            var lit = 0;
            for (var gate = 1; gate <= 64; ++gate) {
                var at = WheelVolume.Body.GATE_PLACES.get(gate);
                var number = shapes.get("body." + gate + ".number");
                var behind = shapes.get("body." + gate + ".number.behind");
                var box = shapes.get("body." + gate);
                var face = box == null ? 0.028 : ((Number) box.get("depth")).doubleValue() / 2.0;
                assertPlace(new double[]{at[0], at[1], face + 0.002}, number.get("at"), "gate " + gate);
                assertPlace(new double[]{at[0], at[1], -face - 0.002}, behind.get("at"), "gate " + gate);
                assertNull(number.get("turned"), "gate " + gate);
                assertEquals(List.of(0.0, 180.0, 0.0), numbers(behind.get("turned")), "gate " + gate);
                for (var written : List.of(number, behind)) {
                    assertNull(written.get("faces"), "gate " + gate);
                    // two digits, of six tenths of its size each, and its height fit in the box
                    var size = ((Number) written.get("size")).doubleValue();
                    assertEquals(0.033, size, "gate " + gate);
                    assertTrue(1.2 * size <= 0.046 - 0.004 && 0.75 * size <= 0.031 - 0.004, "gate " + gate);
                }
                var center = outline(shapes.get("center." + Analysis.center(gate).name()));
                var corners = box != null ? outline(box) : List.of(List.of(-0.023, -0.0155), List.of(0.023, -0.0155), List.of(0.023, 0.0155), List.of(-0.023, 0.0155));
                if (box != null) {
                    ++lit;
                    assertEquals(List.of(at[0], at[1], 0.0), numbers(box.get("at")), "gate " + gate);
                    assertEquals("matte", box.get("look"), "gate " + gate);
                    assertEquals(0.046, corners.stream().mapToDouble(point -> point.get(0)).max().orElseThrow() - corners.stream().mapToDouble(point -> point.get(0)).min().orElseThrow(), 1e-9, "gate " + gate);
                    assertEquals(0.031, corners.stream().mapToDouble(point -> point.get(1)).max().orElseThrow() - corners.stream().mapToDouble(point -> point.get(1)).min().orElseThrow(), 1e-9, "gate " + gate);
                    assertEquals(20, corners.size(), "gate " + gate);
                    // all of it on the face of its center, so nothing of it reaches over the edge
                    for (var corner : corners) {
                        assertTrue(inside(center, at[0] + corner.get(0), at[1] + corner.get(1)), "gate " + gate + " " + corner);
                    }
                }
                for (var other = gate + 1; other <= 64; ++other) {
                    var place = WheelVolume.Body.GATE_PLACES.get(other);
                    // the drawing has some boxes a little in each other, along the slanted sides of a center
                    assertTrue(Math.abs(place[0] - at[0]) >= 0.046 + 0.001 || Math.abs(place[1] - at[1]) >= 0.031 + 0.001, gate + " and " + other);
                }
            }
            assertTrue(lit > 20, String.valueOf(lit));

            // a channel is two halves that meet, each in the colors of who has its gate, in
            // turn when both have it, as finely as the drawing stripes it
            var banded = 0;
            for (var channel : Analysis.allChannels()) {
                if (Set.of(10, 20, 34, 57).containsAll(List.of(channel.gate1(), channel.gate2()))) {
                    continue;
                }
                var id = "channel." + channel.gate1() + "-" + channel.gate2() + ".";
                for (var gate : List.of(channel.gate1(), channel.gate2())) {
                    var colors = colorsAlong(shapes, id + gate);
                    var expected = new ArrayList<String>();
                    if (personality.contains(gate)) {
                        expected.add(PERSONALITY);
                    }
                    if (design.contains(gate)) {
                        expected.add(DESIGN);
                    }
                    if (expected.isEmpty()) {
                        expected.add("#ffffff");
                    }
                    assertEquals(expected, colors.stream().distinct().toList(), id + gate);
                    var first = shapes.containsKey(id + gate) ? shapes.get(id + gate) : shapes.get(id + gate + ".0");
                    if (expected.size() == 2) {
                        ++banded;
                        for (var band = 0; band < colors.size(); ++band) {
                            assertEquals(expected.get(band % 2), colors.get(band), id + gate);
                        }
                        var length = between(numbers(first.get("from")), numbers(first.get("to")));
                        assertEquals(0.0075, length, 0.002, id + gate);
                    }
                    var at = WheelVolume.Body.GATE_PLACES.get(gate);
                    assertEquals(List.of(at[0], at[1]), numbers(first.get("from")).subList(0, 2), id + gate);
                    assertTrue(first.getString("part").startsWith("channel." + Math.min(channel.gate1(), channel.gate2()) + "-" + Math.max(channel.gate1(), channel.gate2())), id + gate);
                }
            }
            assertTrue(banded > 0, "a gate that both the personality and the design have");

            // The wheel is around it, tipped. A gate has its number lying on it, so the number
            // is on its own gate from wherever it's looked at, with its top away from whoever
            // looks. One that's lit is higher, and has a thin segment of the circle inside it,
            // towards the body graph, as wide as the gate where it starts.
            for (var i = 0; i < 64; ++i) {
                var gate = gatesofhumanity.models.Entities.GATES_ORDER.get(i);
                var slab = shapes.get("wheel." + gate);
                assertEquals(ON_THE_WHEEL, numbers(slab.get("turned")));
                assertEquals(30, outline(slab).size(), "gate " + gate);
                var number = shapes.get("wheel." + gate + ".number");
                var depth = ((Number) slab.get("depth")).doubleValue();
                assertEquals(String.valueOf(gate), number.getString("text"));
                assertNull(number.get("faces"), "gate " + gate);
                assertEquals(ON_THE_WHEEL, numbers(number.get("turned")), "gate " + gate);
                assertEquals(0.042, ((Number) number.get("size")).doubleValue(), "gate " + gate);
                var middle = -91.75 - 5.625 * (i + 0.5);
                assertPlace(WheelVolume.over(middle, 0.77, depth + 0.002), number.get("at"), "gate " + gate);
                var on = WheelVolume.around(middle, 0.77);
                assertTrue(inside(outline(slab), on[0], on[1]), "gate " + gate);
                var ray = shapes.get("wheel." + gate + ".ray");
                if (ray != null) {
                    assertEquals("matte", ray.get("look"), "gate " + gate);
                    assertEquals(ON_THE_WHEEL, numbers(ray.get("turned")));
                    assertEquals(0.006, ((Number) ray.get("depth")).doubleValue(), "gate " + gate);
                    var reach = outline(ray).stream().mapToDouble(point -> Math.hypot(point.get(0), point.get(1))).toArray();
                    assertEquals(0.685, java.util.Arrays.stream(reach).max().orElseThrow(), 1e-3, "gate " + gate);
                    assertEquals(0.5, java.util.Arrays.stream(reach).min().orElseThrow(), 2e-3, "gate " + gate);
                    var angles = outline(ray).stream().mapToDouble(point -> Math.toDegrees(Math.atan2(point.get(1), point.get(0)))).toArray();
                    var tile = outline(slab).stream().mapToDouble(point -> Math.toDegrees(Math.atan2(point.get(1), point.get(0)))).toArray();
                    assertEquals(java.util.Arrays.stream(tile).min().orElseThrow(), java.util.Arrays.stream(angles).min().orElseThrow(), 0.05, "gate " + gate);
                    assertEquals(java.util.Arrays.stream(tile).max().orElseThrow(), java.util.Arrays.stream(angles).max().orElseThrow(), 0.05, "gate " + gate);
                }
            }
            assertEquals(20, outline(shapes.get("wheel.band.0")).size());
            // nothing of the body graph is where the segments are
            for (var center : Center.values()) {
                for (var corner : outline(shapes.get("center." + center.name() + ".edge"))) {
                    var over = corner.get(1) * Math.cos(Math.toRadians(WheelVolume.TIPPED));
                    assertTrue(fromAxis(List.of(corner.get(0), corner.get(1), 0.0)) < 0.47 || Math.abs(over) > 0.1, center.name() + " " + corner);
                }
            }
            // the signs lie on their ring as the numbers do on their gates
            for (var sign = 0; sign < 12; ++sign) {
                var slab = shapes.get("wheel.sign." + sign);
                assertEquals("wheel.zodiac", slab.getString("part"));
                var name = shapes.get("wheel.sign." + sign + ".name");
                assertEquals(String.valueOf((char) ('\u2648' + sign)) + '\ufe0e', name.getString("text"));
                assertNull(name.get("faces"));
                assertEquals(ON_THE_WHEEL, numbers(name.get("turned")));
                assertPlace(WheelVolume.over(30.0 * sign + 15.0, 0.9625, 0.014), name.get("at"), "sign " + sign);
            }

            // Every planet of the personality is over the band beside its gate of the wheel,
            // held by a stem and named by its sign, which faces whoever looks. Those that are
            // close together stand apart. A line that isn't there until it's chosen goes to
            // its gate in the body graph from where the segment of its gate ends.
            assertApart(shapes);
            var positions = birth.personality().positions();
            assertEquals(14, positions.size());
            var shown = WheelVolume.spread(positions.stream().map(Position::longitude).toList(), Math.toDegrees(0.07 / 0.885));
            for (var position : positions) {
                var id = "planet.1." + position.planet().name();
                var ball = shapes.get(id);
                assertPlace(WheelVolume.over(shown.get(positions.indexOf(position)), 0.885, 0.11), ball.get("at"), id);
                assertEquals(0.03, ((Number) ball.get("radius")).doubleValue(), id);
                var name = shapes.get(id + ".name");
                assertPlace(WheelVolume.over(shown.get(positions.indexOf(position)), 0.885, 0.18), name.get("at"), id);
                assertEquals(WheelVolume.glyph(position.planet()), name.getString("text"));
                assertEquals(true, name.get("faces"), id);
                var beside = WheelVolume.around(position.longitude(), 0.77);
                assertTrue(inside(outline(shapes.get("wheel." + position.gateNumber())), beside[0], beside[1]), id);
                var stem = shapes.get(id + ".stem");
                assertPlace(WheelVolume.over(position.longitude(), 0.885, 0.0), stem.get("from"), id);
                assertEquals(numbers(ball.get("at")), numbers(stem.get("to")), id);
                var line = shapes.get(id + ".line");
                if (position.planet() != Planet.CHIRON) {
                    assertPlace(WheelVolume.over(position.longitude(), 0.5, -0.01), line.get("from"), id);
                    assertEquals(true, line.get("quiet"), id);
                    assertEquals(ball.getString("part"), line.getString("part"), id);
                    assertTrue(List.of(shapes.get("wheel." + position.gateNumber()).getString("part").split(" ")).contains(ball.getString("part").split(" ")[0]), id);
                    assertEquals(0.005, ((Number) line.get("radius")).doubleValue(), id);
                    // through nothing of the wheel, and through none of its segments
                    var to = numbers(line.get("to"));
                    assertTrue(fromAxis(to) < 0.6, id);
                    assertTrue(fromAxis(to) < 0.47 || Math.abs(to.get(1) * Math.cos(Math.toRadians(WheelVolume.TIPPED))) > 0.1, id);
                }
            }
            // the design isn't on the wheel of the drawing, so it isn't here
            assertTrue(shapes.keySet().stream().noneMatch(id -> id.startsWith("planet.2.")));
            assertEquals(13L, shapes.values().stream().filter(shape -> shape.get("quiet") != null).count());
        }
    }

    @Test
    void aPlanetIsReadFromTheDrawingHoweverItsPlaceIsWritten() {
        // a template writes a number under a thousandth with an exponent
        var planets = WheelVolume.Drawing.planets("""
            <g data-explore="activation.1.SUN wheel.planets" transform="rotate(4.999999999881766E-4 480 480) translate(480 59)">
            <g data-explore="activation.1.MOON wheel.planets" transform="rotate(123.456 480 480) translate(480 62)">
            <g data-explore="activation.1.NORTH_NODE wheel.planets" transform="rotate(-0.5 480 480) translate(480 62)">
            """);
        assertEquals(List.of(Planet.SUN, Planet.MOON, Planet.NORTH_NODE), planets.stream().map(WheelVolume.Drawing.OnWheel::planet).toList());
        assertEquals(-91.7505, planets.get(0).longitude(), 1e-9);
        assertEquals(-215.206, planets.get(1).longitude(), 1e-9);
        assertEquals(-91.25, planets.get(2).longitude(), 1e-9);
        assertEquals("activation.1.SUN wheel.planets", planets.get(0).part());
    }

    @Test
    void thePlanetBetweenTwoGatesHasItsLineToTheGateThatTheDrawingNames() {
        // the Sun at 302 degrees, where gate 41 starts and gate 60 ends
        var drawn = TemplateFactory.SVG.get("wheel");
        drawn.setValue("planets", "<g data-explore=\"activation.1.SUN wheel.planets\" transform=\"rotate(326.25 480 480) translate(480 59)\">");
        drawn.setValue("sun-gate1", "41.1");
        drawn.setValue("channel-on-color", "rgb(0,0,0)");
        var shapes = new JsonArray();
        WheelVolume.planets(shapes, new WheelVolume.Drawing(drawn));
        var line = shapes.stream().map(JsonObject.class::cast).filter(shape -> "planet.1.SUN.line".equals(shape.getString("id"))).findFirst().orElseThrow();
        assertEquals("body.41", String.valueOf(((JsonArray) line.get("with")).get(0)));
    }

    @Test
    void whatTheDrawingSaysIsReadFromItsStyles() {
        assertEquals("#202052", WheelVolume.Drawing.color("rgb(32,32,82)"));
        assertEquals("#be0a00", WheelVolume.Drawing.color("rgb(190, 10, 0)"));
        assertEquals("#ff5b3b", WheelVolume.Drawing.color("#FF5B3B"));
        assertEquals("#aabbcc", WheelVolume.Drawing.color("#abc"));
        assertEquals("#ffffff", WheelVolume.Drawing.color("white"));
        assertEquals("#000000", WheelVolume.Drawing.color("black"));
        // a color that isn't read stops the chart rather than showing another one
        assertThrows(IllegalStateException.class, () -> WheelVolume.Drawing.color("url(#stripes)"));
        // half as strong over white, and as it is at its full strength
        assertEquals("#fff080", WheelVolume.Drawing.washed("#ffe000", 0.5));
        assertEquals("#ffe000", WheelVolume.Drawing.washed("#ffe000", 1.0));
        assertEquals("#ffffff", WheelVolume.Drawing.washed("#ffe000", 0.0));
    }

    @Test
    void theTransitsKeepTheirShapesFromOneMomentToTheNext() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var now = shapes(m, "/202611171200/2643743");
            var later = shapes(m, "/202611181200/2643743");
            // only what a channel is banded with, the boxes of the gates and the segments of the gates that are lit come and go
            var stays = (java.util.function.Function<Map<String, JsonObject>, Set<String>>) shapes ->
                new HashSet<>(shapes.keySet().stream().filter(id -> !id.startsWith("channel.") && !id.matches("body\\.\\d+") && !id.matches("wheel\\.\\d+\\.ray")).toList());
            assertEquals(stays.apply(now), stays.apply(later));
            // a day later the Moon is elsewhere
            assertNotEquals(numbers(now.get("planet.1.MOON.stem").get("from")), numbers(later.get("planet.1.MOON.stem").get("from")));
            var sun = Charts.position(Planet.SUN, ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, ZoneOffset.UTC).toInstant());
            assertPlace(WheelVolume.over(sun.longitude(), 0.885, 0.0), now.get("planet.1.SUN.stem").get("from"), "the Sun");
        }
    }

    @Test
    void planetsThatAreCloseTogetherStandApartAroundWhereTheyWereAndTheOthersStay() {
        // in the order that they were given, whatever their order around the wheel
        assertEquals(List.of(200.0, 10.0, 100.0), WheelVolume.spread(List.of(200.0, 10.0, 100.0), 5.0));
        // two that are a degree apart: each two degrees further from the other
        var pair = WheelVolume.spread(List.of(100.0, 101.0, 250.0), 5.0);
        assertEquals(98.0, pair.get(0), 1e-6);
        assertEquals(103.0, pair.get(1), 1e-6);
        assertEquals(250.0, pair.get(2));
        // three together: the middle one stays, and one that's pushed pushes its neighbor on
        var three = WheelVolume.spread(List.of(50.0, 50.0, 50.0, 57.0), 5.0);
        var sorted = three.stream().sorted().toList();
        for (var i = 1; i < sorted.size(); ++i) {
            assertTrue(sorted.get(i) - sorted.get(i - 1) >= 5.0 - 1e-6, sorted.toString());
        }
        assertTrue(sorted.get(0) > 43.0 && sorted.get(3) < 62.0, sorted.toString());
        // across where the circle starts
        var across = WheelVolume.spread(List.of(359.0, 1.0), 6.0);
        assertEquals(357.0, across.get(0), 1e-6);
        assertEquals(3.0, across.get(1), 1e-6);
        assertEquals(List.of(42.0), WheelVolume.spread(List.of(42.0), 5.0));
        assertEquals(List.of(), WheelVolume.spread(List.of(), 5.0));
    }

    @Test
    void anOutlineIsGrownByMovingEachOfItsSidesOutward() {
        var square = List.of(new double[]{0.0, 0.0}, new double[]{1.0, 0.0}, new double[]{1.0, 1.0}, new double[]{0.0, 1.0});
        var grown = WheelVolume.grown(square, 0.1);
        assertArrayEquals(new double[]{-0.1, -0.1}, grown.get(0), 1e-9);
        assertArrayEquals(new double[]{1.1, -0.1}, grown.get(1), 1e-9);
        assertArrayEquals(new double[]{1.1, 1.1}, grown.get(2), 1e-9);
        assertArrayEquals(new double[]{-0.1, 1.1}, grown.get(3), 1e-9);
        // whichever way it goes round
        grown = WheelVolume.grown(List.of(square.get(3), square.get(2), square.get(1), square.get(0)), 0.1);
        assertArrayEquals(new double[]{-0.1, 1.1}, grown.get(0), 1e-9);
        assertArrayEquals(new double[]{1.1, -0.1}, grown.get(2), 1e-9);
        // a point that's sharper is moved further, along the line that halves it
        grown = WheelVolume.grown(List.of(new double[]{0.0, 0.0}, new double[]{2.0, 0.0}, new double[]{1.0, Math.sqrt(3.0)}), 0.1);
        assertArrayEquals(new double[]{1.0, Math.sqrt(3.0) + 0.2}, grown.get(2), 1e-9);
    }

    @Test
    void aCornerIsRoundedOffWithACurveThatStaysOnItsSidesAndWithinAThirdOfTheShorterOne() {
        var square = List.of(new double[]{0.0, 0.0}, new double[]{1.0, 0.0}, new double[]{1.0, 1.0}, new double[]{0.0, 1.0});
        assertSame(square.get(2), WheelVolume.softened(square, 0.0).get(2));
        assertEquals(4, WheelVolume.softened(square, 0.0).size());

        var soft = WheelVolume.softened(square, 0.1);
        assertEquals(20, soft.size());
        // the corner at the start: from a tenth along the side before it to a tenth along the next
        assertArrayEquals(new double[]{0.0, 0.1}, soft.get(0), 1e-9);
        assertArrayEquals(new double[]{0.1, 0.0}, soft.get(4), 1e-9);
        // halfway, a quarter of the way out to the corner from between the two
        assertArrayEquals(new double[]{0.025, 0.025}, soft.get(2), 1e-9);
        // and so round the square, in its order
        assertArrayEquals(new double[]{0.9, 0.0}, soft.get(5), 1e-9);
        assertArrayEquals(new double[]{1.0, 0.1}, soft.get(9), 1e-9);

        // a short side gives up a third of itself to each of its corners, and keeps the third between
        var sliver = List.of(new double[]{0.0, 0.0}, new double[]{3.0, 0.0}, new double[]{3.0, 0.03}, new double[]{0.0, 0.03});
        soft = WheelVolume.softened(sliver, 0.1);
        assertArrayEquals(new double[]{0.0, 0.01}, soft.get(0), 1e-9);
        assertArrayEquals(new double[]{0.01, 0.0}, soft.get(4), 1e-9);
        assertArrayEquals(new double[]{0.0, 0.02}, soft.get(19), 1e-9);
    }

    @Test
    void theLineThatTheIntegrationChannelsShareIsLitOnceAGateAtEachOfItsEndsIs() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var shapes = shapes(m, BIRTH);
            var all = Analysis.gates(Charts.birth(BORN));
            var lit = (all.contains(10) || all.contains(20)) && (all.contains(34) || all.contains(57));
            var shared = colorsAlong(shapes, "channel.integration.shared");
            assertEquals(lit, !shared.contains("#ffffff"), shared.toString());
            // each of the four gates has its own stretch up to the line, which begins where the
            // stretches of 10 and 20 end and ends where the ones of 34 and 57 do
            var line = shapes.containsKey("channel.integration.shared") ? List.of(shapes.get("channel.integration.shared")) :
                List.of(shapes.get("channel.integration.shared.0"));
            var upper = numbers(line.getFirst().get("from"));
            for (var gate : List.of(10, 20, 34, 57)) {
                var id = "channel.integration." + gate;
                var bands = colorsAlong(shapes, id).size();
                var last = bands == 1 && shapes.containsKey(id) ? shapes.get(id) : shapes.get(id + "." + (bands - 1));
                var end = numbers(last.get("to"));
                if (gate == 10 || gate == 20) {
                    assertEquals(upper, end, id);
                } else {
                    assertNotEquals(upper, end, id);
                    assertTrue(end.get(1) < upper.get(1), id);
                }
            }
        }
    }

    // noon in London on the first day from one on when the planets are in gates as asked
    private static ZonedDateTime dayWith(Predicate<Set<Integer>> gates) {
        for (var day = 0; day < 3000; ++day) {
            var at = ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, ZoneId.of("Europe/London")).plusDays(day);
            if (gates.test(Analysis.gates(Charts.transit(at.withZoneSameInstant(ZoneOffset.UTC))))) {
                return at;
            }
        }
        throw new AssertionError("no such day in eight years");
    }

    @Test
    void theSharedLineStaysOffWhileOnlyOneOfItsEndsHasAGate() {
        Predicate<Set<Integer>> upper = gates -> gates.contains(10) || gates.contains(20);
        Predicate<Set<Integer>> lower = gates -> gates.contains(34) || gates.contains(57);
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var at : List.of(dayWith(upper.and(lower.negate())), dayWith(lower.and(upper.negate())))) {
                var shapes = shapes(m, "/" + HdImage.MOMENT_FORMAT.format(at) + "/2643743");
                assertEquals(List.of("#ffffff"), colorsAlong(shapes, "channel.integration.shared"), at.toString());
            }
            var shapes = shapes(m, "/" + HdImage.MOMENT_FORMAT.format(dayWith(upper.and(lower))) + "/2643743");
            assertEquals(List.of(PERSONALITY), colorsAlong(shapes, "channel.integration.shared"));
        }
    }

    @Test
    void theWheelGoesRoundAsTheDrawingDoesTippedTowardsWhoeverLooks() {
        // the top of the drawing, where gate 10 ends and gate 11 begins
        var top = WheelVolume.around(268.25, 1.0);
        assertEquals(0.0, top[0], 1e-9);
        assertEquals(1.0, top[1], 1e-9);
        // a quarter further in longitude is to the left, as on the drawing
        var left = WheelVolume.around(358.25, 1.0);
        assertEquals(-1.0, left[0], 1e-9);
        assertEquals(0.0, left[1], 1e-9);

        // in the room the top of the drawing is away and higher, its bottom near and lower
        var tipped = Math.toRadians(28.0);
        assertArrayEquals(new double[]{0.0, Math.sin(tipped), -Math.cos(tipped)}, WheelVolume.over(268.25, 1.0, 0.0), 1e-9);
        assertArrayEquals(new double[]{0.0, -Math.sin(tipped), Math.cos(tipped)}, WheelVolume.over(88.25, 1.0, 0.0), 1e-9);
        assertArrayEquals(new double[]{-1.0, 0.0, 0.0}, WheelVolume.over(358.25, 1.0, 0.0), 1e-9);
        // what's over the wheel is over its face, which looks up and towards whoever looks
        assertArrayEquals(new double[]{-1.0, 0.5 * Math.cos(tipped), 0.5 * Math.sin(tipped)}, WheelVolume.over(358.25, 1.0, 0.5), 1e-9);
    }
}

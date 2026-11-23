package gatesofhumanity.elements;

import gatesofhumanity.models.ActivatedGates;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.TestPositions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntegrationChannelsTest {
    private static final ActivatedGates NONE = new ActivatedGates();

    private static ActivatedGates gates(int... numbers) {
        var result = new ActivatedGates();
        for (var number : numbers) {
            result.activateGateLine(TestPositions.at(Planet.SUN, number, 1));
        }
        return result;
    }

    private static String stroke(ActivatedGates g1, ActivatedGates g2, ActivatedGates g3, ActivatedGates g4) {
        return WheelImage.integrationMiddleStroke(WheelImage.Sides.of(1, List.of(g1, g2, g3, g4)));
    }

    private static String twoCharts(ActivatedGates g1, ActivatedGates g2, ActivatedGates g3, ActivatedGates g4) {
        return WheelImage.integrationMiddleStroke(WheelImage.Sides.of(2, List.of(g1, g2, g3, g4)));
    }

    private static final Pattern CORNER = Pattern.compile("(-?[\\d.]+),(-?[\\d.]+)");

    private static List<List<Double>> corners(Document graph, String explore) {
        var paths = graph.select("path[data-explore='" + explore + "']");
        assertEquals(1, paths.size(), explore);
        var corners = new ArrayList<List<Double>>();
        var written = CORNER.matcher(paths.getFirst().attr("d"));
        while (written.find()) {
            corners.add(List.of(Double.parseDouble(written.group(1)), Double.parseDouble(written.group(2))));
        }
        return corners;
    }

    @Test
    void theSharedPieceEndsUnderTheCornerOfGates10And20() throws IOException {
        // drawn later than both, it cut the line of gate 20 off from the one of gate 10 when it reached into their corner
        try (var template = getClass().getResourceAsStream("/templates/bodygraph.svg")) {
            var graph = Jsoup.parse(new String(template.readAllBytes(), StandardCharsets.UTF_8), Parser.xmlParser());
            var ten = corners(graph, "channel.10-20 channel.10-34 channel.10-57 channels");
            var twenty = corners(graph, "channel.10-20 channel.20-34 channel.20-57 channels");
            var shared = corners(graph, "channel.10-34 channel.10-57 channel.20-34 channel.20-57 channels");
            var under = ten.stream().mapToDouble(List::getLast).max().orElseThrow();
            assertEquals(under, shared.stream().mapToDouble(List::getLast).min().orElseThrow());
            var top = shared.stream().filter(corner -> corner.getLast() == under).distinct().toList();
            assertEquals(2, top.size());
            assertEquals(top, twenty.stream().filter(corner -> corner.getLast() == under).distinct().toList());
        }
    }

    @Test
    void channelsCrossingTheMiddle() {
        assertEquals("hd-channel-on-stroke", stroke(gates(10, 34), NONE, NONE, NONE));
        assertEquals("hd-channel-on-stroke", stroke(gates(10, 57), NONE, NONE, NONE));
        assertEquals("hd-channel-on-stroke", stroke(gates(20, 34), NONE, NONE, NONE));
        assertEquals("hd-channel-design-stroke", stroke(NONE, gates(20, 57), NONE, NONE));
        assertEquals("hd-channel-both-stroke", stroke(gates(10), gates(34), NONE, NONE));
        assertEquals("hd-channel-both-stroke", stroke(gates(10, 20), gates(34, 57), NONE, NONE));
    }

    @Test
    void channelsAndGatesNotCrossingTheMiddle() {
        assertEquals("hd-channel-off-stroke", stroke(gates(10, 20), NONE, NONE, NONE));
        assertEquals("hd-channel-off-stroke", stroke(NONE, gates(34, 57), NONE, NONE));
        assertEquals("hd-channel-off-stroke", stroke(gates(20), NONE, NONE, NONE));
        assertEquals("hd-channel-off-stroke", stroke(gates(57), NONE, NONE, NONE));
        assertEquals("hd-channel-off-stroke", stroke(NONE, NONE, NONE, NONE));
    }

    @Test
    void birthTransitCountsTheBirthPersonality() {
        assertEquals("hd-channel-design-stroke", twoCharts(NONE, NONE, gates(10, 34), NONE));
        assertEquals("hd-channel-both-stroke", twoCharts(gates(20), NONE, gates(57), NONE));
        assertEquals("hd-channel-design-stroke", twoCharts(NONE, NONE, gates(20), gates(57)));
    }

    @Test
    void connectionsCountTheFirstDesignForTheFirstPerson() {
        assertEquals("hd-channel-on-stroke", twoCharts(NONE, gates(10, 34), NONE, NONE));
        assertEquals("hd-channel-on-stroke", twoCharts(gates(10), gates(34), NONE, NONE));
        assertEquals("hd-channel-both-stroke", twoCharts(NONE, gates(10), gates(34), NONE));
    }

    // the transits, then the Personality and Design of each person of a connection
    private static WheelImage.Sides threeCharts(ActivatedGates transits, ActivatedGates first, ActivatedGates second) {
        return WheelImage.Sides.of(3, List.of(transits, NONE, NONE, first, second, NONE));
    }

    @Test
    void connectionTransitsStripeTheGatesOfEachOfTheThree() {
        var sides = threeCharts(gates(1, 4, 5, 7), gates(2, 4, 6, 7), gates(3, 5, 6, 7));
        assertEquals(List.of("on", "design", "third", "both", "on-third", "design-third", "all"),
            List.of(1, 2, 3, 4, 5, 6, 7).stream().map(gate -> sides.style(List.of(gate))).toList());
        assertEquals(null, sides.style(List.of(8)));
        assertEquals("hd-channel-design-third-stroke", WheelImage.integrationMiddleStroke(threeCharts(NONE, gates(10), gates(57))));
        assertEquals("hd-channel-all-stroke", WheelImage.integrationMiddleStroke(threeCharts(gates(20), gates(10), gates(57))));
    }
}

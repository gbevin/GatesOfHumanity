package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static gatesofhumanity.models.TestPositions.at;

import static org.junit.jupiter.api.Assertions.*;

public class GateTest {
    private static String activation(double longitude) {
        var p = new Position(Planet.SUN, longitude);
        return p.gateNumber() + "." + p.gateLine();
    }

    private static double degrees(int degrees, int minutes, int seconds) {
        return degrees + minutes / 60.0 + seconds / 3600.0;
    }

    @Test
    void gatesAndLinesIncludeTheirStart() {
        assertEquals("41.1", activation(302.0));
        assertEquals("60.6", activation(Math.nextDown(302.0)));
        assertEquals("41.2", activation(302.0 + 360.0 / 384.0));
        assertEquals("41.6", activation(302.0 + 360.0 / 64.0 - 1e-9));
        assertEquals("19.1", activation(302.0 + 360.0 / 64.0));
    }

    // line ranges from The Complete Rave I'Ching, in tropical longitude
    @Test
    void lineRangesMatchTheRaveIChing() {
        assertEquals("25.3", activation(degrees(0, 7, 30)));
        assertEquals("25.3", activation(degrees(1, 3, 44)));
        assertEquals("25.4", activation(degrees(1, 3, 45)));
        assertEquals("25.4", activation(degrees(1, 59, 59)));
        assertEquals("25.2", activation(0.0));
        assertEquals("25.2", activation(359.999));
        // Sagittarius 25°26'15" to 26°22'30" is 11.4
        assertEquals("11.4", activation(240.0 + degrees(25, 26, 15)));
        assertEquals("11.4", activation(240.0 + degrees(26, 22, 29)));
        assertEquals("11.5", activation(240.0 + degrees(26, 22, 30)));
    }

    @Test
    void everyGateHasSixLinesInWheelOrder() {
        for (var line = 0; line < 384; ++line) {
            var p = new Position(Planet.SUN, 302.0 + (line + 0.5) * Position.LINE_DEGREES);
            assertEquals(line % 6 + 1, p.gateLine());
            assertEquals(Entities.GATES_ORDER.get(Math.floorMod(57 - line / 6, 64)), p.gateNumber());
            // the wheel draws the gates in that order, each over 5.625 degrees, and the planet in its own
            assertEquals(Entities.GATES_ORDER.indexOf(p.gateNumber()), (int) (p.degree() / Position.GATE_DEGREES));
        }
    }

    private static ActivatedLine fixedLine(Planet planet, double longitude) {
        var activated = new ActivatedGates();
        var position = new Position(planet, longitude);
        activated.activateGateLine(position);
        activated.fixResonatingPlanets();
        return activated.activatedGateLines(position.gateNumber()).allActivatedLines().iterator().next();
    }

    @Test
    void secondaryExaltationsFixTheirLine() {
        var venus_in_11_4 = fixedLine(Planet.VENUS, 240.0 + degrees(26, 0, 0));
        assertTrue(venus_in_11_4.isExalted());
        assertFalse(venus_in_11_4.isInDetriment());
        assertTrue(fixedLine(Planet.MOON, 240.0 + degrees(26, 0, 0)).isExalted());
        assertTrue(fixedLine(Planet.SUN, 240.0 + degrees(26, 0, 0)).isInDetriment());

        assertTrue(fixedLine(Planet.JUPITER, degrees(1, 30, 0)).isExalted());
        assertTrue(fixedLine(Planet.VENUS, degrees(1, 30, 0)).isExalted());
        assertFalse(fixedLine(Planet.MARS, degrees(1, 30, 0)).isExalted());
    }

    @Test
    void noneNeverFixesALine() {
        // a line without an exaltation or a detriment holds NONE in their place
        var none = new Dignity(Planet.NONE, Planet.NONE);
        assertFalse(none.isDetrimentBy(Planet.NONE));
        assertFalse(none.isExaltedBy(Planet.NONE));
        assertTrue(new Dignity(Planet.VENUS, Planet.NONE).isExaltedBy(Planet.VENUS));
    }

    private static ActivatedLine line(ActivatedGates gates, int gate, int line, Planet planet) {
        return gates.activatedGateLines(gate).allActivatedLines().stream()
            .filter(l -> l.planets().contains(planet)).findFirst().orElseThrow();
    }

    // the examples of "Line Fixing in Human Design" by Ryan Hanna, Jovian Archive, June 2026
    @Test
    void aPlanetFixesOtherLinesOfItsGate() {
        var gates = new ActivatedGates();
        gates.activateGateLine(at(Planet.MARS, 16, 1));
        gates.activateGateLine(at(Planet.SUN, 16, 3));
        gates.activateGateLine(at(Planet.MOON, 16, 4));
        gates.fixResonatingPlanets();
        assertTrue(line(gates, 16, 3, Planet.SUN).isInDetriment());
        assertTrue(line(gates, 16, 4, Planet.MOON).isInDetriment());
        assertFalse(line(gates, 16, 1, Planet.MARS).isInDetriment());
    }

    @Test
    void aPlanetFixesItsOwnLine() {
        var gates = new ActivatedGates();
        gates.activateGateLine(at(Planet.PLUTO, 43, 2));
        gates.fixResonatingPlanets();
        assertTrue(line(gates, 43, 2, Planet.PLUTO).isExalted());
    }

    @Test
    void personalityFixesDesignThroughTheHarmonicGate() {
        var personality = new ActivatedGates();
        personality.activateGateLine(at(Planet.VENUS, 39, 5));
        personality.fixResonatingPlanets();
        var design = new ActivatedGates();
        design.activateGateLine(at(Planet.SUN, 55, 2));
        design.fixResonatingPlanets();
        assertFalse(line(design, 55, 2, Planet.SUN).isExalted());
        personality.resonateWithinComposite(design, false);
        assertTrue(line(design, 55, 2, Planet.SUN).isExalted());
    }

    private static int hexagram(int gate) {
        var lines = Entities.GATES.get(gate).iching();
        return lines.one() | lines.two() << 1 | lines.three() << 2 | lines.four() << 3 | lines.five() << 4 | lines.six() << 5;
    }

    // on the Rave Mandala, every hexagram faces its complement, the one with every line changed
    @Test
    void oppositeGatesHaveComplementaryHexagrams() {
        var hexagrams = new HashSet<Integer>();
        for (var i = 0; i < 64; ++i) {
            var gate = Entities.GATES_ORDER.get(i);
            var opposite = Entities.GATES_ORDER.get((i + 32) % 64);
            assertEquals(0b111111, hexagram(gate) ^ hexagram(opposite), gate + " and " + opposite);
            hexagrams.add(hexagram(gate));
        }
        assertEquals(64, hexagrams.size());
    }

    private static Set<Planet> planets(String names) {
        return Arrays.stream(names.split(",")).filter(name -> !name.isEmpty()).map(name -> Planet.valueOf(name.toUpperCase(Locale.ROOT))).collect(Collectors.toSet());
    }

    // generated from The Complete Rave I'Ching by Tools/generate-dignities.py
    @Test
    void dignitiesMatchTheRaveIChing() {
        var rows = TsvResource.rows("dignities.tsv");
        assertEquals(384, rows.size());
        for (var columns : rows) {
            var row = String.join("\t", columns);
            var dignity = Entities.GATES.get(Integer.parseInt(columns[0])).dignities().get(Integer.parseInt(columns[1]) - 1);
            var exalted = new HashSet<>(dignity.exaltations());
            exalted.remove(Planet.NONE);
            assertEquals(planets(columns[2]), exalted, row);
            assertEquals(columns[3].isEmpty() ? Planet.NONE : Planet.valueOf(columns[3].toUpperCase(Locale.ROOT)), dignity.detriment(), row);
        }
    }
}

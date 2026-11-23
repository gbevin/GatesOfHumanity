package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Body;
import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.ephemeris.OracleData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PositionTest {
    private static final double JD_DELTA_T_PREDICTED = 2461041.5; // 2026-01-01
    private static final double GATE_DEGREES = 360.0 / 64.0;
    private static final double LINE_DEGREES = 360.0 / 384.0;
    private static final double TONE_DEGREES = LINE_DEGREES / 36.0;
    // written out, so that the boundaries are checked against Position rather than with it
    private static final double GATE_41_DEGREES = 302.0;

    private static ZonedDateTime utc(double jd) {
        var seconds = (jd - 2440587.5) * 86400.0;
        return Instant.ofEpochSecond((long) Math.floor(seconds), Math.round((seconds - Math.floor(seconds)) * 1e9)).atZone(ZoneOffset.UTC);
    }

    private static double distanceToBoundary(double longitude, double width) {
        var offset = ((longitude - Position.GATE_41_START) % width + width) % width;
        return Math.min(offset, width - offset) * 3600.0;
    }

    private static String activation(Position p) {
        return p.gateNumber() + "." + p.gateLine() + "." + p.color() + "." + p.tone();
    }

    private static final class Comparisons {
        int lines_;
        int tones_;
    }

    private static void assertSameActivations(List<Position> positions, OracleData.Row row, boolean design, double tolerance,
                                              Comparisons comparisons) {
        assertEquals(14, positions.size());
        for (var body : OracleData.COLUMNS) {
            var planet = Charts.planet(body);
            var expected = new Position(planet, design ? row.designLongitude(body) : row.longitude(body));
            var actual = positions.stream().filter(p -> p.planet() == planet).findFirst().orElseThrow();
            var label = planet + (design ? " design" : " personality") + " at JD " + row.jdUt();
            if (distanceToBoundary(expected.longitude(), LINE_DEGREES) > tolerance) {
                assertEquals(expected.gateNumber() + "." + expected.gateLine(), actual.gateNumber() + "." + actual.gateLine(), label);
                ++comparisons.lines_;
            }
            if (distanceToBoundary(expected.longitude(), TONE_DEGREES) > tolerance) {
                assertEquals(activation(expected), activation(actual), label);
                ++comparisons.tones_;
            }
        }
    }

    @Test
    void raveChartActivationsMatchSwissEphemeris() {
        var compared = 0;
        var comparisons = new Comparisons();
        for (var row : OracleData.ROWS) {
            var birth = utc(row.jdUt());
            var design = utc(row.designJdUt());
            if (design.isBefore(Ephemeris.start().atZone(ZoneOffset.UTC)) || birth.isAfter(Ephemeris.end().atZone(ZoneOffset.UTC))) {
                continue;
            }
            // no tone is ever 100" from its boundary, so the tones are only compared where Delta T is measured
            var tolerance = row.jdUt() < JD_DELTA_T_PREDICTED ? 10.0 : 100.0;
            var chart = Charts.birth(birth);
            assertSameActivations(chart.personality().positions(), row, false, tolerance, comparisons);
            assertSameActivations(chart.design().positions(), row, true, tolerance, comparisons);
            assertEquals(row.designJdUt(), chart.design().time().toInstant().getEpochSecond() / 86400.0 + 2440587.5, 10.0 / 86400.0);
            ++compared;
        }
        assertTrue(compared > 1990, "compared " + compared);
        assertTrue(comparisons.lines_ > 45000, "lines compared " + comparisons.lines_);
        assertTrue(comparisons.tones_ > 20000, "tones compared " + comparisons.tones_);
    }

    @Test
    void colorsAndTonesDivideEachLine() {
        for (var k = 0; k < 36; ++k) {
            var p = new Position(Planet.SUN, GATE_41_DEGREES + (k + 0.5) * TONE_DEGREES);
            assertEquals("41.1." + (k / 6 + 1) + "." + (k % 6 + 1), activation(p));
        }
        assertEquals("41.1.1.1", activation(new Position(Planet.SUN, GATE_41_DEGREES)));
        // a color is exactly 9.375', so its boundary is exact
        assertEquals("41.1.2.1", activation(new Position(Planet.SUN, GATE_41_DEGREES + 0.15625)));
        assertEquals("41.1.1.6", activation(new Position(Planet.SUN, Math.nextDown(GATE_41_DEGREES + 0.15625))));
        assertEquals("41.2.1.1", activation(new Position(Planet.SUN, GATE_41_DEGREES + LINE_DEGREES)));
        assertEquals("41.6.6.6", activation(new Position(Planet.SUN, Math.nextDown(GATE_41_DEGREES + GATE_DEGREES))));
        assertEquals("19.1.1.1", activation(new Position(Planet.SUN, GATE_41_DEGREES + GATE_DEGREES)));
        // the last tone before gate 41, up to the closest longitude below it
        assertEquals("60.6.6.6", activation(new Position(Planet.SUN, Math.nextDown(GATE_41_DEGREES))));
        assertEquals("60.6.6.6", activation(new Position(Planet.SUN, GATE_41_DEGREES - TONE_DEGREES / 2.0)));
    }

    @Test
    void earthAndSouthNodeAreOpposite() {
        var positions = Charts.positions(Instant.parse("1990-05-17T08:00:00Z"));
        var sun = positions.stream().filter(p -> p.planet() == Planet.SUN).findFirst().orElseThrow();
        var earth = positions.stream().filter(p -> p.planet() == Planet.EARTH).findFirst().orElseThrow();
        var north = positions.stream().filter(p -> p.planet() == Planet.NORTH_NODE).findFirst().orElseThrow();
        var south = positions.stream().filter(p -> p.planet() == Planet.SOUTH_NODE).findFirst().orElseThrow();
        assertEquals(180.0, OracleData.angularDistance(sun.longitude(), earth.longitude()), 1e-9);
        assertEquals(180.0, OracleData.angularDistance(north.longitude(), south.longitude()), 1e-9);
    }

    @Test
    void planetPositionsStepThroughTime() {
        var start = ZonedDateTime.parse("2024-03-01T10:00:00Z");
        var days = Charts.series(start, 3, Charts.Step.DAYS);
        assertEquals(3, days.size());
        assertEquals(Charts.positions(start.plusDays(2).toInstant()), days.get(2));
        var hours = Charts.series(start, 24, Charts.Step.HOURS);
        assertEquals(24, hours.size());
        assertEquals(Charts.positions(start.plusHours(23).toInstant()), hours.get(23));
    }

    @Test
    void everyBodyHasAPlanet() {
        for (var body : Body.values()) {
            assertNotEquals(Planet.NONE, Charts.planet(body));
        }
    }
}

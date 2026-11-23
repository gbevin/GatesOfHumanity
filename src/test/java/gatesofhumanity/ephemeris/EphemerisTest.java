package gatesofhumanity.ephemeris;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class EphemerisTest {
    // Delta T is measured up to now, beyond that Swiss Ephemeris and JPL Horizons predict it differently
    private static final double JD_DELTA_T_PREDICTED = 2461041.5; // 2026-01-01
    private static final double MAX_ARCSECONDS_MEASURED = 6.0;
    private static final double MAX_ARCSECONDS_PREDICTED = 90.0;
    private static final double MAX_DESIGN_SECONDS_MEASURED = 3.0;
    private static final double MAX_DESIGN_SECONDS_PREDICTED = 10.0;

    private static boolean covered(OracleData.Row row) {
        return Ephemeris.covers(Ephemeris.instant(row.designJdUt())) && Ephemeris.covers(Ephemeris.instant(row.jdUt()));
    }

    @Test
    void longitudesMatchSwissEphemeris() {
        var compared = 0;
        for (var row : OracleData.ROWS) {
            if (!covered(row)) {
                continue;
            }
            var tolerance = row.jdUt() < JD_DELTA_T_PREDICTED ? MAX_ARCSECONDS_MEASURED : MAX_ARCSECONDS_PREDICTED;
            for (var body : OracleData.COLUMNS) {
                var difference = OracleData.angularDistance(Ephemeris.longitude(body, row.jdUt()), row.longitude(body)) * 3600.0;
                assertTrue(difference <= tolerance, body + " at JD " + row.jdUt() + " differs by " + difference + "\"");
            }
            ++compared;
        }
        assertTrue(compared > 1990, "compared " + compared);
    }

    @Test
    void designMomentsMatchSwissEphemeris() {
        for (var row : OracleData.ROWS) {
            if (!covered(row)) {
                continue;
            }
            var tolerance = row.jdUt() < JD_DELTA_T_PREDICTED ? MAX_DESIGN_SECONDS_MEASURED : MAX_DESIGN_SECONDS_PREDICTED;
            var design = Ephemeris.julianDay(Ephemeris.designMoment(Ephemeris.instant(row.jdUt())));
            var difference = Math.abs(design - row.designJdUt()) * 86400.0;
            assertTrue(difference <= tolerance, "design for JD " + row.jdUt() + " differs by " + difference + "s");
        }
    }

    @Test
    void designMomentIsTheSolarArcEarlier() {
        var birth = Instant.parse("1975-06-21T14:30:00Z");
        var design = Ephemeris.designMoment(birth);
        var arc = Ephemeris.longitude(Body.SUN, birth) - Ephemeris.longitude(Body.SUN, design);
        assertEquals(Ephemeris.DESIGN_SOLAR_ARC, (arc + 360.0) % 360.0, 1e-6);
        assertTrue(design.isBefore(birth.minus(Duration.ofDays(85))) && design.isAfter(birth.minus(Duration.ofDays(95))), design.toString());
    }

    @Test
    void range() {
        assertEquals(Instant.parse("1800-01-03T00:00:00Z"), Ephemeris.start());
        assertEquals(Instant.parse("2199-12-17T00:00:00Z"), Ephemeris.end());
        assertTrue(Ephemeris.covers(Ephemeris.start()));
        assertTrue(Ephemeris.covers(Ephemeris.end()));
        assertFalse(Ephemeris.covers(Ephemeris.start().minusSeconds(1)));
        assertFalse(Ephemeris.covers(Ephemeris.end().plusSeconds(1)));
        assertDoesNotThrow(() -> Ephemeris.longitude(Body.MOON, Ephemeris.end()));
        assertThrows(IllegalArgumentException.class, () -> Ephemeris.longitude(Body.MOON, Ephemeris.start().minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> Ephemeris.longitude(Body.MOON, Ephemeris.end().plusSeconds(1)));
    }

    @Test
    void julianDayRoundTrip() {
        assertEquals(2451545.0, Ephemeris.julianDay(Instant.parse("2000-01-01T12:00:00Z")));
        var moment = Instant.parse("1987-03-04T05:06:07.250Z");
        var round_trip = Ephemeris.instant(Ephemeris.julianDay(moment));
        assertTrue(Duration.between(moment, round_trip).abs().toNanos() < 100_000, round_trip.toString());
    }

    @Test
    void theSolverFindsRootsInsideAJump() {
        // the tables jump by up to a few millionths of a degree where their segments meet
        var root = Ephemeris.solveIncreasing(t -> t < 0.4 ? t - 0.4 - 3e-6 : t - 0.4 + 3e-6, 0.0, 1.0);
        assertEquals(0.4, root, 1e-7);
        assertEquals(0.25, Ephemeris.solveIncreasing(t -> t * t - 0.0625, 0.0, 1.0), 1e-7);
        assertThrows(IllegalArgumentException.class, () -> Ephemeris.solveIncreasing(t -> t + 1.0, 0.0, 1.0));
    }

    @Test
    void designMomentsWhereTheSunTableJumps() {
        // births whose design target lands exactly where two segments of the Sun table meet
        var start = Ephemeris.julianDay(Ephemeris.start());
        for (var k = 100; k < 3600; k += 350) {
            var boundary = start + k * 40.0;
            var target = Ephemeris.longitude(Body.SUN, boundary);
            var birth = Ephemeris.solveIncreasing(t ->
                (Ephemeris.longitude(Body.SUN, t) - Ephemeris.DESIGN_SOLAR_ARC - target + 540.0) % 360.0 - 180.0,
                boundary + 85.0, boundary + 94.0);
            var design = Ephemeris.julianDay(Ephemeris.designMoment(Ephemeris.instant(birth)));
            assertEquals(boundary, design, 1.0 / 86400.0, "segment " + k);
        }
    }

    @Test
    void aPassIsNeverTheFarSideOfTheLongitude() {
        // Saturn moved backwards in July 2024, across the point opposite to the one looked for
        var moment = Instant.parse("2024-07-15T00:00:00Z");
        assertTrue(Ephemeris.longitude(Body.SATURN, moment.plus(Duration.ofDays(5))) < Ephemeris.longitude(Body.SATURN, moment));
        var opposite = Ephemeris.longitude(Body.SATURN, moment) + 180.0;
        assertTrue(Ephemeris.firstPass(Body.SATURN, opposite, moment.minus(Duration.ofDays(5)), moment.plus(Duration.ofDays(5))).isEmpty());
        // and the planet's own longitude is passed once it moves forward again, in December
        var pass = Ephemeris.firstPass(Body.SATURN, Ephemeris.longitude(Body.SATURN, moment), moment.minus(Duration.ofDays(5)), moment.plus(Duration.ofDays(365))).orElseThrow();
        assertTrue(pass.isAfter(Instant.parse("2024-11-15T00:00:00Z")) && pass.isBefore(Instant.parse("2025-03-01T00:00:00Z")), pass.toString());
    }
}

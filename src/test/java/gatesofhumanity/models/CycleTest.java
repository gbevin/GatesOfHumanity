package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Body;
import gatesofhumanity.ephemeris.Ephemeris;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CycleTest {
    private static final Map<Cycle, Body> BODIES = Map.of(Cycle.SATURN_RETURN, Body.SATURN, Cycle.URANUS_OPPOSITION, Body.URANUS, Cycle.CHIRON_RETURN, Body.CHIRON);
    private static final Map<Cycle, Double> ANGLES = Map.of(Cycle.SATURN_RETURN, 0.0, Cycle.URANUS_OPPOSITION, 180.0, Cycle.CHIRON_RETURN, 0.0);

    // from -180 to 180 degrees, ahead of the target when positive
    private static double ahead(Body body, Instant moment, double target) {
        var difference = (Ephemeris.longitude(body, moment) - target) % 360.0;
        return difference > 180.0 ? difference - 360.0 : difference <= -180.0 ? difference + 360.0 : difference;
    }

    @Test
    void steveJobs() {
        var birth = LocalDateTime.of(1955, 2, 24, 19, 15).atZone(ZoneId.of("America/Los_Angeles")).toInstant();
        assertEquals(Instant.parse("1984-11-28T21:33:00Z"), Cycle.SATURN_RETURN.moment(birth).orElseThrow().truncatedTo(ChronoUnit.MINUTES));
        assertEquals(Instant.parse("1994-02-14T08:35:00Z"), Cycle.URANUS_OPPOSITION.moment(birth).orElseThrow().truncatedTo(ChronoUnit.MINUTES));
        assertEquals(Instant.parse("2005-03-30T07:32:00Z"), Cycle.CHIRON_RETURN.moment(birth).orElseThrow().truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    void eachCycleIsTheFirstTimeThePlanetReachesItsPoint() {
        var checked = 0;
        // births spread over the whole ephemeris, every few years and at different times of the year
        for (var birth = Ephemeris.firstChart(); birth.isBefore(Ephemeris.end()); birth = birth.plus(Duration.ofDays(1500))) {
            for (var cycle : Cycle.values()) {
                var body = BODIES.get(cycle);
                var target = Ephemeris.longitude(body, birth) + ANGLES.get(cycle);
                var found = cycle.moment(birth);
                if (found.isEmpty()) {
                    // only when the ephemeris ends before the planet gets there
                    assertTrue(birth.plus(Duration.ofDays(52 * 366)).isAfter(Ephemeris.end()), cycle + " of " + birth);
                    continue;
                }
                var moment = found.get();
                assertEquals(0.0, ahead(body, moment, target), 1e-6, cycle + " of " + birth);
                assertTrue(ahead(body, moment.minus(Duration.ofDays(1)), target) < 0.0, cycle + " of " + birth);
                // the planet hasn't passed the point since it last went around, checked every ten days
                for (var day = moment.minus(Duration.ofDays(3000)); day.isBefore(moment.minus(Duration.ofDays(10))); day = day.plus(Duration.ofDays(10))) {
                    assertTrue(ahead(body, day, target) < 0.0 || ahead(body, day, target) > 90.0, cycle + " of " + birth + " passed on " + day);
                }
                ++checked;
            }
        }
        assertTrue(checked > 200, "checked " + checked);
    }
}

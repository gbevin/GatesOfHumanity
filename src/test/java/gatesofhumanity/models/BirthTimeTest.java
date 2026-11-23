package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BirthTimeTest {
    private static final Duration WITHIN = Duration.ofHours(3);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void eachPartHoldsOverItsRangeAndChangesRightOutsideOfIt() {
        for (var birth : List.of(Instant.parse("1955-02-25T03:15:00Z"), Instant.parse("1990-06-01T12:00:00Z"))) {
            var ranges = BirthTime.ranges(birth, WITHIN);
            // every minute, as the ranges are found, the charts are only made once
            var charts = new java.util.HashMap<Instant, BirthTime.Moment>();
            for (var t = birth.minus(WITHIN); !t.isAfter(birth.plus(WITHIN)); t = t.plus(MINUTE)) {
                charts.put(t, BirthTime.Moment.of(t));
            }
            for (var part : BirthTime.Part.values()) {
                var range = ranges.get(part);
                var what = part + " for " + birth + " " + range.first() + " to " + range.last();
                var key = part.key(charts.get(birth));
                for (var t = range.first(); !t.isAfter(range.last()); t = t.plus(MINUTE)) {
                    assertEquals(key, part.key(charts.get(t)), what + " at " + t);
                }
                if (range.before() == null) {
                    assertEquals(birth.minus(WITHIN), range.first(), what);
                } else {
                    assertEquals(range.first().minus(MINUTE), range.before().time(), what);
                    assertNotEquals(key, part.key(range.before()), what);
                }
                if (range.after() == null) {
                    assertEquals(birth.plus(WITHIN), range.last(), what);
                } else {
                    assertEquals(range.last().plus(MINUTE), range.after().time(), what);
                    assertNotEquals(key, part.key(range.after()), what);
                }
            }
            // the planets change line far more often than the Type
            assertNotNull(ranges.get(BirthTime.Part.ACTIVATIONS).before());
        }
    }

    @Test
    void aRangeEndsWithTheTablesOfThePlanets() {
        var birth = Ephemeris.firstChart().plus(Duration.ofMinutes(30)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var range = BirthTime.ranges(birth, WITHIN).get(BirthTime.Part.TYPE);
        assertFalse(range.first().isBefore(Ephemeris.firstChart()), range.toString());
        assertTrue(Duration.between(range.first(), birth).toMinutes() <= 30, range.toString());
    }

    @Test
    void chironChangingLineChangesNoGatesAndLines() {
        // the minute before Chiron moves into another line, found by halving a day it does
        var line = (java.util.function.Function<Instant, Integer>) t -> Charts.position(Planet.CHIRON, t).gateNumber() * 10 + Charts.position(Planet.CHIRON, t).gateLine();
        var before = Instant.parse("1990-06-01T00:00:00Z");
        var after = before;
        while (line.apply(after).equals(line.apply(before))) {
            before = after;
            after = after.plus(Duration.ofDays(1));
        }
        while (Duration.between(before, after).toMinutes() > 1) {
            var middle = before.plus(Duration.between(before, after).dividedBy(2)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            if (line.apply(middle).equals(line.apply(before))) {
                before = middle;
            } else {
                after = middle;
            }
        }
        assertNotEquals(line.apply(before), line.apply(after));
        var part = BirthTime.Part.ACTIVATIONS;
        assertEquals(part.key(BirthTime.Moment.of(before)), part.key(BirthTime.Moment.of(after)));
    }
}

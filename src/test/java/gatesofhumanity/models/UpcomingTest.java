package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class UpcomingTest {
    private static final Duration YEAR = Duration.ofDays(365);

    private static boolean completes(int gate, RaveChart birth) {
        var gates = Analysis.gates(birth);
        var defined = Analysis.definedChannels(gates);
        return Analysis.channels(gate).stream()
            .anyMatch(channel -> gates.contains(channel.gate1() == gate ? channel.gate2() : channel.gate1()) && !defined.contains(channel));
    }

    @Test
    void eachPlanetIsFoundWhenItFirstGetsToAGateThatCompletesAChannel() {
        var births = List.of(LocalDateTime.of(1955, 2, 25, 3, 15), LocalDateTime.of(1950, 8, 11, 16, 45), LocalDateTime.of(1990, 6, 1, 12, 0));
        var from = Instant.parse("2024-03-01T10:00:00Z");
        for (var moment : births) {
            var birth = Charts.birth(moment.atZone(ZoneOffset.UTC));
            for (var planet : Upcoming.planets()) {
                var found = Upcoming.next(planet, birth, from, YEAR);
                var what = planet + " for " + moment;
                found.ifPresent(completion -> {
                    // exact to the minute, at both ends
                    assertEquals(completion.gate(), Upcoming.gate(planet, completion.start()), what);
                    assertNotEquals(completion.gate(), Upcoming.gate(planet, completion.start().minus(Duration.ofMinutes(1))), what);
                    assertEquals(completion.gate(), Upcoming.gate(planet, completion.end().minus(Duration.ofMinutes(1))), what);
                    assertNotEquals(completion.gate(), Upcoming.gate(planet, completion.end()), what);
                    assertFalse(completion.channels().isEmpty(), what);
                });
                // sampled far more finely than the search steps, the planet doesn't get to such a gate
                // any earlier, not even turning back into one for a while, once it has left the gate
                // it's in at the start
                var until = found.map(Upcoming.Completion::start).orElse(from.plus(YEAR));
                var sample = Upcoming.step(planet).dividedBy(24);
                var first_gate = Upcoming.gate(planet, from);
                var left = false;
                for (var t = from; t.isBefore(until.minus(Duration.ofMinutes(1))); t = t.plus(sample)) {
                    var gate = Upcoming.gate(planet, t);
                    left |= gate != first_gate;
                    assertFalse(left && completes(gate, birth), what + " is in gate " + gate + " on " + t);
                }
                assertTrue(planet != Planet.MOON || found.isPresent(), what);
            }
        }
    }

    // exact to the minute at both ends, and in the same place all along, sampled far more finely than the search steps
    private static void assertStay(Upcoming.Stay stay, Instant moment, BiFunction<Planet, Instant, Integer> place, Planet planet, Duration sample) {
        var what = planet + " at " + moment + " " + stay;
        var value = place.apply(planet, moment);
        assertFalse(stay.start().isAfter(moment), what);
        assertTrue(stay.end().isAfter(moment), what);
        assertEquals(value, place.apply(planet, stay.start()), what);
        if (!stay.start().equals(Ephemeris.start())) {
            assertNotEquals(value, place.apply(planet, stay.start().minus(Duration.ofMinutes(1))), what);
        }
        assertEquals(value, place.apply(planet, stay.end().minus(Duration.ofMinutes(1))), what);
        if (!stay.end().equals(Ephemeris.end())) {
            assertNotEquals(value, place.apply(planet, stay.end()), what);
        }
        for (var t = stay.start(); t.isBefore(stay.end().minus(Duration.ofMinutes(1))); t = t.plus(sample)) {
            assertEquals(value, place.apply(planet, t), what + " at " + t);
        }
    }

    @Test
    void eachPlanetStaysInItsGateAndLineFromTheMinuteItGetsThereToTheMinuteItLeaves() {
        for (var moment : List.of(Instant.parse("2024-03-01T10:00:00Z"), Instant.parse("1987-11-23T04:37:00Z"))) {
            for (var planet : Upcoming.planets()) {
                assertStay(Upcoming.gateStay(planet, moment), moment, Upcoming::gate, planet, Upcoming.step(planet).dividedBy(24));
                assertStay(Upcoming.lineStay(planet, moment), moment, Upcoming::line, planet, Upcoming.step(planet).dividedBy(6 * 24));
            }
        }
    }

    @Test
    void aStayAtTheEdgeOfTheTablesEndsThere() {
        var last = Ephemeris.end().minus(Duration.ofMinutes(1));
        assertEquals(Ephemeris.end(), Upcoming.gateStay(Planet.PLUTO, last).end());
        var first = Ephemeris.start().plus(Duration.ofMinutes(1));
        assertEquals(Ephemeris.start(), Upcoming.gateStay(Planet.PLUTO, first).start());
    }
}

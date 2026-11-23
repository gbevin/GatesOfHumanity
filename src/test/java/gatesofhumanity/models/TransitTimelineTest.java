package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransitTimelineTest {
    private static final List<Planet> PLANETS = List.of(Planet.SUN, Planet.EARTH, Planet.MOON);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private static void assertPlaces(TransitTimeline.Moment moment, Instant at) {
        for (var place : moment.places()) {
            var position = Charts.position(place.planet(), at);
            assertEquals(place.gate() + "." + place.line(), position.gateNumber() + "." + position.gateLine(), place + " at " + at);
        }
    }

    @Test
    void aMomentBeginsEachTimeAPlanetMovesToAnotherLine() {
        for (var from : List.of(Instant.parse("2026-11-15T17:00:00Z"), Instant.parse("1987-11-23T04:37:21Z"))) {
            var within = Duration.ofDays(3);
            var moments = TransitTimeline.of(PLANETS, from, within);
            assertEquals(from, moments.getFirst().start());
            assertTrue(moments.getLast().start().isBefore(from.plus(within)));
            assertFalse(moments.getLast().end().isBefore(from.plus(within)), "the moments go on until the end of the time");
            for (var i = 0; i < moments.size(); ++i) {
                var moment = moments.get(i);
                assertEquals(PLANETS, moment.places().stream().map(TransitTimeline.Place::planet).toList());
                if (i > 0) {
                    assertEquals(moments.get(i - 1).end(), moment.start());
                }
                // the places are those of the whole moment, sampled far more finely than the Moon crosses a line
                for (var t = moment.start(); t.isBefore(moment.end()); t = t.plus(Duration.ofMinutes(7))) {
                    assertPlaces(moment, t);
                }
                assertPlaces(moment, moment.end().minus(MINUTE));
                for (var place : moment.places()) {
                    // exact to the minute: a planet is elsewhere at the end of its place, and not before
                    var after = Charts.position(place.planet(), place.until());
                    assertNotEquals(place.gate() + "." + place.line(), after.gateNumber() + "." + after.gateLine(), place.toString());
                    var before = Charts.position(place.planet(), place.until().minus(MINUTE));
                    assertEquals(place.gate() + "." + place.line(), before.gateNumber() + "." + before.gateLine(), place.toString());
                }
                // something changed, or this would be no new moment
                if (i > 0) {
                    var earlier = moments.get(i - 1).places().stream().map(place -> place.gate() + "." + place.line()).toList();
                    var now = moment.places().stream().map(place -> place.gate() + "." + place.line()).toList();
                    assertNotEquals(earlier, now, moment.toString());
                }
            }
            // the Moon crosses a line in less than two hours, the Sun in about a day
            assertTrue(moments.size() > 36 && moments.size() < 60, moments.size() + " moments");
        }
    }

    @Test
    void theSunAndTheEarthChangeTogether() {
        for (var moment : TransitTimeline.of(List.of(Planet.SUN, Planet.EARTH), Instant.parse("2026-11-15T17:00:00Z"), Duration.ofDays(4))) {
            assertEquals(moment.places().get(0).until(), moment.places().get(1).until());
            assertEquals(moment.places().get(0).line(), moment.places().get(1).line());
        }
    }

    @Test
    void lookingAtGatesAMomentOnlyBeginsWhenAPlanetMovesToAnotherGate() {
        var from = Instant.parse("2026-11-15T17:00:00Z");
        var planets = Upcoming.planets();
        var moments = TransitTimeline.ofGates(planets, from, Duration.ofDays(10));
        assertEquals(from, moments.getFirst().start());
        for (var i = 0; i < moments.size(); ++i) {
            var moment = moments.get(i);
            assertEquals(planets, moment.places().stream().map(TransitTimeline.Place::planet).toList());
            for (var place : moment.places()) {
                // each planet is in its gate until the minute it's in another one
                assertEquals(place.gate(), Charts.position(place.planet(), moment.start()).gateNumber(), place.toString());
                assertEquals(place.gate(), Charts.position(place.planet(), place.until().minus(MINUTE)).gateNumber(), place.toString());
                assertNotEquals(place.gate(), Charts.position(place.planet(), place.until()).gateNumber(), place.toString());
                assertEquals(Upcoming.gateStay(place.planet(), moment.start()).end(), place.until());
            }
            if (i > 0) {
                assertEquals(moments.get(i - 1).end(), moment.start());
                assertNotEquals(moments.get(i - 1).places().stream().map(TransitTimeline.Place::gate).toList(), moment.places().stream().map(TransitTimeline.Place::gate).toList());
            }
        }
        // the Moon alone crosses a gate in less than half a day, and a line of a gate is no change
        assertTrue(moments.size() > 20 && moments.size() < 40, moments.size() + " moments");
        assertEquals(TransitTimeline.atGates(planets, moments.get(3).start()), moments.get(3));
    }

    @Test
    void nothingBeginsOutsideTheTime() {
        var from = Instant.parse("2026-11-15T17:00:00Z");
        assertTrue(TransitTimeline.of(PLANETS, from, Duration.ZERO).isEmpty());
        assertEquals(1, TransitTimeline.of(PLANETS, from, MINUTE).size());
    }

    @Test
    void theMomentsEndWithTheTables() {
        var moments = TransitTimeline.of(PLANETS, Ephemeris.end().minus(Duration.ofHours(5)), Duration.ofDays(3));
        assertFalse(moments.isEmpty());
        assertEquals(Ephemeris.end(), moments.getLast().end());
        assertTrue(moments.size() < 6, moments.size() + " moments");
    }
}

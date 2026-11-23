package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a few planets are from a moment on, with a new moment each time one of them moves into
 * another line, or into another gate, which is when what's shown of them changes.
 */
public final class TransitTimeline {
    private TransitTimeline() {
    }

    /**
     * A planet in a line of a gate, until the first whole minute it's in another line, or in
     * another gate where only gates are looked at.
     */
    public record Place(Planet planet, int gate, int line, Instant until) {
    }

    /**
     * The places of the planets from a moment on, until the first of them ends.
     */
    public record Moment(Instant start, List<Place> places) {
        public Instant end() {
            return places.stream().map(Place::until).min(Instant::compareTo).orElseThrow();
        }
    }

    /**
     * The moments from one on that begin within a time, the first one at that moment itself.
     */
    public static List<Moment> of(List<Planet> planets, Instant from, Duration within) {
        return of(planets, from, within, true);
    }

    /**
     * The same with a new moment only when a planet moves into another gate.
     */
    public static List<Moment> ofGates(List<Planet> planets, Instant from, Duration within) {
        return of(planets, from, within, false);
    }

    private static List<Moment> of(List<Planet> planets, Instant from, Duration within, boolean lines) {
        var moments = new ArrayList<Moment>();
        var until = from.plus(within);
        var start = from;
        if (until.isAfter(Ephemeris.end())) {
            until = Ephemeris.end();
        }
        while (start.isBefore(until)) {
            var moment = at(planets, start, lines);
            moments.add(moment);
            start = moment.end();
        }
        return moments;
    }

    /**
     * The places of the planets at a moment.
     */
    public static Moment at(List<Planet> planets, Instant start) {
        return at(planets, start, true);
    }

    /**
     * The places of the planets at a moment, each until the planet leaves its gate.
     */
    public static Moment atGates(List<Planet> planets, Instant start) {
        return at(planets, start, false);
    }

    private static Moment at(List<Planet> planets, Instant start, boolean lines) {
        var places = new ArrayList<Place>();
        for (var planet : planets) {
            var position = Charts.position(planet, start);
            var stay = lines ? Upcoming.lineStay(planet, start) : Upcoming.gateStay(planet, start);
            places.add(new Place(planet, position.gateNumber(), position.gateLine(), stay.end()));
        }
        return new Moment(start, List.copyOf(places));
    }
}

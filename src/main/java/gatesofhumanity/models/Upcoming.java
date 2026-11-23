package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * When the planets of the transits next move into a gate that completes a channel with a birth
 * chart.
 */
public final class Upcoming {
    private Upcoming() {
    }

    /**
     * A planet in a gate from its start to its end, completing channels with the birth chart.
     */
    public record Completion(Planet planet, int gate, List<Analysis.NamedChannel> channels, Instant start, Instant end) {
    }

    /**
     * The time a planet stays in a gate or a line, from the first whole minute it's there to the
     * first whole minute it's somewhere else, the start or the end of the tables when it's still
     * there then.
     */
    public record Stay(Instant start, Instant end) {
    }

    // checked a step at a time, short enough that a planet turning back doesn't go into a gate and
    // out again between two checks, about the time each one takes at its fastest to cross half a gate
    private static final Map<Planet, Duration> STEPS = Map.ofEntries(
        Map.entry(Planet.SUN, Duration.ofDays(2)),
        Map.entry(Planet.EARTH, Duration.ofDays(2)),
        Map.entry(Planet.MOON, Duration.ofHours(3)),
        Map.entry(Planet.NORTH_NODE, Duration.ofDays(5)),
        Map.entry(Planet.SOUTH_NODE, Duration.ofDays(5)),
        Map.entry(Planet.MERCURY, Duration.ofDays(1)),
        Map.entry(Planet.VENUS, Duration.ofDays(1)),
        Map.entry(Planet.MARS, Duration.ofDays(3)),
        Map.entry(Planet.JUPITER, Duration.ofDays(7)),
        Map.entry(Planet.SATURN, Duration.ofDays(14)),
        Map.entry(Planet.URANUS, Duration.ofDays(28)),
        Map.entry(Planet.NEPTUNE, Duration.ofDays(28)),
        Map.entry(Planet.PLUTO, Duration.ofDays(28)));

    static Duration step(Planet planet) {
        return STEPS.get(planet);
    }

    /**
     * The planets that activate gates, in the order of the rows below the wheel.
     */
    public static List<Planet> planets() {
        return STEPS.keySet().stream().sorted().toList();
    }

    /**
     * The next time a planet moves into a gate that completes a channel with the birth chart,
     * after a moment and within a time, nothing when it doesn't get there before then.
     */
    public static Optional<Completion> next(Planet planet, RaveChart birth, Instant from, Duration within) {
        var birth_gates = Analysis.gates(birth);
        var birth_channels = new HashSet<>(Analysis.definedChannels(birth_gates));
        var until = from.plus(within);
        if (until.isAfter(Ephemeris.end())) {
            until = Ephemeris.end();
        }
        var step = STEPS.get(planet);
        var moment = from;
        var gate = gate(planet, moment);
        while (moment.isBefore(until)) {
            var later = moment.plus(step).isAfter(until) ? until : moment.plus(step);
            if (gate(planet, later) == gate) {
                moment = later;
                continue;
            }
            moment = change(moment, later, t -> gate(planet, t));
            gate = gate(planet, moment);
            var completed = completed(gate, birth_gates, birth_channels);
            if (!completed.isEmpty()) {
                return Optional.of(new Completion(planet, gate, completed, moment, edge(moment, t -> gate(planet, t), step, Ephemeris.end())));
            }
        }
        return Optional.empty();
    }

    // the channels of a gate whose other gate the birth chart has, which it doesn't define itself
    private static List<Analysis.NamedChannel> completed(int gate, Set<Integer> birthGates, Set<Analysis.NamedChannel> birthChannels) {
        return Analysis.channels(gate).stream()
            .filter(channel -> birthGates.contains(channel.gate1() == gate ? channel.gate2() : channel.gate1()))
            .filter(channel -> !birthChannels.contains(channel))
            .toList();
    }

    /**
     * The stay of a planet in the gate it's in at a moment.
     */
    public static Stay gateStay(Planet planet, Instant moment) {
        return stay(moment, t -> gate(planet, t), STEPS.get(planet));
    }

    /**
     * The stay of a planet in the line it's in at a moment, a sixth of a gate checked in steps a
     * sixth as long.
     */
    public static Stay lineStay(Planet planet, Instant moment) {
        return stay(moment, t -> line(planet, t), STEPS.get(planet).dividedBy(6));
    }

    private static Stay stay(Instant moment, ToIntFunction<Instant> place, Duration step) {
        return new Stay(edge(moment, place, step.negated(), Ephemeris.start()), edge(moment, place, step, Ephemeris.end()));
    }

    // going back, the first whole minute of the place of a moment, going ahead, the first whole
    // minute in another place, the limit when the place doesn't change before it
    private static Instant edge(Instant moment, ToIntFunction<Instant> place, Duration step, Instant limit) {
        var forward = !step.isNegative();
        var value = place.applyAsInt(moment);
        while (forward ? moment.isBefore(limit) : moment.isAfter(limit)) {
            var next = moment.plus(step);
            if (forward ? next.isAfter(limit) : next.isBefore(limit)) {
                next = limit;
            }
            if (place.applyAsInt(next) != value) {
                return forward ? change(moment, next, place) : change(next, moment, place);
            }
            moment = next;
        }
        return limit;
    }

    // the first whole minute in another place, between a moment in one place and a later one in another
    private static Instant change(Instant before, Instant after, ToIntFunction<Instant> place) {
        var value = place.applyAsInt(before);
        while (Duration.between(before, after).compareTo(Duration.ofMinutes(1)) > 0) {
            var middle = before.plus(Duration.between(before, after).dividedBy(2));
            if (place.applyAsInt(middle) == value) {
                before = middle;
            } else {
                after = middle;
            }
        }
        var minute = after.truncatedTo(ChronoUnit.MINUTES);
        return place.applyAsInt(minute) == value ? minute.plus(Duration.ofMinutes(1)) : minute;
    }

    static int gate(Planet planet, Instant moment) {
        return Charts.position(planet, moment).gateNumber();
    }

    static int line(Planet planet, Instant moment) {
        var position = Charts.position(planet, moment);
        return position.gateNumber() * 10 + position.gateLine();
    }
}

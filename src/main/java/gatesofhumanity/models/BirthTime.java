package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Ephemeris;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * How much earlier or later a birth could be with each part of its chart staying the same, as a
 * birth time is often rounded or remembered.
 */
public final class BirthTime {
    private BirthTime() {
    }

    public enum Part {
        TYPE(a -> a.analysis().type()),
        AUTHORITY(a -> a.analysis().authority()),
        PROFILE(a -> a.analysis().profile()),
        DEFINITION(a -> a.analysis().definition()),
        CROSS(a -> a.analysis().cross()),
        CHANNELS(a -> new HashSet<>(a.analysis().definedChannels())),
        // the colors and arrows that the chart shows, a tone only matters for its arrow
        VARIABLES(a -> a.variables().stream().map(v -> List.of(v.color(), v.left())).toList()),
        ACTIVATIONS(a -> a.activations());

        private final Function<Moment, Object> key_;

        Part(Function<Moment, Object> key) {
            key_ = key;
        }

        Object key(Moment moment) {
            return key_.apply(moment);
        }
    }

    /**
     * The chart at a moment, with what the parts are compared by.
     */
    public record Moment(Instant time, RaveChart chart, Analysis analysis, List<Variables.Variable> variables) {
        public static Moment of(Instant time) {
            var chart = Charts.birth(time.atZone(ZoneOffset.UTC));
            var variables = Variables.of(chart);
            return new Moment(time, chart, Analysis.of(chart),
                List.of(variables.determination(), variables.environment(), variables.motivation(), variables.perspective()));
        }

        // the gate and line of every planet that activates one, as the rows below the wheel show them
        public List<String> activations() {
            var activations = new ArrayList<String>();
            for (var data : List.of(chart.personality(), chart.design())) {
                for (var position : data.positions()) {
                    if (position.planet() != Planet.CHIRON) {
                        activations.add(position.planet() + " " + position.gateNumber() + "." + position.gateLine());
                    }
                }
            }
            return activations;
        }
    }

    /**
     * The first and last whole minute of birth with a part as it is, and the charts a minute
     * outside of them, null when the part stays the same over the whole time looked at.
     */
    public record Range(Instant first, Instant last, Moment before, Moment after) {
        public Duration earlier(Instant birth) {
            return Duration.between(first, birth);
        }

        public Duration later(Instant birth) {
            return Duration.between(birth, last);
        }
    }

    /**
     * The range of each part within a time either side of a birth, a minute at a time, which
     * finds every change however briefly a planet crosses into another line.
     */
    public static Map<Part, Range> ranges(Instant birth, Duration within) {
        var moment = Moment.of(birth);
        var keys = new EnumMap<Part, Object>(Part.class);
        for (var part : Part.values()) {
            keys.put(part, part.key(moment));
        }
        var earlier = edges(birth, keys, Duration.ofMinutes(-1), within);
        var later = edges(birth, keys, Duration.ofMinutes(1), within);
        var ranges = new EnumMap<Part, Range>(Part.class);
        for (var part : Part.values()) {
            var before = earlier.found().get(part);
            var after = later.found().get(part);
            ranges.put(part, new Range(
                before == null ? earlier.checked() : before.time().plus(Duration.ofMinutes(1)),
                after == null ? later.checked() : after.time().minus(Duration.ofMinutes(1)),
                before, after));
        }
        return ranges;
    }

    // the first chart in a direction where each part differs, and the last minute that was checked
    private record Edges(Map<Part, Moment> found, Instant checked) {
    }

    // the charts end with the tables of the planets
    private static Edges edges(Instant birth, Map<Part, Object> keys, Duration step, Duration within) {
        var found = new EnumMap<Part, Moment>(Part.class);
        var checked = birth;
        for (var i = 1; i <= within.toMinutes() && found.size() < keys.size(); ++i) {
            var time = birth.plus(step.multipliedBy(i));
            if (!Ephemeris.coversChart(time)) {
                break;
            }
            var moment = Moment.of(time);
            for (var part : Part.values()) {
                if (!found.containsKey(part) && !Objects.equals(keys.get(part), part.key(moment))) {
                    found.put(part, moment);
                }
            }
            checked = time;
        }
        return new Edges(found, checked);
    }
}

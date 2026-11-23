package gatesofhumanity.ephemeris;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

/**
 * Geocentric apparent ecliptic longitudes of date, from tables fitted to JPL Horizons.
 */
public final class Ephemeris {
    public static final double DESIGN_SOLAR_ARC = 88.0;

    private static final double JD_UNIX_EPOCH = 2440587.5;
    private static final double SECONDS_PER_DAY = 86400.0;
    // the Sun takes between 86 and 93 days to move 88 degrees
    private static final double DESIGN_EARLIEST_DAYS = 94.0;
    private static final double DESIGN_LATEST_DAYS = 85.0;
    private static final Duration DESIGN_MARGIN = Duration.ofDays(95);
    private static final double SOLVE_DEGREES = 1e-7;
    private static final double SOLVE_DAYS = 1e-8;
    // no body turns around and passes a longitude again within a day
    private static final double PASS_STEP_DAYS = 1.0;

    private Ephemeris() {
    }

    private static final class Tables {
        static final Map<Body, ChebyshevTable> TABLES = load();
        static final Instant START = instant(TABLES.values().stream().mapToDouble(t -> t.jdStart_).max().orElseThrow());
        static final Instant END = instant(TABLES.values().stream().mapToDouble(t -> t.jdEnd_).min().orElseThrow());

        private static Map<Body, ChebyshevTable> load() {
            var tables = new EnumMap<Body, ChebyshevTable>(Body.class);
            for (var body : Body.values()) {
                var name = "ephemeris/" + body.resourceName_ + ".bin";
                try (var stream = Ephemeris.class.getClassLoader().getResourceAsStream(name)) {
                    if (stream == null) {
                        throw new IllegalStateException("Missing ephemeris resource " + name);
                    }
                    tables.put(body, ChebyshevTable.read(stream));
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to read ephemeris resource " + name, e);
                }
            }
            return tables;
        }
    }

    public static Instant start() {
        return Tables.START;
    }

    public static Instant end() {
        return Tables.END;
    }

    public static boolean covers(Instant moment) {
        return !moment.isBefore(start()) && !moment.isAfter(end());
    }

    /**
     * Whether both a birth at this moment and its design moment are within the ephemeris.
     */
    public static boolean coversChart(Instant birth) {
        return covers(birth) && covers(birth.minus(DESIGN_MARGIN));
    }

    public static Instant firstChart() {
        return start().plus(DESIGN_MARGIN);
    }

    public static double longitude(Body body, Instant moment) {
        return longitude(body, julianDay(moment));
    }

    static double longitude(Body body, double jd) {
        return Tables.TABLES.get(body).longitude(jd);
    }

    /**
     * The moment before {@code birth} at which the Sun was {@link #DESIGN_SOLAR_ARC} degrees
     * earlier in longitude.
     */
    public static Instant designMoment(Instant birth) {
        var jd = julianDay(birth);
        var target = longitude(Body.SUN, jd) - DESIGN_SOLAR_ARC;
        return instant(solveIncreasing(t -> difference(longitude(Body.SUN, t), target),
            jd - DESIGN_EARLIEST_DAYS, jd - DESIGN_LATEST_DAYS));
    }

    /**
     * The first moment from {@code from} to {@code to} at which a body passes a longitude moving
     * forward, or nothing when it doesn't before then or before the end of the tables.
     */
    public static Optional<Instant> firstPass(Body body, double longitude, Instant from, Instant to) {
        if (!covers(from)) {
            return Optional.empty();
        }
        var end = julianDay(to.isAfter(end()) ? end() : to);
        DoubleUnaryOperator distance = t -> difference(longitude(body, t), longitude);
        var low = julianDay(from);
        var f_low = distance.applyAsDouble(low);
        while (low < end) {
            var high = Math.min(low + PASS_STEP_DAYS, end);
            var f_high = distance.applyAsDouble(high);
            // on the far side of the longitude the distance wraps around from 180 to -180 degrees
            if (f_low < 0.0 && f_high >= 0.0 && f_high - f_low < 90.0) {
                return Optional.of(instant(f_high == 0.0 ? high : solveIncreasing(distance, low, high)));
            }
            low = high;
            f_low = f_high;
        }
        return Optional.empty();
    }

    /**
     * The root of a function that increases from {@code low} to {@code high}, with the Illinois
     * variant of regula falsi. The bracket also ends the search where the function jumps over
     * zero, as the tables do by a few millionths of a degree where their segments meet.
     */
    static double solveIncreasing(DoubleUnaryOperator function, double low, double high) {
        var f_low = function.applyAsDouble(low);
        var f_high = function.applyAsDouble(high);
        if (!(f_low < 0.0 && f_high > 0.0)) {
            throw new IllegalArgumentException("The root isn't between " + low + " and " + high);
        }
        var side = 0;
        for (var i = 0; i < 200; ++i) {
            var t = (low * f_high - high * f_low) / (f_high - f_low);
            var f = function.applyAsDouble(t);
            if (Math.abs(f) < SOLVE_DEGREES || high - low < SOLVE_DAYS) {
                return t;
            }
            if (f < 0.0) {
                low = t;
                f_low = f;
                if (side == -1) {
                    f_high /= 2.0;
                }
                side = -1;
            } else {
                high = t;
                f_high = f;
                if (side == 1) {
                    f_low /= 2.0;
                }
                side = 1;
            }
        }
        throw new IllegalStateException("No root was found between " + low + " and " + high);
    }

    static double julianDay(Instant moment) {
        return (moment.getEpochSecond() + moment.getNano() / 1e9) / SECONDS_PER_DAY + JD_UNIX_EPOCH;
    }

    static Instant instant(double jd) {
        var seconds = (jd - JD_UNIX_EPOCH) * SECONDS_PER_DAY;
        var whole = Math.floor(seconds);
        return Instant.ofEpochSecond((long) whole, Math.round((seconds - whole) * 1e9));
    }

    private static double difference(double longitude, double target) {
        var result = (longitude - target) % 360.0;
        if (result > 180.0) {
            return result - 360.0;
        }
        if (result <= -180.0) {
            return result + 360.0;
        }
        return result;
    }
}

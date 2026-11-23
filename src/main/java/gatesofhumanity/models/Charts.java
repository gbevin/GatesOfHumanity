package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Body;
import gatesofhumanity.ephemeris.Ephemeris;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The positions of the planets at moments, from the ephemeris.
 */
public final class Charts {
    public enum Step {
        DAYS, HOURS
    }

    private Charts() {
    }

    public static List<Position> positions(Instant moment) {
        var positions = new ArrayList<Position>();
        for (var body : Body.values()) {
            var planet = planet(body);
            var longitude = Ephemeris.longitude(body, moment);
            positions.add(new Position(planet, longitude));
            switch (planet) {
                case SUN -> positions.add(new Position(Planet.EARTH, opposite(longitude)));
                case NORTH_NODE -> positions.add(new Position(Planet.SOUTH_NODE, opposite(longitude)));
                default -> {
                }
            }
        }
        return positions;
    }

    /**
     * The position of one planet at a moment, the Earth opposite the Sun and the South Node
     * opposite the North Node.
     */
    public static Position position(Planet planet, Instant moment) {
        return switch (planet) {
            case EARTH -> new Position(planet, opposite(Ephemeris.longitude(Body.SUN, moment)));
            case SOUTH_NODE -> new Position(planet, opposite(Ephemeris.longitude(Body.TRUE_NODE, moment)));
            default -> new Position(planet, Ephemeris.longitude(body(planet), moment));
        };
    }

    private static double opposite(double longitude) {
        return (longitude + 180.0) % 360.0;
    }

    static Body body(Planet planet) {
        for (var body : Body.values()) {
            if (planet(body) == planet) {
                return body;
            }
        }
        throw new IllegalArgumentException("No body is the " + planet);
    }

    /**
     * The positions at a number of moments a step apart, a list for each moment. Days keep the
     * time of day of the start in its time zone.
     */
    public static List<List<Position>> series(ZonedDateTime start, int count, Step step) {
        var series = new ArrayList<List<Position>>();
        for (var i = 0; i < count; ++i) {
            series.add(positions((step == Step.DAYS ? start.plusDays(i) : start.plusHours(i)).toInstant()));
        }
        return series;
    }

    public static RaveChart birth(ZonedDateTime moment) {
        var personality = moment.toInstant();
        var design = Ephemeris.designMoment(personality);
        return new RaveChart(
            new PositionData(personality.atZone(ZoneOffset.UTC), positions(personality)),
            new PositionData(design.atZone(ZoneOffset.UTC), positions(design)));
    }

    /**
     * The positions at a moment without a design, as transits are drawn.
     */
    public static RaveChart transit(ZonedDateTime moment) {
        var instant = moment.toInstant();
        return new RaveChart(new PositionData(instant.atZone(ZoneOffset.UTC), positions(instant)), PositionData.NONE);
    }

    static Planet planet(Body body) {
        return switch (body) {
            case SUN -> Planet.SUN;
            case MOON -> Planet.MOON;
            case MERCURY -> Planet.MERCURY;
            case VENUS -> Planet.VENUS;
            case MARS -> Planet.MARS;
            case JUPITER -> Planet.JUPITER;
            case SATURN -> Planet.SATURN;
            case URANUS -> Planet.URANUS;
            case NEPTUNE -> Planet.NEPTUNE;
            case PLUTO -> Planet.PLUTO;
            case TRUE_NODE -> Planet.NORTH_NODE;
            case CHIRON -> Planet.CHIRON;
        };
    }
}

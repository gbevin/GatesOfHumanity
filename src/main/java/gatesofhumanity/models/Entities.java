package gatesofhumanity.models;

import java.util.*;

public final class Entities {
    private Entities() {
    }

    public static final List<String> SIGNS = List.of(
        "sagittarius", "scorpio", "libra", "virgo",
        "leo", "cancer", "gemini", "taurus",
        "aries", "pisces", "aquarius", "capricorn"
    );

    public static final List<Integer> GATES_ORDER = List.of(
        11, 26, 5, 9, 34, 14, 43, 1, 44, 28, 50, 32, 57, 48, 18, 46,
        6, 47, 64, 40, 59, 29, 4, 7, 33, 31, 56, 62, 53, 39, 52, 15,
        12, 45, 35, 16, 20, 8, 23, 2, 24, 27, 3, 42, 51, 21, 17, 25,
        36, 22, 63, 37, 55, 30, 49, 13, 19, 41, 60, 61, 54, 38, 58, 10
    );

    public static final Map<Integer, Gate> GATES;

    public static final Map<Center, List<Activation>> ACTIVATIONS;

    static {
        var gates = new HashMap<Integer, Gate>();
        gates.put(1, new Gate(Gate.Color.YELLOW, new IChing(1, 1, 1, 1, 1, 1),
            List.of(
                new Dignity(Planet.MOON, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.MARS, Planet.EARTH),
                new Dignity(Planet.EARTH, Planet.JUPITER),
                new Dignity(Planet.MARS, Planet.URANUS),
                new Dignity(Planet.EARTH, Planet.PLUTO)
            )
        ));
        gates.put(2, new Gate(Gate.Color.YELLOW, new IChing(0, 0, 0, 0, 0, 0),
            List.of(
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.MERCURY, Planet.EARTH),
                new Dignity(Planet.MERCURY, Planet.SATURN)
            )));
        gates.put(3, new Gate(Gate.Color.RED, new IChing(1, 0, 0, 0, 1, 0),
            List.of(
                new Dignity(Planet.EARTH, Planet.MERCURY),
                new Dignity(Planet.MARS, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.PLUTO),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.MARS, Planet.EARTH),
                new Dignity(Planet.SUN, Planet.PLUTO)
            )));
        gates.put(4, new Gate(Gate.Color.GREEN, new IChing(0, 1, 0, 0, 0, 1),
            List.of(
                new Dignity(Planet.MOON, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.PLUTO),
                new Dignity(Planet.SUN, Planet.SATURN),
                new Dignity(Planet.JUPITER, Planet.PLUTO),
                new Dignity(Planet.MERCURY, Planet.MARS)
            )));
        gates.put(5, new Gate(Gate.Color.RED, new IChing(1, 1, 1, 0, 1, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.EARTH),
                new Dignity(Planet.VENUS, Planet.PLUTO),
                new Dignity(Planet.NEPTUNE, Planet.MOON),
                new Dignity(Planet.URANUS, Planet.SUN),
                new Dignity(Planet.VENUS, Planet.PLUTO),
                new Dignity(Planet.NEPTUNE, Planet.NONE)
            )));
        gates.put(6, new Gate(Gate.Color.BROWN, new IChing(0, 1, 0, 1, 1, 1),
            List.of(
                new Dignity(Planet.PLUTO, Planet.MERCURY),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.NEPTUNE, Planet.PLUTO),
                new Dignity(Planet.SUN, Planet.PLUTO),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.MERCURY, Planet.VENUS)
            )));
        gates.put(7, new Gate(Gate.Color.YELLOW, new IChing(0, 1, 0, 0, 0, 0),
            List.of(
                new Dignity(Planet.VENUS, Planet.MERCURY),
                new Dignity(Planet.NEPTUNE, Planet.MERCURY),
                new Dignity(Planet.MOON, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.NEPTUNE),
                new Dignity(Planet.MERCURY, Planet.URANUS)
            )));
        gates.put(8, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 0, 1, 0),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.SATURN),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.SUN),
                new Dignity(Planet.VENUS, Planet.PLUTO)
            )));
        gates.put(9, new Gate(Gate.Color.RED, new IChing(1, 1, 1, 0, 1, 1),
            List.of(
                new Dignity(Planet.PLUTO, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.EARTH, Planet.SUN),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.PLUTO)
            )));
        gates.put(10, new Gate(Gate.Color.YELLOW, new IChing(1, 1, 0, 1, 1, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.MOON),
                new Dignity(Planet.MERCURY, Planet.MARS),
                new Dignity(Planet.EARTH, Planet.MOON),
                new Dignity(Planet.URANUS, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.SATURN)
            )));
        gates.put(11, new Gate(Gate.Color.GREEN, new IChing(1, 1, 1, 0, 0, 0),
            List.of(
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Set.of(Planet.MOON, Planet.VENUS), Planet.SUN),
                new Dignity(Planet.MOON, Planet.MERCURY),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER)
            )));
        gates.put(12, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 1, 1, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.MERCURY),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.EARTH, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.SUN, Planet.EARTH)
            )));
        gates.put(13, new Gate(Gate.Color.YELLOW, new IChing(1, 0, 1, 1, 1, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.MOON, Planet.SUN),
                new Dignity(Planet.EARTH, Planet.VENUS),
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER),
                new Dignity(Planet.MARS, Planet.MERCURY)
            )));
        gates.put(14, new Gate(Gate.Color.RED, new IChing(1, 1, 1, 1, 0, 1),
            List.of(
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.EARTH, Planet.NEPTUNE),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.EARTH)
            )));
        gates.put(15, new Gate(Gate.Color.YELLOW, new IChing(0, 0, 1, 0, 0, 0),
            List.of(
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.EARTH, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.SATURN),
                new Dignity(Planet.JUPITER, Planet.PLUTO),
                new Dignity(Planet.PLUTO, Planet.VENUS)
            )));
        gates.put(16, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 1, 0, 0),
            List.of(
                new Dignity(Planet.EARTH, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.MERCURY),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER)
            )));
        gates.put(17, new Gate(Gate.Color.GREEN, new IChing(1, 0, 0, 1, 1, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.MOON),
                new Dignity(Planet.PLUTO, Planet.EARTH),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.MOON, Planet.JUPITER)
            )));
        gates.put(18, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 0, 0, 1),
            List.of(
                new Dignity(Planet.EARTH, Planet.JUPITER),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER),
                new Dignity(Planet.EARTH, Planet.MERCURY),
                new Dignity(Planet.SATURN, Planet.URANUS),
                new Dignity(Planet.MARS, Planet.MOON)
            )));
        gates.put(19, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 0, 0, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.MOON),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.EARTH, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.MARS)
            )));
        gates.put(20, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 0, 1, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.SATURN, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.MERCURY)
            )));
        gates.put(21, new Gate(Gate.Color.RED, new IChing(1, 0, 0, 1, 0, 1),
            List.of(
                new Dignity(Planet.MARS, Planet.MOON),
                new Dignity(Planet.MARS, Planet.NEPTUNE),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.EARTH),
                new Dignity(Planet.JUPITER, Planet.PLUTO),
                new Dignity(Planet.PLUTO, Planet.VENUS)
            )));
        gates.put(22, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 0, 0, 1),
            List.of(
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.SUN, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.SUN, Planet.MARS)
            )));
        gates.put(23, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 0, 0, 1),
            List.of(
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MOON),
                new Dignity(Planet.SUN, Planet.PLUTO),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.JUPITER, Planet.MOON),
                new Dignity(Planet.MARS, Planet.JUPITER)
            )));
        gates.put(24, new Gate(Gate.Color.GREEN, new IChing(1, 0, 0, 0, 0, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.NEPTUNE),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.NEPTUNE),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.PLUTO)
            )));
        gates.put(25, new Gate(Gate.Color.YELLOW, new IChing(1, 0, 0, 1, 1, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MERCURY),
                new Dignity(Planet.MERCURY, Planet.MARS),
                new Dignity(Planet.MARS, Planet.PLUTO),
                new Dignity(Set.of(Planet.VENUS, Planet.JUPITER), Planet.NONE),
                new Dignity(Planet.VENUS, Planet.JUPITER),
                new Dignity(Planet.EARTH, Planet.URANUS)
            )));
        gates.put(26, new Gate(Gate.Color.RED, new IChing(1, 1, 1, 0, 0, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.SUN, Planet.PLUTO),
                new Dignity(Planet.SUN, Planet.SATURN),
                new Dignity(Planet.PLUTO, Planet.SATURN),
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.MOON)
            )));
        gates.put(27, new Gate(Gate.Color.RED, new IChing(1, 0, 0, 0, 0, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.SATURN),
                new Dignity(Planet.MOON, Planet.PLUTO)
            )));
        gates.put(28, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 1, 1, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.PLUTO, Planet.SUN),
                new Dignity(Planet.PLUTO, Planet.NEPTUNE)
            )));
        gates.put(29, new Gate(Gate.Color.RED, new IChing(0, 1, 0, 0, 1, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.NEPTUNE),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.MARS, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.MARS, Planet.JUPITER)
            )));
        gates.put(30, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 1, 0, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.JUPITER),
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.PLUTO),
                new Dignity(Planet.MARS, Planet.MOON)
            )));
        gates.put(31, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 1, 1, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.JUPITER),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.SUN, Planet.MOON)
            )));
        gates.put(32, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 1, 0, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.JUPITER),
                new Dignity(Planet.MERCURY, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.SATURN),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.NEPTUNE)
            )));
        gates.put(33, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 1, 1, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.NEPTUNE),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.NEPTUNE),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.SUN, Planet.JUPITER)
            )));
        gates.put(34, new Gate(Gate.Color.RED, new IChing(1, 1, 1, 1, 0, 0),
            List.of(
                new Dignity(Planet.SATURN, Planet.PLUTO),
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SATURN, Planet.MERCURY),
                new Dignity(Planet.PLUTO, Planet.MARS),
                new Dignity(Planet.MARS, Planet.MOON),
                new Dignity(Planet.EARTH, Planet.JUPITER)
            )));
        gates.put(35, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 1, 0, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.NEPTUNE),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.JUPITER, Planet.SUN),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.MERCURY, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.MARS)
            )));
        gates.put(36, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 0, 0, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.JUPITER),
                new Dignity(Planet.NEPTUNE, Planet.MOON),
                new Dignity(Planet.PLUTO, Planet.JUPITER),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.PLUTO, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.SATURN)
            )));
        gates.put(37, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 0, 1, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.NONE),
                new Dignity(Planet.JUPITER, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.MOON, Planet.SATURN),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.MERCURY)
            )));
        gates.put(38, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 1, 0, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.PLUTO, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.PLUTO),
                new Dignity(Planet.SATURN, Planet.EARTH)
            )));
        gates.put(39, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 0, 1, 0),
            List.of(
                new Dignity(Planet.MARS, Planet.MERCURY),
                new Dignity(Planet.MOON, Planet.JUPITER),
                new Dignity(Planet.JUPITER, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.SUN),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.MOON, Planet.MARS)
            )));
        gates.put(40, new Gate(Gate.Color.RED, new IChing(0, 1, 0, 1, 0, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.MOON),
                new Dignity(Planet.SUN, Planet.MOON),
                new Dignity(Planet.PLUTO, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.EARTH),
                new Dignity(Planet.SUN, Planet.EARTH)
            )));
        gates.put(41, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 0, 0, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MERCURY),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.MOON),
                new Dignity(Planet.EARTH, Planet.VENUS),
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SATURN, Planet.PLUTO)
            )));
        gates.put(42, new Gate(Gate.Color.RED, new IChing(1, 0, 0, 0, 1, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.MARS, Planet.MOON),
                new Dignity(Planet.MOON, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.SATURN)
            )));
        gates.put(43, new Gate(Gate.Color.GREEN, new IChing(1, 1, 1, 1, 1, 0),
            List.of(
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.MERCURY, Planet.JUPITER),
                new Dignity(Planet.MOON, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.MARS)
            )));
        gates.put(44, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 1, 1, 1),
            List.of(
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.MARS, Planet.NEPTUNE),
                new Dignity(Planet.PLUTO, Planet.SUN),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.EARTH)
            )));
        gates.put(45, new Gate(Gate.Color.BROWN, new IChing(0, 0, 0, 1, 1, 0),
            List.of(
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.JUPITER),
                new Dignity(Planet.URANUS, Planet.JUPITER)
            )));
        gates.put(46, new Gate(Gate.Color.YELLOW, new IChing(0, 1, 1, 0, 0, 0),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.JUPITER),
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.EARTH, Planet.PLUTO),
                new Dignity(Planet.MOON, Planet.NEPTUNE),
                new Dignity(Planet.SATURN, Planet.NEPTUNE)
            )));
        gates.put(47, new Gate(Gate.Color.GREEN, new IChing(0, 1, 0, 1, 1, 0),
            List.of(
                new Dignity(Planet.SATURN, Planet.NEPTUNE),
                new Dignity(Planet.SATURN, Planet.MERCURY),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.MOON),
                new Dignity(Planet.VENUS, Planet.NONE),
                new Dignity(Planet.NONE , Planet.SUN)
            )));
        gates.put(48, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 0, 1, 0),
            List.of(
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.EARTH),
                new Dignity(Planet.MARS, Planet.MOON),
                new Dignity(Planet.VENUS, Planet.MOON)
            )));
        gates.put(49, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 1, 1, 0),
            List.of(
                new Dignity(Planet.JUPITER, Planet.SUN),
                new Dignity(Planet.EARTH, Planet.PLUTO),
                new Dignity(Planet.NEPTUNE, Planet.PLUTO),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.NEPTUNE, Planet.SATURN)
            )));
        gates.put(50, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 1, 0, 1),
            List.of(
                new Dignity(Planet.MARS, Planet.VENUS),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.MERCURY),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.MOON)
            )));
        gates.put(51, new Gate(Gate.Color.RED, new IChing(1, 0, 0, 1, 0, 0),
            List.of(
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.MARS, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.JUPITER),
                new Dignity(Planet.URANUS, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.SUN, Planet.PLUTO)
            )));
        gates.put(52, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 0, 0, 1),
            List.of(
                new Dignity(Planet.EARTH, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.VENUS),
                new Dignity(Planet.SATURN, Planet.JUPITER),
                new Dignity(Planet.EARTH, Planet.PLUTO),
                new Dignity(Planet.VENUS, Planet.NEPTUNE)
            )));
        gates.put(53, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 0, 1, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.MOON, Planet.VENUS),
                new Dignity(Planet.NEPTUNE, Planet.EARTH),
                new Dignity(Planet.MOON, Planet.PLUTO)
            )));
        gates.put(54, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 1, 0, 0),
            List.of(
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.VENUS),
                new Dignity(Planet.NONE, Planet.NONE),
                new Dignity(Planet.SUN, Planet.NONE),
                new Dignity(Planet.SATURN, Planet.JUPITER)
            )));
        gates.put(55, new Gate(Gate.Color.BROWN, new IChing(1, 0, 1, 1, 0, 0),
            List.of(
                new Dignity(Planet.JUPITER, Planet.VENUS),
                new Dignity(Planet.VENUS, Planet.EARTH),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.SUN),
                new Dignity(Planet.SATURN, Planet.MOON)
            )));
        gates.put(56, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 1, 0, 1),
            List.of(
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.URANUS, Planet.MOON),
                new Dignity(Planet.SUN, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.MERCURY),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.SUN, Planet.PLUTO)
            )));
        gates.put(57, new Gate(Gate.Color.BROWN, new IChing(0, 1, 1, 0, 1, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.MERCURY, Planet.NONE),
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MOON),
                new Dignity(Planet.URANUS, Planet.MARS)
            )));
        gates.put(58, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 1, 1, 0),
            List.of(
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.NONE, Planet.URANUS),
                new Dignity(Planet.URANUS, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.NEPTUNE),
                new Dignity(Planet.MOON, Planet.SUN),
                new Dignity(Planet.MOON, Planet.MERCURY)
            )));
        gates.put(59, new Gate(Gate.Color.RED, new IChing(0, 1, 0, 0, 1, 1),
            List.of(
                new Dignity(Planet.SUN, Planet.MERCURY),
                new Dignity(Planet.URANUS, Planet.PLUTO),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.MERCURY),
                new Dignity(Planet.SUN, Planet.URANUS),
                new Dignity(Planet.VENUS, Planet.MERCURY)
            )));
        gates.put(60, new Gate(Gate.Color.BROWN, new IChing(1, 1, 0, 0, 1, 0),
            List.of(
                new Dignity(Planet.VENUS, Planet.MERCURY),
                new Dignity(Planet.SATURN, Planet.EARTH),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.MERCURY, Planet.VENUS),
                new Dignity(Planet.NEPTUNE, Planet.JUPITER),
                new Dignity(Planet.URANUS, Planet.MERCURY)
            )));
        gates.put(61, new Gate(Gate.Color.YELLOW, new IChing(1, 1, 0, 0, 1, 1),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.VENUS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.JUPITER),
                new Dignity(Planet.SATURN, Planet.MARS),
                new Dignity(Planet.PLUTO, Planet.MARS)
            )));
        gates.put(62, new Gate(Gate.Color.BROWN, new IChing(0, 0, 1, 1, 0, 0),
            List.of(
                new Dignity(Planet.NEPTUNE, Planet.MARS),
                new Dignity(Planet.SATURN, Planet.MERCURY),
                new Dignity(Planet.URANUS, Planet.VENUS),
                new Dignity(Planet.VENUS, Planet.PLUTO),
                new Dignity(Planet.MOON, Planet.NEPTUNE),
                new Dignity(Planet.SATURN, Planet.MERCURY)
            )));
        gates.put(63, new Gate(Gate.Color.YELLOW, new IChing(1, 0, 1, 0, 1, 0),
            List.of(
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.URANUS),
                new Dignity(Planet.JUPITER, Planet.SATURN),
                new Dignity(Planet.MERCURY, Planet.MARS),
                new Dignity(Planet.SUN, Planet.MARS),
                new Dignity(Planet.JUPITER, Planet.PLUTO)
            )));
        gates.put(64, new Gate(Gate.Color.YELLOW, new IChing(0, 1, 0, 1, 0, 1),
            List.of(
                new Dignity(Planet.VENUS, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.MOON),
                new Dignity(Planet.SATURN, Planet.MOON),
                new Dignity(Planet.MOON, Planet.MARS),
                new Dignity(Planet.VENUS, Planet.JUPITER),
                new Dignity(Planet.MERCURY, Planet.VENUS)
            )));

        GATES = Map.copyOf(gates);

        var activations = new EnumMap<Center, List<Activation>>(Center.class);
        activations.put(Center.HEAD, List.of(
            new Activation(64, 47),
            new Activation(61, 24),
            new Activation(63, 4)));
        activations.put(Center.AJNA, List.of(
            new Activation(47, 64),
            new Activation(24, 61),
            new Activation(4, 63),
            new Activation(17, 62),
            new Activation(43, 23),
            new Activation(11, 56)));
        activations.put(Center.THROAT, List.of(
            new Activation(62, 17),
            new Activation(23, 43),
            new Activation(56, 11),
            new Activation(16, 48),
            new Activation(20, 57),
            new Activation(20, 10),
            new Activation(20, 34),
            new Activation(31, 7),
            new Activation(8, 1),
            new Activation(33, 13),
            new Activation(35, 36),
            new Activation(12, 22),
            new Activation(45, 21)));
        activations.put(Center.G, List.of(
            new Activation(7, 31),
            new Activation(1, 8),
            new Activation(13, 33),
            new Activation(10, 20),
            new Activation(10, 57),
            new Activation(10, 34),
            new Activation(15, 5),
            new Activation(2, 14),
            new Activation(46, 29),
            new Activation(25, 51)));
        activations.put(Center.HEART, List.of(
            new Activation(21, 45),
            new Activation(51, 25),
            new Activation(26, 44),
            new Activation(40, 37)));
        activations.put(Center.SPLEEN, List.of(
            new Activation(48, 16),
            new Activation(57, 20),
            new Activation(57, 10),
            new Activation(57, 34),
            new Activation(44, 26),
            new Activation(50, 27),
            new Activation(32, 54),
            new Activation(28, 38),
            new Activation(18, 58)));
        activations.put(Center.SACRAL, List.of(
            new Activation(5, 15),
            new Activation(14, 2),
            new Activation(29, 46),
            new Activation(34, 20),
            new Activation(34, 10),
            new Activation(34, 57),
            new Activation(27, 50),
            new Activation(42, 53),
            new Activation(3, 60),
            new Activation(9, 52),
            new Activation(59, 6)));
        activations.put(Center.SOLAR, List.of(
            new Activation(36, 35),
            new Activation(22, 12),
            new Activation(37, 40),
            new Activation(6, 59),
            new Activation(49, 19),
            new Activation(55, 39),
            new Activation(30, 41)));
        activations.put(Center.ROOT, List.of(
            new Activation(53, 42),
            new Activation(60, 3),
            new Activation(52, 9),
            new Activation(54, 32),
            new Activation(38, 28),
            new Activation(58, 18),
            new Activation(19, 49),
            new Activation(39, 55),
            new Activation(41, 30)));
        ACTIVATIONS = Collections.unmodifiableMap(activations);
    }
}

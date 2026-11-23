package gatesofhumanity.models;

import java.util.Locale;

public enum Planet {
    NONE,
    SUN,
    EARTH,
    MOON,
    NORTH_NODE,
    SOUTH_NODE,
    MERCURY,
    VENUS,
    MARS,
    JUPITER,
    SATURN,
    URANUS,
    NEPTUNE,
    PLUTO,
    CHIRON;

    // the id of the planet in the templates
    public final String id_ = name().toLowerCase(Locale.ROOT);
}

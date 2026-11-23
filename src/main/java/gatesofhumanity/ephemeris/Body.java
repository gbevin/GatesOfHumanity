package gatesofhumanity.ephemeris;

public enum Body {
    SUN("sun"),
    MOON("moon"),
    MERCURY("mercury"),
    VENUS("venus"),
    MARS("mars"),
    JUPITER("jupiter"),
    SATURN("saturn"),
    URANUS("uranus"),
    NEPTUNE("neptune"),
    PLUTO("pluto"),
    TRUE_NODE("true_node"),
    CHIRON("chiron");

    final String resourceName_;

    Body(String resourceName) {
        resourceName_ = resourceName;
    }
}

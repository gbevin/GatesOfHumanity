package gatesofhumanity.models;

import java.util.*;

public class ActivatedLine {
    private final Set<Planet> planets_ = new HashSet<>();
    // fixed by a planet of the line's own chart, or only by one of another chart, which the
    // transits or another person do for now
    private boolean exalted_;
    private boolean inDetriment_;
    private boolean exaltedByOther_;
    private boolean inDetrimentByOther_;

    public Set<Planet> planets() {
        return Collections.unmodifiableSet(planets_);
    }

    public boolean isExalted() {
        return exalted_ || exaltedByOther_;
    }

    public boolean isInDetriment() {
        return inDetriment_ || inDetrimentByOther_;
    }

    public boolean isExaltedOnlyByOther() {
        return exaltedByOther_ && !exalted_;
    }

    public boolean isInDetrimentOnlyByOther() {
        return inDetrimentByOther_ && !inDetriment_;
    }

    void addPlanet(Planet planet) {
        planets_.add(planet);
    }

    void exalt(boolean byOther) {
        if (byOther) {
            exaltedByOther_ = true;
        } else {
            exalted_ = true;
        }
    }

    void putInDetriment(boolean byOther) {
        if (byOther) {
            inDetrimentByOther_ = true;
        } else {
            inDetriment_ = true;
        }
    }
}

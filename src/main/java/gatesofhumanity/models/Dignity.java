package gatesofhumanity.models;

import java.util.Set;

public record Dignity(Set<Planet> exaltations, Planet detriment) {
    public Dignity(Planet exaltation, Planet detriment) {
        this(Set.of(exaltation), detriment);
    }

    public boolean isExaltedBy(Planet planet) {
        return planet != Planet.NONE && exaltations.contains(planet);
    }

    public boolean isDetrimentBy(Planet planet) {
        return planet != Planet.NONE && planet == detriment;
    }
}

package gatesofhumanity.models;

import gatesofhumanity.ephemeris.Body;
import gatesofhumanity.ephemeris.Ephemeris;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The turning points of a life that Human Design times by the slow planets, each when a planet
 * first reaches a point measured from where it was at birth.
 */
public enum Cycle {
    SATURN_RETURN("Saturn Return", Body.SATURN, 0.0, 26, 33),
    URANUS_OPPOSITION("Uranus Opposition", Body.URANUS, 180.0, 34, 48),
    CHIRON_RETURN("Chiron Return", Body.CHIRON, 0.0, 45, 55);

    public final String label_;
    private final Body body_;
    private final double angle_;
    // the ages between which the planet reaches the point, wide enough for its slowest and fastest orbits
    private final int earliestAge_;
    private final int latestAge_;

    Cycle(String label, Body body, double angle, int earliestAge, int latestAge) {
        label_ = label;
        body_ = body;
        angle_ = angle;
        earliestAge_ = earliestAge;
        latestAge_ = latestAge;
    }

    /**
     * The moment of the cycle for a birth, the first time the planet gets there since it can pass
     * the point up to three times as it moves back and forth, or nothing when the ephemeris
     * doesn't cover the chart of the birth or ends before the cycle.
     */
    public Optional<Instant> moment(Instant birth) {
        if (!Ephemeris.coversChart(birth)) {
            return Optional.empty();
        }
        var born = birth.atZone(ZoneOffset.UTC);
        return Ephemeris.firstPass(body_, Ephemeris.longitude(body_, birth) + angle_,
            born.plusYears(earliestAge_).toInstant(), born.plusYears(latestAge_).toInstant());
    }
}

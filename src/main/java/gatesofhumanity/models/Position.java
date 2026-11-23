package gatesofhumanity.models;

import static gatesofhumanity.models.Entities.GATES_ORDER;

public record Position(Planet planet, double longitude) {
    public static final double GATE_41_START = 302.0;
    public static final double GATE_DEGREES = 360.0 / 64.0;
    public static final double LINE_DEGREES = GATE_DEGREES / 6.0;
    private static final int GATE_41_INDEX = 57;
    private static final long TONES_PER_COLOR = 6;
    private static final long TONES_PER_LINE = 6 * TONES_PER_COLOR;
    private static final long TONES_PER_GATE = 6 * TONES_PER_LINE;
    private static final long TONES = 64 * TONES_PER_GATE;

    /**
     * The angle on the drawing of the wheel, which runs the other way from the longitude and has
     * the start of gate 41, at 302 degrees, at 58 gates of 5.625 degrees.
     */
    public double degree() {
        var degree = -91.75 - longitude;
        while (degree < 0.0) {
            degree += 360.0;
        }
        return degree % 360.0;
    }

    public int gateNumber() {
        var steps = (int) (tonesFromGate41() / TONES_PER_GATE);
        return GATES_ORDER.get(Math.floorMod(GATE_41_INDEX - steps, GATES_ORDER.size()));
    }

    public int gateLine() {
        return (int) (tonesFromGate41() / TONES_PER_LINE % 6) + 1;
    }

    public int color() {
        return (int) (tonesFromGate41() / TONES_PER_COLOR % 6) + 1;
    }

    public int tone() {
        return (int) (tonesFromGate41() % 6) + 1;
    }

    // every gate, line, color and tone includes its start and excludes its end, in increasing
    // longitude, all counted from one number so that they can't disagree at a boundary
    private long tonesFromGate41() {
        var offset = (longitude - GATE_41_START) % 360.0;
        if (offset < 0.0) {
            offset += 360.0;
        }
        return (long) Math.floor(offset * TONES / 360.0);
    }
}

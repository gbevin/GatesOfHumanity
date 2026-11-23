package gatesofhumanity.models;

public final class TestPositions {
    private TestPositions() {
    }

    /**
     * A planet in the middle of a gate line.
     */
    public static Position at(Planet planet, int gate, int line) {
        var steps = Math.floorMod(Entities.GATES_ORDER.indexOf(41) - Entities.GATES_ORDER.indexOf(gate), 64);
        return new Position(planet, (Position.GATE_41_START + steps * Position.GATE_DEGREES + (line - 0.5) * Position.LINE_DEGREES) % 360.0);
    }
}

package gatesofhumanity.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The names of the gates in Human Design and of their hexagrams in the I Ching, as The Definitive
 * Book of Human Design gives them.
 */
public final class GateNames {
    public record Names(String hexagram, String name) {
    }

    private static final Map<Integer, Names> BY_GATE = new HashMap<>();

    static {
        for (var columns : TsvResource.rows("gates.tsv")) {
            BY_GATE.put(Integer.parseInt(columns[0]), new Names(columns[1], columns[2]));
        }
    }

    private GateNames() {
    }

    public static Names of(int gate) {
        return Objects.requireNonNull(BY_GATE.get(gate), "No name for gate " + gate);
    }
}

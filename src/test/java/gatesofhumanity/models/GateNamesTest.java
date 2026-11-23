package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class GateNamesTest {
    @Test
    void everyGateHasBothNames() {
        var names = new HashSet<String>();
        for (var gate = 1; gate <= 64; ++gate) {
            var gate_names = GateNames.of(gate);
            assertFalse(gate_names.hexagram().isBlank(), String.valueOf(gate));
            names.add(gate_names.name());
        }
        assertEquals(64, names.size());
        assertEquals(new GateNames.Names("Decrease", "Contraction"), GateNames.of(41));
        assertEquals(new GateNames.Names("Biting Through", "the Hunter/Huntress"), GateNames.of(21));
        assertEquals(new GateNames.Names("The Gentle", "Intuitive Clarity"), GateNames.of(57));
    }

    private static String letters(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    // the index of the crosses writes Self Expression, the gate it's named after is Self-Expression
    @Test
    void aCrossNamedAfterAGateIsWrittenLikeTheGate() {
        var gates = new HashMap<String, String>();
        for (var gate = 1; gate <= 64; ++gate) {
            gates.put(letters(GateNames.of(gate).name()), GateNames.of(gate).name());
        }
        var named = 0;
        for (var cross : TsvResource.rows("crosses.tsv")) {
            var gate = gates.get(letters(cross[2]));
            if (gate != null) {
                assertTrue(gate.equalsIgnoreCase(cross[2]), cross[2] + " and " + gate);
                ++named;
            }
        }
        assertTrue(named >= 20, String.valueOf(named));
    }

    @Test
    void everyGateHasACenterAndChannels() {
        assertEquals(Center.ROOT, Analysis.center(41));
        assertEquals("[30-41 Recognition]", Analysis.channels(41).toString());
        assertEquals(Center.G, Analysis.center(10));
        assertEquals("[10-20 Awakening, 10-34 Exploration, 10-57 Perfected Form]", Analysis.channels(10).toString());
        // the book writes Judgement in one heading and Judgment everywhere else
        assertEquals("[18-58 Judgment]", Analysis.channels(58).toString());
        for (var gate = 1; gate <= 64; ++gate) {
            assertFalse(Analysis.channels(gate).isEmpty(), String.valueOf(gate));
        }
    }
}

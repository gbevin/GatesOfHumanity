package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitTest {
    @Test
    void everyChannelIsInExactlyOneCircuit() {
        var seen = new HashSet<String>();
        for (var circuit : Circuit.values()) {
            for (var channel : circuit.channels()) {
                assertTrue(seen.add(channel.key()), channel.toString());
                assertEquals(circuit, Circuit.of(channel));
            }
        }
        assertEquals(36, seen.size());
        assertEquals(Analysis.allChannels().size(), seen.size());
    }

    @Test
    void theGroupsHaveTheirCircuits() {
        assertArrayEquals(new int[]{9, 2, 4, 5, 2, 7, 7}, Arrays.stream(Circuit.values()).mapToInt(c -> c.channels().size()).toArray());
        // the channels around the Integration gates that aren't the Integration Channel
        assertEquals(Circuit.KNOWING, Circuit.of(channel(20, 57)));
        assertEquals(Circuit.CENTERING, Circuit.of(channel(10, 34)));
        assertEquals(Circuit.INTEGRATION, Circuit.of(channel(34, 57)));
        assertEquals(Circuit.Group.INDIVIDUAL, Circuit.INTEGRATION.group_);
        assertEquals(Circuit.Group.TRIBAL, Circuit.of(channel(6, 59)).group_);
        assertEquals(Circuit.Group.COLLECTIVE, Circuit.of(channel(30, 41)).group_);
        assertEquals("Defense circuit, Tribal group", Circuit.DEFENSE.place());
    }

    private static Analysis.NamedChannel channel(int gate1, int gate2) {
        return Analysis.allChannels().stream().filter(c -> c.key().equals(gate1 + "-" + gate2)).findFirst().orElseThrow();
    }
}

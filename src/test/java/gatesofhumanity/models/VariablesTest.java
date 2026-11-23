package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class VariablesTest {
    private static final double TONE_DEGREES = Position.LINE_DEGREES / 36.0;

    // in the middle of a color and tone of gate 41, line 1
    private static Position at(Planet planet, int color, int tone) {
        return new Position(planet, Position.GATE_41_START + ((color - 1) * 6 + tone - 0.5) * TONE_DEGREES);
    }

    @Test
    void theSunsAndTheNorthNodesGiveTheArrows() {
        // the Earth and the South Node elsewhere, to tell them apart
        var personality = List.of(at(Planet.SUN, 3, 3), at(Planet.EARTH, 1, 1), at(Planet.NORTH_NODE, 6, 6), at(Planet.SOUTH_NODE, 1, 1));
        var design = List.of(at(Planet.SUN, 1, 2), at(Planet.EARTH, 6, 6), at(Planet.NORTH_NODE, 2, 5), at(Planet.SOUTH_NODE, 6, 6));
        var variables = Variables.of(new RaveChart(new PositionData(null, personality), new PositionData(null, design)));
        assertEquals(new Variables.Variable(Variables.Kind.DETERMINATION, 1, 2), variables.determination());
        assertEquals(new Variables.Variable(Variables.Kind.ENVIRONMENT, 2, 5), variables.environment());
        assertEquals(new Variables.Variable(Variables.Kind.MOTIVATION, 3, 3), variables.motivation());
        assertEquals(new Variables.Variable(Variables.Kind.PERSPECTIVE, 6, 6), variables.perspective());
        assertEquals("Appetite", variables.determination().colorName());
        assertEquals("Markets", variables.environment().colorName());
        assertEquals("Desire", variables.motivation().colorName());
        assertEquals("Personal", variables.perspective().colorName());
    }

    @Test
    void tonesOneToThreeFaceLeft() {
        for (var tone = 1; tone <= 6; ++tone) {
            assertEquals(tone <= 3, new Variables.Variable(Variables.Kind.PERSPECTIVE, 1, tone).left(), "tone " + tone);
        }
    }

    @Test
    void everyKindNamesSixColors() {
        for (var kind : Variables.Kind.values()) {
            for (var color = 1; color <= 6; ++color) {
                assertFalse(kind.color(color).isBlank());
            }
            assertThrows(IndexOutOfBoundsException.class, () -> kind.color(7));
        }
        assertEquals("Light", Variables.Kind.DETERMINATION.color(6));
        assertEquals("Shores", Variables.Kind.ENVIRONMENT.color(6));
        assertEquals("Innocence", Variables.Kind.MOTIVATION.color(6));
        assertEquals("Wanting", Variables.Kind.PERSPECTIVE.color(4));
    }

    @Test
    void oppositePointsShareTheColorAndTone() {
        var random = new Random(64);
        for (var i = 0; i < 2000; ++i) {
            var longitude = random.nextDouble() * 360.0;
            var a = new Position(Planet.SUN, longitude);
            var b = new Position(Planet.EARTH, (longitude + 180.0) % 360.0);
            assertEquals(a.gateLine() + "." + a.color() + "." + a.tone(), b.gateLine() + "." + b.color() + "." + b.tone(), "at " + longitude);
        }
    }
}

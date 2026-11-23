package gatesofhumanity.elements;

import gatesofhumanity.models.Analysis;
import org.junit.jupiter.api.Test;

import static gatesofhumanity.models.Entities.GATES;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GateColorsTest {
    // the Gates description says that the ring of gates is tinted in the color of their center
    @Test
    void eachGateIsTintedInTheColorOfItsCenter() {
        for (var gate = 1; gate <= 64; ++gate) {
            assertEquals(WheelImage.centerColor(Analysis.center(gate)), "gate-on-" + GATES.get(gate).color().id_, "gate " + gate);
        }
    }
}

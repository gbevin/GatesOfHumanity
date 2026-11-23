package gatesofhumanity.elements;

import gatesofhumanity.models.ActivatedGates;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.RaveChart;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Jovian Archive's Line Fixing article: in connection charts, another person can temporarily fix a
// Line in your design
public class ConnectionFixingTest {
    private static RaveChart birth(String local) {
        return Charts.birth(LocalDateTime.parse(local).atZone(ZoneId.of("America/Los_Angeles")));
    }

    private static List<ActivatedGates> activations(RaveChart first, RaveChart second) {
        return WheelImage.activations(new WheelImage.ChartSet(first, second));
    }

    // the fixed lines of the personality and design of the chart in the rows from the first given
    private static List<String> fixed(List<ActivatedGates> activations, int firstRow) {
        var fixed = new ArrayList<String>();
        for (var row = firstRow; row < firstRow + 2; ++row) {
            var side = row == firstRow ? "personality " : "design ";
            for (var gate : activations.get(row).activatedGates()) {
                for (var line : activations.get(row).activatedGateLines(gate).allActivatedLines()) {
                    for (var planet : line.planets()) {
                        if (line.isExalted()) {
                            fixed.add(side + gate + " " + planet + " exalted" + (line.isExaltedOnlyByOther() ? " by the other" : ""));
                        }
                        if (line.isInDetriment()) {
                            fixed.add(side + gate + " " + planet + " in detriment" + (line.isInDetrimentOnlyByOther() ? " by the other" : ""));
                        }
                    }
                }
            }
        }
        return fixed;
    }

    private static List<String> gained(List<String> alone, List<String> together) {
        var gained = new ArrayList<>(together);
        gained.removeAll(alone);
        gained.sort(null);
        return gained;
    }

    @Test
    void eachPersonFixesLinesOfTheOther() {
        var jobs = birth("1955-02-24T19:15");
        var wozniak = birth("1950-08-11T09:45");
        var together = activations(jobs, wozniak);
        // only for now, since a person's own chart doesn't fix them, while their own fixings stay theirs
        assertEquals(List.of("design 43 MERCURY exalted by the other", "design 50 NEPTUNE exalted by the other", "personality 62 URANUS exalted by the other"),
            gained(fixed(activations(jobs, null), 0), fixed(together, 0)));
        assertEquals(List.of("design 55 JUPITER in detriment by the other", "personality 13 EARTH in detriment by the other", "personality 55 JUPITER in detriment by the other"),
            gained(fixed(activations(wozniak, null), 0), fixed(together, 2)));
        assertTrue(fixed(activations(jobs, null), 0).stream().noneMatch(line -> line.endsWith("by the other")));
    }
}

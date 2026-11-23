package gatesofhumanity.models;

import java.util.*;

public class ActivatedGateLines {
    private final Map<Integer, ActivatedLine> lines_ = new HashMap<>();

    public Collection<ActivatedLine> allActivatedLines() {
        return Collections.unmodifiableCollection(lines_.values());
    }

    public void activateLine(int lineNumber, Planet planet) {
        var activated_line = lines_.computeIfAbsent(lineNumber, _ -> new ActivatedLine());
        activated_line.addPlanet(planet);
    }

    public ActivatedLine activatedLine(int lineNumber) {
        return lines_.get(lineNumber);
    }

    public void fixResonatingPlanet(List<Dignity> gateDignities, Planet planet, boolean byOther) {
        for (var entry_target : lines_.entrySet()) {
            var line_target = entry_target.getKey() - 1;
            var activated_gate_line_target = entry_target.getValue();
            var dignity = gateDignities.get(line_target);
            if (dignity.isExaltedBy(planet)) {
                activated_gate_line_target.exalt(byOther);
            }
            if (dignity.isDetrimentBy(planet)) {
                activated_gate_line_target.putInDetriment(byOther);
            }
        }
    }
}

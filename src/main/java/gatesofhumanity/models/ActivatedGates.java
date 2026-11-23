package gatesofhumanity.models;

import java.util.*;

import static gatesofhumanity.models.Entities.ACTIVATIONS;
import static gatesofhumanity.models.Entities.GATES;

public class ActivatedGates {
    private final Map<Integer, ActivatedGateLines> gatesLines_ = new HashMap<>();

    public boolean isGateActivated(int number) {
        return gatesLines_.containsKey(number);
    }

    public Set<Integer> activatedGates() {
        return Collections.unmodifiableSet(gatesLines_.keySet());
    }

    public Set<Planet> activatedPlanets(int gate) {
        Set<Planet> planets = new HashSet<>();
        var lines = activatedGateLines(gate);
        if (lines != null) {
            for (var line : lines.allActivatedLines()) {
                planets.addAll(line.planets());
            }
        }
        return planets;
    }

    public ActivatedGateLines activatedGateLines(int gateNumber) {
        return gatesLines_.get(gateNumber);
    }

    public Collection<ActivatedGateLines> allActivatedGateLines() {
        return Collections.unmodifiableCollection(gatesLines_.values());
    }

    public void activateGateLine(Position p) {
        var activated_lines = gatesLines_.computeIfAbsent(p.gateNumber(), _ -> new ActivatedGateLines());
        activated_lines.activateLine(p.gateLine(), p.planet());
    }

    public void fixResonatingPlanets() {
        for (var center : Center.values()) {
            for (var activation : ACTIVATIONS.get(center)) {
                var planets_gate1 = activatedPlanets(activation.gate1());
                var planets_gate2 = activatedPlanets(activation.gate2());

                // planets in the same gate will self-resonate across the lines
                fixResonatingPlanets(planets_gate1, activation.gate1(), false);
                fixResonatingPlanets(planets_gate2, activation.gate2(), false);
                // planets in harmonic gates will resonate the other gate's lines
                fixResonatingPlanets(planets_gate1, activation.gate2(), false);
                fixResonatingPlanets(planets_gate2, activation.gate1(), false);
            }
        }
    }

    public void fixResonatingPlanets(Set<Planet> planets, int gateTarget, boolean byOther) {
        var lines_target = activatedGateLines(gateTarget);
        if (planets.isEmpty() || lines_target == null) {
            return;
        }

        for (var planet_source : planets) {
            var dignities = GATES.get(gateTarget).dignities();
            lines_target.fixResonatingPlanet(dignities, planet_source, byOther);
        }
    }

    /**
     * Lets the planets of each fix the lines of the other, the Personality and Design of a chart,
     * or two charts, which is by another chart.
     */
    public void resonateWithinComposite(ActivatedGates composite, boolean otherChart) {
        for (var center : Center.values()) {
            for (var activation : ACTIVATIONS.get(center)) {
                var planets_gate1 = activatedPlanets(activation.gate1());
                var planets_gate2 = activatedPlanets(activation.gate2());

                composite.fixResonatingPlanets(planets_gate1, activation.gate1(), otherChart);
                composite.fixResonatingPlanets(planets_gate2, activation.gate2(), otherChart);
                composite.fixResonatingPlanets(planets_gate1, activation.gate2(), otherChart);
                composite.fixResonatingPlanets(planets_gate2, activation.gate1(), otherChart);

                var composite_planets_gate1 = composite.activatedPlanets(activation.gate1());
                var composite_planets_gate2 = composite.activatedPlanets(activation.gate2());

                fixResonatingPlanets(composite_planets_gate1, activation.gate1(), otherChart);
                fixResonatingPlanets(composite_planets_gate2, activation.gate2(), otherChart);
                fixResonatingPlanets(composite_planets_gate1, activation.gate2(), otherChart);
                fixResonatingPlanets(composite_planets_gate2, activation.gate1(), otherChart);
            }
        }
    }
}

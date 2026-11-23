package gatesofhumanity.models;

import java.util.List;

/**
 * The four arrows of a chart, from the color and tone of the Suns and the Nodes. The Earth and
 * the South Node are exactly 32 gates away, so they always share the color and tone.
 */
public record Variables(Variable determination, Variable environment, Variable motivation, Variable perspective) {
    public enum Kind {
        DETERMINATION("Determination", true, Planet.SUN, "Appetite", "Taste", "Thirst", "Touch", "Sound", "Light"),
        ENVIRONMENT("Environment", true, Planet.NORTH_NODE, "Caves", "Markets", "Kitchens", "Mountains", "Valleys", "Shores"),
        MOTIVATION("Motivation", false, Planet.SUN, "Fear", "Hope", "Desire", "Need", "Guilt", "Innocence"),
        PERSPECTIVE("Perspective", false, Planet.NORTH_NODE, "Survival", "Possibility", "Power", "Wanting", "Probability", "Personal");

        public final String label_;
        // the planet whose color and tone the variable comes from, in the design or the personality
        public final boolean design_;
        public final Planet planet_;
        private final List<String> colors_;

        Kind(String label, boolean design, Planet planet, String... colors) {
            label_ = label;
            design_ = design;
            planet_ = planet;
            colors_ = List.of(colors);
        }

        public String color(int color) {
            return colors_.get(color - 1);
        }

        // each color of the Design arrows has a version for each direction, the others only differ by the arrow
        public boolean hasSides() {
            return design_;
        }
    }

    public record Variable(Kind kind, int color, int tone) {
        public boolean left() {
            return tone <= 3;
        }

        public String colorName() {
            return kind.color(color);
        }

        static Variable of(Kind kind, RaveChart chart) {
            var position = Analysis.find((kind.design_ ? chart.design() : chart.personality()).positions(), kind.planet_);
            return new Variable(kind, position.color(), position.tone());
        }
    }

    public static Variables of(RaveChart chart) {
        return new Variables(
            Variable.of(Kind.DETERMINATION, chart),
            Variable.of(Kind.ENVIRONMENT, chart),
            Variable.of(Kind.MOTIVATION, chart),
            Variable.of(Kind.PERSPECTIVE, chart));
    }
}

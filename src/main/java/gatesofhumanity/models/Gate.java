package gatesofhumanity.models;

import java.util.List;
import java.util.Locale;

public record Gate(Color color, IChing iching, List<Dignity> dignities) {
    public enum Color {
        YELLOW, GREEN, BROWN, RED;

        // the id of the color in the templates
        public final String id_ = name().toLowerCase(Locale.ROOT);
    }
}

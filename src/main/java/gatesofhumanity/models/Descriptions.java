package gatesofhumanity.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The introductions to the parts of a chart that the Explore panel shows, from explore.tsv.
 */
public final class Descriptions {
    private Descriptions() {
    }

    private static final class Texts {
        static final Map<String, String> BY_KEY;

        static {
            var texts = new HashMap<String, String>();
            for (var columns : TsvResource.rows("explore.tsv")) {
                texts.put(columns[0], columns[1]);
            }
            BY_KEY = Map.copyOf(texts);
        }
    }

    public static String of(String key) {
        return Objects.requireNonNull(Texts.BY_KEY.get(key), "No description for " + key);
    }

    public static Map<String, String> all() {
        return Texts.BY_KEY;
    }
}

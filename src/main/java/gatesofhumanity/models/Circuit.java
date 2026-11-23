package gatesofhumanity.models;

import java.util.Arrays;
import java.util.List;

/**
 * The circuits that the 36 channels are grouped in, as the Jovian Archive groups them: the
 * Integration Channel goes with the Individual circuits without being one of them.
 */
public enum Circuit {
    KNOWING("Knowing circuit", Group.INDIVIDUAL, "1-8", "2-14", "3-60", "12-22", "20-57", "23-43", "24-61", "28-38", "39-55"),
    CENTERING("Centering circuit", Group.INDIVIDUAL, "10-34", "25-51"),
    INTEGRATION("Integration Channel", Group.INDIVIDUAL, "10-20", "10-57", "20-34", "34-57"),
    EGO("Ego circuit", Group.TRIBAL, "19-49", "21-45", "26-44", "32-54", "37-40"),
    DEFENSE("Defense circuit", Group.TRIBAL, "6-59", "27-50"),
    LOGIC("Logic circuit", Group.COLLECTIVE, "4-63", "5-15", "7-31", "9-52", "16-48", "17-62", "18-58"),
    ABSTRACT("Abstract circuit", Group.COLLECTIVE, "11-56", "13-33", "29-46", "30-41", "35-36", "42-53", "47-64");

    public enum Group {
        INDIVIDUAL("Individual"),
        TRIBAL("Tribal"),
        COLLECTIVE("Collective");

        public final String label_;

        Group(String label) {
            label_ = label;
        }
    }

    public final String label_;
    public final Group group_;
    private final List<String> channels_;

    Circuit(String label, Group group, String... channels) {
        label_ = label;
        group_ = group;
        channels_ = List.of(channels);
    }

    public static Circuit of(Analysis.NamedChannel channel) {
        return Arrays.stream(values()).filter(c -> c.channels_.contains(channel.key())).findFirst().orElseThrow();
    }

    /**
     * The channels of the circuit, in the order of the gates.
     */
    public List<Analysis.NamedChannel> channels() {
        return Analysis.allChannels().stream().filter(c -> channels_.contains(c.key())).toList();
    }

    /**
     * The circuit and its group, as a channel's place.
     */
    public String place() {
        return label_ + ", " + group_.label_ + " group";
    }
}

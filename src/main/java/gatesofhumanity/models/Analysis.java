package gatesofhumanity.models;

import java.util.*;
import java.util.stream.Stream;

import static gatesofhumanity.models.Entities.ACTIVATIONS;

/**
 * Type, Authority, Profile, Definition and Incarnation Cross of a birth chart, following
 * The Definitive Book of Human Design.
 */
public record Analysis(Type type, Authority authority, Profile profile, Definition definition,
                       IncarnationCross cross, Set<Center> definedCenters, List<NamedChannel> definedChannels) {
    /**
     * A channel with its gates in the order and with the name of the book.
     */
    public record NamedChannel(int gate1, int gate2, String name) {
        static final Comparator<NamedChannel> ORDER = Comparator.comparingInt((NamedChannel c) -> Math.min(c.gate1(), c.gate2()))
            .thenComparingInt(c -> Math.max(c.gate1(), c.gate2()));

        /**
         * The gates with the lower one first, as chart tools write them.
         */
        public String key() {
            return Math.min(gate1, gate2) + "-" + Math.max(gate1, gate2);
        }

        @Override
        public String toString() {
            return key() + " " + name;
        }
    }

    public enum Type {
        MANIFESTOR("Manifestor", "To Inform", "Peace", "Anger", "Closed and Repelling"),
        GENERATOR("Generator", "To Respond", "Satisfaction", "Frustration", "Open and Enveloping"),
        MANIFESTING_GENERATOR("Manifesting Generator", "To Respond", "Satisfaction", "Frustration", "Open and Enveloping"),
        PROJECTOR("Projector", "Wait for the Invitation", "Success", "Bitterness", "Focused and Absorbing"),
        REFLECTOR("Reflector", "Wait a Lunar Cycle", "Surprise", "Disappointment", "Resistant and Sampling");

        public final String label_;
        public final String strategy_;
        public final String signature_;
        public final String notSelf_;
        public final String aura_;

        Type(String label, String strategy, String signature, String notSelf, String aura) {
            label_ = label;
            strategy_ = strategy;
            signature_ = signature;
            notSelf_ = notSelf;
            aura_ = aura;
        }
    }

    public enum Authority {
        EMOTIONAL("Emotional", Center.SOLAR),
        SACRAL("Sacral", Center.SACRAL),
        SPLENIC("Splenic", Center.SPLEEN),
        EGO_MANIFESTED("Ego Manifested", Center.HEART),
        EGO_PROJECTED("Ego Projected", Center.HEART),
        SELF_PROJECTED("Self Projected", Center.G),
        // what Jovian Archive's charts show, other tools write None, Mental or Environmental
        ENVIRONMENT("Sounding Board", null),
        LUNAR("Lunar Cycle", null);

        public final String label_;
        // the defined center that the authority comes from, none for the last two
        public final Center center_;

        Authority(String label, Center center) {
            label_ = label;
            center_ = center;
        }
    }

    public enum Definition {
        NONE("No Definition"),
        SINGLE("Single Definition"),
        // a single gate would join the two areas
        SIMPLE_SPLIT("Split Definition (Simple)"),
        // joining the two areas takes more than one gate
        WIDE_SPLIT("Split Definition (Wide)"),
        TRIPLE_SPLIT("Triple Split Definition"),
        QUADRUPLE_SPLIT("Quadruple Split Definition");

        public final String label_;

        Definition(String label) {
            label_ = label;
        }
    }

    public enum Angle {
        RIGHT("Right Angle Cross"),
        JUXTAPOSITION("Juxtaposition Cross"),
        LEFT("Left Angle Cross");

        public final String label_;

        Angle(String label) {
            label_ = label;
        }
    }

    public record Profile(int personalityLine, int designLine) {
        private static final String[] LINE_NAMES = {"Investigator", "Hermit", "Martyr", "Opportunist", "Heretic", "Role Model"};

        public Angle angle() {
            if (personalityLine == 4 && designLine == 1) {
                return Angle.JUXTAPOSITION;
            }
            return personalityLine >= 5 ? Angle.LEFT : Angle.RIGHT;
        }

        /**
         * The name of a line of a profile, from 1 to 6.
         */
        public static String lineName(int line) {
            return LINE_NAMES[line - 1];
        }

        public String lines() {
            return personalityLine + "/" + designLine;
        }

        public String lineNames() {
            return lineName(personalityLine) + " / " + lineName(designLine);
        }

        public String name() {
            return lines() + " " + lineNames();
        }
    }

    public record IncarnationCross(Angle angle, String name, int personalitySun, int personalityEarth,
                                   int designSun, int designEarth) {
        public String title() {
            return angle.label_ + " of " + name;
        }

        @Override
        public String toString() {
            return title() + " (" + personalitySun + "/" + personalityEarth + " | " +
                   designSun + "/" + designEarth + ")";
        }
    }

    private static final Set<Center> MOTORS = EnumSet.of(Center.SACRAL, Center.HEART, Center.SOLAR, Center.ROOT);

    record Channel(int gate1, int gate2, Center center1, Center center2) {
        boolean isDefinedBy(Set<Integer> gates) {
            return gates.contains(gate1) && gates.contains(gate2);
        }
    }

    static List<Channel> channels() {
        return Channels.ALL;
    }

    static NamedChannel name(Channel channel) {
        return ChannelNames.of(channel);
    }

    private static final class Channels {
        static final List<Channel> ALL;

        static {
            // every channel is listed under both its centers, with that center's gate first
            var center_of_gate = new HashMap<Integer, Center>();
            ACTIVATIONS.forEach((center, activations) -> activations.forEach(a -> center_of_gate.put(a.gate1(), center)));
            var channels = new ArrayList<Channel>();
            ACTIVATIONS.forEach((center, activations) -> activations.forEach(a -> {
                if (a.gate1() < a.gate2()) {
                    channels.add(new Channel(a.gate1(), a.gate2(), center, center_of_gate.get(a.gate2())));
                }
            }));
            ALL = List.copyOf(channels);
        }
    }

    private static final class ChannelNames {
        static final Map<Set<Integer>, NamedChannel> BY_GATES = new HashMap<>();

        static {
            for (var columns : TsvResource.rows("channels.tsv")) {
                var channel = new NamedChannel(Integer.parseInt(columns[0]), Integer.parseInt(columns[1]), columns[2]);
                BY_GATES.put(Set.of(channel.gate1(), channel.gate2()), channel);
            }
        }

        static NamedChannel of(Channel channel) {
            return Objects.requireNonNull(BY_GATES.get(Set.of(channel.gate1(), channel.gate2())), "No name for " + channel);
        }
    }

    private static final class Crosses {
        static final Map<String, String> NAMES = new HashMap<>();

        static {
            for (var columns : TsvResource.rows("crosses.tsv")) {
                NAMES.put(columns[0] + "/" + columns[1], columns[2]);
            }
        }

        static String name(int personalitySun, Angle angle) {
            var key = personalitySun + "/" + switch (angle) {
                case RIGHT -> "right";
                case JUXTAPOSITION -> "juxtaposition";
                case LEFT -> "left";
            };
            return Objects.requireNonNull(NAMES.get(key), "No incarnation cross for " + key);
        }
    }

    public static Position find(List<Position> positions, Planet planet) {
        return positions.stream().filter(p -> p.planet() == planet).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The chart has no " + planet));
    }

    /**
     * The gates that the Personality and Design of a chart activate.
     */
    public static Set<Integer> gates(RaveChart chart) {
        var gates = new HashSet<Integer>();
        for (var side : List.of(chart.personality().positions(), chart.design().positions())) {
            for (var position : side) {
                // Chiron doesn't activate gates
                if (position.planet() != Planet.CHIRON) {
                    gates.add(position.gateNumber());
                }
            }
        }
        return gates;
    }

    /**
     * The channels that the transits define together with the gates of the charts, which the
     * charts don't define by themselves, in the order of their gates.
     */
    public static List<NamedChannel> completedByTransits(RaveChart transit, List<RaveChart> charts) {
        var together = new HashSet<Integer>();
        charts.forEach(chart -> together.addAll(gates(chart)));
        var defined = definedChannels(together);
        together.addAll(gates(transit));
        return definedChannels(together).stream().filter(channel -> !defined.contains(channel)).toList();
    }

    /**
     * The centers that the charts define together with the transits, as the body graph shows them.
     */
    public static Set<Center> definedCentersWithTransits(RaveChart transit, List<RaveChart> charts) {
        var together = new HashSet<>(gates(transit));
        charts.forEach(chart -> together.addAll(gates(chart)));
        return definedCenters(together);
    }

    /**
     * Every channel, in the order of their gates.
     */
    public static List<NamedChannel> allChannels() {
        return definedChannels(Channels.ALL.stream());
    }

    /**
     * The channels that a set of activated gates defines, in the order of their gates.
     */
    public static List<NamedChannel> definedChannels(Set<Integer> gates) {
        return definedChannels(Channels.ALL.stream().filter(c -> c.isDefinedBy(gates)));
    }

    private static List<NamedChannel> definedChannels(Stream<Channel> channels) {
        return channels.map(ChannelNames::of).sorted(NamedChannel.ORDER).toList();
    }

    /**
     * The centers that the channels of a set of activated gates define.
     */
    public static Set<Center> definedCenters(Set<Integer> gates) {
        var centers = EnumSet.noneOf(Center.class);
        for (var channel : Channels.ALL) {
            if (channel.isDefinedBy(gates)) {
                centers.add(channel.center1());
                centers.add(channel.center2());
            }
        }
        return centers;
    }

    /**
     * The center that a gate is in.
     */
    public static Center center(int gate) {
        for (var channel : Channels.ALL) {
            if (channel.gate1() == gate) {
                return channel.center1();
            }
            if (channel.gate2() == gate) {
                return channel.center2();
            }
        }
        throw new IllegalArgumentException("No center for gate " + gate);
    }

    /**
     * The channels that a gate is one end of, in the order of their gates.
     */
    public static List<NamedChannel> channels(int gate) {
        return definedChannels(Channels.ALL.stream().filter(c -> c.gate1() == gate || c.gate2() == gate));
    }

    /**
     * The two centers that a channel connects.
     */
    public static List<Center> centers(NamedChannel channel) {
        var gates = Set.of(channel.gate1(), channel.gate2());
        var found = Channels.ALL.stream().filter(c -> Set.of(c.gate1(), c.gate2()).equals(gates)).findFirst().orElseThrow();
        return List.of(found.center1(), found.center2());
    }

    public static Analysis of(RaveChart chart) {
        var gates = gates(chart);
        return of(gates,
            find(chart.personality().positions(), Planet.SUN), find(chart.personality().positions(), Planet.EARTH),
            find(chart.design().positions(), Planet.SUN), find(chart.design().positions(), Planet.EARTH));
    }

    static Analysis of(Set<Integer> gates, Position personalitySun, Position personalityEarth,
                       Position designSun, Position designEarth) {
        var defined = Channels.ALL.stream().filter(c -> c.isDefinedBy(gates)).toList();

        // the groups of defined centers that the defined channels connect
        var group = new EnumMap<Center, Center>(Center.class);
        for (var channel : defined) {
            group.putIfAbsent(channel.center1(), channel.center1());
            group.putIfAbsent(channel.center2(), channel.center2());
        }
        for (var channel : defined) {
            var root1 = root(group, channel.center1());
            var root2 = root(group, channel.center2());
            if (root1 != root2) {
                group.put(root1, root2);
            }
        }
        var defined_centers = EnumSet.noneOf(Center.class);
        defined_centers.addAll(group.keySet());
        var groups = new HashSet<Center>();
        for (var center : defined_centers) {
            groups.add(root(group, center));
        }

        var motor_to_throat = defined_centers.contains(Center.THROAT) &&
                              MOTORS.stream().anyMatch(m -> defined_centers.contains(m) && root(group, m) == root(group, Center.THROAT));

        var type = type(defined_centers, motor_to_throat);
        var authority = authority(defined_centers, type);

        var definition = switch (groups.size()) {
            case 0 -> Definition.NONE;
            case 1 -> Definition.SINGLE;
            case 2 -> bridgedByOneGate(gates, group, defined_centers) ? Definition.SIMPLE_SPLIT : Definition.WIDE_SPLIT;
            case 3 -> Definition.TRIPLE_SPLIT;
            default -> Definition.QUADRUPLE_SPLIT;
        };

        var profile = new Profile(personalitySun.gateLine(), designSun.gateLine());
        var angle = profile.angle();
        var cross = new IncarnationCross(angle, Crosses.name(personalitySun.gateNumber(), angle),
            personalitySun.gateNumber(), personalityEarth.gateNumber(), designSun.gateNumber(), designEarth.gateNumber());

        var named = definedChannels(defined.stream());
        return new Analysis(type, authority, profile, definition, cross, Collections.unmodifiableSet(defined_centers), named);
    }

    private static Type type(Set<Center> definedCenters, boolean motorToThroat) {
        if (definedCenters.isEmpty()) {
            return Type.REFLECTOR;
        }
        if (definedCenters.contains(Center.SACRAL)) {
            return motorToThroat ? Type.MANIFESTING_GENERATOR : Type.GENERATOR;
        }
        return motorToThroat ? Type.MANIFESTOR : Type.PROJECTOR;
    }

    // the first defined center in the order of the book decides
    private static Authority authority(Set<Center> definedCenters, Type type) {
        for (var authority : List.of(Authority.EMOTIONAL, Authority.SACRAL, Authority.SPLENIC,
                                     type == Type.MANIFESTOR ? Authority.EGO_MANIFESTED : Authority.EGO_PROJECTED, Authority.SELF_PROJECTED)) {
            if (definedCenters.contains(authority.center_)) {
                return authority;
            }
        }
        return definedCenters.isEmpty() ? Authority.LUNAR : Authority.ENVIRONMENT;
    }

    private static boolean bridgedByOneGate(Set<Integer> gates, Map<Center, Center> group, Set<Center> definedCenters) {
        for (var channel : Channels.ALL) {
            var one_gate = gates.contains(channel.gate1()) != gates.contains(channel.gate2());
            if (one_gate && definedCenters.contains(channel.center1()) && definedCenters.contains(channel.center2()) &&
                root(group, channel.center1()) != root(group, channel.center2())) {
                return true;
            }
        }
        return false;
    }

    private static Center root(Map<Center, Center> group, Center center) {
        var root = center;
        while (group.get(root) != root) {
            root = group.get(root);
        }
        return root;
    }
}

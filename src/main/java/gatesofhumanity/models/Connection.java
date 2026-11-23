package gatesofhumanity.models;

import java.util.*;

/**
 * How the channels of two people combine, following the relationship dynamics of The Definitive
 * Book of Human Design.
 */
public record Connection(List<Analysis.NamedChannel> electromagnetic, List<Analysis.NamedChannel> companionship,
                         List<Owned> dominance, List<Owned> compromise, Set<Center> definedCenters) {
    /**
     * A channel that only one of the two people has completely, the first or the second.
     */
    public record Owned(Analysis.NamedChannel channel, int owner) {
        /**
         * The name of the person with the whole channel, from the names of the first and second.
         */
        public String ownerName(String firstName, String secondName) {
            return owner == 1 ? firstName : secondName;
        }
    }

    public static Connection of(RaveChart first, RaveChart second) {
        return of(Analysis.gates(first), Analysis.gates(second));
    }

    static Connection of(Set<Integer> first, Set<Integer> second) {
        var electromagnetic = new ArrayList<Analysis.NamedChannel>();
        var companionship = new ArrayList<Analysis.NamedChannel>();
        var dominance = new ArrayList<Owned>();
        var compromise = new ArrayList<Owned>();
        var centers = EnumSet.noneOf(Center.class);

        for (var channel : Analysis.channels()) {
            var named = Analysis.name(channel);
            var first_gate1 = first.contains(channel.gate1());
            var first_gate2 = first.contains(channel.gate2());
            var second_gate1 = second.contains(channel.gate1());
            var second_gate2 = second.contains(channel.gate2());
            var first_whole = first_gate1 && first_gate2;
            var second_whole = second_gate1 && second_gate2;

            if (first_whole && second_whole) {
                companionship.add(named);
            } else if (first_whole || second_whole) {
                var owner = first_whole ? 1 : 2;
                var other_has_a_gate = first_whole ? second_gate1 || second_gate2 : first_gate1 || first_gate2;
                (other_has_a_gate ? compromise : dominance).add(new Owned(named, owner));
            } else if ((first_gate1 && second_gate2) || (first_gate2 && second_gate1)) {
                electromagnetic.add(named);
            } else {
                continue;
            }
            centers.add(channel.center1());
            centers.add(channel.center2());
        }

        electromagnetic.sort(Analysis.NamedChannel.ORDER);
        companionship.sort(Analysis.NamedChannel.ORDER);
        dominance.sort(Comparator.comparing(Owned::channel, Analysis.NamedChannel.ORDER));
        compromise.sort(Comparator.comparing(Owned::channel, Analysis.NamedChannel.ORDER));
        return new Connection(List.copyOf(electromagnetic), List.copyOf(companionship), List.copyOf(dominance),
            List.copyOf(compromise), Collections.unmodifiableSet(centers));
    }
}

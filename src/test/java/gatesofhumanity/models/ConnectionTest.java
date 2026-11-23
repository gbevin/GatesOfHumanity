package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConnectionTest {
    private static RaveChart birth(String local, String zone) {
        return Charts.birth(LocalDateTime.parse(local).atZone(ZoneId.of(zone)).withZoneSameInstant(ZoneOffset.UTC));
    }

    // "Together, Jobs and Wozniak formed five electromagnetic Channels", Jovian Archive's "Steve
    // Jobs' Channels and Openness", with Wozniak's birth time from Astro-Databank, rated AA
    @Test
    void jobsAndWozniak() {
        var connection = Connection.of(birth("1955-02-24T19:15", "America/Los_Angeles"), birth("1950-08-11T09:45", "America/Los_Angeles"));
        assertEquals(5, connection.electromagnetic().size(), connection.toString());
    }

    // The Definitive Book of Human Design, connection charts
    @Test
    void theFourKindsOfConnection() {
        var electromagnetic = Connection.of(Set.of(34), Set.of(20));
        assertEquals("[20-34 Charisma]", electromagnetic.electromagnetic().toString());
        assertEquals(Set.of(Center.SACRAL, Center.THROAT), electromagnetic.definedCenters());

        var dominance = Connection.of(Set.of(34, 20), Set.of(1));
        assertEquals("[Owned[channel=20-34 Charisma, owner=1]]", dominance.dominance().toString());
        assertEquals("[Owned[channel=20-34 Charisma, owner=2]]", Connection.of(Set.of(), Set.of(34, 20)).dominance().toString());

        var compromise = Connection.of(Set.of(34, 20), Set.of(20));
        assertEquals("[Owned[channel=20-34 Charisma, owner=1]]", compromise.compromise().toString());
        assertEquals("[Owned[channel=20-34 Charisma, owner=2]]", Connection.of(Set.of(34), Set.of(34, 20)).compromise().toString());

        var companionship = Connection.of(Set.of(34, 20), Set.of(20, 34));
        assertEquals("[20-34 Charisma]", companionship.companionship().toString());

        var nothing = Connection.of(Set.of(34), Set.of(34));
        assertEquals(0, nothing.electromagnetic().size() + nothing.companionship().size() + nothing.dominance().size() + nothing.compromise().size());
        assertEquals(Set.of(), nothing.definedCenters());
    }
}

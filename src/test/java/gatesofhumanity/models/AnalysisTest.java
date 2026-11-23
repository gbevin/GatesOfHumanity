package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static gatesofhumanity.models.Analysis.Authority.*;
import static gatesofhumanity.models.Analysis.Definition.*;
import static gatesofhumanity.models.Analysis.Type.*;
import static gatesofhumanity.models.TestPositions.at;
import static org.junit.jupiter.api.Assertions.*;

public class AnalysisTest {
    private static Analysis birth(int year, int month, int day, int hour, int minute, String zone) {
        var moment = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).withZoneSameInstant(ZoneOffset.UTC);
        return Analysis.of(Charts.birth(moment));
    }

    // "Steve Jobs' Type, Authority, and Profile", "Channels and Openness" and "Incarnation Cross",
    // Jovian Archive, with his widely published birth time
    @Test
    void steveJobs() {
        var jobs = birth(1955, 2, 24, 19, 15, "America/Los_Angeles");
        assertEquals(GENERATOR, jobs.type());
        assertEquals(EMOTIONAL, jobs.authority());
        assertEquals("6/3 Role Model / Martyr", jobs.profile().name());
        // no gate of 34-20, 12-22 or 35-36 joins Root, Sacral and Solar Plexus to Ajna and Throat
        assertEquals(WIDE_SPLIT, jobs.definition());
        // the channels 30-41, 9-52 and 17-62
        assertEquals(Set.of(Center.ROOT, Center.SOLAR, Center.SACRAL, Center.AJNA, Center.THROAT), jobs.definedCenters());
        assertEquals("[9-52 Concentration, 17-62 Acceptance, 30-41 Recognition]", jobs.definedChannels().toString());
        assertEquals("Left Angle Cross of Spirit (55/59 | 9/16)", jobs.cross().toString());
    }

    // "Keynoting Stephen King's Human Design", parts 1, 2 and 4, Jovian Archive, with his widely
    // published birth time
    @Test
    void stephenKing() {
        var king = birth(1947, 9, 21, 1, 30, "America/New_York");
        assertEquals(MANIFESTING_GENERATOR, king.type());
        assertEquals(SACRAL, king.authority());
        assertEquals("6/2 Role Model / Hermit", king.profile().name());
        assertEquals(SINGLE, king.definition());
        assertEquals("Left Angle Cross of the Plane (6/36 | 15/10)", king.cross().toString());
    }

    private static Analysis withGates(int... gates) {
        var set = new HashSet<Integer>();
        for (var gate : gates) {
            set.add(gate);
        }
        return Analysis.of(set, at(Planet.SUN, 13, 1), at(Planet.EARTH, 7, 1), at(Planet.SUN, 1, 3), at(Planet.EARTH, 2, 3));
    }

    // The Definitive Book of Human Design, sections three and four
    @Test
    void typesAndAuthorities() {
        assertEquals(REFLECTOR, withGates().type());
        assertEquals("Resistant and Sampling", withGates().type().aura_);
        assertEquals("Closed and Repelling", withGates(21, 45).type().aura_);
        assertEquals("Focused and Absorbing", withGates(17, 62).type().aura_);
        assertEquals("Open and Enveloping", withGates(34, 20).type().aura_);
        assertEquals(LUNAR, withGates(1, 2, 3).authority());
        assertEquals(Set.of(), withGates(1, 2, 3).definedCenters());

        assertEquals(PROJECTOR, withGates(17, 62).type());
        assertEquals(ENVIRONMENT, withGates(17, 62).authority());
        assertEquals(ENVIRONMENT, withGates(64, 47).authority());

        assertEquals(PROJECTOR, withGates(1, 8).type());
        assertEquals(SELF_PROJECTED, withGates(1, 8).authority());

        assertEquals(PROJECTOR, withGates(25, 51).type());
        assertEquals(EGO_PROJECTED, withGates(25, 51).authority());

        assertEquals(MANIFESTOR, withGates(21, 45).type());
        assertEquals(EGO_MANIFESTED, withGates(21, 45).authority());

        // a motor connected to the Throat through other centers
        assertEquals(MANIFESTOR, withGates(54, 32, 16, 48).type());
        assertEquals(SPLENIC, withGates(54, 32, 16, 48).authority());
        assertEquals(PROJECTOR, withGates(54, 32).type());

        assertEquals(GENERATOR, withGates(5, 15).type());
        assertEquals(SACRAL, withGates(5, 15).authority());
        assertEquals(MANIFESTING_GENERATOR, withGates(34, 20).type());
        assertEquals(MANIFESTING_GENERATOR, withGates(5, 15, 1, 8).type());
        // any motor connected to the Throat makes a Generator a Manifesting Generator
        assertEquals(MANIFESTING_GENERATOR, withGates(59, 6, 21, 45).type());
        assertEquals(EMOTIONAL, withGates(59, 6, 21, 45).authority());
        assertEquals(EMOTIONAL, withGates(37, 40, 34, 20).authority());
    }

    @Test
    void chironDoesntActivateGates() {
        var personality = new ArrayList<>(List.of(at(Planet.SUN, 13, 1), at(Planet.EARTH, 7, 1), at(Planet.CHIRON, 33, 2)));
        var design = new ArrayList<>(List.of(at(Planet.SUN, 1, 3), at(Planet.EARTH, 2, 3)));
        var chart = new RaveChart(new PositionData(null, personality), new PositionData(null, design));
        assertEquals(REFLECTOR, Analysis.of(chart).type());
        personality.add(at(Planet.MARS, 33, 2));
        assertEquals(PROJECTOR, Analysis.of(chart).type());
    }

    @Test
    void definitions() {
        assertEquals(NONE, withGates().definition());
        assertEquals(SINGLE, withGates(34, 20, 1, 8).definition());
        // Ajna and Throat, Sacral and Root, which the hanging gate 34 would join through 34-20
        assertEquals(SIMPLE_SPLIT, withGates(17, 62, 52, 9, 34).definition());
        assertEquals(SIMPLE_SPLIT, withGates(17, 62, 52, 9, 20).definition());
        // gates 20 and 34 are both needed, or another channel through an undefined center
        assertEquals(WIDE_SPLIT, withGates(17, 62, 52, 9).definition());
        assertEquals(WIDE_SPLIT, withGates(17, 62, 52, 9, 57).definition());
        assertEquals(TRIPLE_SPLIT, withGates(64, 47, 32, 54, 59, 6).definition());
        // Head and Ajna, Spleen and Root, Sacral and Solar Plexus, G and Throat
        assertEquals(QUADRUPLE_SPLIT, withGates(64, 47, 32, 54, 59, 6, 1, 8).definition());
    }

    // The Definitive Book of Human Design, section seven, the three geometries
    @Test
    void profilesAndAngles() {
        for (var profile : new int[][]{{1, 3}, {1, 4}, {2, 4}, {2, 5}, {3, 5}, {3, 6}, {4, 6}}) {
            assertEquals(Analysis.Angle.RIGHT, new Analysis.Profile(profile[0], profile[1]).angle());
        }
        assertEquals(Analysis.Angle.JUXTAPOSITION, new Analysis.Profile(4, 1).angle());
        for (var profile : new int[][]{{5, 1}, {5, 2}, {6, 2}, {6, 3}}) {
            assertEquals(Analysis.Angle.LEFT, new Analysis.Profile(profile[0], profile[1]).angle());
        }
        assertEquals("4/1 Opportunist / Investigator", new Analysis.Profile(4, 1).name());
        assertEquals("Right Angle Cross of the Sphinx (13/7 | 1/2)", withGates().cross().toString());
    }

    @Test
    void channelTable() {
        var channels = Analysis.channels();
        assertEquals(36, channels.size());
        var names = new HashSet<String>();
        for (var channel : channels) {
            assertNotEquals(channel.center1(), channel.center2(), channel.toString());
            names.add(Analysis.name(channel).name());
        }
        assertEquals(36, names.size());
        assertEquals("[20-34 Charisma]", withGates(20, 34).definedChannels().toString());
    }
}

package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Charts of public figures as published by others, with birth times from Astro-Databank rated AA
 * or A unless noted. The book is The Definitive Book of Human Design, the web references are
 * totalhumandesign.com.
 */
public class ReferenceChartsTest {
    private record Reference(String name, String birth, String offset, String expected) {
    }

    private static final List<Reference> REFERENCES = List.of(
        // the book's sample chart overviews
        new Reference("Henry Miller", "1891-12-26T12:30", "-05:00", "Manifestor | Splenic | 1/4 | Split (Simple) | Right Angle Cross of Service"),
        new Reference("Julia Child", "1912-08-15T23:30", "-08:00", "Generator | Sacral | 5/1 | Single | Left Angle Cross of Revolution"),
        new Reference("Ernest Hemingway", "1899-07-21T08:00", "-06:00", "Manifesting Generator | Emotional | 3/5 | Split (Simple) | Right Angle Cross of Laws"),
        new Reference("Ringo Starr", "1940-07-07T00:05", "+01:00", "Projector | Splenic | 6/2 | Split (Simple) | Left Angle Cross of Individualism"),
        // the web references
        new Reference("Albert Einstein", "1879-03-14T11:30", "+00:39:58", "Generator | Emotional | 1/4 | Split | Right Angle Cross of Eden (36/6 | 11/12)"),
        new Reference("Donald Trump", "1946-06-14T10:54", "-04:00", "Manifesting Generator | Emotional | 1/3 | Single | Right Angle Cross of Eden (12/11 | 36/6)"),
        new Reference("Bill Gates", "1955-10-28T22:00", "-08:00", "Generator | Sacral | 4/6 | Split | Right Angle Cross of the Unexpected (28/27 | 31/41)"),
        new Reference("Barack Obama", "1961-08-04T19:24", "-10:00", "Projector | Emotional | 6/2 | Single | Left Angle Cross of Refinement (33/19 | 2/1)"),
        new Reference("Princess Diana", "1961-07-01T19:45", "+01:00", "Projector | Emotional | 1/3 | Split | Right Angle Cross of Tension (39/38 | 21/48)"),
        new Reference("H.P. Lovecraft", "1890-08-20T09:00", "-05:00", "Projector | Emotional | 4/6 | Split"),
        new Reference("Adolf Hitler", "1889-04-20T18:30", "+00:52:16", "Manifestor | Splenic | 5/1 | Single | Left Angle Cross of Wishes (3/50 | 41/31)"),
        // German summer time all year in 1942
        new Reference("Hans-Juergen Baeumler", "1942-01-28T10:00", "+02:00", "Manifestor | Ego Manifested | 1/3 | Split"),
        new Reference("Wendell Berry", "1934-08-05T23:33", "-04:00", "Projector | Self Projected | 6/2 | Single | Left Angle Cross of Refinement (33/19 | 2/1)"),
        new Reference("John Wooden", "1910-10-14T01:00", "-06:00", "Projector | Sounding Board | 6/2 | Single | Left Angle Cross of the Clarion (57/51 | 62/61)"),
        // Astro-Databank rates this time C, the only Reflector found
        new Reference("Sandra Bullock", "1964-07-26T03:15", "-04:00", "Reflector | Lunar Cycle | 2/4 | No | Right Angle Cross of the Unexpected (31/41 | 27/28)"));

    private static String describe(Analysis a, String expected) {
        var definition = a.definition().label_.replace(" Definition", "");
        // most references don't say which kind of split
        if (!expected.contains("Split (")) {
            definition = definition.replaceAll(" \\(\\w+\\)", "");
        }
        var parts = new ArrayList<>(List.of(a.type().label_, a.authority().label_,
            a.profile().personalityLine() + "/" + a.profile().designLine(), definition));
        // a cross is compared with or without its gates, as the reference gives it
        var cross = a.cross().toString();
        var expected_parts = expected.split(" \\| ", 5);
        if (expected_parts.length == 5) {
            parts.add(expected_parts[4].contains("(") ? cross : cross.substring(0, cross.indexOf(" (")));
        }
        return String.join(" | ", parts);
    }

    private static RaveChart chart(String birth, String offset) {
        return Charts.birth(LocalDateTime.parse(birth).atOffset(ZoneOffset.of(offset)).atZoneSameInstant(ZoneOffset.UTC));
    }

    @Test
    void publishedCharts() {
        for (var reference : REFERENCES) {
            var analysis = Analysis.of(chart(reference.birth(), reference.offset()));
            assertEquals(reference.expected(), describe(analysis, reference.expected()), reference.name());
        }
    }

    private static String activations(List<Position> positions) {
        return positions.stream().filter(p -> p.planet() != Planet.CHIRON)
            .map(p -> p.planet().id_ + " " + p.gateNumber() + "." + p.gateLine()).collect(Collectors.joining(", "));
    }

    // the book's charts, as printed with their channels
    @Test
    void theBooksActivations() {
        var miller = chart("1891-12-26T12:30", "-05:00");
        assertEquals("[16-48, 18-58, 28-38, 47-64]", sortedChannels(miller));
        var child = chart("1912-08-15T23:30", "-08:00");
        assertEquals("[2-14, 3-60, 5-15]", sortedChannels(child));
        var hemingway = chart("1899-07-21T08:00", "-06:00");
        assertEquals("[3-60, 10-34, 11-56, 28-38, 35-36]", sortedChannels(hemingway));
        var starr = chart("1940-07-07T00:05", "+01:00");
        assertEquals("[20-57, 25-51]", sortedChannels(starr));

        // the preface shows the chart of a birth five hours before 00:05 in Montreal, as if that
        // time were in UTC
        var ra = chart("1948-04-09T00:05", "Z");
        assertEquals("sun 51.5, earth 57.5, moon 21.4, mercury 25.2, venus 20.5, mars 7.6, jupiter 10.1, saturn 7.3, " +
                     "uranus 12.1, neptune 48.3, pluto 33.6, north_node 2.1, south_node 1.1", activations(ra.personality().positions()));
        assertEquals("sun 61.1, earth 62.1, moon 41.1, mercury 60.1, venus 49.5, mars 40.2, jupiter 26.1, saturn 4.3, " +
                     "uranus 12.1, neptune 48.4, pluto 7.2, north_node 23.3, south_node 43.3", activations(ra.design().positions()));
        assertEquals("Manifestor | Splenic | 5/1 | Single | Left Angle Cross of the Clarion (51/57 | 61/62)",
            describe(Analysis.of(ra), "x | x | x | x | x (x)"));
    }

    private static String sortedChannels(RaveChart chart) {
        return Analysis.of(chart).definedChannels().stream().map(Analysis.NamedChannel::key).toList().toString();
    }
}

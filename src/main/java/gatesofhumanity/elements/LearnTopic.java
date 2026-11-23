package gatesofhumanity.elements;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The Learn pages, which show the descriptions of the Explore panels as pages that search engines
 * can read, a page for each part of a chart.
 */
public enum LearnTopic {
    CHARTS("charts", "Choosing a Chart", "All the charts", "Which Human Design Chart to Use",
        "What the birth, transit, birth transit, connection and connection transit charts and the transit tables show, and when to choose each one."),
    CHART("the-chart", "The Chart", "Reading a chart", "How a Human Design Chart Is Built",
        "How a Human Design chart brings together astrology and the I Ching, from the zodiac and the planets to the 64 gates and the body graph."),
    CENTERS("centers", "Centers", "All nine centers", "The Nine Human Design Centers, Defined and Open",
        "The nine centers of the Human Design body graph, from the Head to the Root, and what each one means when it's defined and when it's open."),
    TYPES("types", "Types", "All the Types", "The Human Design Types and Their Strategies",
        "Manifestors, Generators, Manifesting Generators, Projectors and Reflectors in Human Design, with the Strategy, Signature, Not-Self Theme and Aura of each Type."),
    AUTHORITIES("authorities", "Authorities", "All the Authorities", "The Human Design Inner Authorities",
        "The Human Design Authorities, from Emotional, Sacral and Splenic to Ego, Self Projected, Sounding Board and Lunar Cycle, and how each one makes decisions."),
    PROFILES("profiles", "Profiles and Crosses", "All the Profiles and Crosses", "Human Design Profiles, Lines and Incarnation Crosses",
        "The six lines of the Human Design Profile, from the Investigator to the Role Model, and the Right Angle, Juxtaposition and Left Angle Incarnation Crosses."),
    DEFINITION("definition", "Definition", "Every kind of Definition", "Human Design Definition and Splits",
        "Single, split, triple split and quadruple split Definition in Human Design, or none at all, and what each one says about how the defined centers connect."),
    CHANNELS("channels", "Channels", "All 36 channels", "The 36 Human Design Channels",
        "All 36 channels of the Human Design body graph, the centers each one connects, its theme and its Individual, Tribal or Collective circuit."),
    GATES("gates", "Gates", "All 64 gates", "The 64 Human Design Gates and Their Hexagrams",
        "All 64 gates of the Human Design body graph, with their names, the I Ching hexagrams they come from, their centers, channels and lines."),
    PLANETS("planets", "Planets", "All the planets", "The Planets in Human Design",
        "What the Sun, the Earth, the Moon, the Nodes and the planets from Mercury to Pluto stand for in a Human Design chart, and how Chiron is shown."),
    VARIABLES("variables", "Variables", "All the Variables", "The Human Design Variables and Their Arrows",
        "Determination, Environment, Motivation and Perspective, the four Variables of Human Design, their six colors and what a left or right arrow means."),
    TRANSITS("transits", "Transits", "Everything about transits", "Human Design Transits",
        "How transits activate gates, define centers, complete channels with one or two people, and mark the Saturn return, Uranus opposition and Chiron return."),
    CONNECTIONS("connections", "Connections", "Everything about connections", "Human Design Connection Charts",
        "How two Human Design charts meet in a connection chart, with electromagnetic, companionship, dominance and compromise channels.");

    public static final String PATTERN = String.join("|", Arrays.stream(values()).map(topic -> Pattern.quote(topic.slug_)).toList());

    public final String slug_;
    // what the page holds, for the links of the Explore panel that lead to it
    public final String all_;
    private final PageDescription page_;

    LearnTopic(String slug, String heading, String all, String title, String description) {
        slug_ = slug;
        all_ = all;
        page_ = new PageDescription((c, site) -> c.urlFor(site.learnTopic).param("topic", slug), heading, title, description);
    }

    public PageDescription page() {
        return page_;
    }

    /**
     * The anchor of a section of a Learn page, from its label.
     */
    static String anchor(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * The topic of a URL, null for the index of the topics.
     */
    static LearnTopic of(String slug) {
        for (var topic : values()) {
            if (topic.slug_.equals(slug)) {
                return topic;
            }
        }
        return null;
    }
}

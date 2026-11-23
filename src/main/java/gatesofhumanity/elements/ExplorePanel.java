package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Center;
import gatesofhumanity.models.Descriptions;
import gatesofhumanity.models.GateNames;
import gatesofhumanity.models.Planet;
import rife.engine.Context;
import rife.engine.UrlBuilder;
import rife.template.Template;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Fills the explore.html template: sections of items, each with paragraphs from explore.tsv.
 */
final class ExplorePanel {
    private ExplorePanel() {
    }

    static String centerName(Center center) {
        return switch (center) {
            case G -> "G Center";
            case SOLAR -> "Solar Plexus";
            default -> center.name().charAt(0) + center.name().substring(1).toLowerCase(Locale.ROOT);
        };
    }

    static String planetName(Planet planet) {
        return Arrays.stream(planet.name().split("_"))
            .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(" "));
    }

    static String gateTitle(int gate) {
        return "Gate " + gate + ", " + GateNames.of(gate).name();
    }

    /**
     * What's said of a gate: its names, its description, and where it is in the body graph.
     */
    static List<String> gateTexts(int gate) {
        var names = GateNames.of(gate);
        // within a sentence, "The Army" reads as "the Army"
        var hexagram = names.hexagram().startsWith("The ") ? "the " + names.hexagram().substring(4) : names.hexagram();
        var channels = Analysis.channels(gate).stream().map(channel -> channel.name() + " (" + channel.key() + ")").toList();
        var listed = channels.size() == 1 ? channels.getFirst() :
            String.join(", ", channels.subList(0, channels.size() - 1)) + " and " + channels.getLast();
        return List.of(
            gateTitle(gate) + ", is hexagram " + gate + " of the I Ching, where it's called " + hexagram + (names.hexagram().equals(names.name()) ? " too." : "."),
            Descriptions.of("gate." + gate),
            "This gate is in the " + centerName(Analysis.center(gate)) + ", at one end of the channel" + (channels.size() == 1 ? "" : "s") + " of " + listed + ".");
    }

    /**
     * What's said of a gate, the first of it beside its hexagram.
     */
    static void gateParagraphs(Template t, int gate) {
        var texts = gateTexts(gate);
        Hexagram.print(t, "hexagram-lines", gate, 0);
        t.setValueEncoded("text", texts.getFirst());
        t.appendBlock("paragraphs", "hexagram-paragraph");
        for (var text : texts.subList(1, texts.size())) {
            paragraph(t, text);
        }
    }

    static String connects(Analysis.NamedChannel channel) {
        var centers = Analysis.centers(channel);
        return "It connects the " + centerName(centers.get(0)) + " and the " + centerName(centers.get(1)) + ".";
    }

    /**
     * The panel of a chart, or of a table when the subject is "table", which starts with what it's
     * for, from its section of the guide to the charts.
     */
    static Template start(Context c, AppSite site, String subject, String guide, String guideSection, String... introductions) {
        var t = c.template("explore");
        t.setValueEncoded("subject", subject);
        t.setValueEncoded("purpose", Descriptions.of(guide));
        t.setValue("purpose-url", More.of(c, site, LearnTopic.CHARTS, guideSection).url());
        t.setValueEncoded("tap", Descriptions.of("explore.tap." + subject));
        t.setValueEncoded("guide-title", guideSection);
        t.setValueEncoded("intro", Descriptions.of("intro"));
        for (var introduction : introductions) {
            t.setValueEncoded("text", Descriptions.of(introduction));
            t.appendBlock("introductions", "introduction");
        }
        return t;
    }

    static void startSection(Template t) {
        t.setValue("items", "");
    }

    /**
     * The items of the section that's shown with the introductions, under "How to read".
     */
    static void endGuide(Template t) {
        t.setValue("guide", t.getValue("items"));
    }

    static void endSection(Template t, String title, String introduction) {
        endSectionIntroducedBy(t, title, List.of(introduction));
    }

    static void endSectionIntroducedBy(Template t, String title, List<String> introductions) {
        t.setValueEncoded("section-title", title);
        t.setValue("section-intro", "");
        for (var introduction : introductions) {
            t.setValueEncoded("text", Descriptions.of(introduction));
            t.appendBlock("section-intro", "section-introduction");
        }
        t.appendBlock("sections", introductions.isEmpty() ? "section" : "section-introduced");
    }

    /**
     * A link from an item to its section of a Learn page.
     */
    record More(UrlBuilder url, String text) {
        static More of(Context c, AppSite site, LearnTopic topic, String section) {
            return of(c, site, topic, section, topic.all_ + " in Learn");
        }

        static More of(Context c, AppSite site, LearnTopic topic, String section, String text) {
            var url = topic.page().url(c, site);
            return new More(section == null ? url : url.fragment(LearnTopic.anchor(section)), text);
        }
    }

    // the part is what a tap on the chart looks for, the key it carries in the SVG
    static void item(Template t, String part, String label, String value, More more, String... descriptions) {
        item(t, part, List.of(), label, value, more, descriptions);
    }

    // the related parts are what the card of the item links to, when the panel has them
    static void item(Template t, String part, Collection<String> related, String label, String value, More more, String... descriptions) {
        t.setValue("paragraphs", "");
        for (var key : descriptions) {
            paragraph(t, Descriptions.of(key));
        }
        more(t, more);
        addItem(t, part, related, label, value);
    }

    static void more(Template t, More more) {
        if (more != null) {
            t.setValue("more-url", more.url());
            t.setValueEncoded("more-text", more.text());
            t.appendBlock("paragraphs", "more-paragraph");
        }
    }

    static void labeled(Template t, String label, String text) {
        t.setValueEncoded("text-label", label);
        t.setValueEncoded("text", text);
        t.appendBlock("paragraphs", "labeled-paragraph");
    }

    static void paragraph(Template t, String text) {
        t.setValueEncoded("text", text);
        t.appendBlock("paragraphs", "paragraph");
    }

    static void addItem(Template t, String part, Collection<String> related, String label, String value) {
        addItem(t, part, related, null, label, value);
    }

    // the row is part of the label in the card of a tap, the panel shows it in the title of the section
    static void addItem(Template t, String part, Collection<String> related, String row, String label, String value) {
        addItem(t, part, related, row, label, value, "");
    }

    // the timeline is HTML drawn below the value
    static void addItem(Template t, String part, Collection<String> related, String row, String label, String value, String timeline) {
        t.setValue("part", part);
        t.setValueEncoded("related", String.join(" ", new LinkedHashSet<>(related).stream().filter(each -> !each.equals(part)).toList()));
        var encoder = t.getEncoder();
        t.setValue("label", (row == null ? "" : "<span class=\"explore-label-row\">" + encoder.encode(row) + " </span>") + encoder.encode(label));
        t.setValueEncoded("value", value);
        t.setValue("timeline", timeline);
        t.appendBlock("items", "item");
    }
}

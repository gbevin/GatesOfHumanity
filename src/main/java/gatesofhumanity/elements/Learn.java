package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.models.*;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.UrlBuilder;
import rife.engine.annotations.ActiveSite;
import rife.engine.annotations.Parameter;
import rife.template.Template;
import rife.tools.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

import static gatesofhumanity.elements.ExplorePanel.centerName;
import static gatesofhumanity.elements.ExplorePanel.connects;
import static gatesofhumanity.elements.ExplorePanel.gateParagraphs;
import static gatesofhumanity.elements.ExplorePanel.gateTitle;
import static gatesofhumanity.elements.ExplorePanel.paragraph;
import static gatesofhumanity.elements.ExplorePanel.planetName;

/**
 * The Learn pages: the index of the topics and a page for each one.
 */
public class Learn implements Element {
    @ActiveSite AppSite site;
    @Parameter String topic;

    @Override
    public void process(Context c) {
        var t = c.template("learn");
        var learn = LearnTopic.of(topic);
        if (learn == null) {
            Pages.header(c, site, t, PageDescription.LEARN);
            t.setValueEncoded("intro-title", PageDescription.LEARN.title());
            introduction(t, "intro");
            t.setBlock("sources", "sources-block");
            for (var each : LearnTopic.values()) {
                t.setValue("topic-url", each.page().url(c, site));
                t.setValueEncoded("topic-title", each.page().heading());
                t.setValueEncoded("topic-description", each.page().description());
                t.appendBlock("topics", "topic");
            }
        } else {
            Pages.header(c, site, t, learn.page());
            t.setValueEncoded("intro-title", learn.page().title());
            write(c, site, t, learn);
            invite(c, site, t, learn);
            t.setValue("crumb-learn", PageDescription.LEARN.url(c, site));
            t.setValue("crumb-url", learn.page().url(c, site));
            t.setValueEncoded("crumb-current", learn.page().heading());
            t.setValue("crumb-current-json", StringUtils.encodeJson(learn.page().heading()));
            t.setBlock("breadcrumbs", "breadcrumbs-block");
            for (var other : LearnTopic.values()) {
                if (other != learn) {
                    t.setValue("pill-url", other.page().url(c, site));
                    t.setValueEncoded("pill-label", other.page().heading());
                    t.appendBlock("pills", "pill");
                }
            }
            t.setBlock("more", "more-topics");
        }
        c.print(t);
    }

    private static void write(Context c, AppSite site, Template t, LearnTopic topic) {
        switch (topic) {
            case CHARTS -> {
                introduction(t, "about.guide");
                guide(t, "Birth Chart", "guide.birth", ChartType.BIRTH.url(c, site), "Open the Birth Chart");
                guide(t, "Connection Chart", "guide.connection", ChartType.CONNECTION.url(c, site), "Open the Connection Chart");
                guide(t, "Transit Chart", "guide.transit", ChartType.TRANSIT.url(c, site), "Open the Transit Chart");
                guide(t, "Birth Transit Chart", "guide.birth_transit", ChartType.BIRTH_TRANSIT.url(c, site), "Open the Birth Transit Chart");
                guide(t, "Connection Transit Chart", "guide.connection_transit", ChartType.CONNECTION_TRANSIT.url(c, site), "Open the Connection Transit Chart");
                guide(t, "Transits by Month", "guide.range_month", PageDescription.RANGE_MONTH.url(c, site), "Open Transits by Month");
                guide(t, "Transits by Day", "guide.range_day", PageDescription.RANGE_DAY.url(c, site), "Open Transits by Day");
            }
            case CHART -> {
                introduction(t, "intro", "intro.chart", "intro.bodygraph");
                section(t, "Zodiac", "wheel.zodiac");
                section(t, "Planets", "wheel.planets");
                section(t, "Gates", "wheel.gates");
                section(t, "Body graph", "wheel.bodygraph");
                section(t, "Rows of planets", "wheel.rows");
                section(t, "Overview", "about.overview");
                section(t, "Exaltation and detriment", "about.fixing");
                section(t, "Outlined triangles", "fixing.other");
                section(t, "Birth time", "about.birthtime");
            }
            case PLANETS -> {
                introduction(t, "about.planets");
                section(t, "Personality", "about.planets.personality");
                section(t, "Design", "about.planets.design");
                for (var planet : Planet.values()) {
                    if (planet != Planet.NONE) {
                        section(t, planetName(planet), "planet." + planet.name(), "planet." + planet.name() + ".more");
                    }
                }
            }
            case GATES -> {
                introduction(t, "about.gates");
                for (var gate = 1; gate <= 64; ++gate) {
                    startSection(t);
                    gateParagraphs(t, gate);
                    endSection(t, gateTitle(gate));
                }
                for (var line = 1; line <= 6; ++line) {
                    section(t, "Line " + line + " of a gate", "gate.line." + line);
                }
            }
            case TYPES -> {
                introduction(t, "about.type");
                for (var type : Analysis.Type.values()) {
                    startSection(t);
                    paragraph(t, Descriptions.of("type." + type.name()));
                    paragraph(t, "Strategy: " + type.strategy_ + ". Signature: " + type.signature_ + ". Not-Self Theme: " + type.notSelf_ + ". Aura: " + type.aura_ + ".");
                    endSection(t, type.label_);
                }
            }
            case AUTHORITIES -> {
                introduction(t, "about.authority");
                for (var authority : Analysis.Authority.values()) {
                    section(t, authority.label_, "authority." + authority.name());
                }
            }
            case PROFILES -> {
                introduction(t, "about.profile");
                for (var line = 1; line <= 6; ++line) {
                    section(t, "Line " + line + ", " + Analysis.Profile.lineName(line), "line." + line);
                }
                section(t, "Incarnation Cross", "about.cross");
                for (var angle : Analysis.Angle.values()) {
                    section(t, angle.label_, "angle." + angle.name());
                }
            }
            case DEFINITION -> {
                introduction(t, "about.definition");
                for (var definition : Analysis.Definition.values()) {
                    section(t, definition.label_, "definition." + definition.name());
                }
            }
            case CENTERS -> {
                introduction(t, "about.centers");
                for (var center : Center.values()) {
                    startSection(t);
                    labeled(t, "Defined", Descriptions.of("center." + center.name() + ".defined"));
                    labeled(t, "Open", Descriptions.of("center." + center.name() + ".open"));
                    endSection(t, centerName(center));
                }
            }
            case CHANNELS -> {
                introduction(t, "about.channels");
                section(t, "Circuits", "about.circuits");
                for (var circuit : Circuit.values()) {
                    startSection(t);
                    paragraph(t, Descriptions.of("circuit." + circuit.name()));
                    labeled(t, "Channels", String.join(", ", circuit.channels().stream().map(Analysis.NamedChannel::toString).toList()));
                    endSection(t, circuit.label_);
                }
                for (var channel : Analysis.allChannels()) {
                    startSection(t);
                    paragraph(t, connects(channel));
                    paragraph(t, Descriptions.of("channel." + channel.key()));
                    labeled(t, "Part of the", Circuit.of(channel).place());
                    endSection(t, channel.toString());
                }
            }
            case VARIABLES -> {
                introduction(t, "about.variables");
                for (var kind : Variables.Kind.values()) {
                    startSection(t);
                    paragraph(t, Descriptions.of("variable." + kind.name()));
                    labeled(t, "\u2190", Descriptions.of("variable." + kind.name() + ".left"));
                    labeled(t, "\u2192", Descriptions.of("variable." + kind.name() + ".right"));
                    for (var color = 1; color <= 6; ++color) {
                        var key = "variable." + kind.name() + ".color." + color;
                        labeled(t, kind.color(color), Descriptions.of(key) +
                            (kind.hasSides() ? " " + Descriptions.of(key + ".left") + " " + Descriptions.of(key + ".right") : ""));
                    }
                    endSection(t, kind.label_);
                }
            }
            case TRANSITS -> {
                introduction(t, "about.transits");
                section(t, "Planets in a transit", "transit.planet");
                section(t, "Defined centers", "about.transits.centers");
                section(t, "Channels in a transit", "about.channels.transit");
                section(t, "Completed channels", "about.transits.birth");
                section(t, "Centers defined now", "about.transits.center");
                section(t, "Coming up", "about.upcoming");
                section(t, "Completed for two people", "about.transits.connection");
                section(t, "Defined centers of two people", "about.transits.connection.centers");
                section(t, "Cycles", "about.cycles");
                for (var cycle : Cycle.values()) {
                    section(t, cycle.label_, "cycle." + cycle.name());
                }
            }
            case CONNECTIONS -> {
                introduction(t, "about.connection");
                section(t, "Defined centers", "about.connection.centers");
                section(t, "Each person", "about.person");
                for (var kind : new String[]{"electromagnetic", "companionship", "dominance", "compromise"}) {
                    section(t, Widget.capitalized(kind), "connection." + kind);
                }
            }
        }
    }

    // the charts in which the parts of a topic can be seen, the guide links to every chart already
    private static void invite(Context c, AppSite site, Template t, LearnTopic topic) {
        var birth = ChartType.BIRTH.url(c, site);
        switch (topic) {
            case CHARTS -> {
                return;
            }
            case TRANSITS -> {
                invitation(t, ChartType.TRANSIT.url(c, site), "Open the Transit Chart");
                invitation(t, ChartType.BIRTH_TRANSIT.url(c, site), "Open the Birth Transit Chart");
                invitation(t, ChartType.CONNECTION_TRANSIT.url(c, site), "Open the Connection Transit Chart");
            }
            case CONNECTIONS -> {
                invitation(t, ChartType.CONNECTION.url(c, site), "Open the Connection Chart");
                invitation(t, ChartType.CONNECTION_TRANSIT.url(c, site), "Open the Connection Transit Chart");
            }
            default -> invitation(t, birth, "See them in a Birth Chart");
        }
        t.setBlock("invite", "invite-block");
    }

    private static void invitation(Template t, UrlBuilder url, String text) {
        t.setValue("link", url);
        t.setValueEncoded("link-text", text);
        t.appendBlock("invitations", "invitation");
    }

    private static void guide(Template t, String label, String key, UrlBuilder url, String linkText) {
        startSection(t);
        paragraph(t, Descriptions.of(key) + " " + Descriptions.of(key + ".use"));
        t.setValue("link", url);
        t.setValueEncoded("link-text", linkText);
        t.appendBlock("paragraphs", "link-paragraph");
        endSection(t, label);
    }

    private static void introduction(Template t, String... keys) {
        for (var key : keys) {
            t.setValueEncoded("text", Descriptions.of(key));
            t.appendBlock("introduction", "paragraph");
        }
    }

    // the texts of the keys follow each other in one paragraph
    private static void section(Template t, String label, String... keys) {
        startSection(t);
        paragraph(t, Arrays.stream(keys).map(Descriptions::of).collect(Collectors.joining(" ")));
        endSection(t, label);
    }

    private static void startSection(Template t) {
        t.setValue("paragraphs", "");
    }

    private static void endSection(Template t, String label) {
        t.setValue("anchor", LearnTopic.anchor(label));
        t.setValueEncoded("label", label);
        t.appendBlock("sections", "section");
    }

    private static void labeled(Template t, String label, String text) {
        t.setValueEncoded("text-label", label);
        t.setValueEncoded("text", text);
        t.appendBlock("paragraphs", "labeled-paragraph");
    }
}

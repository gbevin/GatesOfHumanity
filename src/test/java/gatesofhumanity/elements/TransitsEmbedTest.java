package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.AppSiteEmbedded;
import gatesofhumanity.models.GateNames;
import gatesofhumanity.models.TransitTimeline;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import rife.test.MockConversation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static gatesofhumanity.elements.Drawings.*;
import static org.junit.jupiter.api.Assertions.*;

public class TransitsEmbedTest {
    // what the small image says of the Sun at a moment, and the medium one of each planet
    private static List<String> small(TransitTimeline.Moment moment) {
        var sun = moment.places().getFirst();
        var texts = new ArrayList<String>(List.of("SUN", "localhost", sun.gate() + "." + sun.line()));
        texts.addAll(Fitted.of(capitalized(GateNames.of(sun.gate()).name()), 15, 15, 11, 2).lines());
        texts.add("until " + WRITTEN.format(sun.until()) + " UTC");
        for (var place : moment.places().subList(1, 3)) {
            texts.add(capitalized(place.planet().id_) + " " + place.gate() + "." + place.line());
        }
        return texts;
    }

    private static List<String> medium(TransitTimeline.Moment moment) {
        var texts = new ArrayList<String>(List.of("TRANSITS NOW", "UNTIL, UTC", "localhost"));
        for (var place : moment.places()) {
            texts.add(capitalized(place.planet().id_));
            texts.add(place.gate() + "." + place.line());
            texts.addAll(Fitted.of(capitalized(GateNames.of(place.gate()).name()), 13, 15, 10, 2).lines());
            texts.add(WRITTEN.format(place.until()));
        }
        return texts;
    }

    @Test
    void theTransitsOfThisMomentAreAnImageForAPageElsewhere() {
        try (var m = new MockConversation(new AppSite())) {
            for (var size : List.of("small", "medium")) {
                var before = TransitTimeline.at(TransitsWidget.PLANETS, Instant.now().truncatedTo(ChronoUnit.MINUTES));
                var response = m.doRequest("/embed/transits/" + size);
                var after = TransitTimeline.at(TransitsWidget.PLANETS, Instant.now().truncatedTo(ChronoUnit.MINUTES));
                assertEquals(200, response.getStatus(), size);
                assertEquals("image/svg+xml", response.getContentType().split(";")[0], size);
                var svg = Jsoup.parse(response.getText(), Parser.xmlParser());
                var root = svg.selectFirst("svg");
                assertEquals(size.equals("small") ? "0 0 158 158" : "0 0 338 158", root.attr("viewBox"), size);
                // the moments are in UTC and say so, since nothing writes them again for who looks, and it says where it's from
                var texts = svg.select("text").eachText();
                var expected = size.equals("small") ? List.of(small(before), small(after)) : List.of(medium(before), medium(after));
                assertTrue(expected.contains(texts), size + " says " + texts);
                assertEquals("http://localhost/wheel", svg.selectFirst("a").attr("href"), size);
                // kept by who shows it until a planet moves on, ten minutes at most
                var kept = response.getHeader("Cache-Control");
                assertTrue(kept.matches("public, max-age=\\d+"), kept);
                var seconds = Long.parseLong(kept.substring(kept.indexOf('=') + 1));
                assertTrue(seconds <= 600, kept);
                assertTrue(seconds >= Math.min(600, java.time.Duration.between(Instant.now(), after.end()).toSeconds()), kept);
            }
            assertEquals(404, m.doRequest("/embed/transits/large").getStatus());
            assertEquals(404, m.doRequest("/embed/transits").getStatus());
            assertEquals(404, m.doRequest("/embed/transits/small/202611151700").getStatus());
        }
    }

    @Test
    void aWidgetOfTheAppSaysNeitherUtcNorWhereItsFrom() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            for (var size : List.of("systemSmall", "systemMedium")) {
                var texts = String.join("|", texts(m, "/widget/transits/" + size + moment(FROM)));
                assertFalse(texts.contains("UTC") || texts.contains("localhost"), texts);
            }
        }
    }
}

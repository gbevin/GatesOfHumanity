package gatesofhumanity.elements;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import rife.test.MockConversation;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the tests of the widgets ask of the site and check of every drawing.
 */
final class Drawings {
    static final Instant FROM = Instant.parse("2026-11-15T17:00:00Z");
    static final DateTimeFormatter WRITTEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    // what ios/widgets/Drawn.swift takes out of a drawing as text: one element that closes itself
    private static final Pattern BACKGROUND = Pattern.compile("<\\w+\\b[^>]*\\bid=\"background\"[^>]*/>");
    private static final Pattern SKELETON = Pattern.compile("\\{(EEEjmm|MMMd|yMMM)\\}");

    private Drawings() {
    }

    static String moment(Instant at) {
        return "/" + HdImage.MOMENT_FORMAT.format(at.atZone(ZoneOffset.UTC));
    }

    static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String svg(MockConversation m, String path) {
        var response = m.doRequest(path);
        assertEquals(200, response.getStatus(), path);
        assertEquals("image/svg+xml", response.getContentType().split(";")[0], path);
        return response.getText();
    }

    static Document drawing(MockConversation m, String path) {
        return Jsoup.parse(svg(m, path), Parser.xmlParser());
    }

    static List<String> texts(MockConversation m, String path) {
        return drawing(m, path).select("text").eachText();
    }

    /**
     * What every drawing of a widget has to be for the app to draw it and to take out of it
     * what a tap opens, what's behind it and the moments that the device writes.
     */
    static void assertDrawn(String svg, String path, int backgrounds) {
        assertDrawn(svg, path, backgrounds, "http://localhost/wheel");
    }

    static void assertDrawn(String svg, String path, int backgrounds, String opens) {
        var what = path + ": " + svg;
        org.w3c.dom.Document document;
        try {
            // a browser and the app both refuse what isn't well-formed
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        } catch (Exception e) {
            throw new AssertionError(what, e);
        }
        var root = document.getDocumentElement();
        assertEquals("svg", root.getTagName(), what);
        assertEquals("0 0 " + root.getAttribute("width") + " " + root.getAttribute("height"), root.getAttribute("viewBox"), what);
        assertFalse(svg.contains("{{") || svg.contains("<!--v") || svg.contains("<!--b") || svg.contains("<!--i") || svg.contains("<!v"), what);
        var drawn = Jsoup.parse(svg, Parser.xmlParser());
        // a tap opens a page of the app, which the drawing says as a link around what it shows
        assertEquals(List.of(opens), drawn.select("a").eachAttr("href"), what);
        assertFalse(drawn.select("text").isEmpty(), what);
        assertEquals(drawn.select("text").size(), drawn.select("a text").size(), what);
        assertTrue(drawn.select("text").stream().noneMatch(text -> text.text().isBlank()), what);
        // what the library that draws it leaves out
        assertTrue(drawn.select("tspan").isEmpty(), what);
        assertFalse(svg.contains("letter-spacing"), what);
        // what's behind the widget is one element of its own, which iOS leaves out where it has its own
        assertEquals(svg.split("id=\"background\"", -1).length - 1, BACKGROUND.matcher(svg).results().count(), what);
        assertEquals(backgrounds, BACKGROUND.matcher(svg).results().count(), what);
        // a moment is written as a browser shows it, and says how the device writes it
        for (var text : drawn.select("text")) {
            assertEquals(text.hasAttr("data-time"), text.hasAttr("data-text"), what);
            if (text.hasAttr("data-time")) {
                var pattern = text.attr("data-text");
                var skeleton = SKELETON.matcher(pattern);
                assertTrue(skeleton.find(), what);
                if (skeleton.group(1).equals("EEEjmm")) {
                    assertEquals(pattern.replace("{EEEjmm}", WRITTEN.format(Instant.parse(text.attr("data-time")))), text.text(), what);
                }
            }
        }
    }
}

package gatesofhumanity;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import rife.ioc.HierarchicalProperties;
import rife.nativeapp.NativeApp;
import rife.nativeapp.SiteProvider;
import rife.test.MockConversation;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

public class AppSiteProviderTest {
    private static HierarchicalProperties told(String platform) {
        var properties = new HierarchicalProperties();
        if (platform != null) {
            properties.put(NativeApp.PLATFORM_PROPERTY, platform);
        }
        return properties;
    }

    @Test
    void anAppFindsItsSiteByTheNameOfWhatMakesIt() {
        var providers = ServiceLoader.load(SiteProvider.class).stream().map(provider -> provider.type().getName()).toList();
        assertEquals(List.of(AppSiteProvider.class.getName()), providers);
    }

    @Test
    void theSiteOfAnAppSaysWhatAnAppIsUnlessTheAppSaidItItself() {
        var properties = told("ios");
        var site = new AppSiteProvider().site(properties);
        assertInstanceOf(AppSiteEmbedded.class, site);
        assertEquals(List.of("ios", "this app", "none", "https://hd.uwyn.com", "Tap"),
            List.of(properties.getValueString("gatesofhumanity.app"), properties.getValueString("gatesofhumanity.keptIn"), properties.getValueString("gatesofhumanity.columnWidth"),
                properties.getValueString("gatesofhumanity.shareRootUrl"), properties.getValueString("gatesofhumanity.tap")));
        assertTrue(properties.getValueString("gatesofhumanity.credit").startsWith("This app has the pages inside it as native code, made with "));
        assertTrue(properties.getValueString("gatesofhumanity.widgetsCredit").contains("SwiftDraw"));
        // which version of the app it is isn't made up
        assertFalse(properties.contains("gatesofhumanity.version") || properties.contains("gatesofhumanity.build"));

        // a Mac has a mouse, and an app says where it keeps the people and what draws its widgets
        properties = told("mac");
        properties.put("gatesofhumanity.keptIn", "this app and your own iCloud");
        properties.put("gatesofhumanity.widgetsCredit", "Drawn with something else.");
        properties.put(NativeApp.VERSION_PROPERTY, "1.2.3");
        properties.put(NativeApp.BUILD_NUMBER_PROPERTY, "45");
        new AppSiteProvider().site(properties);
        assertEquals(List.of("mac", "this app and your own iCloud", "Click", "Drawn with something else.", "1.2.3", "45"),
            List.of(properties.getValueString("gatesofhumanity.app"), properties.getValueString("gatesofhumanity.keptIn"), properties.getValueString("gatesofhumanity.tap"),
                properties.getValueString("gatesofhumanity.widgetsCredit"), properties.getValueString("gatesofhumanity.version"), properties.getValueString("gatesofhumanity.build")));
    }

    @Test
    void thePagesOfAnAppSayWhatItsSiteWasTold() {
        for (var platform : List.of("ios", "mac")) {
            var properties = told(platform);
            properties.put(NativeApp.VERSION_PROPERTY, "1.2.3");
            properties.put(NativeApp.BUILD_NUMBER_PROPERTY, "45");
            try (var m = new MockConversation(new AppSiteProvider().site(properties), properties)) {
                var chart = Jsoup.parse(m.doRequest("/wheel/birth").getText());
                // the welcome says how a part of a chart is chosen there, and where the people stay
                var tap = platform.equals("mac") ? "Click" : "Tap";
                var welcome = chart.selectFirst("dialog#welcome");
                assertEquals(List.of("Charts and transits", tap + " to explore", "Kept in this app"), welcome.select("li h3").eachText(), platform);
                assertTrue(welcome.select("li p").get(1).text().startsWith(tap + " any part of a chart to read what it is."), platform);
                assertTrue(welcome.select("li p").last().text().startsWith("The people you save stay in this app, "), platform);
                // the pages are as wide as the screen, keep the people in the app and share where a chart is on the web
                assertTrue(chart.select("head style").first().data().contains("--column-width: none;"), platform);
                var script = chart.select("head script:not([src])").first().data();
                assertTrue(script.contains("kept_in = 'this app';"), platform);
                assertTrue(script.contains("share_root = 'https://hd.uwyn.com';"), platform);
                // and the About page says what the app has inside it and which one it is
                var about = Jsoup.parse(m.doRequest("/about").getText()).body().text();
                assertTrue(about.contains("This app has the pages inside it as native code, made with "), platform);
                assertTrue(about.contains("SwiftDraw"), platform);
                assertTrue(about.contains("1.2.3") && about.contains("45"), platform);
            }
        }
    }

    @Test
    void aPageOfAnAppHasTheScriptOfRife2ForItAfterItsViewport() {
        var properties = told("vision");
        try (var m = new MockConversation(new AppSiteProvider().site(properties), properties)) {
            for (var path : List.of("/wheel/birth", "/learn", "/people", "/panel")) {
                var head = Jsoup.parse(m.doRequest(path).getText()).head();
                var scripts = head.select("script[src*=rife2-native-app]");
                assertEquals(1, scripts.size(), path);
                assertEquals("vision", scripts.getFirst().attr("data-platform"), path);
                // the script changes the viewport that's there before it
                var before = head.children().subList(0, head.children().indexOf(scripts.getFirst()));
                assertTrue(before.stream().anyMatch(element -> element.attr("name").equals("viewport")), path);
                // and the pages are told which app they're in
                assertTrue(head.html().contains("inApp('vision');"), path);
            }
        }
        // on the web a page has none of it
        try (var m = new MockConversation(new AppSite())) {
            var head = Jsoup.parse(m.doRequest("/wheel/birth").getText()).head();
            assertTrue(head.select("script[src*=rife2-native-app]").isEmpty());
            assertTrue(head.html().contains("inApp('');"));
        }
    }
}

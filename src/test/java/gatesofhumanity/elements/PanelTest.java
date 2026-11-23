package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.AppSiteEmbedded;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import rife.test.MockConversation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PanelTest {
    // the files that a page loads, without what keeps a browser from using an older one
    private static List<String> loaded(Document page) {
        return page.select("head link[rel=stylesheet], head script[src]").stream()
            .map(file -> (file.hasAttr("href") ? file.attr("href") : file.attr("src")).replaceAll("\\?.*", "")).toList();
    }

    @Test
    void thePanelOfAnAppIsTheCardOfAChartsPageAsAllOfAPage() {
        try (var m = new MockConversation(new AppSiteEmbedded())) {
            var response = m.doRequest("/panel");
            assertEquals(200, response.getStatus());
            var panel = Jsoup.parse(response.getText());
            var chart = Jsoup.parse(m.doRequest("/wheel/birth").getText());
            assertNotNull(chart.selectFirst("#explore-card #explore-card-content"));
            assertEquals(chart.selectFirst("#explore-card").outerHtml(), panel.selectFirst("#explore-card").outerHtml());
            assertEquals(List.of("explore-card"), panel.body().children().stream().filter(each -> !each.tagName().equals("script")).map(Element::id).toList());
            // with the styles and the scripts of that page, and what makes a panel of it
            assertFalse(loaded(chart).isEmpty());
            assertEquals(loaded(chart), loaded(panel));
            assertTrue(panel.select("body script").html().contains("appPanel("), panel.body().html());
            assertEquals("noindex", panel.selectFirst("meta[name=robots]").attr("content"));
        }
        // the site on the web has no panel
        try (var m = new MockConversation(new AppSite())) {
            assertEquals(404, m.doRequest("/panel").getStatus());
        }
    }
}

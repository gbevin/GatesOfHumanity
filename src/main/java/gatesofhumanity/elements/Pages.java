package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.template.Template;

/**
 * What every page sets up: its description for the header and search engines, the menu and the
 * footer links.
 */
final class Pages {
    static final String APP_ID = "6834835560";
    static final String APP_STORE = "https://apps.apple.com/app/id" + APP_ID;
    static final String GOOGLE_PLAY = "https://play.google.com/store/apps/details?id=com.uwyn.gatesofhumanity";

    private Pages() {
    }

    /**
     * Whether the pages are on the web themselves, rather than inside an app.
     */
    static boolean onTheWeb(Context c) {
        return !c.hasProperty("gatesofhumanity.shareRootUrl");
    }

    static void header(Context c, AppSite site, Template t, PageDescription page) {
        page.apply(c, site, t);
        // the name of a chart is the heading of its page, a page of text has a title of its own
        t.setBlock("page-name", t.hasValueId("intro-title") ? "page-name-plain" : "page-name-heading");
        // the Learn topics are under Learn in the menu, a notice is under nothing
        var current = PageDescription.MENU.contains(page) ? page : PageDescription.NOTICES.contains(page) ? null : PageDescription.LEARN;
        for (var item : PageDescription.MENU) {
            t.setValue("menu-url", item.url(c, site));
            t.setValueEncoded("menu-label", item.heading());
            var divided = item == PageDescription.TRANSIT || item == PageDescription.PEOPLE ? "menu-divided" : "";
            var selected = item == current ? "current" : "";
            t.setValue("menu-class", (divided + " " + selected).strip());
            t.appendBlock("menu-items", "menu-item");
        }
        t.setValue("learn-url", PageDescription.LEARN.url(c, site));
        t.setValue("about-url", PageDescription.ABOUT.url(c, site));
        // the welcome of the charts again, for whoever asks: the first of them shows it at this address
        t.setValue("welcome-url", PageDescription.BIRTH.url(c, site).fragment("welcome"));
        // an app doesn't offer itself
        if (onTheWeb(c)) {
            t.setValue("app-store-url", APP_STORE);
            t.setValueEncoded("google-play-url", GOOGLE_PLAY);
            t.setBlock("app-link");
            // Safari on an iPhone or an iPad offers the app above the page from this
            t.setValue("app-id", APP_ID);
            t.setBlock("app-banner");
        }
    }

    static void notice(Context c, AppSite site, PageDescription page, String text) {
        var t = c.template("notice");
        header(c, site, t, page);
        t.setValueEncoded("intro-title", page.title());
        t.setValueEncoded("notice", text);
        c.print(t);
    }
}

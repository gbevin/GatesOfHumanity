package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.UrlBuilder;
import rife.template.Template;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * What a page is called and how search engines and link previews describe it.
 *
 * @param link        the canonical URL, from the routes of the site
 * @param heading     the name of the chart in the page header
 * @param title       the title of search results and link previews, and of the browser tab with the name of the site
 * @param description the summary in search results and link previews
 */
public record PageDescription(Link link, String heading, String title, String description) {
    @FunctionalInterface
    public interface Link {
        UrlBuilder url(Context c, AppSite site);
    }

    public static final String SITE_NAME = "Gates of Humanity";

    public static final PageDescription TRANSIT = new PageDescription((c, site) -> ChartType.TRANSIT.url(c, site), "Transit Chart",
        "Human Design Transit Chart",
        "The current Human Design transits on the wheel of the 64 gates: the gate and line that each planet activates at any moment, seen from any place.");
    public static final PageDescription BIRTH = new PageDescription((c, site) -> ChartType.BIRTH.url(c, site), "Birth Chart",
        "Human Design Birth Chart",
        "Calculate a Human Design birth chart: Type, Strategy, Authority, Profile, Definition, Variables, Incarnation Cross and channels, on the wheel of the 64 gates.");
    public static final PageDescription BIRTH_TRANSIT = new PageDescription((c, site) -> ChartType.BIRTH_TRANSIT.url(c, site), "Birth Transit Chart",
        "Human Design Birth Transit Chart",
        "A Human Design birth chart overlaid with the planetary transits of any moment, on the wheel of the 64 gates and the body graph.");
    public static final PageDescription CONNECTION = new PageDescription((c, site) -> ChartType.CONNECTION.url(c, site), "Connection Chart",
        "Human Design Connection Chart",
        "The Human Design connection chart of two people, with their electromagnetic, companionship, dominance and compromise channels.");
    public static final PageDescription CONNECTION_TRANSIT = new PageDescription((c, site) -> ChartType.CONNECTION_TRANSIT.url(c, site), "Connection Transit Chart",
        "Human Design Connection Transit Chart",
        "The Human Design connection chart of two people with the transits of any moment, and the channels the planets complete for them together.");
    public static final PageDescription RANGE_MONTH = new PageDescription((c, site) -> c.urlFor(site.range), "Transits by Month",
        "Human Design Transits by Month",
        "Every Human Design gate and line change of the Sun, Moon and planets over a month, in one table.");
    public static final PageDescription RANGE_DAY = new PageDescription((c, site) -> c.urlFor(site.rangeDuration).param("duration", RangeTable.DAY), "Transits by Day",
        "Human Design Transits by Day",
        "Every Human Design gate and line change of the Sun, Moon and planets over a day, in one table.");

    public static final PageDescription PEOPLE = new PageDescription((c, site) -> c.urlFor(site.people), "People",
        "Saved People",
        "The people saved for their Human Design charts, to rename, group, delete, export and import.");
    public static final PageDescription LEARN = new PageDescription((c, site) -> c.urlFor(site.learn), "Learn",
        "Learn about Human Design",
        "An introduction to every part of a Human Design chart, from the wheel and the body graph to the Types, Authorities, centers, channels and Variables.");
    public static final PageDescription ABOUT = new PageDescription((c, site) -> c.urlFor(site.about), "About",
        "About " + SITE_NAME,
        "Who made " + SITE_NAME + " and why: free Human Design charts with planetary positions calculated in pure Java, private and without ads or tracking.");

    // what the site answers when there's no page to show, with the address of the first page as theirs
    public static final PageDescription NOT_FOUND = new PageDescription((c, site) -> c.urlFor(site.home), "Not Found",
        "This Page Doesn't Exist",
        "There's no page at this address.");
    public static final PageDescription FAILURE = new PageDescription((c, site) -> c.urlFor(site.home), "Error",
        "This Page Couldn't Be Shown",
        "An error kept this page from being shown.");
    static final List<PageDescription> NOTICES = List.of(NOT_FOUND, FAILURE);

    // in the order of the menu, from the charts of people to the transits, then the saved people and the pages about it all
    public static final List<PageDescription> MENU = List.of(BIRTH, CONNECTION, TRANSIT, BIRTH_TRANSIT, CONNECTION_TRANSIT, RANGE_MONTH, RANGE_DAY, PEOPLE, LEARN, ABOUT);

    /**
     * Every page. A method rather than a constant, since the Learn topics create their pages while
     * they're initialized.
     */
    public static List<PageDescription> all() {
        return Stream.concat(MENU.stream(), Arrays.stream(LearnTopic.values()).map(LearnTopic::page)).toList();
    }

    /**
     * The pages of the sitemap, which are those with something for a search engine to read: the
     * saved people are only in the visitor's browser.
     */
    public static List<PageDescription> listed() {
        return all().stream().filter(page -> page != PEOPLE).toList();
    }

    public UrlBuilder url(Context c, AppSite site) {
        return link.url(c, site);
    }

    void apply(Context c, AppSite site, Template t) {
        t.setValue("page-url", url(c, site));
        // a page without a title bar has nowhere to say what it's called
        if (t.hasValueId("page-heading")) {
            t.setValueEncoded("page-heading", heading);
        }
        t.setValueEncoded("page-title", title);
        t.setValueEncoded("tab-title", title.contains(SITE_NAME) ? title : title + " | " + SITE_NAME);
        t.setValueEncoded("page-description", description);
    }
}

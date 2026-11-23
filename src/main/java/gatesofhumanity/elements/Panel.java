package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;

/**
 * The card that explains a tapped part of a chart, as all of a page: for an app that shows it
 * in a panel beside its window rather than over the chart. It has what the page of a chart has
 * for it and nothing of its own, and the app gives it what the card of that page holds, see
 * app.js.
 */
public class Panel implements Element {
    private static final PageDescription PAGE = new PageDescription((c, site) -> c.urlFor(site.home), "Explore",
        "Explore", "What a part of a chart is about.");

    @ActiveSite AppSite site;

    @Override
    public void process(Context c) {
        var t = c.template("panel");
        PAGE.apply(c, site, t);
        c.print(t);
    }
}

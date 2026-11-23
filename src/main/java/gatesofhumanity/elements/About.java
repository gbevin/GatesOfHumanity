package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;

import java.util.List;

public class About implements Element {
    static final String EMBED_SAYS = "The Human Design transits of this moment";

    @ActiveSite AppSite site;

    @Override
    public void process(Context c) {
        var t = c.template("about");
        Pages.header(c, site, t, PageDescription.ABOUT);
        t.setValueEncoded("intro-title", PageDescription.ABOUT.title());
        // where the pages are on the web themselves, a page elsewhere can show their transits
        if (Pages.onTheWeb(c)) {
            t.setBlock("apps");
            var chart = c.urlFor(site.wheel).toString();
            t.setValueEncoded("embed-says", EMBED_SAYS);
            for (var size : TransitsEmbed.SIZES) {
                var image = c.urlFor(site.transitsEmbed).pathInfo(size.name()).toString();
                t.setValueEncoded("embed-image", image);
                t.setValue("embed-width", size.width());
                t.setValue("embed-height", size.height());
                t.setValueEncoded("embed-code", "<a href=\"" + chart + "\"><img src=\"" + image + "\" width=\"" + size.width() + "\" height=\"" + size.height() +
                                                "\" alt=\"" + EMBED_SAYS + "\"></a>");
                t.appendBlock("embeds", "embed");
            }
            t.setBlock("embedding");
        }
        // what a program has inside it that the site alone doesn't
        for (var credit : List.of("gatesofhumanity.credit", "gatesofhumanity.widgetsCredit")) {
            if (c.hasProperty(credit)) {
                t.setValueEncoded("credit", String.valueOf(c.property(credit)));
                t.appendBlock("program-credit");
            }
        }
        if (c.hasProperty("gatesofhumanity.version")) {
            t.setValueEncoded("version", String.valueOf(c.property("gatesofhumanity.version")));
            if (c.hasProperty("gatesofhumanity.build")) {
                t.setValueEncoded("build", String.valueOf(c.property("gatesofhumanity.build")));
                t.setBlock("program-build");
            }
            t.setBlock("program-version");
        }
        c.print(t);
    }
}

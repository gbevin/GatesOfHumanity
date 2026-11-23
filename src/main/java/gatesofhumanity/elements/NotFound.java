package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;

/**
 * Answers every address that no route handles.
 */
public class NotFound implements Element {
    @ActiveSite AppSite site;

    @Override
    public void process(Context c) {
        c.setStatus(404);
        Pages.notice(c, site, PageDescription.NOT_FOUND,
            "There's no page at this address. The menu at the top opens every chart, and Learn introduces each part of them.");
    }
}

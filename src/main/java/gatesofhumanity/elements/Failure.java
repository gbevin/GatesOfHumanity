package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.Element;

/**
 * Answers a request that ended in an exception, on the deployed site. It's given its site rather
 * than having it injected, so that the native executable needs no reflection for an element that
 * the tracing agent never sees at work.
 */
public class Failure implements Element {
    private final AppSite site_;

    public Failure(AppSite site) {
        site_ = site;
    }

    @Override
    public void process(Context c) {
        c.setStatus(500);
        Pages.notice(c, site_, PageDescription.FAILURE,
            "An error kept this page from being shown. Trying again in a moment usually helps.");
    }
}

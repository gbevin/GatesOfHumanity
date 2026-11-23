package com.uwyn.gatesofhumanity;

import android.app.Activity;
import android.net.Uri;
import android.webkit.WebView;
import rife.nativeapp.android.NativePages;
import rife.nativeapp.android.NativeSite;

/**
 * The pages of the site in the web view that RIFE2 has for them, see NativePages, with what's
 * this app's own: the people that the pages say they saved, for the widgets, and a tap on a
 * widget.
 */
final class Pages extends NativePages {
    private final Probe probe_;

    Pages(Activity activity, Probe probe) {
        super(activity, "/wheel/birth");
        probe_ = probe;
    }

    @Override
    protected void configure(WebView view) {
        view.setBackgroundColor(MainActivity.EDGE);
        // a page says who it saved, see app.js of the site
        listen("people", told -> People.told(activity.getApplicationContext(), told));
        if (probe_ != null) {
            view.addJavascriptInterface(probe_, "HdProbe");
        }
    }

    /**
     * Shows what a widget was tapped for, when it's an address of the site. One that names a
     * person is a chart for someone the pages saved: the page that's shown remembers them as
     * the People page does for one of its charts, and goes there.
     */
    boolean show(Uri address) {
        var person = address.getQueryParameter("person");
        if (person == null) {
            return open(address.toString());
        }
        var chart = address.buildUpon().clearQuery().build();
        if (!NativeSite.own(activity, chart)) {
            return false;
        }
        whenLoaded(() -> call("showPersonChart", new Object[] {chart.getPath(), person}, (called, shown) -> {
            if (!called) {
                open(chart.toString());
            }
        }));
        return true;
    }
}

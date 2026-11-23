package com.uwyn.gatesofhumanity;

import android.app.Application;
import rife.nativeapp.android.NativeSite;

/**
 * The app before any of it runs, which is where it says what its site is: a widget can ask the
 * site before the pages do.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NativeSite.library = "gatesofhumanity";
        // where the pages keep what they store, the saved people among it: at another address
        // they'd start without them
        NativeSite.host = "app.gatesofhumanity.invalid";
        NativeSite.properties.put("gatesofhumanity.widgetsCredit", "The widgets of this app are drawn with AndroidSVG by Paul LeBeau, licensed under the Apache License, version 2.0.");
    }
}

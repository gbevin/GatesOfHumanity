package com.uwyn.gatesofhumanity;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import rife.nativeapp.android.NativeActivity;
import rife.nativeapp.android.NativePages;

/**
 * The app: the pages of the site, which the app answers itself instead of a server, and the way
 * in for a tap on a widget.
 */
public class MainActivity extends NativeActivity {
    /** The color at the top of the title bar of the pages, which goes on behind the status bar. */
    static final int EDGE = 0xff21214e;
    /** What a widget says with the address that it opens, so that nothing else can open one. */
    static final String TOKEN = "token";

    @Override
    protected NativePages pages() {
        // only an app that was built to be looked into has a probe, see android/build.sh
        var probed = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return new Pages(this, probed ? new Probe(getApplicationContext()) : null);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) {
            open(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        open(intent);
    }

    // beside the camera, when the screen is on its side and has one in it
    @Override
    protected void fit(View shown, WindowInsets insets) {
        var camera = insets.getDisplayCutout();
        shown.setPadding(camera == null ? 0 : camera.getSafeInsetLeft(), 0, camera == null ? 0 : camera.getSafeInsetRight(), 0);
    }

    /**
     * Opens the address that a widget was tapped for. Any app can start this activity with an
     * address, so a widget also says what only this app knows.
     */
    @Override
    protected boolean open(Intent intent) {
        var address = intent.getData();
        return address != null && Widgets.token(this).equals(intent.getStringExtra(TOKEN)) && ((Pages)pages).show(address);
    }
}

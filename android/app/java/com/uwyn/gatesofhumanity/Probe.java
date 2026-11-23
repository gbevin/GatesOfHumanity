package com.uwyn.gatesofhumanity;

import android.content.Context;
import android.graphics.Bitmap;
import android.icu.util.TimeZone;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import rife.nativeapp.android.NativeSite;

/**
 * What android/check-app.sh asks of the app besides what its pages say. Only an app that was
 * built to be looked into has it, see android/build.sh.
 */
final class Probe {
    private final Context context_;

    Probe(Context context) {
        context_ = context;
    }

    /** The picture that a widget shows of the drawing at an address of the site, as a PNG in base64, or why there's none. */
    @JavascriptInterface
    public String widget(String path, int width, int height) {
        try {
            var drawn = new Drawn(new String(NativeSite.get(context_, path), StandardCharsets.UTF_8), Locale.US, TimeZone.getTimeZone("UTC"), false);
            var written = new ByteArrayOutputStream();
            drawn.picture(width, height, 48).compress(Bitmap.CompressFormat.PNG, 100, written);
            return Base64.encodeToString(written.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "failed: " + e;
        }
    }
}

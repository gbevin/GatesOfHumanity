package com.uwyn.gatesofhumanity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.icu.text.DateFormat;
import android.icu.util.TimeZone;
import android.net.Uri;

import com.caverock.androidsvg.PreserveAspectRatio;
import com.caverock.androidsvg.RenderOptions;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a widget shows, since it can't show a page: a drawing that the site makes in SVG, as it
 * draws its charts. A drawing says three things in ways that SVG has and that a browser shows
 * too. They're taken out of its text here, since the library that draws it doesn't act on them:
 *
 * <ul>
 * <li>The link around what it shows is the address that a tap opens in the app.
 * <li>The one element with the id "background" is what's behind the widget, which is stretched
 * over all of it while the rest keeps its shape.
 * <li>A text with a data-time is a moment that the device writes in its own language and time
 * zone, as its data-text says, with a skeleton of Unicode between braces: "until {EEEjmm}".
 * </ul>
 */
final class Drawn {
    private static final Pattern TEXT = Pattern.compile("<text\\b([^>]*)>([^<]*)</text>");
    private static final Pattern TIME = Pattern.compile("\\bdata-time=\"([^\"]*)\"");
    private static final Pattern WRITTEN = Pattern.compile("\\bdata-text=\"([^\"]*)\"");
    private static final Pattern SKELETON = Pattern.compile("\\{([A-Za-z]+)\\}");
    private static final Pattern LINK = Pattern.compile("<a\\b[^>]*\\bhref=\"([^\"]*)\"");
    private static final Pattern BEHIND = Pattern.compile("<\\w+\\b[^>]*\\bid=\"background\"[^>]*/>");
    private static final Pattern ROOT = Pattern.compile("<svg\\b[^>]*>");
    private static final Pattern SHARED = Pattern.compile("(?s)<(?:style|defs)\\b.*?</(?:style|defs)>");
    private static final String[][] ENTITIES = {{"&lt;", "<"}, {"&gt;", ">"}, {"&quot;", "\""}, {"&apos;", "'"}, {"&amp;", "&"}};

    final String foreground;
    final String background;
    final Uri url;
    /** What the drawing says, for who has the screen read out. */
    final String words;

    /**
     * @param hours whether the device writes a time with 24 hours, which someone can choose
     * apart from its language
     */
    Drawn(String svg, Locale locale, TimeZone zone, boolean hours) {
        // a StringBuffer, as the one after it: a Matcher only takes a StringBuilder since Android 14
        var timed = new StringBuffer();
        var texts = TEXT.matcher(svg);
        while (texts.find()) {
            var attributes = texts.group(1);
            var time = TIME.matcher(attributes);
            var written = WRITTEN.matcher(attributes);
            var replaced = texts.group();
            if (time.find() && written.find()) {
                try {
                    var moment = Date.from(Instant.parse(time.group(1)));
                    var said = new StringBuffer();
                    var skeletons = SKELETON.matcher(plain(written.group(1)));
                    while (skeletons.find()) {
                        var format = DateFormat.getInstanceForSkeleton(skeletons.group(1).replace('j', hours ? 'H' : 'h'), locale);
                        format.setTimeZone(zone);
                        skeletons.appendReplacement(said, Matcher.quoteReplacement(format.format(moment)));
                    }
                    skeletons.appendTail(said);
                    replaced = "<text" + attributes + ">" + said.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</text>";
                } catch (DateTimeParseException e) {
                    // as the site wrote it
                }
            }
            texts.appendReplacement(timed, Matcher.quoteReplacement(replaced));
        }
        texts.appendTail(timed);

        var said = new StringBuilder();
        for (var text = TEXT.matcher(timed); text.find(); ) {
            var word = plain(text.group(2)).trim();
            if (!word.isEmpty()) {
                said.append(said.length() == 0 ? "" : ", ").append(word);
            }
        }
        words = said.toString();
        var link = LINK.matcher(timed);
        url = link.find() ? Uri.parse(plain(link.group(1))) : null;
        var behind = BEHIND.matcher(timed);
        var root = ROOT.matcher(timed);
        if (behind.find() && root.find()) {
            // alone in a drawing of the same size, with the styles and the gradients that it may use
            var shared = new StringBuilder();
            for (var parts = SHARED.matcher(timed); parts.find(); ) {
                shared.append(parts.group());
            }
            background = root.group() + shared + behind.group() + "</svg>";
            foreground = timed.substring(0, behind.start()) + timed.substring(behind.end());
        } else {
            background = null;
            foreground = timed.toString();
        }
    }

    private static String plain(String text) {
        for (var entity : ENTITIES) {
            text = text.replace(entity[0], entity[1]);
        }
        return text;
    }

    /**
     * The drawing as a picture of a size in pixels with round corners: what's behind it over
     * all of the picture, the rest as large as fits, in the middle.
     */
    Bitmap picture(int width, int height, float radius) throws SVGParseException {
        var picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(picture);
        var round = new Path();
        round.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
        canvas.clipPath(round);
        if (background != null) {
            draw(background, canvas, PreserveAspectRatio.STRETCH);
        }
        draw(foreground, canvas, PreserveAspectRatio.LETTERBOX);
        return picture;
    }

    private static void draw(String svg, Canvas canvas, PreserveAspectRatio fitted) throws SVGParseException {
        var parsed = SVG.getFromString(svg);
        // a drawing has a size of its own, which is the room that it gets here
        parsed.setDocumentWidth("100%");
        parsed.setDocumentHeight("100%");
        parsed.renderToCanvas(canvas, RenderOptions.create().viewPort(0, 0, canvas.getWidth(), canvas.getHeight()).preserveAspectRatio(fitted));
    }
}

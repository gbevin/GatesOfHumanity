package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.TransitTimeline;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;
import rife.engine.annotations.Parameter;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The transits of this moment as an image for a page elsewhere: the drawing that a widget of
 * the app shows, which is an SVG of its own, with its moments in UTC and where it's from.
 */
public class TransitsEmbed implements Element {
    /**
     * An image that can be asked for, as wide and as high as its drawing is.
     */
    public record Size(String name, int width, int height) {
    }

    public static final List<Size> SIZES = List.of(new Size("medium", 338, 158), new Size("small", 158, 158));
    public static final String SIZE_PATTERN = "small|medium";
    // a page that shows the image asks for it again after this at the latest, and sooner when a planet moves on
    static final Duration KEPT = Duration.ofMinutes(10);

    @ActiveSite AppSite site;

    @Parameter String size;

    @Override
    public void process(Context c) {
        var now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        if (!Ephemeris.covers(now)) {
            c.setStatus(404);
            return;
        }
        var moment = TransitTimeline.at(TransitsWidget.PLANETS, now);
        var kept = Duration.between(Instant.now(), moment.end());
        c.setHeader("Cache-Control", "public, max-age=" + Math.clamp(kept.toSeconds(), 0, KEPT.toSeconds()));
        c.print(TransitsWidget.drawing(c, site, "system" + Character.toUpperCase(size.charAt(0)) + size.substring(1), moment, true));
    }
}

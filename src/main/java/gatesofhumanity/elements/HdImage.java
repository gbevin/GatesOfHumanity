package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.CityDirectory;
import gatesofhumanity.models.Location;
import rife.engine.*;
import rife.template.Template;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

public abstract class HdImage extends LocatedElement {
    static final int LOCATION_COOKIE_MAX_AGE = 365 * 24 * 60 * 60;
    // the charts are in English whatever the locale of the server
    static final DateTimeFormatter MOMENT_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("E, MMMM d, yyyy @ h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter ZONED_TIME_FORMAT = DateTimeFormatter.ofPattern("E, MMMM d, yyyy @ h:mm a z", Locale.ENGLISH);

    Location location_;
    protected ZonedDateTime momentLocation_;
    protected ZonedDateTime momentUtc_;

    /**
     * Prepares the location and moment, or answers with an error image and returns false
     * when the city isn't known or the moment isn't a valid date.
     */
    protected boolean processParameters(Context c) {
        // an id too large for an Integer arrives as null
        var location_requested = c.parameter("locationId") != null;
        if (location_requested && (locationId == null || CityDirectory.find(locationId) == null)) {
            printUnknownLocation(c);
            return false;
        }
        location_ = chosenLocation();
        // only an image with its moment and city in its URL is the same for everyone
        if (datetime == null || !location_requested) {
            c.preventCaching();
        }
        if (location_requested && remembersLocation()) {
            // set by the server, since Safari keeps cookies that scripts write for at most 7 days
            c.addCookie(new CookieBuilder(LOCATION_COOKIE, String.valueOf(location_.id()))
                .path("/")
                .maxAge(LOCATION_COOKIE_MAX_AGE)
                .httpOnly(true)
                .secure(c.webappRootUrl().startsWith("https:"))
                .sameSite(SameSite.LAX));
        }

        var zone = ZoneId.of(location_.timezone());

        var moment = LocalDateTime.now(zone);
        if (datetime != null) {
            moment = parseMoment(c, datetime);
            if (moment == null) {
                return false;
            }
        }

        momentLocation_ = moment.atZone(zone);
        momentUtc_ = momentLocation_.withZoneSameInstant(ZoneOffset.UTC);
        return true;
    }

    /**
     * Whether the location of this image is the one the visitor is at, which pages use for the
     * current time.
     */
    protected boolean remembersLocation() {
        return true;
    }

    // the moment that an address has, or nothing when it isn't one
    static LocalDateTime moment(String written) {
        try {
            return LocalDateTime.parse(written, MOMENT_FORMAT);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    protected LocalDateTime parseMoment(Context c, String value) {
        var moment = moment(value);
        if (moment == null) {
            // unlike the other errors, which visitors can reach from the pages, only a hand-made URL holds an invalid date
            c.setStatus(400);
            printError(c, "This isn't a valid date.");
        }
        return moment;
    }

    protected void printError(Context c, String message) {
        var t = c.templateSvg("error");
        t.setValueEncoded("message", message);
        c.print(t);
    }

    // these two are answered with status 200, since the pages only draw an answer that succeeded
    protected void printUnknownLocation(Context c) {
        printError(c, "This city isn't known, please choose it again.");
    }

    protected void printOutOfRange(Context c, Instant first, String what) {
        printError(c, what + " can be made from " + DAY_FORMAT.format(first.atZone(ZoneOffset.UTC)) +
                      " to " + DAY_FORMAT.format(Ephemeris.end().atZone(ZoneOffset.UTC)) + ".");
    }

    static String localMoment(ZonedDateTime moment) {
        return ZONED_TIME_FORMAT.format(moment);
    }

    static String utcMoment(ZonedDateTime moment) {
        return TIME_FORMAT.format(moment) + " UTC";
    }

    // the footer link shows the root URL without its trailing slash, of the site on the web when
    // the drawing is made elsewhere, as in an app: a saved drawing leaves the place it's made in
    Template prepareTemplate(Context c, Template t) {
        var site_url = c.hasProperty("gatesofhumanity.shareRootUrl") ? String.valueOf(c.property("gatesofhumanity.shareRootUrl")) : c.webappRootUrl();
        t.setValue("site-url", site_url);
        t.setValue("site-name", site_url.endsWith("/") ? site_url.substring(0, site_url.length() - 1) : site_url);
        return t;
    }

    static String coordinates(Location l) {
        return (l.latitude() >= 0 ? l.latitude() + "\u00b0 N, " : Math.abs(l.latitude()) + "\u00b0 S, ") +
               (l.longitude() >= 0 ? l.longitude() + "\u00b0 E" : Math.abs(l.longitude()) + "\u00b0 W");
    }
}

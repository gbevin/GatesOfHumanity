package gatesofhumanity.elements;

import rife.engine.Context;
import rife.template.Template;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

public abstract class HdPage extends LocatedElement {
    static final DateTimeFormatter FIELD_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);

    void processTemplate(Context c, Template t, PageDescription page) {
        // a page starts at the current time of the city the visitor's cookie remembers
        c.preventCaching();
        Pages.header(c, site, t, page);

        var location = chosenLocation();
        var requested = parseField(datetime);
        var shown = requested == null ? LocalDateTime.now(ZoneId.of(location.timezone())) : requested;
        t.setValue("datetime", shown.format(FIELD_FORMAT));
        if (requested == null) {
            t.setValue("now-location", location.id());
        }
    }

    private static LocalDateTime parseField(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, FIELD_FORMAT);
        } catch (DateTimeParseException _) {
            return null;
        }
    }
}

package gatesofhumanity.elements;

import gatesofhumanity.models.CityDirectory;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.Parameter;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The current wall clock time at a location, which the browser can't know when the location
 * is in another time zone.
 */
public class Now implements Element {
    @Parameter Integer locationId;

    @Override
    public void process(Context c) {
        c.preventCaching();
        var location = CityDirectory.findOrLondon(locationId);
        c.setContentType("text/plain");
        c.print(LocalDateTime.now(ZoneId.of(location.timezone())).format(HdPage.FIELD_FORMAT));
    }
}

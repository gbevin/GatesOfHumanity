package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.CityDirectory;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.Parameter;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The moment in UTC of a birth at a time on the clock of a place, which is how a widget of the
 * app is asked for a person: looking up a place takes the memory that the app has and a widget
 * doesn't, so the app asks this once for each person that it saved.
 */
public class Born implements Element {
    @Parameter String datetime;
    @Parameter Integer locationId;

    @Override
    public void process(Context c) {
        var local = HdImage.moment(datetime);
        if (local == null) {
            c.setStatus(400);
            return;
        }
        var born = local.atZone(ZoneId.of(CityDirectory.findOrLondon(locationId).timezone())).toInstant();
        if (!Ephemeris.coversChart(born)) {
            c.setStatus(400);
            return;
        }
        c.setContentType("text/plain");
        c.print(HdImage.MOMENT_FORMAT.format(born.atZone(ZoneOffset.UTC)));
    }
}

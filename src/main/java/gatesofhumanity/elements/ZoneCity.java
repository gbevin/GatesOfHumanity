package gatesofhumanity.elements;

import gatesofhumanity.models.CityDirectory;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.Parameter;
import rife.json.Json;

import java.util.List;

/**
 * The most populated city of a time zone, which is where a page starts for a visitor who hasn't
 * chosen a city: a browser knows the time zone it's set to, and not the cities in it.
 */
public class ZoneCity implements Element {
    @Parameter String zone;

    @Override
    public void process(Context c) {
        var location = zone == null ? null : CityDirectory.largestIn(zone);
        c.setContentType("application/json");
        // as a row of the city search, or nothing for a zone without a city, such as UTC
        c.print(Json.toString(location == null ? List.of() : List.of(location.city(), location.id())));
    }
}

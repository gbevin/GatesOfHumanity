package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import gatesofhumanity.models.CityDirectory;
import gatesofhumanity.models.Location;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;
import rife.engine.annotations.Cookie;
import rife.engine.annotations.Parameter;

/**
 * A page or image of a moment seen from a location, which the visitor chose or which a cookie
 * remembers.
 */
abstract class LocatedElement implements Element {
    static final String LOCATION_COOKIE = "locationId";

    @ActiveSite AppSite site;

    @Parameter String datetime;
    @Parameter Integer locationId;
    @Cookie(LOCATION_COOKIE) Integer locationIdCookie;

    Location chosenLocation() {
        return CityDirectory.findOrLondon(locationId != null ? locationId : locationIdCookie);
    }
}

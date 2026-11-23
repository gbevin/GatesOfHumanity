package gatesofhumanity.elements;

import gatesofhumanity.models.CityDirectory;
import gatesofhumanity.models.Cycle;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.Parameter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The wall clock time at a location of a cycle of a birth, which the transits of a Birth
 * Transit Chart can be set to.
 */
public class CycleTime implements Element {
    public static final String PATTERN = String.join("|", Arrays.stream(Cycle.values()).map(cycle -> Pattern.quote(slug(cycle))).toList());

    @Parameter String cycle;
    @Parameter String birth;
    @Parameter Integer birthLocationId;
    @Parameter Integer locationId;

    /**
     * The first minute at which the planet has reached its point, as the charts show minutes.
     */
    static ZonedDateTime firstMinute(Instant moment, ZoneId zone) {
        var minute = moment.truncatedTo(ChronoUnit.MINUTES);
        return (minute.equals(moment) ? minute : minute.plus(1, ChronoUnit.MINUTES)).atZone(zone);
    }

    static String slug(Cycle cycle) {
        return cycle.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public void process(Context c) {
        var chosen = Arrays.stream(Cycle.values()).filter(each -> slug(each).equals(cycle)).findFirst();
        var birth_location = birthLocationId == null ? null : CityDirectory.find(birthLocationId);
        var birth_moment = HdImage.moment(birth);
        if (chosen.isEmpty() || birth_location == null || birth_moment == null) {
            c.setStatus(404);
            return;
        }
        var moment = chosen.get().moment(birth_moment.atZone(ZoneId.of(birth_location.timezone())).toInstant());
        if (moment.isEmpty()) {
            c.setStatus(404);
            return;
        }
        c.setContentType("text/plain");
        c.print(firstMinute(moment.get(), ZoneId.of(CityDirectory.findOrLondon(locationId).timezone())).format(HdPage.FIELD_FORMAT));
    }
}

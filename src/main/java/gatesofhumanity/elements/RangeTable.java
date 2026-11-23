package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.*;
import rife.engine.Context;
import rife.engine.annotations.Parameter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The table of a month or a day: a row for each day or hour, with the gate and line of every
 * planet that changed since the row before. It's HTML that the page shows where a chart is drawn.
 */
public class RangeTable extends HdImage {
    public static final String DAY = "d";
    // the month by name, since 03/04 is a day in March for some and in April for others
    private static final DateTimeFormatter DAY_ROW_FORMAT = DateTimeFormatter.ofPattern("E, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter HOUR_ROW_FORMAT = DateTimeFormatter.ofPattern("MMM d, h a", Locale.ENGLISH);

    @Parameter String duration;

    boolean monthly() {
        return !DAY.equals(duration);
    }

    int rows() {
        return monthly() ? 31 : 24;
    }

    // stepped in the local time, so that days keep their time of day across a change to or from daylight saving time
    ZonedDateTime rowMoment(int row) {
        return monthly() ? momentLocation_.plusDays(row) : momentLocation_.plusHours(row);
    }

    boolean coversRange() {
        return Ephemeris.covers(momentLocation_.toInstant()) && Ephemeris.covers(rowMoment(rows() - 1).toInstant());
    }

    @Override
    public void process(Context c) {
        if (!processParameters(c)) {
            return;
        }
        if (!coversRange()) {
            printOutOfRange(c, Ephemeris.start(), "Transits");
            return;
        }

        var t = c.template("range_table");
        // the symbols of the planets are those of the charts
        t.setValue("planet-paths", c.templateSvg("planet_paths").getContent());
        var monthly = monthly();
        t.setValue("table-name", monthly ? "Month" : "Day");
        t.setValue("chart-date", localMoment(momentLocation_));
        t.setValue("chart-date-utc", utcMoment(momentUtc_));
        t.setValue("chart-coordinates", coordinates(location_));

        var format = monthly ? DAY_ROW_FORMAT : HOUR_ROW_FORMAT;
        var range = rows();
        var series = Charts.series(momentLocation_, range, monthly ? Charts.Step.DAYS : Charts.Step.HOURS);
        var last_positions = new HashMap<Planet, Position>();
        for (var d = 0; d < range; ++d) {
            t.setValue("gate-date", format.format(rowMoment(d)));

            for (var current : series.get(d)) {
                var last = last_positions.get(current.planet());
                if (d == range - 1 || last == null || last.gateNumber() != current.gateNumber() || last.gateLine() != current.gateLine()) {
                    t.setValue(current.planet().id_ + "-gate", current.gateNumber() + "." + current.gateLine());
                } else {
                    t.setValue(current.planet().id_ + "-gate", "");
                }

                last_positions.put(current.planet(), current);
            }
            t.appendBlock("rows", "row");
        }
        c.print(t);
    }
}

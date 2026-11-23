package gatesofhumanity.elements;

import rife.engine.Context;

import static gatesofhumanity.elements.ExplorePanel.*;

/**
 * The Explore panel below a range table: what its rows, columns and cells show.
 */
public class RangeExplore extends RangeTable {
    // the table that the panel is loaded with explains errors
    @Override
    protected void printError(Context c, String message) {
    }

    @Override
    public void process(Context c) {
        if (!processParameters(c) || !coversRange()) {
            return;
        }
        var t = start(c, site, "table", monthly() ? "guide.range_month" : "guide.range_day", monthly() ? "Transits by Month" : "Transits by Day", "about.range");
        startSection(t);
        var more = More.of(c, site, LearnTopic.CHARTS, monthly() ? "Transits by Month" : "Transits by Day");
        item(t, "range.dates", monthly() ? "Days" : "Hours", "", more, "range.dates");
        item(t, "range.planets", "Planets", "", more, "range.planets");
        item(t, "range.cells", "Gates and lines", "", more, "range.cells");
        endGuide(t);
        c.print(t);
    }
}

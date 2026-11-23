package gatesofhumanity.elements;

import rife.engine.Context;
import rife.engine.annotations.Parameter;

public class Range extends HdPage {
    // what the scripts of the page know it by, a table isn't saved as an image
    static final String TABLE = "table";

    @Parameter String duration;

    @Override
    public void process(Context c) {
        var t = c.template("range");

        var day = RangeTable.DAY.equals(duration);
        var table_url = c.urlFor(site.rangeTable);
        t.setValue("image-url", day ? table_url.param("duration", RangeTable.DAY) : table_url);
        t.setValue("chart-type", TABLE);

        processTemplate(c, t, day ? PageDescription.RANGE_DAY : PageDescription.RANGE_MONTH);

        c.print(t);
    }
}

package gatesofhumanity.elements;

import gatesofhumanity.models.Cycle;
import rife.engine.*;
import rife.engine.annotations.Parameter;

public class Wheel extends HdPage {
    @Parameter String type;

    @Override
    public void process(Context c) {
        var chart = ChartType.of(type);
        var t = c.template(chart.template_);
        t.setValue("chart-type", chart.parameter_);
        t.setValue("image-url", chart.imageUrl(c, site));
        if (chart == ChartType.BIRTH_TRANSIT) {
            for (var cycle : Cycle.values()) {
                t.setValue("cycle-slug", CycleTime.slug(cycle));
                t.setValueEncoded("cycle-label", cycle.label_);
                t.appendBlock("cycles", "cycle");
            }
        }

        processTemplate(c, t, chart.page_);
        c.print(t);
    }
}

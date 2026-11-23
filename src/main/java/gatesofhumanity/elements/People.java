package gatesofhumanity.elements;

import gatesofhumanity.AppSite;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.ActiveSite;

import java.util.List;

/**
 * The page of the saved people. They're only in the visitor's browser, so the page is filled in
 * there, and what it gets from here are the charts that a person can be opened in.
 */
public class People implements Element {
    // the charts whose first person is the one that the browser remembers
    static final List<ChartType> CHARTS = List.of(ChartType.BIRTH, ChartType.BIRTH_TRANSIT, ChartType.CONNECTION);

    @ActiveSite AppSite site;

    @Override
    public void process(Context c) {
        var t = c.template("people");
        Pages.header(c, site, t, PageDescription.PEOPLE);
        t.setValueEncoded("intro-title", PageDescription.PEOPLE.title());
        for (var chart : CHARTS) {
            t.setValue("chart-label", chart.page_.heading());
            t.setValue("chart-url", chart.url(c, site));
            t.appendBlock("person-charts", "person-chart");
        }
        c.print(t);
    }
}

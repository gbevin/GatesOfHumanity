package gatesofhumanity;

import gatesofhumanity.elements.BodygraphWidget;
import gatesofhumanity.elements.Born;
import gatesofhumanity.elements.Panel;
import gatesofhumanity.elements.PersonWidget;
import gatesofhumanity.elements.TransitsWidget;
import gatesofhumanity.elements.WheelVolume;
import gatesofhumanity.elements.Widget;
import rife.engine.PathInfoHandling;
import rife.engine.Route;

/**
 * The site inside an app, which asks it for more than pages: what its widgets show, and for
 * an app that has room around its window, the card that explains a part of a chart as a page
 * of its own and a chart as shapes.
 */
public class AppSiteEmbedded extends AppSiteDeployed {
    public Route transitsWidget = get("/widget/transits", PathInfoHandling.MAP(
        m -> m.p("family", TransitsWidget.FAMILY_PATTERN),
        m -> m.p("family", TransitsWidget.FAMILY_PATTERN).s().p("datetime", MOMENT),
        m -> m.p("family", TransitsWidget.FAMILY_PATTERN).s().p("datetime", MOMENT).s().p("ahead", Widget.AHEAD_PATTERN)), TransitsWidget.class);
    public Route bodygraphWidget = get("/widget/bodygraph", PathInfoHandling.MAP(
        m -> m.p("family", BodygraphWidget.FAMILY_PATTERN),
        m -> m.p("family", BodygraphWidget.FAMILY_PATTERN).s().p("datetime", MOMENT),
        m -> m.p("family", BodygraphWidget.FAMILY_PATTERN).s().p("datetime", MOMENT).s().p("ahead", Widget.AHEAD_PATTERN)), BodygraphWidget.class);
    // a person is the moment of their birth in UTC, which the app asks for with what it saved of them
    public Route personWidget = get("/widget/person", PathInfoHandling.MAP(
        m -> m.p("birth", MOMENT).s().p("family", BodygraphWidget.FAMILY_PATTERN),
        m -> m.p("birth", MOMENT).s().p("family", BodygraphWidget.FAMILY_PATTERN).s().p("datetime", MOMENT),
        m -> m.p("birth", MOMENT).s().p("family", BodygraphWidget.FAMILY_PATTERN).s().p("datetime", MOMENT).s().p("ahead", Widget.AHEAD_PATTERN)), PersonWidget.class);
    public Route born = get("/widget/born", PathInfoHandling.MAP(m -> m.p("datetime", MOMENT).s().p("locationId", ID)), Born.class);
    // the card of a chart's page as a page of its own, for a panel beside the window of an app
    public Route panel = get("/panel", Panel.class);
    // a chart as shapes, at the address of its image with volume in the place of image
    public Route wheelVolume = get("/wheel/volume", wheelPathInfo(), WheelVolume.class);
}

package gatesofhumanity.elements;

import gatesofhumanity.ephemeris.Ephemeris;
import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Charts;
import rife.engine.Context;
import rife.engine.Route;
import rife.engine.annotations.Parameter;
import rife.template.Template;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The widget of a person with the transits of a moment: the body graph of their birth chart,
 * with what the planets add to it where they are, the channels they complete by name with
 * until when each one lasts, and the centers that those define. The person is a moment of
 * birth in UTC, which the app has from the people it saved, and a name to show.
 */
public class PersonWidget extends BodygraphWidget {
    @Parameter String birth;
    @Parameter String name;

    private Instant born_;

    @Override
    public void process(Context c) {
        var local = HdImage.moment(birth);
        born_ = local == null ? null : local.toInstant(ZoneOffset.UTC);
        if (born_ == null || !Ephemeris.coversChart(born_)) {
            c.setStatus(400);
            return;
        }
        name = WheelImage.xmlText(name);
        super.process(c);
    }

    @Override
    Route route() {
        return site.personWidget;
    }

    @Override
    String address() {
        return route().path() + "/" + birth;
    }

    @Override
    String query() {
        return name == null ? "" : "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    @Override
    Set<Integer> own() {
        return Analysis.gates(Charts.birth(born_.atZone(ZoneOffset.UTC)));
    }

    // the chart of the person with the transits, which the page shows for who the app saved under that name
    @Override
    String opens(Context c) {
        var chart = ChartType.BIRTH_TRANSIT.url(c, site);
        return (name == null ? chart : chart.param("person", name)).toString();
    }

    // a channel that the planets complete can end in a center that the person has, so one alone can be added
    @Override
    String centers(int centers) {
        return centers == 1 ? "1 center" : super.centers(centers);
    }

    @Override
    String are() {
        return "added";
    }

    @Override
    List<String> about() {
        return descriptions("about.transits.birth", "about.transits.center");
    }

    @Override
    void word(Template t, int centers) {
        super.word(t, centers);
        if (name != null && !name.isBlank()) {
            set(t, "title", "The Transits of " + name.strip());
            // in the letters of a heading, as long as it has the room for
            var heading = Fitted.of(name.strip().toUpperCase(Locale.ROOT), 16, 11, 11, 1).lines().getFirst();
            set(t, "heading", heading);
            if (t.hasBlock("owner")) {
                // under a body graph that leaves a line for it, in smaller letters when it's long
                var owner = Fitted.of(name.strip().toUpperCase(Locale.ROOT), number(t, "owner_letters"), number(t, "owner_size"), number(t, "owner_smallest"), 1);
                set(t, "owner_name", owner.lines().getFirst());
                set(t, "owner_fitted", owner.written());
                t.setBlock("owner", "owner");
                t.setBlock("placed", "placed_owner");
            }
        }
    }
}

import gatesofhumanity.models.Analysis;
import gatesofhumanity.models.Charts;
import gatesofhumanity.models.Circuit;
import gatesofhumanity.models.Planet;
import gatesofhumanity.models.PositionData;
import gatesofhumanity.models.Variables;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes the site's charts for the moments in a file, one UTC instant a line, as JSON lines for
 * Tools/compare/compare.py.
 */
public class ExportCharts {
    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    private static String list(List<String> items) {
        return "[" + items.stream().map(ExportCharts::quote).collect(Collectors.joining(",")) + "]";
    }

    private static String imprint(PositionData data) {
        var planets = new ArrayList<String>();
        for (var p : data.positions()) {
            if (p.planet() == Planet.CHIRON) {
                continue;
            }
            planets.add(quote(p.planet().id_) + ":[" + p.gateNumber() + "," + p.gateLine() + "," + p.color() + "," +
                        p.tone() + "," + p.longitude() + "]");
        }
        return "{" + String.join(",", planets) + "}";
    }

    private static String variable(Variables.Variable v) {
        return quote(v.kind().name().toLowerCase(Locale.ROOT)) + ":[" + v.color() + "," + v.tone() + "]";
    }

    public static void main(String[] args)
    throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -cp \"build/main:src/main/resources:lib/compile/*\" Tools/compare/ExportCharts.java moments.txt > site.jsonl");
            System.exit(2);
        }
        var out = new StringBuilder();
        for (var line : Files.readAllLines(Path.of(args[0]))) {
            if (line.isBlank()) {
                continue;
            }
            var chart = Charts.birth(Instant.parse(line.strip()).atZone(ZoneOffset.UTC));
            var a = Analysis.of(chart);
            var v = Variables.of(chart);
            var cross = a.cross();
            out.append("{\"m\":").append(quote(line.strip()))
                .append(",\"dm\":").append(quote(chart.design().time().toInstant().toString()))
                .append(",\"p\":").append(imprint(chart.personality()))
                .append(",\"d\":").append(imprint(chart.design()))
                .append(",\"type\":").append(quote(a.type().label_))
                .append(",\"authority\":").append(quote(a.authority().label_))
                .append(",\"profile\":").append(quote(a.profile().personalityLine() + "/" + a.profile().designLine()))
                .append(",\"definition\":").append(quote(a.definition().label_))
                .append(",\"angle\":").append(quote(cross.angle().label_))
                .append(",\"cross_gates\":[").append(cross.personalitySun()).append(",").append(cross.personalityEarth())
                .append(",").append(cross.designSun()).append(",").append(cross.designEarth()).append("]")
                .append(",\"cross\":").append(quote(cross.title()))
                .append(",\"channels\":").append(list(a.definedChannels().stream().map(Analysis.NamedChannel::key).sorted().toList()))
                .append(",\"centers\":").append(list(a.definedCenters().stream().map(c -> c.name().toLowerCase(Locale.ROOT)).sorted().toList()))
                .append(",\"circuits\":{").append(a.definedChannels().stream()
                    .map(c -> quote(c.key()) + ":" + quote(Circuit.of(c).name().toLowerCase(Locale.ROOT))).collect(Collectors.joining(",")))
                .append("},\"variables\":{").append(String.join(",", variable(v.determination()), variable(v.environment()),
                    variable(v.motivation()), variable(v.perspective())))
                .append("}}\n");
        }
        System.out.print(out);
    }
}

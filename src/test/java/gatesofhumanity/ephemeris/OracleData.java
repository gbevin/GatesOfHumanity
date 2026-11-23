package gatesofhumanity.ephemeris;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reference positions computed with Swiss Ephemeris by {@code Tools/generate-oracle.py}.
 */
public final class OracleData {
    // the order of the columns of the CSV
    public static final List<Body> COLUMNS = List.of(
        Body.SUN, Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN,
        Body.URANUS, Body.NEPTUNE, Body.PLUTO, Body.TRUE_NODE, Body.CHIRON);

    public record Row(double jdUt, double[] longitudes, double designJdUt, double[] designLongitudes) {
        public double longitude(Body body) {
            return longitudes[COLUMNS.indexOf(body)];
        }

        public double designLongitude(Body body) {
            return designLongitudes[COLUMNS.indexOf(body)];
        }
    }

    public static final List<Row> ROWS = read();

    private static List<Row> read() {
        var rows = new ArrayList<Row>();
        try (var reader = new BufferedReader(new InputStreamReader(
            OracleData.class.getClassLoader().getResourceAsStream("oracle/swisseph.csv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                var columns = line.split(",");
                var longitudes = new double[COLUMNS.size()];
                var design_longitudes = new double[COLUMNS.size()];
                for (var i = 0; i < COLUMNS.size(); ++i) {
                    longitudes[i] = Double.parseDouble(columns[i + 1]);
                    design_longitudes[i] = Double.parseDouble(columns[i + COLUMNS.size() + 2]);
                }
                rows.add(new Row(Double.parseDouble(columns[0]), longitudes, Double.parseDouble(columns[COLUMNS.size() + 1]), design_longitudes));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(rows);
    }

    public static double angularDistance(double a, double b) {
        var d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    private OracleData() {
    }
}

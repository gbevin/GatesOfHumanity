package gatesofhumanity;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Fits Chebyshev series to geocentric apparent ecliptic longitudes from JPL Horizons and writes
 * them as the tables that {@code gatesofhumanity.ephemeris.Ephemeris} evaluates at runtime.
 * <p>
 * Every table is indexed by Julian day in UT, the time scale Horizons is queried in, so the
 * runtime needs no Delta T model.
 */
public class EphemerisGenerator {
    public static final int MAGIC = 0x48444550;
    public static final int FORMAT_VERSION = 1;
    // Horizons has Pluto from 1800-01-02 23:59 UT to 2199-12-28 23:58 UT, the end is a whole
    // number of segments after the start for every body
    public static final double JD_START = 2378498.5; // 1800-01-03 00:00 UT
    public static final double JD_END = 2524578.5;   // 2199-12-17 00:00 UT

    private static final String API = "https://ssd.jpl.nasa.gov/api/horizons.api";
    private static final int MAX_LINES_PER_REQUEST = 80000;
    private static final DateTimeFormatter HORIZONS_DATE = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.ENGLISH);

    record Body(String name, String command, double stepDays, int segmentDays, int degree) {
    }

    // the planets fit worst within a degree of the Sun, where the gravitational deflection of
    // light changes quickly, still under 2" while a line is 0.9375 degrees
    static final List<Body> BODIES = List.of(
        new Body("sun", "10", 1.0, 40, 13),
        new Body("moon", "301", 0.25, 16, 18),
        new Body("mercury", "199", 0.5, 40, 16),
        new Body("venus", "299", 1.0, 40, 12),
        new Body("mars", "499", 1.0, 40, 11),
        new Body("jupiter", "599", 1.0, 80, 10),
        new Body("saturn", "699", 1.0, 80, 10),
        new Body("uranus", "799", 1.0, 80, 9),
        new Body("neptune", "899", 1.0, 80, 9),
        new Body("pluto", "999", 1.0, 80, 9),
        new Body("chiron", "2060;", 1.0, 80, 10));
    static final Body TRUE_NODE = new Body("true_node", "301", 0.25, 40, 20);

    private final File cacheDirectory_;
    private final File outputDirectory_;
    private final HttpClient client_ = HttpClient.newHttpClient();

    public EphemerisGenerator(File cacheDirectory, File outputDirectory) {
        cacheDirectory_ = cacheDirectory;
        outputDirectory_ = outputDirectory;
    }

    public void generate(Collection<String> names)
    throws IOException, InterruptedException {
        var known = Stream.concat(BODIES.stream(), Stream.of(TRUE_NODE)).map(Body::name).toList();
        var unknown = names.stream().filter(name -> !known.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown bodies " + unknown + ", the ones to choose from are " + String.join(", ", known));
        }
        outputDirectory_.mkdirs();
        for (var body : BODIES) {
            if (names.isEmpty() || names.contains(body.name())) {
                write(body, fit(body, observerLongitudes(body)));
            }
        }
        if (names.isEmpty() || names.contains(TRUE_NODE.name())) {
            write(TRUE_NODE, fit(TRUE_NODE, trueNodeLongitudes()));
        }
    }

    private static int segmentCount(Body body) {
        var count = (JD_END - JD_START) / body.segmentDays();
        if (count != Math.rint(count)) {
            throw new IllegalStateException("The range is not a whole number of segments for " + body.name());
        }
        return (int) count;
    }

    private NavigableMap<Double, Double> observerLongitudes(Body body)
    throws IOException, InterruptedException {
        var result = new TreeMap<Double, Double>();
        for (var line : fetch(body, "OBSERVER", Map.of("QUANTITIES", "31", "EXTRA_PREC", "YES"))) {
            var columns = line.split(",");
            var jd = toJulianDay(LocalDateTime.parse(columns[0].trim(), HORIZONS_DATE));
            result.put(jd, Double.parseDouble(columns[3].trim()));
        }
        return result;
    }

    private NavigableMap<Double, Double> trueNodeLongitudes()
    throws IOException, InterruptedException {
        var result = new TreeMap<Double, Double>();
        for (var line : fetch(TRUE_NODE, "VECTORS", Map.of("REF_PLANE", "ECLIPTIC", "VEC_TABLE", "2", "VEC_CORR", "NONE", "OUT_UNITS", "KM-S"))) {
            var c = line.split(",");
            var jd = Double.parseDouble(c[0].trim());
            double[] r = {Double.parseDouble(c[2].trim()), Double.parseDouble(c[3].trim()), Double.parseDouble(c[4].trim())};
            double[] v = {Double.parseDouble(c[5].trim()), Double.parseDouble(c[6].trim()), Double.parseDouble(c[7].trim())};
            result.put(jd, trueNodeOfDate(jd, r, v));
        }
        return result;
    }

    /**
     * The osculating ascending node of the Moon's geocentric orbit, from a J2000 ecliptic state
     * vector, precessed to the ecliptic and equinox of date and corrected for nutation in longitude.
     */
    static double trueNodeOfDate(double jd, double[] r, double[] v) {
        var hx = r[1] * v[2] - r[2] * v[1];
        var hy = r[2] * v[0] - r[0] * v[2];
        var hz = r[0] * v[1] - r[1] * v[0];
        var pole_longitude = Math.atan2(hy, hx);
        var pole_latitude = Math.atan2(hz, Math.hypot(hx, hy));
        var pole_of_date = precessLongitude(jd, pole_longitude, pole_latitude);
        return normalize(Math.toDegrees(pole_of_date) + 90.0 + nutationInLongitude(jd));
    }

    // Meeus, Astronomical Algorithms, 21.5 and 21.7, from the J2000 ecliptic to the ecliptic of date
    static double precessLongitude(double jd, double longitude, double latitude) {
        var t = (jd - 2451545.0) / 36525.0;
        var eta = arcseconds(47.0029 * t - 0.03302 * t * t + 0.000060 * t * t * t);
        var pi = Math.toRadians(174.876384) + arcseconds(-869.8089 * t + 0.03536 * t * t);
        var p = arcseconds(5029.0966 * t + 1.11113 * t * t - 0.000006 * t * t * t);
        var a = Math.cos(eta) * Math.cos(latitude) * Math.sin(pi - longitude) - Math.sin(eta) * Math.sin(latitude);
        var b = Math.cos(latitude) * Math.cos(pi - longitude);
        return p + pi - Math.atan2(a, b);
    }

    // Meeus, Astronomical Algorithms, table 22.A, the terms of IAU 1980 nutation above 0.01"
    private static final int[][] NUTATION_ARGUMENTS = {
        {0, 0, 0, 0, 1}, {-2, 0, 0, 2, 2}, {0, 0, 0, 2, 2}, {0, 0, 0, 0, 2}, {0, 1, 0, 0, 0},
        {0, 0, 1, 0, 0}, {-2, 1, 0, 2, 2}, {0, 0, 0, 2, 1}, {0, 0, 1, 2, 2}, {-2, -1, 0, 2, 2},
        {-2, 0, 1, 0, 0}, {-2, 0, 0, 2, 1}, {0, 0, -1, 2, 2}, {2, 0, 0, 0, 0}, {0, 0, 1, 0, 1}};
    private static final double[][] NUTATION_COEFFICIENTS = {
        {-171996, -174.2}, {-13187, -1.6}, {-2274, -0.2}, {2062, 0.2}, {1426, -3.4},
        {712, 0.1}, {-517, 1.2}, {-386, -0.4}, {-301, 0}, {217, -0.5},
        {-158, 0}, {129, 0.1}, {123, 0}, {63, 0}, {63, 0.1}};

    static double nutationInLongitude(double jd) {
        var t = (jd - 2451545.0) / 36525.0;
        double[] fundamental = {
            297.85036 + 445267.111480 * t - 0.0019142 * t * t + t * t * t / 189474.0,
            357.52772 + 35999.050340 * t - 0.0001603 * t * t - t * t * t / 300000.0,
            134.96298 + 477198.867398 * t + 0.0086972 * t * t + t * t * t / 56250.0,
            93.27191 + 483202.017538 * t - 0.0036825 * t * t + t * t * t / 327270.0,
            125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000.0};
        var sum = 0.0;
        for (var i = 0; i < NUTATION_ARGUMENTS.length; ++i) {
            var argument = 0.0;
            for (var j = 0; j < 5; ++j) {
                argument += NUTATION_ARGUMENTS[i][j] * fundamental[j];
            }
            sum += (NUTATION_COEFFICIENTS[i][0] + NUTATION_COEFFICIENTS[i][1] * t) * Math.sin(Math.toRadians(argument));
        }
        return sum * 0.0001 / 3600.0;
    }

    private List<String> fetch(Body body, String type, Map<String, String> parameters)
    throws IOException, InterruptedException {
        var lines = new ArrayList<String>();
        var chunk_days = MAX_LINES_PER_REQUEST * body.stepDays();
        for (var start = JD_START; start < JD_END; start += chunk_days) {
            var stop = Math.min(start + chunk_days, JD_END);
            lines.addAll(fetchChunk(body, type, parameters, start, stop));
        }
        return lines;
    }

    private List<String> fetchChunk(Body body, String type, Map<String, String> parameters, double start, double stop)
    throws IOException, InterruptedException {
        var cache = new File(cacheDirectory_, "%s-%s-%.1f-%.1f-%s.txt".formatted(
            body.command().replace(";", ""), type, start, stop, body.stepDays()));
        if (!cache.exists()) {
            var query = new LinkedHashMap<String, String>();
            query.put("format", "text");
            query.put("COMMAND", "'" + body.command() + "'");
            query.put("OBJ_DATA", "NO");
            query.put("MAKE_EPHEM", "YES");
            query.put("EPHEM_TYPE", type);
            query.put("CENTER", "500@399");
            query.put("START_TIME", "'JD " + start + "'");
            query.put("STOP_TIME", "'JD " + stop + "'");
            query.put("STEP_SIZE", "'" + Math.round(body.stepDays() * 24 * 60) + " min'");
            query.put("TIME_TYPE", "UT");
            query.put("CSV_FORMAT", "YES");
            query.putAll(parameters);
            var uri = new StringJoiner("&", API + "?", "");
            query.forEach((k, v) -> uri.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));

            System.out.println("Fetching " + cache.getName());
            var response = client_.send(HttpRequest.newBuilder(URI.create(uri.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains("$$SOE")) {
                throw new IOException("Horizons request failed for " + cache.getName() + ":\n" + response.body());
            }
            cacheDirectory_.mkdirs();
            Files.writeString(cache.toPath(), response.body());
        }

        var content = Files.readString(cache.toPath());
        var data = content.substring(content.indexOf("$$SOE") + 5, content.indexOf("$$EOE")).trim();
        return Arrays.asList(data.split("\n"));
    }

    static double toJulianDay(LocalDateTime utc) {
        return utc.toEpochSecond(ZoneOffset.UTC) / 86400.0 + 2440587.5;
    }

    /**
     * The Chebyshev coefficients of each segment, which reproduce the samples.
     */
    static double[][] fit(Body body, NavigableMap<Double, Double> samples) {
        var times = samples.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        var values = unwrap(samples.values().stream().mapToDouble(Double::doubleValue).toArray());
        var segments = segmentCount(body);
        var coefficients = new double[segments][];
        var max_error = 0.0;
        var max_error_jd = 0.0;
        var index = 0;
        for (var s = 0; s < segments; ++s) {
            var a = JD_START + s * (double) body.segmentDays();
            var b = a + body.segmentDays();
            while (index < times.length && times[index] < a - 1e-6) {
                ++index;
            }
            var end = index;
            while (end < times.length && times[end] <= b + 1e-6) {
                ++end;
            }
            if (end - index < 2 * (body.degree() + 1)) {
                throw new IllegalStateException("Not enough samples for " + body.name() + " segment " + s);
            }
            var offset = 360.0 * Math.floor(values[index] / 360.0);
            var xs = new double[end - index];
            var ys = new double[end - index];
            for (var i = index; i < end; ++i) {
                xs[i - index] = 2.0 * (times[i] - a) / body.segmentDays() - 1.0;
                ys[i - index] = values[i] - offset;
            }
            var c = quantize(leastSquares(xs, ys, body.degree()));
            for (var i = 0; i < xs.length; ++i) {
                var error = Math.abs(evaluate(c, xs[i]) - ys[i]);
                if (error > max_error) {
                    max_error = error;
                    max_error_jd = times[index + i];
                }
            }
            coefficients[s] = c;
        }
        System.out.printf("%-10s %6d segments, max fit error %.4f\" at JD %.2f%n", body.name(), segments, max_error * 3600.0, max_error_jd);
        return coefficients;
    }

    static double[] unwrap(double[] values) {
        var result = values.clone();
        var shift = 0.0;
        for (var i = 1; i < values.length; ++i) {
            var delta = values[i] - values[i - 1];
            if (delta < -180.0) {
                shift += 360.0;
            } else if (delta > 180.0) {
                shift -= 360.0;
            }
            result[i] = values[i] + shift;
        }
        return result;
    }

    // the runtime stores all but the constant term as floats, fitting must see the same values
    private static double[] quantize(double[] coefficients) {
        var result = coefficients.clone();
        for (var i = 1; i < result.length; ++i) {
            result[i] = (float) result[i];
        }
        return result;
    }

    static double[] leastSquares(double[] xs, double[] ys, int degree) {
        var n = degree + 1;
        var normal = new double[n][n + 1];
        var basis = new double[n];
        for (var i = 0; i < xs.length; ++i) {
            chebyshev(xs[i], basis);
            for (var r = 0; r < n; ++r) {
                for (var c = 0; c < n; ++c) {
                    normal[r][c] += basis[r] * basis[c];
                }
                normal[r][n] += basis[r] * ys[i];
            }
        }
        for (var p = 0; p < n; ++p) {
            var pivot = p;
            for (var r = p + 1; r < n; ++r) {
                if (Math.abs(normal[r][p]) > Math.abs(normal[pivot][p])) {
                    pivot = r;
                }
            }
            var tmp = normal[p];
            normal[p] = normal[pivot];
            normal[pivot] = tmp;
            for (var r = p + 1; r < n; ++r) {
                var factor = normal[r][p] / normal[p][p];
                for (var c = p; c <= n; ++c) {
                    normal[r][c] -= factor * normal[p][c];
                }
            }
        }
        var result = new double[n];
        for (var r = n - 1; r >= 0; --r) {
            var sum = normal[r][n];
            for (var c = r + 1; c < n; ++c) {
                sum -= normal[r][c] * result[c];
            }
            result[r] = sum / normal[r][r];
        }
        return result;
    }

    private static void chebyshev(double x, double[] basis) {
        basis[0] = 1.0;
        if (basis.length > 1) {
            basis[1] = x;
        }
        for (var k = 2; k < basis.length; ++k) {
            basis[k] = 2.0 * x * basis[k - 1] - basis[k - 2];
        }
    }

    static double evaluate(double[] c, double x) {
        var b1 = 0.0;
        var b2 = 0.0;
        for (var k = c.length - 1; k >= 1; --k) {
            var b0 = 2.0 * x * b1 - b2 + c[k];
            b2 = b1;
            b1 = b0;
        }
        return x * b1 - b2 + c[0];
    }

    private void write(Body body, double[][] coefficients)
    throws IOException {
        var file = new File(outputDirectory_, body.name() + ".bin");
        try (var out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeDouble(JD_START);
            out.writeInt(body.segmentDays());
            out.writeInt(body.degree());
            out.writeInt(coefficients.length);
            for (var c : coefficients) {
                out.writeDouble(c[0]);
                for (var k = 1; k < c.length; ++k) {
                    out.writeFloat((float) c[k]);
                }
            }
        }
    }

    private static double arcseconds(double value) {
        return Math.toRadians(value / 3600.0);
    }

    private static double normalize(double degrees) {
        var result = degrees % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }
}

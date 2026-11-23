package gatesofhumanity;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipInputStream;

/**
 * Writes the cities that the location search uses from the GeoNames export, which is licensed
 * under CC BY 4.0.
 */
public class CitiesGenerator {
    private static final String DUMP = "https://download.geonames.org/export/dump/";
    private static final String CITIES = "cities5000";
    // smaller places rarely have another name that someone would type
    private static final int ALTERNATE_NAMES_POPULATION = 15000;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern LATIN = Pattern.compile("[a-z0-9 .'-]{3,}");

    private final File cacheDirectory_;
    private final File output_;
    private final HttpClient client_ = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public CitiesGenerator(File cacheDirectory, File output) {
        cacheDirectory_ = cacheDirectory;
        output_ = output;
    }

    public void generate()
    throws IOException, InterruptedException {
        var countries = new HashMap<String, String>();
        for (var line : Files.readAllLines(download("countryInfo.txt").toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            var columns = line.split("\t", -1);
            countries.put(columns[0], columns[4]);
        }

        var admins = new HashMap<String, String>();
        for (var line : Files.readAllLines(download("admin1CodesASCII.txt").toPath(), StandardCharsets.UTF_8)) {
            var columns = line.split("\t", -1);
            admins.put(columns[0], columns[1]);
        }

        var rows = new ArrayList<String>();
        try (var zip = new ZipInputStream(new FileInputStream(download(CITIES + ".zip")))) {
            var entry = zip.getNextEntry();
            while (entry != null && !entry.getName().equals(CITIES + ".txt")) {
                entry = zip.getNextEntry();
            }
            if (entry == null) {
                throw new IOException(CITIES + ".txt is missing from " + CITIES + ".zip");
            }
            var reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                var c = line.split("\t", -1);
                var country = countries.get(c[8]);
                if (country == null) {
                    throw new IOException("Unknown country code " + c[8] + " for " + c[1]);
                }
                var admin = admins.getOrDefault(c[8] + "." + c[10], "");
                rows.add(String.join("\t", c[0], c[1], c[2], admin, country, c[4], c[5], c[17], c[14], alternateNames(c)));
            }
        }

        output_.getParentFile().mkdirs();
        try (var out = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(output_)), StandardCharsets.UTF_8)) {
            out.write("# GeoNames " + CITIES + ", https://www.geonames.org, CC BY 4.0\n");
            out.write("# id\tname\tascii_name\tadmin1\tcountry\tlatitude\tlongitude\ttimezone\tpopulation\talternate_names\n");
            for (var row : rows) {
                out.write(row);
                out.write('\n');
            }
        }
        System.out.println("Wrote " + rows.size() + " cities to " + output_);
    }

    // the other names of a city in Latin script, such as Cologne and Keulen for Köln, in lower case
    // and without accents, the way the search compares them
    private static String alternateNames(String[] city) {
        if (Long.parseLong(city[14]) < ALTERNATE_NAMES_POPULATION) {
            return "";
        }
        var names = new LinkedHashSet<String>();
        for (var name : city[3].split(",")) {
            var normalized = normalize(name.strip());
            if (LATIN.matcher(normalized).matches()) {
                names.add(normalized);
            }
        }
        names.remove(normalize(city[1]));
        names.remove(normalize(city[2]));
        return String.join(",", names);
    }

    private static String normalize(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private File download(String name)
    throws IOException, InterruptedException {
        var file = new File(cacheDirectory_, name);
        if (!file.exists()) {
            cacheDirectory_.mkdirs();
            System.out.println("Downloading " + name);
            var response = client_.send(HttpRequest.newBuilder(URI.create(DUMP + name)).build(),
                HttpResponse.BodyHandlers.ofFile(file.toPath()));
            if (response.statusCode() != 200) {
                file.delete();
                throw new IOException("Downloading " + name + " failed with status " + response.statusCode());
            }
        }
        return file;
    }
}

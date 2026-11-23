package gatesofhumanity.models;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The cities from GeoNames that the location search offers, see {@code gatesofhumanity.CitiesGenerator}.
 */
public final class CityDirectory {
    // the alternate names each start and end with a comma, so that a whole name and the start of one can be looked for
    private record City(Location location, String searchName, String searchAsciiName, String alternateNames, long population) {
        boolean isCalled(String needle) {
            return searchName.equals(needle) || searchAsciiName.equals(needle) || alternateNames.contains("," + needle + ",");
        }

        boolean matches(String needle) {
            return searchName.contains(needle) || searchAsciiName.contains(needle) || alternateNames.contains("," + needle);
        }
    }

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private static final class Cities {
        static final List<City> ALL = new ArrayList<>();
        static final Map<Integer, Location> BY_ID = new HashMap<>();

        static {
            for (var c : TsvResource.rows("cities.tsv.gz")) {
                var label = c[3].isEmpty() || c[3].equals(c[1]) ? c[1] + ", " + c[4] : c[1] + ", " + c[3] + ", " + c[4];
                var location = new Location(Integer.parseInt(c[0]), label, Double.parseDouble(c[5]), Double.parseDouble(c[6]), c[7]);
                ALL.add(new City(location, normalize(c[1]), normalize(c[2]), c[9].isEmpty() ? "" : "," + c[9] + ",", Long.parseLong(c[8])));
                BY_ID.put(location.id(), location);
            }
            ALL.sort(Comparator.comparingLong(City::population).reversed());
        }
    }

    private CityDirectory() {
    }

    static String normalize(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * The cities that are called what the query says, by their own name or by another one such as
     * Cologne for Köln, and then the ones whose name contains the query or that have another name
     * starting with it. Case and accents are ignored, and the most populated come first.
     */
    public static List<Location> search(String query, int limit) {
        var needle = normalize(query.trim());
        var called = new ArrayList<Location>();
        var matching = new ArrayList<Location>();
        // a comma separates the alternate names
        if (needle.isEmpty() || needle.contains(",")) {
            return called;
        }
        for (var city : Cities.ALL) {
            if (city.isCalled(needle)) {
                called.add(city.location());
                if (called.size() == limit) {
                    break;
                }
            } else if (matching.size() < limit && city.matches(needle)) {
                matching.add(city.location());
            }
        }
        called.addAll(matching.subList(0, Math.min(matching.size(), limit - called.size())));
        return called;
    }

    static Collection<Location> all() {
        return Collections.unmodifiableCollection(Cities.BY_ID.values());
    }

    public static Location find(int id) {
        return Cities.BY_ID.get(id);
    }

    /**
     * The most populated city of a time zone, or null when no city is in it. A zone can be asked
     * for by an older name than the cities have, Asia/Calcutta for Asia/Kolkata, which is then
     * found by its rules, since both names lead to the same ones. The zone's own cities come
     * first, since zones of different countries can have the same rules too, as Asia/Muscat has
     * those of Asia/Dubai.
     */
    public static Location largestIn(String zone) {
        for (var city : Cities.ALL) {
            if (city.location().timezone().equals(zone)) {
                return city.location();
            }
        }
        try {
            var rules = ZoneId.of(zone).getRules();
            var same = new HashMap<String, Boolean>();
            for (var city : Cities.ALL) {
                if (same.computeIfAbsent(city.location().timezone(), name -> ZoneId.of(name).getRules().equals(rules))) {
                    return city.location();
                }
            }
        } catch (DateTimeException _) {
            // not the name of a zone
        }
        return null;
    }

    /**
     * The city with this id, or London when nothing was chosen yet or the city is no longer known.
     */
    public static Location findOrLondon(Integer id) {
        var location = id == null ? null : find(id);
        return location == null ? Location.LONDON : location;
    }
}

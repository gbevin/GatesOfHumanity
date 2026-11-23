package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class CityDirectoryTest {
    @Test
    void londonIsTheDefaultLocation() {
        assertEquals(Location.LONDON, CityDirectory.find(Location.LONDON.id()));
    }

    @Test
    void unknownIdsAreNotFound() {
        assertNull(CityDirectory.find(1826645935));
    }

    @Test
    void searchPutsTheMostPopulatedFirst() {
        var result = CityDirectory.search("london", 10);
        assertEquals(Location.LONDON, result.get(0));
        assertTrue(result.stream().anyMatch(l -> l.city().equals("London, Ontario, Canada")));
    }

    @Test
    void searchIgnoresCaseAndAccents() {
        assertEquals("Zürich, Zurich, Switzerland", CityDirectory.search("ZURICH", 1).get(0).city());
        assertEquals("São Paulo, Brazil", CityDirectory.search("sao paulo", 1).get(0).city());
        assertEquals("São Paulo, Brazil", CityDirectory.search("São Paulo", 1).get(0).city());
    }

    @Test
    void searchKnowsTheOtherNamesOfACity() {
        assertEquals("Munich, Bavaria, Germany", CityDirectory.search("München", 1).get(0).city());
        assertEquals("Munich, Bavaria, Germany", CityDirectory.search("munch", 1).get(0).city());
        assertEquals("Köln, North Rhine-Westphalia, Germany", CityDirectory.search("cologne", 1).get(0).city());
        assertEquals("Mumbai, Maharashtra, India", CityDirectory.search("Bombay", 1).get(0).city());
        assertEquals("Gent, Flanders, Belgium", CityDirectory.search("ghent", 1).get(0).city());
        assertEquals("Washington, District of Columbia, United States", CityDirectory.search("washington dc", 1).get(0).city());
        assertEquals("St. Louis, Missouri, United States", CityDirectory.search("saint louis", 1).get(0).city());
    }

    @Test
    void aCityCalledWhatWasTypedComesBeforeBiggerOnesThatOnlyMatch() {
        // Munich is Monaco di Baviera in Italian
        var monaco = CityDirectory.search("monaco", 5).stream().map(Location::city).toList();
        assertEquals("Monaco, Municipality of Monaco, Monaco", monaco.get(0));
        assertTrue(monaco.contains("Munich, Bavaria, Germany"), monaco.toString());
        assertEquals(Location.LONDON, CityDirectory.search("london", 1).get(0));
        // two of its other names, as the data holds them
        assertTrue(CityDirectory.search("muc,minca", 5).isEmpty());
    }

    @Test
    void searchIsLimited() {
        assertEquals(5, CityDirectory.search("a", 5).size());
        assertTrue(CityDirectory.search("   ", 5).isEmpty());
        assertTrue(CityDirectory.search("qqqqqqqq", 5).isEmpty());
    }

    @Test
    void everyCityHasAValidTimezoneAndCoordinates() {
        assertTrue(CityDirectory.all().size() > 60000);
        for (var location : CityDirectory.all()) {
            assertDoesNotThrow(() -> ZoneId.of(location.timezone()), location.city());
            assertTrue(Math.abs(location.latitude()) <= 90.0 && Math.abs(location.longitude()) <= 180.0, location.city());
        }
    }
}

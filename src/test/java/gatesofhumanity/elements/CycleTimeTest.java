package gatesofhumanity.elements;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CycleTimeTest {
    @Test
    void aCycleStartsAtTheFirstMinuteThePlanetIsThere() {
        assertEquals(Instant.parse("1984-11-28T21:34:00Z"), CycleTime.firstMinute(Instant.parse("1984-11-28T21:33:00.000000001Z"), ZoneOffset.UTC).toInstant());
        assertEquals(Instant.parse("1984-11-28T21:33:00Z"), CycleTime.firstMinute(Instant.parse("1984-11-28T21:33:00Z"), ZoneOffset.UTC).toInstant());
    }
}

package gatesofhumanity.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ActivatedLineTest {
    @Test
    void aLineItsOwnChartFixesStaysItsOwnWhenAnotherChartFixesItToo() {
        var line = new ActivatedLine();
        line.exalt(true);
        line.putInDetriment(true);
        assertTrue(line.isExaltedOnlyByOther());
        assertTrue(line.isInDetrimentOnlyByOther());

        line.exalt(false);
        line.putInDetriment(false);
        assertTrue(line.isExalted());
        assertTrue(line.isInDetriment());
        assertFalse(line.isExaltedOnlyByOther());
        assertFalse(line.isInDetrimentOnlyByOther());
    }
}

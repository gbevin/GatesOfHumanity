package gatesofhumanity.models;

import java.time.ZonedDateTime;
import java.util.List;

public record PositionData(ZonedDateTime time, List<Position> positions) {
    /**
     * What transits have in place of a design.
     */
    public static final PositionData NONE = new PositionData(null, List.of());
}

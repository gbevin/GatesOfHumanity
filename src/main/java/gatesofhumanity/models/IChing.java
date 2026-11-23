package gatesofhumanity.models;

public record IChing(int one, int two, int three, int four, int five, int six) {
    /**
     * Whether a line, counted from 1 at the bottom, is solid.
     */
    public boolean yang(int line) {
        return switch (line) {
            case 1 -> one == 1;
            case 2 -> two == 1;
            case 3 -> three == 1;
            case 4 -> four == 1;
            case 5 -> five == 1;
            case 6 -> six == 1;
            default -> throw new IllegalArgumentException("A hexagram has no line " + line);
        };
    }
}

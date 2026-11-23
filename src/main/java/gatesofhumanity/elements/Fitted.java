package gatesofhumanity.elements;

import java.util.ArrayList;
import java.util.List;

/**
 * A text in the room that a drawing has for it, which a drawing can't work out by itself: the
 * lines it's written on and the size of its letters. The room is counted in letters, how many
 * fit a line at the size that a short text is written in, since nothing here knows a font.
 */
public record Fitted(List<String> lines, double size) {
    private static final double STEP = 0.5;

    /**
     * The size as a drawing writes it, without a fraction that it doesn't have.
     */
    public String written() {
        return size == Math.rint(size) ? String.valueOf((int) size) : String.valueOf(size);
    }

    /**
     * The text at the largest size down to the smallest at which it fits the lines, broken
     * between words. A text that doesn't fit at the smallest size is cut short there.
     */
    public static Fitted of(String text, int letters, double size, double smallest, int lines) {
        for (var smaller = size; smaller >= smallest; smaller -= STEP) {
            var wrapped = wrapped(text, room(letters, size, smaller));
            if (wrapped != null && wrapped.size() <= lines) {
                return new Fitted(wrapped, smaller);
            }
        }
        var room = room(letters, size, smallest);
        var cut = new ArrayList<String>();
        var rest = text;
        while (cut.size() < lines - 1 && rest.length() > room) {
            var space = rest.lastIndexOf(' ', room);
            var end = space > 0 ? space : room;
            cut.add(rest.substring(0, end));
            rest = rest.substring(end).stripLeading();
        }
        cut.add(rest.length() > room ? rest.substring(0, room - 1).stripTrailing() + "…" : rest);
        return new Fitted(List.copyOf(cut), smallest);
    }

    private static int room(int letters, double size, double smaller) {
        return (int) Math.floor(letters * size / smaller);
    }

    // nothing when a word is longer than a line
    private static List<String> wrapped(String text, int room) {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.split(" ")) {
            if (word.length() > room) {
                return null;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > room) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return List.copyOf(lines);
    }
}

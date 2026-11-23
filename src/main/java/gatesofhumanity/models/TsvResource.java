package gatesofhumanity.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

final class TsvResource {
    private TsvResource() {
    }

    /**
     * The columns of every row of a tab separated resource, gzipped when its name ends with .gz,
     * without the comment rows that start with #.
     */
    static List<String[]> rows(String name) {
        try (var stream = Objects.requireNonNull(TsvResource.class.getClassLoader().getResourceAsStream(name), "Missing " + name);
             var reader = new BufferedReader(new InputStreamReader(name.endsWith(".gz") ? new GZIPInputStream(stream) : stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(row -> !row.startsWith("#")).map(row -> row.split("\t", -1)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

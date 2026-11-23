package gatesofhumanity.elements;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateFormatsTest {
    // a formatter takes the default locale when it's created, so a test that changes the default
    // can't reach the formatters that classes create when they're loaded
    @Test
    void everyFormatIsInEnglish()
    throws ReflectiveOperationException, IOException {
        var formats = 0;
        // every class of the package, by its source file, so that a new one can't be forgotten
        try (var sources = Files.list(Path.of("src/main/java/gatesofhumanity/elements"))) {
            for (var source : sources.toList()) {
                var type = Class.forName("gatesofhumanity.elements." + source.getFileName().toString().replace(".java", ""));
                for (var field : type.getDeclaredFields()) {
                    if (field.getType() == DateTimeFormatter.class && Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        assertEquals(Locale.ENGLISH, ((DateTimeFormatter) field.get(null)).getLocale(), field.toString());
                        ++formats;
                    }
                }
            }
        }
        assertEquals(13, formats);
    }
}

package gatesofhumanity.elements;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PageDescriptionTest {
    // in a class loader of its own, since the classes of this one are already initialized
    @Test
    void theLearnTopicsCanBeUsedFirst()
    throws Exception {
        var urls = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).map(path -> {
            try {
                return new File(path).toURI().toURL();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toArray(URL[]::new);
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var topics = (Object[]) loader.loadClass("gatesofhumanity.elements.LearnTopic").getMethod("values").invoke(null);
            assertEquals(LearnTopic.values().length, topics.length);
        }
    }
}

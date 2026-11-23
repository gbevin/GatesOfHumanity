package gatesofhumanity;

import org.junit.jupiter.api.Test;
import rife.engine.Element;
import rife.engine.Route;
import rife.engine.annotations.ActiveSite;
import rife.engine.annotations.Parameter;
import rife.json.Json;
import rife.json.JsonArray;
import rife.json.JsonObject;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The site as a native library only has what its metadata names of what RIFE2 looks up by
 * name: an element that a route makes, a route that a template names and a template that's
 * asked for by name. One that's missing there answers nothing in an app, while every test on
 * the JVM passes.
 */
public class NativeMetadataTest {
    private static final List<String> FILES = List.of("gatesofhumanity", "gatesofhumanity-webapp", "gatesofhumanity-embedded");

    // what the metadata names of each type: its fields, and "<init>" when it can be made
    private static Map<String, Set<String>> named() throws IOException {
        var named = new HashMap<String, Set<String>>();
        for (var file : FILES) {
            var metadata = Json.parseObject(Files.readString(Path.of("src/main/resources/META-INF/native-image", file, "reachability-metadata.json"), StandardCharsets.UTF_8));
            // one of the files only names resources
            for (var each : metadata.get("reflection") instanceof JsonArray reflection ? reflection : new JsonArray()) {
                var entry = (JsonObject) each;
                if (!(entry.get("type") instanceof String type)) {
                    continue;
                }
                var names = named.computeIfAbsent(type, _ -> new HashSet<>());
                for (var kind : List.of("fields", "methods")) {
                    if (entry.get(kind) instanceof JsonArray members) {
                        members.forEach(member -> names.add(((JsonObject) member).getString("name")));
                    }
                }
            }
        }
        return named;
    }

    private static List<Class<?>> elements() throws Exception {
        var classes = Path.of(AppSite.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var elements = new ArrayList<Class<?>>();
        try (var files = Files.list(classes.resolve("gatesofhumanity/elements"))) {
            for (var file : files.toList()) {
                var name = file.getFileName().toString();
                if (name.endsWith(".class") && !name.contains("$")) {
                    var type = Class.forName("gatesofhumanity.elements." + name.substring(0, name.length() - ".class".length()));
                    if (Element.class.isAssignableFrom(type)) {
                        elements.add(type);
                    }
                }
            }
        }
        return elements;
    }

    @Test
    void everyElementThatARouteMakesCanBeMadeAndGivenItsParameters() throws Exception {
        var named = named();
        var elements = elements();
        assertTrue(elements.size() > 20, elements.toString());
        for (var element : elements) {
            var names = named.getOrDefault(element.getName(), Set.of());
            // a route makes its element without anything to give it
            var made = !Modifier.isAbstract(element.getModifiers()) && Arrays.stream(element.getDeclaredConstructors()).anyMatch(maker -> maker.getParameterCount() == 0);
            if (made) {
                assertTrue(names.contains("<init>"), element.getName() + " can't be made in the native library");
            }
            for (var field : element.getDeclaredFields()) {
                if (field.isAnnotationPresent(Parameter.class) || field.isAnnotationPresent(ActiveSite.class)) {
                    assertTrue(names.contains(field.getName()), element.getName() + " isn't given " + field.getName() + " in the native library");
                }
            }
        }
    }

    private static final Pattern ROUTE = Pattern.compile("\\bv route:(\\w+)");
    private static final Pattern TEMPLATE = Pattern.compile("\\b(?:template(Svg|Txt|Xml|Json)?|TemplateFactory\\.(\\w+)\\.get)\\(\"(\\w+)\"\\)");

    private static List<Path> files(String folder) throws IOException {
        try (var files = Files.walk(Path.of(folder))) {
            return files.filter(file -> Files.isRegularFile(file) && !file.getFileName().toString().startsWith(".")).toList();
        }
    }

    @Test
    void everyRouteThatATemplateNamesIsFound() throws Exception {
        var named = named();
        var routes = new HashSet<String>();
        for (var template : files("src/main/resources/templates")) {
            var written = ROUTE.matcher(Files.readString(template, StandardCharsets.UTF_8));
            while (written.find()) {
                routes.add(written.group(1));
            }
        }
        assertTrue(routes.size() > 5, routes.toString());
        for (var route : routes) {
            var site = AppSite.class.getField(route).getDeclaringClass();
            assertTrue(Route.class.isAssignableFrom(AppSite.class.getField(route).getType()), route);
            assertTrue(named.getOrDefault(site.getName(), Set.of()).contains(route), "the route " + route + " isn't found in the native library");
        }
    }

    @Test
    void everyTemplateThatsAskedForByNameIsThere() throws Exception {
        var named = named();
        var templates = new HashSet<String>();
        for (var source : files("src/main/java")) {
            var asked = TEMPLATE.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (asked.find()) {
                var kind = asked.group(1) != null ? asked.group(1) : asked.group(2) != null ? asked.group(2) : "html";
                templates.add("rife.template." + kind.toLowerCase(Locale.ROOT) + "." + asked.group(3));
            }
        }
        assertTrue(templates.size() > 8 && templates.contains("rife.template.svg.bodygraph"), templates.toString());
        var files = new HashSet<String>();
        for (var file : FILES) {
            var metadata = Json.parseObject(Files.readString(Path.of("src/main/resources/META-INF/native-image", file, "reachability-metadata.json"), StandardCharsets.UTF_8));
            for (var resource : metadata.get("resources") instanceof JsonArray resources ? resources : new JsonArray()) {
                files.add(String.valueOf(((JsonObject) resource).get("glob")));
            }
        }
        for (var template : templates) {
            assertTrue(named.getOrDefault(template, Set.of()).contains("<init>"), "the template " + template + " isn't in the native library");
            // the class that RIFE2 made of it is looked up as a file too
            assertTrue(files.contains(template.replace('.', '/') + ".class"), "the class of the template " + template + " isn't in the native library");
        }
    }
}

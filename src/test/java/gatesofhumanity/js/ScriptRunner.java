package gatesofhumanity.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Evaluates the browser scripts of the web application in GraalJS, without a browser.
 */
public final class ScriptRunner implements AutoCloseable {
    private static final Engine ENGINE = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();

    // a localStorage with the same API as the browser's, that can be made to refuse writes
    private static final String STORAGE = """
        class MemoryStorage {
            constructor() { this.items = new Map(); this.refuseWrites = false; }
            getItem(key) { return this.items.has(key) ? this.items.get(key) : null; }
            setItem(key, value) {
                if (this.refuseWrites) throw new Error('QuotaExceededError');
                this.items.set(key, String(value));
            }
            removeItem(key) { this.items.delete(key); }
            keys() { return [...this.items.keys()]; }
        }
        """;

    private final Context context_;

    /**
     * @param setup code that runs before the scripts, to prepare the browser they expect
     */
    public ScriptRunner(String setup, String... scripts) {
        // a time zone with daylight saving time, where the browser's clock can shift wall clock times
        context_ = Context.newBuilder("js").engine(ENGINE).allowExperimentalOptions(true).option("js.timezone", "Europe/Brussels").build();
        context_.eval("js", STORAGE);
        context_.eval("js", setup);
        for (var script : scripts) {
            try {
                context_.eval(Source.newBuilder("js", new File("src/main/webapp/js", script)).build());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public Value eval(String code) {
        return context_.eval("js", code);
    }

    @Override
    public void close() {
        context_.close();
    }
}

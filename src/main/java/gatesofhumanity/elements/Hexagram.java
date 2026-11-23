package gatesofhumanity.elements;

import gatesofhumanity.models.Entities;
import rife.template.Template;

/**
 * The hexagram of a gate as six lines, whole or broken, from the blocks that a template has
 * for the two.
 */
final class Hexagram {
    private Hexagram() {
    }

    /**
     * Draws the lines into a value of the template, from the top, since a hexagram has its
     * first line at the bottom. Where the template gives its lines a class, one line is lit,
     * none when it's 0.
     */
    static void print(Template t, String id, int gate, int lit) {
        var iching = Entities.GATES.get(gate).iching();
        t.setValue(id, "");
        for (var line = 6; line >= 1; --line) {
            t.setValue("line-y", (6 - line) * 5);
            if (t.hasValueId("line-class")) {
                t.setValue("line-class", line == lit ? "lit" : "unlit");
            }
            t.appendBlock(id, iching.yang(line) ? "hexagram-yang" : "hexagram-yin");
        }
    }
}

package gatesofhumanity.elements;

import gatesofhumanity.models.CityDirectory;
import rife.engine.Context;
import rife.engine.Element;
import rife.engine.annotations.Parameter;
import rife.json.Json;

import java.util.ArrayList;
import java.util.List;

public class Cities implements Element {
    static final int MAX_RESULTS = 50;

    @Parameter String query;

    @Override
    public void process(Context c) {
        var text = query == null ? "" : query;
        // the query comes first so that the browser can ignore answers to a query that it has moved on from
        var result = new ArrayList<List<Object>>();
        result.add(List.of(text, 0));
        for (var location : CityDirectory.search(text, MAX_RESULTS)) {
            result.add(List.of(location.city(), location.id()));
        }
        c.setContentType("application/json");
        c.print(Json.toString(result));
    }
}

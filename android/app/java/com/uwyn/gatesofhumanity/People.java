package com.uwyn.gatesofhumanity;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * Who the pages saved, for the widgets. A widget can't read what the web view keeps, so the
 * pages tell of it when a page loads and each time it changes, and the app keeps the names and
 * the births where its widgets find them.
 */
final class People {
    /**
     * A person as a widget is asked for them: under their name, with the moment of their birth
     * in UTC as the site writes a moment in an address.
     */
    static final class Saved {
        final String name;
        final String born;

        Saved(String name, String born) {
            this.name = name;
            this.born = born;
        }

        /** The address of this person's widget at the site, in a size. */
        String widget(String size) {
            return "/widget/person/" + born + "/" + size + "?name=" + Uri.encode(name);
        }
    }

    private People() {
    }

    /** By name, as they're offered for a widget. */
    static List<Saved> byName(Context context) {
        var people = all(context);
        var collator = Collator.getInstance();
        people.sort((one, other) -> collator.compare(one.name, other.name));
        return people;
    }

    /** With the one whose chart the pages showed last first: who a widget shows when nobody was chosen for it. */
    static List<Saved> all(Context context) {
        var people = new ArrayList<Saved>();
        try {
            var kept = new JSONArray(context.getSharedPreferences("people", Context.MODE_PRIVATE).getString("saved", "[]"));
            for (var i = 0; i < kept.length(); ++i) {
                people.add(new Saved(kept.getJSONObject(i).getString("name"), kept.getJSONObject(i).getString("born")));
            }
        } catch (JSONException e) {
            // nobody
        }
        return people;
    }

    /**
     * Takes what a page tells of who it saved, see app.js of the site, and tells the widgets when
     * it's other people than before.
     */
    static synchronized void told(Context context, Object told) {
        var people = told instanceof JSONObject said ? said.optJSONArray("saved") : null;
        if (people == null) {
            return;
        }
        var preferences = context.getSharedPreferences("people", Context.MODE_PRIVATE);
        var saved = people.toString();
        if (!saved.equals(preferences.getString("saved", "[]"))) {
            preferences.edit().putString("saved", saved).apply();
            Widgets.changed(context, Widgets.Person.class);
        }
    }
}

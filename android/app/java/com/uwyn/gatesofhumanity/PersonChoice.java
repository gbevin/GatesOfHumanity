package com.uwyn.gatesofhumanity;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * Whose chart a widget shows with the transits: what Android shows to set a widget of a person
 * up, with the people that the pages saved, by name.
 */
public class PersonChoice extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        var widget = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        var placed = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget);
        var people = People.byName(this);
        // with nobody to choose, the widget says what to do
        if (people.isEmpty()) {
            setResult(RESULT_OK, placed);
            finish();
            return;
        }
        // leaving without a choice leaves the widget as it was
        setResult(RESULT_CANCELED, placed);
        setTitle("Person");
        var list = new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, people.stream().map(person -> person.name).toArray(String[]::new)));
        list.setOnItemClickListener((parent, view, position, id) -> {
            Widgets.Person.choose(this, widget, people.get(position).name);
            Widgets.changed(this, Widgets.Person.class);
            setResult(RESULT_OK, placed);
            finish();
        });
        setContentView(list);
    }
}

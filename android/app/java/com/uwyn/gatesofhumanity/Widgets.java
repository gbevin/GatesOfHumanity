package com.uwyn.gatesofhumanity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.icu.util.TimeZone;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import rife.nativeapp.android.NativeSite;

/**
 * The widgets, which show what the site inside the app has them show: the site says at which
 * moments a widget changes and draws it for each. Android shows a picture of the drawing of
 * this moment, and the widget is drawn again at the moment that it changes.
 */
public abstract class Widgets extends AppWidgetProvider {
    /** The gates and lines that the Sun, the Earth and the Moon are in. */
    public static final class Transits extends Widgets {
        @Override
        String address(Context context, int widget, String size) {
            return "/widget/transits/" + size;
        }

        @Override
        boolean large() {
            return false;
        }
    }

    /** The centers and the channels that the planets define at this moment. */
    public static final class Bodygraph extends Widgets {
        @Override
        String address(Context context, int widget, String size) {
            return "/widget/bodygraph/" + size;
        }
    }

    /**
     * The body graph of someone that the pages saved with the transits of this moment: the one
     * that was chosen for the widget, as they're saved now, or else the first of those that the
     * app kept, who is the one whose chart it showed last.
     */
    public static final class Person extends Widgets {
        static void choose(Context context, int widget, String name) {
            context.getSharedPreferences(KEPT, Context.MODE_PRIVATE).edit().putString("person-" + widget, name).apply();
        }

        @Override
        String address(Context context, int widget, String size) {
            var people = People.all(context);
            var chosen = context.getSharedPreferences(KEPT, Context.MODE_PRIVATE).getString("person-" + widget, null);
            return people.stream().filter(person -> person.name.equals(chosen)).findFirst()
                .or(() -> people.stream().findFirst()).map(person -> person.widget(size)).orElse(null);
        }

        @Override
        String nothing() {
            return "Save a person in Gates to see their transits here.";
        }

        @Override
        public void onDeleted(Context context, int[] widgets) {
            var kept = context.getSharedPreferences(KEPT, Context.MODE_PRIVATE).edit();
            for (var widget : widgets) {
                kept.remove("person-" + widget);
            }
            kept.apply();
        }
    }

    private static final String KEPT = "widgets";
    private static final String FAILED = "This can't be shown right now.";
    // one request at a time, away from the thread that Android tells a widget on
    private static final Executor ASKING = Executors.newSingleThreadExecutor();

    /** The address of a widget at the site in a size, or nothing when it has nothing to show yet. */
    abstract String address(Context context, int widget, String size);

    /** Whether the site draws the widget in its largest size too. */
    boolean large() {
        return true;
    }

    /** What a widget says when it has no address. */
    String nothing() {
        return FAILED;
    }

    /**
     * What a tap on a widget hands the app with the address to open, and nothing else has: the
     * app can be started by any other app, which could otherwise have it open an address too.
     */
    static synchronized String token(Context context) {
        var kept = context.getSharedPreferences(KEPT, Context.MODE_PRIVATE);
        var token = kept.getString(MainActivity.TOKEN, null);
        if (token == null) {
            token = UUID.randomUUID().toString();
            kept.edit().putString(MainActivity.TOKEN, token).apply();
        }
        return token;
    }

    /** Has the widgets of a kind drawn again, when what they show changed. */
    static void changed(Context context, Class<? extends Widgets> kind) {
        var widgets = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, kind));
        if (widgets.length > 0) {
            context.sendBroadcast(again(context, kind, widgets));
        }
    }

    private static Intent again(Context context, Class<?> kind, int[] widgets) {
        return new Intent(context, kind).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgets);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // a widget writes a moment in the language, the time zone and the hours of the device
        var action = String.valueOf(intent.getAction());
        if (action.equals(Intent.ACTION_TIMEZONE_CHANGED) || action.equals(Intent.ACTION_LOCALE_CHANGED) || action.equals(Intent.ACTION_TIME_CHANGED)) {
            var manager = AppWidgetManager.getInstance(context);
            var widgets = manager.getAppWidgetIds(new ComponentName(context, getClass()));
            if (widgets.length > 0) {
                onUpdate(context, manager, widgets);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgets) {
        // the site takes longer than the thread that tells this may be kept
        var told = goAsync();
        var began = System.currentTimeMillis();
        ASKING.execute(() -> {
            try {
                // all of this kind, so that the moment to draw them again at is the first of theirs
                var all = manager.getAppWidgetIds(new ComponentName(context, getClass()));
                var next = Long.MAX_VALUE;
                for (var widget : all) {
                    next = Math.min(next, draw(context, manager, widget));
                }
                // Android gives this some ten seconds, which also have to do when the site still has to start
                Log.d("gatesofhumanity", all.length + " of " + getClass().getSimpleName() + " drawn after " + (System.currentTimeMillis() - began) + " ms");
                // Android wakes a widget every half hour at most, and a widget changes at a moment.
                // It's asked to wake it within ten minutes of that moment, the least that it
                // takes without a permission that's for alarm clocks: left to itself, it takes an hour.
                if (next < Long.MAX_VALUE) {
                    var again = PendingIntent.getBroadcast(context, 0, again(context, getClass(), all), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    context.getSystemService(AlarmManager.class).setWindow(AlarmManager.RTC, Math.max(next, System.currentTimeMillis() + 15_000), 600_000, again);
                }
            } finally {
                told.finish();
            }
        });
    }

    // its size changed, which leaves the moment that it changes at as it was
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int widget, Bundle options) {
        var told = goAsync();
        ASKING.execute(() -> {
            try {
                draw(context, manager, widget);
            } finally {
                told.finish();
            }
        });
    }

    @Override
    public void onDisabled(Context context) {
        var again = PendingIntent.getBroadcast(context, 0, again(context, getClass(), new int[0]), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(AlarmManager.class).cancel(again);
    }

    /** Draws a widget as it is now, and answers the moment at which it changes. */
    private long draw(Context context, AppWidgetManager manager, int widget) {
        // when nothing says: in a quarter of an hour
        var next = new long[] {System.currentTimeMillis() + 900_000};
        // A launcher says how much room a widget has when the screen is upright and when it's on
        // its side, and shows the one or the other without asking again.
        var room = manager.getAppWidgetOptions(widget);
        var upright = new int[] {room.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160), room.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)};
        var sideways = new int[] {room.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 160), room.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)};
        try {
            var shown = shown(context, widget, upright[0], upright[1], next);
            manager.updateAppWidget(widget, upright[0] == sideways[0] && upright[1] == sideways[1] ? shown
                : new RemoteViews(shown(context, widget, sideways[0], sideways[1], next), shown));
        } catch (RuntimeException e) {
            Log.e("gatesofhumanity", "A widget couldn't be shown", e);
        }
        return next[0];
    }

    /**
     * What a widget shows in a room of a width and a height: a picture of the drawing of this
     * moment, or a line that says why there's none. Notes the moment at which it changes.
     */
    private RemoteViews shown(Context context, int widget, int width, int height, long[] next) {
        // the sizes that the site draws a widget in: a square, twice as wide, and a large one
        var size = width > height * 1.4 ? "systemMedium" : large() && Math.min(width, height) >= 250 ? "systemLarge" : "systemSmall";
        var shown = new RemoteViews(context.getPackageName(), R.layout.widget);
        var tapped = new Intent(context, MainActivity.class);
        var said = nothing();
        try {
            var address = address(context, widget, size);
            if (address != null) {
                var timeline = new JSONObject(new String(NativeSite.get(context, address), StandardCharsets.UTF_8));
                var entries = timeline.getJSONArray("entries");
                var drawn = new Drawn(new String(NativeSite.get(context, entries.getJSONObject(0).getString("drawing")), StandardCharsets.UTF_8),
                    context.getResources().getConfiguration().getLocales().get(0), TimeZone.getDefault(), DateFormat.is24HourFormat(context));
                var density = context.getResources().getDisplayMetrics().density;
                shown.setImageViewBitmap(R.id.drawn, drawn.picture(Math.round(width * density), Math.round(height * density), context.getResources().getDimension(R.dimen.widget_radius)));
                shown.setContentDescription(R.id.widget, drawn.words);
                if (drawn.url != null) {
                    tapped.setAction(Intent.ACTION_VIEW).setData(drawn.url).putExtra(MainActivity.TOKEN, token(context));
                }
                next[0] = Instant.parse(entries.length() > 1 ? entries.getJSONObject(1).getString("date") : timeline.getString("reload")).toEpochMilli();
                said = null;
            }
        } catch (Exception e) {
            Log.e("gatesofhumanity", "A widget couldn't be drawn", e);
            said = FAILED;
        }
        shown.setViewVisibility(R.id.drawn, said == null ? View.VISIBLE : View.GONE);
        shown.setViewVisibility(R.id.said, said == null ? View.GONE : View.VISIBLE);
        shown.setTextViewText(R.id.said, said == null ? "" : said);
        shown.setOnClickPendingIntent(R.id.widget, PendingIntent.getActivity(context, widget, tapped, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return shown;
    }
}

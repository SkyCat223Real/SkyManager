package de.skycat223.screentime;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Homescreen-Widget "Mein Tag". */
public final class MyDayWidget extends AppWidgetProvider {

    static int res(Context c, String name, String type) { return c.getResources().getIdentifier(name, type, c.getPackageName()); }

    /** Die drei Zeilen: [Titel, Briefing, Stunde/Aufgaben]. */
    static String[] lines(Context c) {
        LocalDate d = LocalDate.now();
        String brief;
        try { brief = MyDay.briefingText(c, d); } catch (Throwable t) { brief = "Öffne SkyManager für deinen Tag"; }
        String extra = "";
        try {
            int wd = d.getDayOfWeek().getValue() - 1;
            LocalTime n = LocalTime.now();
            String l = WidgetMath.lessonLine(MyDayMath.lessons(Db.obj(c, "tt").optJSONObject("slots"), wd), n.getHour() * 60 + n.getMinute());
            String h = WidgetMath.homeworkLine(MyDayMath.homework(Db.list(c, "hw"), d.toEpochDay()));
            extra = (l == null ? "" : l) + (l != null && h != null ? "\n" : "") + (h == null ? "" : h);
        } catch (Throwable ignored) { }
        return new String[]{WidgetMath.title(d), brief, extra};
    }

    static RemoteViews views(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), res(c, "widget_myday", "layout"));
        String[] t = lines(c);
        v.setTextViewText(res(c, "w_title", "id"), t[0]);
        v.setTextViewText(res(c, "w_brief", "id"), t[1]);
        v.setTextViewText(res(c, "w_extra", "id"), t[2]);
        v.setViewVisibility(res(c, "w_extra", "id"), t[2].isEmpty() ? View.GONE : View.VISIBLE);
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open != null) v.setOnClickPendingIntent(res(c, "w_root", "id"), PendingIntent.getActivity(c, 8131, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        try { RemoteViews v = views(c); for (int id : ids) m.updateAppWidget(id, v); } catch (Throwable ignored) { }
    }

    /** Alle Widgets sofort aktualisieren. @return Anzahl. */
    static int refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, MyDayWidget.class));
            if (ids.length > 0) m.updateAppWidget(ids, views(c));
            return ids.length;
        } catch (Throwable t) { return 0; }
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Homescreen-Widget", "Mein Tag auf dem Startbildschirm");
        final int count = refresh(a);
        String[] t = lines(a);
        LinearLayout prev = SchoolUi.hero(a);
        prev.addView(NX.text(a, t[0], 15, C.TEXT, true));
        U.add(prev, NX.text(a, t[1], 13, C.TEXT, false), 4);
        if (!t[2].isEmpty()) U.add(prev, NX.text(a, t[2], 13, C.ACCENT, true), 4);
        U.add(p[1], NX.text(a, "VORSCHAU", 11, C.DIM, true), 2);
        U.add(p[1], prev, 12);
        LinearLayout c = U.card(a, count > 0 ? "✅ Widget ist auf deinem Startbildschirm" : "📱 Widget hinzufügen");
        U.add(c, NX.text(a, "Zeigt Stunden, fällige Aufgaben, den ersten Termin und Geburtstage. Aktualisiert sich etwa alle 30 Minuten, ein Tipp darauf öffnet SkyManager.", 12, C.DIM, false), 2);
        U.add(c, NX.button(a, "Zum Startbildschirm hinzufügen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    AppWidgetManager m = AppWidgetManager.getInstance(a);
                    if (m.isRequestPinAppWidgetSupported() && m.requestPinAppWidget(new ComponentName(a, MyDayWidget.class), null, null)) return;
                } catch (Throwable ignored) { }
                NX.toast(a, "Halte eine freie Stelle auf dem Startbildschirm gedrückt → Widgets → SkyManager");
            }
        }), 8);
        if (count > 0) U.add(c, NX.button(a, "Jetzt aktualisieren", new View.OnClickListener() { @Override public void onClick(View v) { NX.toast(a, refresh(a) > 0 ? "Widget aktualisiert ✓" : "Kein Widget gefunden"); } }), 6);
        U.add(c, NX.text(a, "Falls der Knopf nichts tut: Startbildschirm lange gedrückt halten → Widgets → SkyManager → „Mein Tag“ auf den Bildschirm ziehen.", 11, C.DIM, false), 8);
        U.add(p[1], c, 12);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

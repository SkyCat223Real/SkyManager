package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Event-Planer: Countdown, Teilnehmer, Discord-Ankuendigung und -Erinnerung. Reine Logik. */
final class EventMath {
    private EventMath() {}

    static final long REMIND_BEFORE = 60 * 60_000L, RUNNING = 3 * 3_600_000L;
    static final int MAX_PEOPLE = 200;

    static JSONObject event(String id, String title, long at, String place, String desc, int max) {
        String t = CashMath.clean(title == null ? "" : title, 80);
        if (t.isEmpty() || at <= 0) return null;
        try {
            return new JSONObject().put("id", id).put("title", t).put("at", at).put("place", CashMath.clean(place == null ? "" : place, 80))
                .put("desc", CashMath.clean(desc == null ? "" : desc, 1000)).put("max", Math.max(0, Math.min(MAX_PEOPLE, max))).put("people", new JSONArray());
        } catch (Exception e) { return null; }
    }

    /** "in 2 Tagen 3 Std", "in 45 Min", "läuft gerade", "vorbei". */
    static String countdown(long at, long now) {
        long d = at - now;
        if (d <= 0) return now - at < RUNNING ? "🔴 läuft gerade" : "vorbei";
        long m = (d + 59_999) / 60_000;
        if (m < 60) return "in " + m + " Min";
        long h = m / 60;
        if (h < 24) return "in " + h + " Std" + (m % 60 >= 1 ? " " + (m % 60) + " Min" : "");
        long days = h / 24;
        return "in " + days + (days == 1 ? " Tag" : " Tagen") + (h % 24 > 0 ? " " + (h % 24) + " Std" : "");
    }

    static boolean past(JSONObject e, long now) { return now - e.optLong("at") >= RUNNING; }

    static List<String> people(JSONObject e) {
        List<String> l = new ArrayList<String>();
        JSONArray a = e.optJSONArray("people");
        for (int i = 0; a != null && i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    /** Zusagen. @return null = ok, sonst Grund. */
    static String join(JSONObject e, String name) {
        String n = CashMath.clean(name == null ? "" : name.trim().replaceFirst("^@+", ""), 40);
        if (n.isEmpty()) return "Bitte einen Namen eingeben";
        List<String> p = people(e);
        for (String x : p) if (x.equalsIgnoreCase(n)) return n + " ist schon dabei";
        int max = e.optInt("max", 0);
        if (max > 0 && p.size() >= max) return "Das Event ist voll (" + max + ")";
        if (p.size() >= MAX_PEOPLE) return "Höchstens " + MAX_PEOPLE + " Teilnehmer";
        e.optJSONArray("people").put(n);
        return null;
    }

    static boolean leave(JSONObject e, String name) {
        JSONArray a = e.optJSONArray("people");
        for (int i = 0; a != null && i < a.length(); i++) if (a.optString(i).equalsIgnoreCase(name)) { a.remove(i); return true; }
        return false;
    }

    /** Kommende zuerst (naechstes oben), danach vergangene (juengstes oben). */
    static List<JSONObject> sorted(List<JSONObject> list, final long now) {
        List<JSONObject> l = new ArrayList<JSONObject>(list);
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                boolean pa = past(a, now), pb = past(b, now);
                if (pa != pb) return pa ? 1 : -1;
                return pa ? Long.compare(b.optLong("at"), a.optLong("at")) : Long.compare(a.optLong("at"), b.optLong("at"));
            }
        });
        return l;
    }

    static String when(long at, java.time.ZoneId z) {
        java.time.ZonedDateTime d = java.time.Instant.ofEpochMilli(at).atZone(z);
        String[] wd = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        return wd[d.getDayOfWeek().getValue() - 1] + ", " + d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm", Locale.GERMAN)) + " Uhr";
    }

    /**
     * Entwurf fuer DiscordMath.build (Webhook-Studio-Format). kind: "announce" oder "remind".
     * Zeit als Discord-Zeitstempel, damit jeder sie in seiner Zeitzone und mit Countdown sieht.
     */
    static JSONObject draft(JSONObject e, String kind) {
        JSONObject d = new JSONObject();
        try {
            long unix = e.optLong("at") / 1000;
            boolean rem = "remind".equals(kind);
            d.put("title", (rem ? "⏰ Gleich geht's los: " : "🎉 ") + e.optString("title"));
            if (!e.optString("desc").isEmpty()) d.put("description", e.optString("desc"));
            JSONArray f = new JSONArray();
            f.put(new JSONObject().put("name", "🕒 Wann").put("value", "<t:" + unix + ":F> (<t:" + unix + ":R>)").put("inline", false));
            if (!e.optString("place").isEmpty()) f.put(new JSONObject().put("name", "📍 Wo").put("value", e.optString("place")).put("inline", true));
            int n = people(e).size(), max = e.optInt("max", 0);
            if (n > 0 || max > 0) f.put(new JSONObject().put("name", "👥 Teilnehmer").put("value", n + (max > 0 ? " / " + max : "")).put("inline", true));
            d.put("fields", f);
            d.put("color", rem ? 0xFEE75C : 0x57F287);
            d.put("footer", "SkyManager Event-Planer");
            d.put("timestamp", false);
        } catch (Exception ignored) { }
        return d;
    }

    /** Eintrag fuer die geplanten Nachrichten (Watcher.SCHED). null, wenn die Erinnerung nicht mehr sinnvoll ist. */
    static JSONObject reminder(JSONObject e, String hookId, String hookName, String id, long now) {
        long at = e.optLong("at") - REMIND_BEFORE;
        if (SchedMath.checkTime(at, now) != null) return null;
        try {
            return new JSONObject().put("id", id).put("at", at).put("hookId", hookId).put("hook", hookName).put("draft", draft(e, "remind"))
                .put("title", "Erinnerung: " + e.optString("title")).put("status", SchedMath.PLANNED).put("evId", e.optString("id"));
        } catch (Exception x) { return null; }
    }

    /** Entfernt noch geplante Erinnerungen eines Events. @return Anzahl. */
    static int dropReminders(List<JSONObject> sched, String evId) {
        int n = 0;
        for (int i = sched.size() - 1; i >= 0; i--) {
            JSONObject s = sched.get(i);
            if (evId.equals(s.optString("evId")) && SchedMath.PLANNED.equals(s.optString("status"))) { sched.remove(i); n++; }
        }
        return n;
    }

    static boolean reminderPlanned(List<JSONObject> sched, String evId) {
        for (JSONObject s : sched) if (evId.equals(s.optString("evId")) && SchedMath.PLANNED.equals(s.optString("status"))) return true;
        return false;
    }
}

package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mod-Log: Verwarnungen, Mutes, Kicks, Bans. Reine Logik. */
final class ModLogMath {
    private ModLogMath() {}

    static final String[] ACTIONS = {"warn", "mute", "kick", "ban", "unban", "note"};
    static final String[] NAMES = {"Verwarnung", "Mute", "Kick", "Ban", "Entbannt", "Notiz"};
    static final String[] EMOJI = {"⚠️", "🔇", "👢", "🔨", "✅", "📝"};
    static final int[] COLORS = {0xFEE75C, 0xEB459E, 0xF57C00, 0xED4245, 0x57F287, 0x5865F2};
    static final int WARN_LIMIT = 3, MAX_ENTRIES = 2000;

    static int index(String a) { for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(a)) return i; return -1; }

    /** Benutzername vergleichbar machen (Gross/Klein, @, Leerzeichen egal). */
    static String key(String user) { return user == null ? "" : user.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT); }

    /** Neuer Eintrag. null bei ungueltiger Eingabe (fehlender Name, unbekannte Aktion). */
    static JSONObject entry(String id, long ts, String user, String action, String reason, String mod, String server) {
        String u = CashMath.clean(user == null ? "" : user.trim().replaceFirst("^@+", ""), 40);
        if (u.isEmpty() || index(action) < 0) return null;
        try {
            return new JSONObject().put("id", id).put("ts", ts).put("user", u).put("action", action)
                .put("reason", CashMath.clean(reason == null ? "" : reason, 300)).put("mod", CashMath.clean(mod == null ? "" : mod, 40)).put("server", CashMath.clean(server == null ? "" : server, 40));
        } catch (Exception e) {
            return null;
        }
    }

    /** Aktive Verwarnungen eines Nutzers: Verwarnungen seit dem letzten Entbannen. */
    static int activeWarns(List<JSONObject> log, String user) {
        String k = key(user);
        List<JSONObject> mine = new ArrayList<JSONObject>();
        for (JSONObject e : log) if (key(e.optString("user")).equals(k)) mine.add(e);
        Collections.sort(mine, new Comparator<JSONObject>() { @Override public int compare(JSONObject a, JSONObject b) { return Long.compare(a.optLong("ts"), b.optLong("ts")); } });
        int n = 0;
        for (JSONObject e : mine) { if ("unban".equals(e.optString("action"))) n = 0; else if ("warn".equals(e.optString("action"))) n++; }
        return n;
    }

    /** Ist der Nutzer gerade gebannt (letzter Ban nach dem letzten Entbannen)? */
    static boolean banned(List<JSONObject> log, String user) {
        String k = key(user);
        long ban = -1, unban = -1;
        for (JSONObject e : log) {
            if (!key(e.optString("user")).equals(k)) continue;
            if ("ban".equals(e.optString("action"))) ban = Math.max(ban, e.optLong("ts"));
            if ("unban".equals(e.optString("action"))) unban = Math.max(unban, e.optLong("ts"));
        }
        return ban >= 0 && ban > unban;
    }

    /** Vorschlag nach der n-ten aktiven Verwarnung. null = kein Hinweis. */
    static String hint(int warns) {
        if (warns >= WARN_LIMIT + 2) return "🔨 " + warns + " Verwarnungen – ein Ban wäre angebracht";
        if (warns >= WARN_LIMIT) return "⚠️ " + warns + ". Verwarnung – Zeit für einen Kick oder Mute?";
        return null;
    }

    /** Suche in Name, Grund, Moderator und Server; neueste zuerst. */
    static List<JSONObject> search(List<JSONObject> log, String q, String action) {
        String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject e : log) {
            if (action != null && !action.isEmpty() && !action.equals(e.optString("action"))) continue;
            if (!s.isEmpty() && !(e.optString("user") + " " + e.optString("reason") + " " + e.optString("mod") + " " + e.optString("server")).toLowerCase(Locale.ROOT).contains(s.replaceFirst("^@+", ""))) continue;
            out.add(e);
        }
        Collections.sort(out, new Comparator<JSONObject>() { @Override public int compare(JSONObject a, JSONObject b) { return Long.compare(b.optLong("ts"), a.optLong("ts")); } });
        return out;
    }

    /** Anzahl je Aktion (in fester Reihenfolge). */
    static Map<String, Integer> counts(List<JSONObject> log) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (String a : ACTIONS) m.put(a, 0);
        for (JSONObject e : log) if (m.containsKey(e.optString("action"))) m.put(e.optString("action"), m.get(e.optString("action")) + 1);
        return m;
    }

    /** Discord-Nachricht: farbiges Embed, pingt niemanden an. */
    static JSONObject discord(JSONObject e, int warns, long now) {
        JSONObject m = new JSONObject();
        try {
            int i = Math.max(0, index(e.optString("action")));
            JSONArray f = new JSONArray();
            f.put(new JSONObject().put("name", "Grund").put("value", e.optString("reason").isEmpty() ? "–" : e.optString("reason")).put("inline", false));
            if (!e.optString("mod").isEmpty()) f.put(new JSONObject().put("name", "Moderator").put("value", e.optString("mod")).put("inline", true));
            if (warns > 0) f.put(new JSONObject().put("name", "Aktive Verwarnungen").put("value", String.valueOf(warns)).put("inline", true));
            if (!e.optString("server").isEmpty()) f.put(new JSONObject().put("name", "Server").put("value", e.optString("server")).put("inline", true));
            m.put("embeds", new JSONArray().put(new JSONObject().put("title", EMOJI[i] + " " + NAMES[i] + ": " + e.optString("user")).put("color", COLORS[i]).put("fields", f)
                .put("timestamp", java.time.Instant.ofEpochMilli(e.optLong("ts", now)).toString()).put("footer", new JSONObject().put("text", "SkyManager Mod-Log"))));
            m.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));
            m.put("username", "SkyManager Mod-Log");
        } catch (Exception ignored) { }
        return m;
    }

    /** Lesbarer Text zum Teilen (neueste zuerst, hoechstens max). */
    static String shareText(List<JSONObject> log, int max, java.time.ZoneId z) {
        StringBuilder b = new StringBuilder("Mod-Log (SkyManager)\n");
        List<JSONObject> l = search(log, "", null);
        for (int k = 0; k < Math.min(max, l.size()); k++) {
            JSONObject e = l.get(k);
            int i = Math.max(0, index(e.optString("action")));
            b.append("\n").append(java.time.Instant.ofEpochMilli(e.optLong("ts")).atZone(z).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .append(" ").append(NAMES[i]).append(": ").append(e.optString("user"));
            if (!e.optString("reason").isEmpty()) b.append(" – ").append(e.optString("reason"));
            if (!e.optString("mod").isEmpty()) b.append(" (").append(e.optString("mod")).append(")");
        }
        return b.toString();
    }
}

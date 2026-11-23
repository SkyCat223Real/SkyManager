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

/** Team-Verwaltung: Raenge, Mitglieder, Befoerderungen, Abwesenheit. Reine Logik. */
final class TeamMath {
    private TeamMath() {}

    static final String[] DEFAULT_RANKS = {"Projektleitung", "Admin", "Moderator", "Supporter", "Test-Supporter"};
    static final int MAX_RANKS = 20, MAX_MEMBERS = 500;

    static List<String> ranks(JSONObject cfg) {
        List<String> l = new ArrayList<String>();
        JSONArray a = cfg == null ? null : cfg.optJSONArray("ranks");
        if (a == null || a.length() == 0) { for (String s : DEFAULT_RANKS) l.add(s); return l; }
        for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    /** Rang-Liste aus Text (eine Zeile pro Rang, oben = hoechster). Leere und doppelte fallen weg. */
    static List<String> parseRanks(String text) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String s : (text == null ? "" : text).split("\n")) { String t = CashMath.clean(s.trim(), 30); if (!t.isEmpty() && !m.containsKey(t.toLowerCase(Locale.ROOT)) && m.size() < MAX_RANKS) m.put(t.toLowerCase(Locale.ROOT), t); }
        return new ArrayList<String>(m.values());
    }

    static int level(List<String> ranks, String rank) { for (int i = 0; i < ranks.size(); i++) if (ranks.get(i).equalsIgnoreCase(rank)) return i; return ranks.size(); }

    static JSONObject member(String id, long ts, String name, String rank, String contact) {
        String n = CashMath.clean(name == null ? "" : name.trim().replaceFirst("^@+", ""), 40);
        if (n.isEmpty()) return null;
        try {
            return new JSONObject().put("id", id).put("name", n).put("rank", CashMath.clean(rank == null ? "" : rank, 30)).put("contact", CashMath.clean(contact == null ? "" : contact, 60))
                .put("joined", ts).put("notes", "").put("hist", new JSONArray().put(new JSONObject().put("t", ts).put("text", "Ins Team aufgenommen" + (rank == null || rank.isEmpty() ? "" : " als " + rank))));
        } catch (Exception e) { return null; }
    }

    static boolean exists(List<JSONObject> team, String name) {
        String n = name == null ? "" : name.trim().replaceFirst("^@+", "");
        for (JSONObject m : team) if (m.optString("name").equalsIgnoreCase(n)) return true;
        return false;
    }

    static void log(JSONObject m, long ts, String text) {
        try { JSONArray h = m.optJSONArray("hist"); if (h == null) { h = new JSONArray(); m.put("hist", h); } h.put(new JSONObject().put("t", ts).put("text", text)); } catch (Exception ignored) { }
    }

    /** Befoerdern (dir -1) oder herabstufen (dir +1). @return neuer Rang oder null, wenn nicht moeglich. */
    static String move(JSONObject m, List<String> ranks, int dir, long ts) {
        int cur = level(ranks, m.optString("rank"));
        int to = cur >= ranks.size() ? (dir < 0 ? ranks.size() - 1 : -1) : cur + dir;
        if (to < 0 || to >= ranks.size()) return null;
        String old = m.optString("rank"), nr = ranks.get(to);
        try { m.put("rank", nr); } catch (Exception e) { return null; }
        log(m, ts, (dir < 0 ? "⬆️ Befördert: " : "⬇️ Herabgestuft: ") + (old.isEmpty() ? "ohne Rang" : old) + " → " + nr);
        return nr;
    }

    /** Abwesend bis (Tag als epochDay, -1 = nicht abwesend). */
    static boolean away(JSONObject m, long today) { return m.optLong("awayUntil", -1) >= today; }

    /** Sortiert: hoechster Rang zuerst, darin nach Name. */
    static List<JSONObject> sorted(List<JSONObject> team, final List<String> ranks) {
        List<JSONObject> l = new ArrayList<JSONObject>(team);
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int c = Integer.compare(level(ranks, a.optString("rank")), level(ranks, b.optString("rank")));
                return c != 0 ? c : a.optString("name").compareToIgnoreCase(b.optString("name"));
            }
        });
        return l;
    }

    static long days(JSONObject m, long now) { return Math.max(0, (now - m.optLong("joined")) / 86_400_000L); }

    /** Teamliste fuer Discord: ein Feld je Rang, Abwesende markiert. Pingt niemanden an. */
    static JSONObject discordList(List<JSONObject> team, List<String> ranks, String title, long today, long now) {
        JSONObject msg = new JSONObject();
        try {
            JSONArray f = new JSONArray();
            Map<String, List<String>> g = new LinkedHashMap<String, List<String>>();
            for (String r : ranks) g.put(r, new ArrayList<String>());
            g.put("Ohne Rang", new ArrayList<String>());
            for (JSONObject m : sorted(team, ranks)) {
                int lv = level(ranks, m.optString("rank"));
                g.get(lv < ranks.size() ? ranks.get(lv) : "Ohne Rang").add(m.optString("name").replace("*", "").replace("_", "\\_") + (away(m, today) ? " 🌴" : ""));
            }
            for (Map.Entry<String, List<String>> e : g.entrySet()) {
                if (e.getValue().isEmpty() || f.length() >= 25) continue;
                String v = MyDayMath.join(e.getValue()).replace(" · ", "\n");
                f.put(new JSONObject().put("name", e.getKey() + " (" + e.getValue().size() + ")").put("value", v.length() > 1024 ? v.substring(0, 1020) + " …" : v).put("inline", false));
            }
            msg.put("embeds", new JSONArray().put(new JSONObject().put("title", "👥 " + CashMath.clean(title == null || title.isEmpty() ? "Unser Team" : title, 200)).put("color", 0x5865F2).put("fields", f)
                .put("description", team.size() + " Mitglieder" + (f.length() > 0 ? "" : "\nNoch niemand im Team.")).put("footer", new JSONObject().put("text", "🌴 = abwesend · SkyManager Team")).put("timestamp", java.time.Instant.ofEpochMilli(now).toString())));
            msg.put("allowed_mentions", new JSONObject().put("parse", new JSONArray())).put("username", "SkyManager Team");
        } catch (Exception ignored) { }
        return msg;
    }

    static JSONObject discordPromotion(JSONObject m, boolean up, long now) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("embeds", new JSONArray().put(new JSONObject().put("title", (up ? "🎉 Beförderung" : "Rangänderung")).put("description", "**" + m.optString("name").replace("*", "") + "** ist jetzt **" + m.optString("rank") + "**" + (up ? ". Glückwunsch!" : ".")).put("color", up ? 0x57F287 : 0x99AAB5)
                .put("timestamp", java.time.Instant.ofEpochMilli(now).toString()).put("footer", new JSONObject().put("text", "SkyManager Team"))));
            msg.put("allowed_mentions", new JSONObject().put("parse", new JSONArray())).put("username", "SkyManager Team");
        } catch (Exception ignored) { }
        return msg;
    }
}

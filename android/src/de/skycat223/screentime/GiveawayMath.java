package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Giveaways: Teilnehmer, faire Ziehung, Discord-Nachrichten. Reine Logik. */
final class GiveawayMath {
    private GiveawayMath() {}

    static final int MAX_PEOPLE = 5000, MAX_WINNERS = 50;

    /** Liste aus eingefuegtem Text (Zeilen, Kommas, Semikolons), @ entfernt, Doppelte (Gross/Klein) entfernt, Reihenfolge bleibt. */
    static List<String> parseList(String text) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (text != null) for (String p : text.split("[\\n,;]+")) {
            String n = CashMath.clean(p.trim().replaceFirst("^[-•*]\\s*", "").replaceFirst("^@+", ""), 40);
            if (!n.isEmpty() && !m.containsKey(n.toLowerCase(Locale.ROOT))) m.put(n.toLowerCase(Locale.ROOT), n);
        }
        return new ArrayList<String>(m.values());
    }

    /** Teilnehmer zusammenfuehren (neue hinten, ohne Doppelte). @return Zahl der neu hinzugekommenen. */
    static int merge(JSONArray people, List<String> add) {
        java.util.Set<String> have = new java.util.HashSet<String>();
        for (int i = 0; i < people.length(); i++) have.add(people.optString(i).toLowerCase(Locale.ROOT));
        int n = 0;
        for (String s : add) if (people.length() < MAX_PEOPLE && have.add(s.toLowerCase(Locale.ROOT))) { people.put(s); n++; }
        return n;
    }

    /** Zieht k verschiedene Gewinner aus pool, ohne die ausgeschlossenen. Gleichverteilt (Fisher-Yates mit nextInt). */
    static List<String> draw(List<String> pool, java.util.Collection<String> exclude, int k, Random rnd) {
        java.util.Set<String> ex = new java.util.HashSet<String>();
        for (String s : exclude) ex.add(s.toLowerCase(Locale.ROOT));
        List<String> c = new ArrayList<String>();
        for (String s : pool) if (!ex.contains(s.toLowerCase(Locale.ROOT))) c.add(s);
        int n = Math.min(Math.max(0, k), c.size());
        for (int i = 0; i < n; i++) { int j = i + rnd.nextInt(c.size() - i); String t = c.get(i); c.set(i, c.get(j)); c.set(j, t); }
        return new ArrayList<String>(c.subList(0, n));
    }

    static Random random() { return new SecureRandom(); }

    static List<String> list(JSONArray a) { List<String> l = new ArrayList<String>(); for (int i = 0; a != null && i < a.length(); i++) l.add(a.optString(i)); return l; }

    /** Discord: Ankuendigung oder Gewinner, pingt niemanden an. */
    static JSONObject discord(JSONObject g, boolean winners, long now) {
        JSONObject m = new JSONObject();
        try {
            JSONArray f = new JSONArray();
            String title, desc;
            if (winners) {
                List<String> w = list(g.optJSONArray("winners"));
                title = "🏆 Gewinner: " + g.optString("prize");
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < w.size(); i++) b.append(i < 3 ? new String[]{"🥇", "🥈", "🥉"}[i] : (i + 1) + ".").append(" **").append(w.get(i).replace("*", "")).append("**\n");
                desc = b.length() == 0 ? "Keine Gewinner." : b.toString().trim();
                f.put(new JSONObject().put("name", "Teilnehmer").put("value", String.valueOf(list(g.optJSONArray("people")).size())).put("inline", true));
            } else {
                title = "🎁 Giveaway: " + g.optString("prize");
                desc = g.optString("info").isEmpty() ? "Mach mit und gewinne!" : g.optString("info");
                f.put(new JSONObject().put("name", "Gewinner").put("value", String.valueOf(Math.max(1, g.optInt("count", 1)))).put("inline", true));
                if (g.optLong("end") > 0) { long u = g.optLong("end") / 1000; f.put(new JSONObject().put("name", "Endet").put("value", "<t:" + u + ":F> (<t:" + u + ":R>)").put("inline", false)); }
            }
            m.put("embeds", new JSONArray().put(new JSONObject().put("title", CashMath.clean(title, 250)).put("description", desc).put("color", winners ? 0xF1C40F : 0xEB459E).put("fields", f)
                .put("timestamp", java.time.Instant.ofEpochMilli(now).toString()).put("footer", new JSONObject().put("text", "SkyManager Giveaway"))));
            m.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));
            m.put("username", "SkyManager Giveaway");
        } catch (Exception ignored) { }
        return m;
    }
}

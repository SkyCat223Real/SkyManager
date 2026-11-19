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

/** Bewerbungen fuer Team/Fraktionen: Status, Verlauf, Discord-Nachricht. Reine Logik. */
final class ApplyMath {
    private ApplyMath() {}

    static final String[] STATUS = {"neu", "gespraech", "angenommen", "abgelehnt"};
    static final String[] NAMES = {"Neu", "Gespräch", "Angenommen", "Abgelehnt"};
    static final String[] EMOJI = {"🆕", "💬", "✅", "❌"};
    static final int[] COLORS = {0x5865F2, 0xFEE75C, 0x57F287, 0xED4245};

    static int index(String s) { for (int i = 0; i < STATUS.length; i++) if (STATUS[i].equals(s)) return i; return -1; }
    static boolean open(JSONObject e) { int i = index(e.optString("status")); return i == 0 || i == 1; }

    static JSONObject entry(String id, long ts, String name, String role, String contact, String notes) {
        String n = CashMath.clean(name == null ? "" : name.trim().replaceFirst("^@+", ""), 40);
        if (n.isEmpty()) return null;
        try {
            return new JSONObject().put("id", id).put("ts", ts).put("name", n).put("role", CashMath.clean(role == null ? "" : role, 40))
                .put("contact", CashMath.clean(contact == null ? "" : contact, 60)).put("notes", CashMath.clean(notes == null ? "" : notes, 500))
                .put("status", "neu").put("hist", new JSONArray().put(new JSONObject().put("s", "neu").put("t", ts)));
        } catch (Exception e) { return null; }
    }

    /** Status setzen und im Verlauf vermerken. false bei unbekanntem oder gleichem Status. */
    static boolean setStatus(JSONObject e, String status, long ts) {
        if (index(status) < 0 || status.equals(e.optString("status"))) return false;
        try {
            e.put("status", status);
            JSONArray h = e.optJSONArray("hist");
            if (h == null) { h = new JSONArray(); e.put("hist", h); }
            h.put(new JSONObject().put("s", status).put("t", ts));
            return true;
        } catch (Exception x) { return false; }
    }

    /** Gibt es schon eine offene Bewerbung derselben Person fuer dieselbe Rolle? */
    static boolean duplicate(List<JSONObject> list, String name, String role) {
        String n = name == null ? "" : name.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT), r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        for (JSONObject e : list) if (open(e) && e.optString("name").toLowerCase(Locale.ROOT).equals(n) && e.optString("role").trim().toLowerCase(Locale.ROOT).equals(r)) return true;
        return false;
    }

    static Map<String, Integer> counts(List<JSONObject> list) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (String s : STATUS) m.put(s, 0);
        for (JSONObject e : list) if (m.containsKey(e.optString("status"))) m.put(e.optString("status"), m.get(e.optString("status")) + 1);
        return m;
    }

    /** Filter (Status leer = alle) und Suche; offene zuerst, darin aelteste zuerst (wer wartet am laengsten); erledigte neueste zuerst. */
    static List<JSONObject> view(List<JSONObject> list, String status, String q) {
        String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject e : list) {
            if (status != null && !status.isEmpty() && !status.equals(e.optString("status"))) continue;
            if (!s.isEmpty() && !(e.optString("name") + " " + e.optString("role") + " " + e.optString("contact") + " " + e.optString("notes")).toLowerCase(Locale.ROOT).contains(s)) continue;
            out.add(e);
        }
        Collections.sort(out, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                boolean oa = open(a), ob = open(b);
                if (oa != ob) return oa ? -1 : 1;
                return oa ? Long.compare(a.optLong("ts"), b.optLong("ts")) : Long.compare(b.optLong("ts"), a.optLong("ts"));
            }
        });
        return out;
    }

    static long daysWaiting(JSONObject e, long now) { return Math.max(0, (now - e.optLong("ts")) / 86_400_000L); }

    /** Discord-Nachricht zur Entscheidung, pingt niemanden an. */
    static JSONObject discord(JSONObject e, String message, long now) {
        JSONObject m = new JSONObject();
        try {
            int i = Math.max(0, index(e.optString("status")));
            String head = i == 2 ? "✅ Bewerbung angenommen" : i == 3 ? "❌ Bewerbung abgelehnt" : i == 1 ? "💬 Einladung zum Gespräch" : "🆕 Neue Bewerbung";
            JSONArray f = new JSONArray();
            f.put(new JSONObject().put("name", "Bewerber").put("value", e.optString("name")).put("inline", true));
            if (!e.optString("role").isEmpty()) f.put(new JSONObject().put("name", "Rolle").put("value", e.optString("role")).put("inline", true));
            if (message != null && !message.trim().isEmpty()) f.put(new JSONObject().put("name", "Nachricht").put("value", CashMath.clean(message, 1000)).put("inline", false));
            m.put("embeds", new JSONArray().put(new JSONObject().put("title", head).put("color", COLORS[i]).put("fields", f)
                .put("timestamp", java.time.Instant.ofEpochMilli(now).toString()).put("footer", new JSONObject().put("text", "SkyManager Bewerbungen"))));
            m.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));
            m.put("username", "SkyManager Bewerbungen");
        } catch (Exception ignored) { }
        return m;
    }
}

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

/** Einsatzberichte: Nummern, Arten, Suche, Discord. Reine Logik. */
final class ReportMath {
    private ReportMath() {}

    static final String[] TYPES = {"Verkehrsunfall", "Verkehrskontrolle", "Brand", "Rettungsdienst", "Diebstahl", "Raub", "Körperverletzung", "Verfolgung", "Ruhestörung", "Sonstiges"};
    static final String[] EMOJI = {"🚗", "🚓", "🔥", "🚑", "👜", "💰", "🤕", "🏁", "🔊", "📋"};
    static final String[] RESULTS = {"Festnahme", "Bußgeld", "Verwarnung", "Anzeige", "Übergabe Rettungsdienst", "Keine Maßnahmen"};

    static int type(String t) { for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(t)) return i; return TYPES.length - 1; }

    /** Naechste Nummer E-JJJJ-NNN fuer das Jahr. */
    static String nextNumber(List<JSONObject> reports, int year) {
        int max = 0;
        String pre = "E-" + year + "-";
        for (JSONObject r : reports) { String n = r.optString("nr"); if (n.startsWith(pre)) try { max = Math.max(max, Integer.parseInt(n.substring(pre.length()))); } catch (Exception ignored) { } }
        return pre + String.format(Locale.ROOT, "%03d", max + 1);
    }

    static JSONObject report(String id, String nr, long at, String type, String place, String units, String people, String text, String result, String fines, String author) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new JSONObject().put("id", id).put("nr", nr).put("at", at).put("type", TYPES[type(type)]).put("place", CashMath.clean(place == null ? "" : place, 80)).put("units", CashMath.clean(units == null ? "" : units, 200))
                .put("people", CashMath.clean(people == null ? "" : people, 300)).put("text", text.replace("\r", "").trim().length() > 3000 ? text.replace("\r", "").trim().substring(0, 3000) : text.replace("\r", "").trim())
                .put("result", CashMath.clean(result == null ? "" : result, 60)).put("fines", fines == null ? "" : (fines.length() > 1000 ? fines.substring(0, 1000) : fines)).put("author", CashMath.clean(author == null ? "" : author, 40));
        } catch (Exception e) { return null; }
    }

    static List<JSONObject> search(List<JSONObject> l, String type, String q) {
        String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject r : l) {
            if (type != null && !type.isEmpty() && !type.equals(r.optString("type"))) continue;
            if (!s.isEmpty() && !(r.optString("nr") + " " + r.optString("place") + " " + r.optString("units") + " " + r.optString("people") + " " + r.optString("text") + " " + r.optString("author")).toLowerCase(Locale.ROOT).contains(s)) continue;
            out.add(r);
        }
        Collections.sort(out, new Comparator<JSONObject>() { @Override public int compare(JSONObject a, JSONObject b) { return Long.compare(b.optLong("at"), a.optLong("at")); } });
        return out;
    }

    /** Anzahl je Einsatzart in einem Zeitraum (from inklusive). Nur Arten mit Einsaetzen. */
    static Map<String, Integer> stats(List<JSONObject> l, long from) {
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        for (String t : TYPES) m.put(t, 0);
        for (JSONObject r : l) if (r.optLong("at") >= from) m.put(r.optString("type"), m.containsKey(r.optString("type")) ? m.get(r.optString("type")) + 1 : 1);
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : m.entrySet()) if (e.getValue() > 0) out.put(e.getKey(), e.getValue());
        return out;
    }

    static String clip(String s, int n) { return s.length() > n ? s.substring(0, n - 2) + " …" : s; }

    static JSONObject discord(JSONObject r, java.time.ZoneId z) {
        JSONObject msg = new JSONObject();
        try {
            int t = type(r.optString("type"));
            JSONArray f = new JSONArray();
            f.put(new JSONObject().put("name", "🕒 Zeit").put("value", EventMath.when(r.optLong("at"), z)).put("inline", true));
            if (!r.optString("place").isEmpty()) f.put(new JSONObject().put("name", "📍 Ort").put("value", r.optString("place")).put("inline", true));
            if (!r.optString("units").isEmpty()) f.put(new JSONObject().put("name", "🚓 Einheiten").put("value", clip(r.optString("units"), 1024)).put("inline", false));
            if (!r.optString("people").isEmpty()) f.put(new JSONObject().put("name", "👤 Beteiligte").put("value", clip(r.optString("people"), 1024)).put("inline", false));
            if (!r.optString("result").isEmpty()) f.put(new JSONObject().put("name", "✅ Maßnahme").put("value", r.optString("result")).put("inline", true));
            if (!r.optString("fines").isEmpty()) f.put(new JSONObject().put("name", "⚖️ Strafe").put("value", clip(r.optString("fines"), 1024)).put("inline", false));
            if (!r.optString("author").isEmpty()) f.put(new JSONObject().put("name", "✍️ Bericht von").put("value", r.optString("author")).put("inline", true));
            msg.put("embeds", new JSONArray().put(new JSONObject().put("title", EMOJI[t] + " Einsatzbericht " + r.optString("nr") + ": " + r.optString("type")).put("description", clip(r.optString("text"), 4000))
                .put("color", t == 2 ? 0xF57C00 : t == 3 ? 0xED4245 : 0x2D8CFF).put("fields", f).put("footer", new JSONObject().put("text", "SkyManager Einsatzberichte"))));
            msg.put("allowed_mentions", new JSONObject().put("parse", new JSONArray())).put("username", "SkyManager Einsatzberichte");
        } catch (Exception ignored) { }
        return msg;
    }

    static String text(JSONObject r, java.time.ZoneId z) {
        StringBuilder b = new StringBuilder("Einsatzbericht " + r.optString("nr") + " – " + r.optString("type") + "\n" + EventMath.when(r.optLong("at"), z));
        if (!r.optString("place").isEmpty()) b.append("\nOrt: ").append(r.optString("place"));
        if (!r.optString("units").isEmpty()) b.append("\nEinheiten: ").append(r.optString("units"));
        if (!r.optString("people").isEmpty()) b.append("\nBeteiligte: ").append(r.optString("people"));
        b.append("\n\n").append(r.optString("text"));
        if (!r.optString("result").isEmpty()) b.append("\n\nMaßnahme: ").append(r.optString("result"));
        if (!r.optString("fines").isEmpty()) b.append("\n").append(r.optString("fines"));
        if (!r.optString("author").isEmpty()) b.append("\n\nBericht von ").append(r.optString("author"));
        return b.toString();
    }
}

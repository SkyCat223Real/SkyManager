package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Bussgeldkatalog fuer RP: Eintraege, Berechnung, Text und Discord. Reine Logik. */
final class FineMath {
    private FineMath() {}

    static final int MAX_ENTRIES = 300, MAX_COUNT = 99;

    /** Vorlage: {Kategorie, Vergehen, Bussgeld in Euro, Haft in Hafteinheiten, Fuehrerschein weg (1/0)}. Fuer RP gedacht, an den Server anpassen. */
    static final Object[][] TEMPLATE = {
        {"Verkehr", "Geschwindigkeit innerorts bis 20 km/h zu schnell", 70, 0, 0},
        {"Verkehr", "Geschwindigkeit innerorts 21–40 km/h zu schnell", 260, 0, 0},
        {"Verkehr", "Geschwindigkeit innerorts über 40 km/h zu schnell", 600, 0, 1},
        {"Verkehr", "Rote Ampel überfahren", 200, 0, 0},
        {"Verkehr", "Falschparken / Halteverbot", 55, 0, 0},
        {"Verkehr", "Fahren ohne Führerschein", 1000, 5, 0},
        {"Verkehr", "Fahren unter Alkohol- oder Drogeneinfluss", 1500, 5, 1},
        {"Verkehr", "Unerlaubtes Entfernen vom Unfallort", 1200, 10, 1},
        {"Verkehr", "Gefährlicher Eingriff in den Straßenverkehr", 2500, 15, 1},
        {"Ordnung", "Ruhestörung", 150, 0, 0},
        {"Ordnung", "Beleidigung", 500, 0, 0},
        {"Ordnung", "Missachtung polizeilicher Anweisungen", 800, 5, 0},
        {"Ordnung", "Vermummung in der Öffentlichkeit", 400, 0, 0},
        {"Straftaten", "Diebstahl", 1000, 10, 0},
        {"Straftaten", "Fahrzeugdiebstahl", 2500, 20, 0},
        {"Straftaten", "Sachbeschädigung", 800, 5, 0},
        {"Straftaten", "Körperverletzung", 2000, 15, 0},
        {"Straftaten", "Widerstand gegen Vollstreckungsbeamte", 2500, 20, 0},
        {"Straftaten", "Flucht vor der Polizei", 3000, 20, 1},
        {"Straftaten", "Illegaler Waffenbesitz", 5000, 30, 0},
        {"Straftaten", "Raub", 6000, 40, 0},
        {"Straftaten", "Bankraub", 15000, 60, 0},
    };

    static JSONObject entry(String id, String cat, String name, long fine, int jail, boolean license) {
        String n = CashMath.clean(name == null ? "" : name, 80);
        if (n.isEmpty() || fine < 0 || jail < 0) return null;
        try { return new JSONObject().put("id", id).put("cat", CashMath.clean(cat == null || cat.trim().isEmpty() ? "Sonstiges" : cat, 30)).put("name", n).put("fine", Math.min(fine, 100_000_000L)).put("jail", Math.min(jail, 100_000)).put("lic", license); } catch (Exception e) { return null; }
    }

    static List<JSONObject> template(long seed) {
        List<JSONObject> l = new ArrayList<JSONObject>();
        for (int i = 0; i < TEMPLATE.length; i++) { Object[] t = TEMPLATE[i]; l.add(entry("t" + seed + "_" + i, (String) t[0], (String) t[1], (Integer) t[2], (Integer) t[3], ((Integer) t[4]) == 1)); }
        return l;
    }

    /** Kategorien in Reihenfolge des ersten Auftretens. */
    static List<String> categories(List<JSONObject> cat) {
        List<String> l = new ArrayList<String>();
        for (JSONObject e : cat) if (!l.contains(e.optString("cat"))) l.add(e.optString("cat"));
        return l;
    }

    /** Summe aus Auswahl {id: Anzahl}. @return {fine, jail, lic(0/1), items}. */
    static long[] total(List<JSONObject> cat, JSONObject sel, long maxJail) {
        long fine = 0, jail = 0, lic = 0, items = 0;
        for (JSONObject e : cat) {
            int n = Math.max(0, Math.min(MAX_COUNT, sel == null ? 0 : sel.optInt(e.optString("id"), 0)));
            if (n == 0) continue;
            fine += e.optLong("fine") * n; jail += (long) e.optInt("jail") * n; items += n;
            if (e.optBoolean("lic")) lic = 1;
        }
        if (maxJail > 0) jail = Math.min(jail, maxJail);
        return new long[]{fine, jail, lic, items};
    }

    static String euro(long v) { return RobloxMath.num(v) + " €"; }

    /** Zeilen der Auswahl: "2× Diebstahl". */
    static List<String> lines(List<JSONObject> cat, JSONObject sel) {
        List<String> l = new ArrayList<String>();
        for (JSONObject e : cat) { int n = sel == null ? 0 : Math.min(MAX_COUNT, sel.optInt(e.optString("id"), 0)); if (n > 0) l.add((n > 1 ? n + "× " : "") + e.optString("name")); }
        return l;
    }

    /** Kurztext fuer Chat/Bericht. */
    static String summary(List<JSONObject> cat, JSONObject sel, long maxJail, String who) {
        long[] t = total(cat, sel, maxJail);
        if (t[3] == 0) return "";
        StringBuilder b = new StringBuilder();
        if (who != null && !who.trim().isEmpty()) b.append("Strafe für ").append(who.trim()).append(":\n");
        for (String s : lines(cat, sel)) b.append("• ").append(s).append("\n");
        b.append("= ").append(euro(t[0]));
        if (t[1] > 0) b.append(" · ").append(t[1]).append(" Hafteinheiten");
        if (t[2] == 1) b.append(" · Führerscheinentzug");
        return b.toString();
    }

    static JSONObject discord(List<JSONObject> cat, JSONObject sel, long maxJail, String who, String officer, long now) {
        JSONObject msg = new JSONObject();
        try {
            long[] t = total(cat, sel, maxJail);
            String l = "• " + MyDayMath.join(lines(cat, sel)).replace(" · ", "\n• ");
            JSONArray f = new JSONArray().put(new JSONObject().put("name", "Bußgeld").put("value", euro(t[0])).put("inline", true));
            if (t[1] > 0) f.put(new JSONObject().put("name", "Haft").put("value", t[1] + " HE").put("inline", true));
            if (t[2] == 1) f.put(new JSONObject().put("name", "Führerschein").put("value", "entzogen").put("inline", true));
            if (officer != null && !officer.trim().isEmpty()) f.put(new JSONObject().put("name", "Beamter").put("value", CashMath.clean(officer, 40)).put("inline", true));
            msg.put("embeds", new JSONArray().put(new JSONObject().put("title", "⚖️ Strafe" + (who == null || who.trim().isEmpty() ? "" : ": " + CashMath.clean(who, 40).replace("*", ""))).put("description", l.length() > 4000 ? l.substring(0, 4000) + " …" : l).put("color", 0xED4245).put("fields", f)
                .put("timestamp", java.time.Instant.ofEpochMilli(now).toString()).put("footer", new JSONObject().put("text", "SkyManager Bußgeldkatalog"))));
            msg.put("allowed_mentions", new JSONObject().put("parse", new JSONArray())).put("username", "SkyManager Bußgeldkatalog");
        } catch (Exception ignored) { }
        return msg;
    }
}

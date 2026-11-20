package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Regelwerk: Vorlagen, Bearbeiten, Version, Discord-Nachrichten innerhalb der Discord-Grenzen. Reine Logik. */
final class RulesMath {
    private RulesMath() {}

    static final int MAX_RULES = 100, MAX_RULE = 1000, EMBED_DESC = 4096, MSG_TOTAL = 5800, MAX_EMBEDS = 10;

    static final String[] TEMPLATE_NAMES = {"Discord-Grundregeln", "Roleplay-Server"};
    static final String[][] TEMPLATES = {
        {"Sei respektvoll. Keine Beleidigungen, Diskriminierung oder Drohungen.",
         "Kein Spam, keine Werbung und keine Massen-Pings.",
         "Keine NSFW-, Gewalt- oder Schockinhalte.",
         "Nutze die Kanäle für ihren Zweck.",
         "Keine persönlichen Daten von anderen teilen.",
         "Den Anweisungen des Teams ist Folge zu leisten.",
         "Es gelten die Discord-Nutzungsbedingungen und Community-Richtlinien."},
        {"Bleib im Roleplay. Kein grundloses Verlassen der Rolle (Fail-RP).",
         "Kein RDM: Niemanden ohne Roleplay-Grund angreifen oder töten.",
         "Kein VDM: Fahrzeuge nicht als Waffe benutzen.",
         "Kein Metagaming: Wissen von außerhalb (z. B. Discord, Stream) nicht im RP nutzen.",
         "Kein Powergaming: Anderen keine Handlungen aufzwingen, ihnen immer eine Reaktion lassen.",
         "Fear-RP: Verhalte dich realistisch, wenn dein Leben bedroht ist.",
         "Kein Combat-Logging: Das Spiel nicht verlassen, um einer Situation zu entgehen.",
         "Fraktionsregeln und Anweisungen der Leitung gelten zusätzlich.",
         "Bugs und Exploits nicht ausnutzen, sondern dem Team melden."}
    };

    static JSONObject ruleset(String id, String name, long now) {
        try { return new JSONObject().put("id", id).put("name", CashMath.clean(name == null || name.trim().isEmpty() ? "Regeln" : name, 60)).put("rules", new JSONArray()).put("version", 1).put("changed", now); } catch (Exception e) { return null; }
    }

    static String cleanRule(String s) {
        if (s == null) return "";
        String t = s.replace("\r", "").trim().replaceFirst("^(§\\s*)?\\d{1,3}[.)]\\s+", "");
        return t.length() > MAX_RULE ? t.substring(0, MAX_RULE) : t;
    }

    /** Aenderung vermerken: Version +1, Datum. */
    static void touch(JSONObject rs, long now) { try { rs.put("version", rs.optInt("version", 1) + 1).put("changed", now); } catch (Exception ignored) { } }

    static List<String> rules(JSONObject rs) { List<String> l = new ArrayList<String>(); JSONArray a = rs.optJSONArray("rules"); for (int i = 0; a != null && i < a.length(); i++) l.add(a.optString(i)); return l; }

    static void setRules(JSONObject rs, List<String> l) { try { rs.put("rules", new JSONArray(l)); } catch (Exception ignored) { } }

    /** Regel hinzufuegen. null = ok, sonst Grund. Nummerierung am Anfang wird entfernt. */
    static String add(JSONObject rs, String text, long now) {
        String t = cleanRule(text);
        if (t.isEmpty()) return "Bitte einen Regeltext eingeben";
        List<String> l = rules(rs);
        if (l.size() >= MAX_RULES) return "Höchstens " + MAX_RULES + " Regeln";
        l.add(t); setRules(rs, l); touch(rs, now);
        return null;
    }

    /** Mehrere Regeln aus eingefuegtem Text (eine pro Zeile). @return Zahl der hinzugefuegten. */
    static int addMany(JSONObject rs, String text, long now) {
        List<String> l = rules(rs);
        int n = 0;
        for (String line : (text == null ? "" : text).split("\n")) { String t = cleanRule(line); if (!t.isEmpty() && l.size() < MAX_RULES) { l.add(t); n++; } }
        if (n > 0) { setRules(rs, l); touch(rs, now); }
        return n;
    }

    static boolean move(JSONObject rs, int i, int dir, long now) {
        List<String> l = rules(rs);
        int j = i + dir;
        if (i < 0 || i >= l.size() || j < 0 || j >= l.size()) return false;
        String t = l.get(i); l.set(i, l.get(j)); l.set(j, t);
        setRules(rs, l); touch(rs, now);
        return true;
    }

    static boolean remove(JSONObject rs, int i, long now) {
        List<String> l = rules(rs);
        if (i < 0 || i >= l.size()) return false;
        l.remove(i); setRules(rs, l); touch(rs, now);
        return true;
    }

    static boolean edit(JSONObject rs, int i, String text, long now) {
        List<String> l = rules(rs);
        String t = cleanRule(text);
        if (i < 0 || i >= l.size() || t.isEmpty() || t.equals(l.get(i))) return false;
        l.set(i, t); setRules(rs, l); touch(rs, now);
        return true;
    }

    static String stand(JSONObject rs, java.time.ZoneId z) {
        return "Stand: " + java.time.Instant.ofEpochMilli(rs.optLong("changed")).atZone(z).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " · Version " + rs.optInt("version", 1);
    }

    /** Text zum Teilen. */
    static String text(JSONObject rs, java.time.ZoneId z) {
        StringBuilder b = new StringBuilder("📜 " + rs.optString("name") + "\n");
        List<String> l = rules(rs);
        for (int i = 0; i < l.size(); i++) b.append("\n§").append(i + 1).append(" ").append(l.get(i));
        return b.append("\n\n").append(stand(rs, z)).toString();
    }

    /**
     * Discord-Nachrichten: Regeln als "**§n** Text", aufgeteilt auf Embeds (<= 4096 Zeichen) und Nachrichten (<= 5800 Zeichen, <= 10 Embeds).
     * Erste Nachricht mit Titel, letzte mit Stand im Footer. Pingt niemanden an.
     */
    static List<JSONObject> discord(JSONObject rs, int color, java.time.ZoneId z) {
        List<String> blocks = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        List<String> l = rules(rs);
        for (int i = 0; i < l.size(); i++) {
            String line = "**§" + (i + 1) + "** " + l.get(i).replace("@everyone", "@​everyone").replace("@here", "@​here") + "\n\n";
            if (cur.length() + line.length() > EMBED_DESC - 50 && cur.length() > 0) { blocks.add(cur.toString().trim()); cur = new StringBuilder(); }
            cur.append(line);
        }
        if (cur.length() > 0) blocks.add(cur.toString().trim());
        List<JSONObject> msgs = new ArrayList<JSONObject>();
        try {
            JSONArray embeds = new JSONArray();
            int size = 0;
            for (int i = 0; i < blocks.size(); i++) {
                String title = i == 0 ? "📜 " + rs.optString("name") : null;
                int add = blocks.get(i).length() + (title == null ? 0 : title.length()) + 80;
                if (embeds.length() > 0 && (size + add > MSG_TOTAL || embeds.length() >= MAX_EMBEDS)) { msgs.add(msg(embeds)); embeds = new JSONArray(); size = 0; }
                JSONObject e = new JSONObject().put("description", blocks.get(i)).put("color", color & 0xFFFFFF);
                if (title != null) e.put("title", title);
                embeds.put(e); size += add;
            }
            if (embeds.length() == 0) embeds.put(new JSONObject().put("title", "📜 " + rs.optString("name")).put("description", "Noch keine Regeln.").put("color", color & 0xFFFFFF));
            embeds.optJSONObject(embeds.length() - 1).put("footer", new JSONObject().put("text", stand(rs, z)));
            msgs.add(msg(embeds));
        } catch (Exception ignored) { }
        return msgs;
    }

    static JSONObject msg(JSONArray embeds) throws Exception {
        return new JSONObject().put("embeds", embeds).put("allowed_mentions", new JSONObject().put("parse", new JSONArray())).put("username", "SkyManager Regelwerk");
    }

    /** Zeichen aller Embeds einer Nachricht (wie Discord sie zaehlt: Titel, Beschreibung, Footer). */
    static int chars(JSONObject msg) {
        int n = 0;
        JSONArray e = msg.optJSONArray("embeds");
        for (int i = 0; e != null && i < e.length(); i++) { JSONObject x = e.optJSONObject(i); n += x.optString("title").length() + x.optString("description").length() + (x.optJSONObject("footer") == null ? 0 : x.optJSONObject("footer").optString("text").length()); }
        return n;
    }
}

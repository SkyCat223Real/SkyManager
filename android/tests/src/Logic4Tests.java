package de.skycat223.screentime;

import java.util.*;
import org.json.*;

public class Logic4Tests {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok) { total++; System.out.println((ok ? "OK     " : "FEHLER ") + n); if (!ok) fails++; }

    public static void main(String[] args) throws Exception {
        long t0 = 1_800_000_000_000L, D = 86_400_000L;
        // ---- Team
        List<String> ranks = TeamMath.ranks(new JSONObject());
        check("Standard-Ränge (5, oben Projektleitung)", ranks.size() == 5 && ranks.get(0).equals("Projektleitung") && ranks.get(4).equals("Test-Supporter"));
        check("Ränge aus Text: leere und doppelte (Groß/Klein) fallen weg, Reihenfolge bleibt", TeamMath.parseRanks("Leitung\n\nAdmin\nadmin\n  Mod  ").equals(Arrays.asList("Leitung", "Admin", "Mod")));
        JSONObject m = TeamMath.member("m1", t0, "@MaxRP", "Supporter", "max#1");
        check("Mitglied: @ weg, Rang, Verlauf 'aufgenommen als Supporter'; ohne Namen keins", m.getString("name").equals("MaxRP") && m.getJSONArray("hist").getJSONObject(0).getString("text").equals("Ins Team aufgenommen als Supporter") && TeamMath.member("x", t0, " ", "", "") == null);
        check("Befördern: Supporter → Moderator mit Verlaufseintrag", "Moderator".equals(TeamMath.move(m, ranks, -1, t0 + D)) && m.getJSONArray("hist").getJSONObject(1).getString("text").equals("⬆️ Befördert: Supporter → Moderator"));
        TeamMath.move(m, ranks, -1, t0); TeamMath.move(m, ranks, -1, t0);
        check("Höchster Rang erreicht: weiter befördern geht nicht", m.getString("rank").equals("Projektleitung") && TeamMath.move(m, ranks, -1, t0) == null);
        JSONObject low = TeamMath.member("m2", t0, "Lea", "Test-Supporter", "");
        check("Niedrigster Rang: herabstufen geht nicht; unbekannter Rang: Befördern setzt den niedrigsten", TeamMath.move(low, ranks, 1, t0) == null && "Test-Supporter".equals(TeamMath.move(TeamMath.member("m3", t0, "Neu", "Gast", ""), ranks, -1, t0)));
        List<JSONObject> team = Arrays.asList(low, m, TeamMath.member("m4", t0, "anna", "Admin", ""), TeamMath.member("m5", t0, "Bob", "Admin", ""), TeamMath.member("m6", t0, "Ohne", "Gast", ""));
        List<JSONObject> s = TeamMath.sorted(team, ranks);
        check("Sortierung nach Rang, dann Name; unbekannter Rang ans Ende", s.get(0).getString("name").equals("MaxRP") && s.get(1).getString("name").equals("anna") && s.get(2).getString("name").equals("Bob") && s.get(4).getString("name").equals("Ohne"));
        check("Doppelter Name (Groß/Klein, @) erkannt", TeamMath.exists(team, "@ANNA") && !TeamMath.exists(team, "Zoe"));
        low.put("awayUntil", 20000L);
        check("Abwesenheit gilt bis einschließlich zum Datum", TeamMath.away(low, 20000) && !TeamMath.away(low, 20001) && !TeamMath.away(m, 20000));
        JSONObject dl = TeamMath.discordList(team, ranks, "Team GrenzRP", 20000, t0);
        JSONArray f = dl.getJSONArray("embeds").getJSONObject(0).getJSONArray("fields");
        check("Teamliste für Discord: ein Feld je belegtem Rang (Projektleitung, Admin 2, Test-Supporter 🌴, Ohne Rang), keine Pings", f.length() == 4 && f.getJSONObject(1).getString("name").equals("Admin (2)") && f.getJSONObject(1).getString("value").equals("anna\nBob") && f.getJSONObject(2).getString("value").equals("Lea 🌴") && f.getJSONObject(3).getString("name").equals("Ohne Rang (1)") && dl.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        check("Unterstrich im Namen wird für Discord entschärft", TeamMath.discordList(Arrays.asList(TeamMath.member("u", t0, "a_b", "Admin", "")), ranks, "", 0, t0).toString().contains("a\\\\_b"));
        check("Beförderung für Discord", TeamMath.discordPromotion(m, true, t0).getJSONArray("embeds").getJSONObject(0).getString("description").equals("**MaxRP** ist jetzt **Projektleitung**. Glückwunsch!"));
        check("Tage im Team", TeamMath.days(m, t0 + 10 * D + 3) == 10);

        // ---- Bussgeld
        List<JSONObject> cat = FineMath.template(1);
        check("Vorlage: 22 Einträge in 3 Kategorien (Verkehr, Ordnung, Straftaten)", cat.size() == 22 && FineMath.categories(cat).equals(Arrays.asList("Verkehr", "Ordnung", "Straftaten")));
        JSONObject sel = new JSONObject();
        sel.put(cat.get(0).getString("id"), 2);   // 2x 70
        sel.put(cat.get(6).getString("id"), 1);   // 1500, 5 HE, Fuehrerschein
        sel.put(cat.get(13).getString("id"), 1);  // 1000, 10 HE
        long[] t = FineMath.total(cat, sel, 0);
        check("Summe: 2×70 + 1.500 + 1.000 = 2.640 €, 15 HE, Führerschein weg, 4 Vergehen", t[0] == 2640 && t[1] == 15 && t[2] == 1 && t[3] == 4);
        check("Höchsthaft begrenzt die Haft (12 HE)", FineMath.total(cat, sel, 12)[1] == 12);
        check("Anzahl begrenzt auf 99, negative zählen nicht", FineMath.total(cat, new JSONObject().put(cat.get(0).getString("id"), 500), 0)[0] == 99 * 70 && FineMath.total(cat, new JSONObject().put(cat.get(0).getString("id"), -3), 0)[3] == 0);
        String sum = FineMath.summary(cat, sel, 0, "Max");
        check("Kurztext: Name, Zeilen mit Anzahl, Summe mit Haft und Führerschein", sum.startsWith("Strafe für Max:\n• 2× Geschwindigkeit innerorts bis 20 km/h zu schnell\n") && sum.endsWith("= 2.640 € · 15 Hafteinheiten · Führerscheinentzug") && FineMath.summary(cat, new JSONObject(), 0, "x").isEmpty());
        JSONObject fd = FineMath.discord(cat, sel, 0, "Max", "Officer Sky", t0);
        JSONArray ff = fd.getJSONArray("embeds").getJSONObject(0).getJSONArray("fields");
        check("Discord: Titel mit Täter, Bußgeld, Haft, Führerschein, Beamter; keine Pings", fd.getJSONArray("embeds").getJSONObject(0).getString("title").equals("⚖️ Strafe: Max") && ff.length() == 4 && ff.getJSONObject(0).getString("value").equals("2.640 €") && ff.getJSONObject(1).getString("value").equals("15 HE") && fd.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        check("Eintrag: ohne Namen oder mit negativem Betrag keiner; leere Kategorie = Sonstiges", FineMath.entry("e", "x", " ", 5, 0, false) == null && FineMath.entry("e", "x", "a", -1, 0, false) == null && FineMath.entry("e", "", "a", 5, 0, false).getString("cat").equals("Sonstiges"));

        // ---- Einsatzberichte
        List<JSONObject> reps = new ArrayList<JSONObject>();
        check("Erste Nummer des Jahres: E-2026-001", ReportMath.nextNumber(reps, 2026).equals("E-2026-001"));
        reps.add(ReportMath.report("r1", "E-2026-001", t0, "Brand", "Hafen", "LF 1", "", "Lagerhalle brennt", "Keine Maßnahmen", "", "Sky"));
        reps.add(ReportMath.report("r2", "E-2026-007", t0 + D, "Raub", "Bank", "Streife 12", "Max (Täter)", "Banküberfall mit Geiselnahme", "Festnahme", "= 15.000 € · 60 Hafteinheiten", "Sky"));
        reps.add(ReportMath.report("r3", "E-2025-040", t0 - 400 * D, "Quatsch", "", "", "", "Alter Bericht", "", "", ""));
        check("Nächste Nummer: höchste des Jahres + 1 (E-2026-008), anderes Jahr zählt eigens (E-2025-041)", ReportMath.nextNumber(reps, 2026).equals("E-2026-008") && ReportMath.nextNumber(reps, 2025).equals("E-2025-041"));
        check("Ohne Text kein Bericht; unbekannte Art wird 'Sonstiges'", ReportMath.report("x", "E", t0, "Brand", "", "", "", "  ", "", "", "") == null && reps.get(2).getString("type").equals("Sonstiges"));
        check("Suche (Ort, Beteiligte, Nr.) und Filter nach Art; neueste zuerst", ReportMath.search(reps, "", "bank").size() == 1 && ReportMath.search(reps, "", "max").size() == 1 && ReportMath.search(reps, "", "2026-001").size() == 1 && ReportMath.search(reps, "Brand", "").size() == 1 && ReportMath.search(reps, "", "").get(0).getString("id").equals("r2"));
        Map<String, Integer> st = ReportMath.stats(reps, t0 - D);
        check("Statistik ab Datum: nur Arten mit Einsätzen (Brand 1, Raub 1)", st.size() == 2 && st.get("Brand") == 1 && st.get("Raub") == 1);
        java.time.ZoneId z = java.time.ZoneId.of("Europe/Berlin");
        JSONObject rd = ReportMath.discord(reps.get(1), z);
        JSONObject emb = rd.getJSONArray("embeds").getJSONObject(0);
        check("Discord: Titel mit Nr. und Art, Text, Felder Zeit/Ort/Einheiten/Beteiligte/Maßnahme/Strafe/Autor, keine Pings", emb.getString("title").equals("💰 Einsatzbericht E-2026-007: Raub") && emb.getString("description").equals("Banküberfall mit Geiselnahme") && emb.getJSONArray("fields").length() == 7 && rd.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        String big = "x".repeat(2000);
        JSONObject rb = ReportMath.report("rb", "E-1", t0, "Brand", "", big, "", big + big, "", big, "");
        JSONObject bd = ReportMath.discord(rb, z).getJSONArray("embeds").getJSONObject(0);
        boolean lim = bd.getString("description").length() <= 4096; for (int i = 0; i < bd.getJSONArray("fields").length(); i++) if (bd.getJSONArray("fields").getJSONObject(i).getString("value").length() > 1024) lim = false;
        check("Lange Texte: Bericht gekürzt auf 3.000, Discord-Grenzen eingehalten (4.096 / 1.024)", rb.getString("text").length() == 3000 && lim);
        check("Text zum Teilen enthält Nummer, Art, Ort, Maßnahme, Strafe", ReportMath.text(reps.get(1), z).startsWith("Einsatzbericht E-2026-007 – Raub\n") && ReportMath.text(reps.get(1), z).contains("Maßnahme: Festnahme\n= 15.000 €"));
        System.out.println(fails == 0 ? "ALLE " + total + " TESTS BESTANDEN" : fails + " VON " + total + " FEHLGESCHLAGEN");
        System.exit(fails == 0 ? 0 : 1);
    }
}

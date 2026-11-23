package de.skycat223.screentime;

import java.nio.file.*;
import java.util.*;
import org.json.*;

public class Logic3Tests {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok) { total++; System.out.println((ok ? "OK     " : "FEHLER ") + n); if (!ok) fails++; }
    static String f(String n) throws Exception { return new String(Files.readAllBytes(Paths.get("tests/fixtures/" + n)), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        // ---- Spieler-Check
        check("Profil-Links: /users/ID/profile, mit /de/, web., ohne https; Gruppen- oder Spiel-Link nicht", RbxPlayerMath.linkId("https://www.roblox.com/users/156/profile") == 156 && RbxPlayerMath.linkId("roblox.com/de/users/1/profile") == 1 && RbxPlayerMath.linkId("https://web.roblox.com/users/77") == 77 && RbxPlayerMath.linkId("https://www.roblox.com/communities/7") == 0 && RbxPlayerMath.linkId("https://evil.de/roblox.com/users/5") == 0);
        check("Benutzernamen: @ erlaubt, 3-20 Zeichen, höchstens ein _ und nicht am Rand", "SkyCat223".equals(RbxPlayerMath.username("@SkyCat223")) && "a_b".equals(RbxPlayerMath.username("a_b")) && RbxPlayerMath.username("ab") == null && RbxPlayerMath.username("_abc") == null && RbxPlayerMath.username("a_b_c") == null && RbxPlayerMath.username("mit leer") == null && RbxPlayerMath.username("x".repeat(21)) == null);
        check("Echte Namenssuche: 'Roblox' = ID 1, unbekannter Name fehlt in der Antwort; Anfrage-Text korrekt", RbxPlayerMath.idFromLookup(f("rbx_names.json")) == 1 && RbxPlayerMath.idFromLookup("{\"data\":[]}") == 0 && new JSONObject(RbxPlayerMath.lookupBody("x")).getJSONArray("usernames").getString(0).equals("x"));
        JSONObject u = RbxPlayerMath.parseUser(f("rbx_user.json"));
        check("Echtes Profil (ID 1): Name, Anzeigename, Erstellt 2006, nicht gesperrt, verifiziert", u.optLong("id") == 1 && u.optString("name").equals("Roblox") && java.time.Instant.ofEpochMilli(u.optLong("created")).toString().startsWith("2006-02-27") && !u.optBoolean("banned") && u.optBoolean("verified"));
        JSONArray roles = RbxPlayerMath.parseRoles(f("rbx_roles.json"));
        check("Echte Gruppen des Spielers: alle 3 mit Name, Rolle, Rang", roles.length() == 3 && roles.getJSONObject(2).optInt("rank") == 1 && roles.getJSONObject(0).optString("name").equals("Roblox Wiki") && roles.getJSONObject(0).optString("role").equals("Wiki System Operator") && roles.getJSONObject(0).optInt("rank") == 254);
        List<JSONObject> mine = RbxPlayerMath.inMyGroups(roles, Arrays.asList(new JSONObject().put("gid", 127081), new JSONObject().put("gid", 999)));
        check("In meinen beobachteten Gruppen: genau die passende mit Rang", mine.size() == 1 && mine.get(0).optString("role").equals("Wiki System Operator"));
        check("Zähler aus Antworten ({count}), kaputt = -1", RbxPlayerMath.count("{\"count\":16963696}") == 16963696 && RbxPlayerMath.count("x") == -1);
        List<String> w1 = new ArrayList<String>(), w2 = new ArrayList<String>(), w3 = new ArrayList<String>(), w4 = new ArrayList<String>();
        check("Einschätzung: 2 Tage alt, 0 Freunde, keine Gruppe = Zweitkonto-Verdacht mit 3 Gründen", RbxPlayerMath.risk(2, 0, 0, w1) == RbxPlayerMath.ALT && w1.size() == 3 && w1.get(0).equals("Konto erst 2 Tage alt"));
        check("Einschätzung: 20 Tage, 10 Freunde, 2 Gruppen = genauer prüfen", RbxPlayerMath.risk(20, 10, 2, w2) == RbxPlayerMath.CHECK);
        check("Einschätzung: 3 Jahre, 50 Freunde, Gruppen = unauffällig ohne Gründe; Freunde unbekannt (-1) zählt nicht", RbxPlayerMath.risk(1100, 50, 4, w3) == RbxPlayerMath.OK && w3.isEmpty() && RbxPlayerMath.risk(1100, -1, 1, w4) == RbxPlayerMath.OK);
        List<String> w5 = new ArrayList<String>(), w6 = new ArrayList<String>();
        check("Alte Konten ohne Freunde/Gruppen (z. B. offizielle Roblox-Konten, 20 Jahre) sind unauffällig; 200 Tage alt ohne Freunde wird geprüft", RbxPlayerMath.risk(7500, 0, 0, w5) == RbxPlayerMath.OK && w5.isEmpty() && RbxPlayerMath.risk(200, 0, 3, w6) == RbxPlayerMath.CHECK && w6.equals(Arrays.asList("keine Freunde")));
        long now = 2_000_000_000_000L;
        check("Kontoalter-Text: Tage, Monate, Jahre", RbxPlayerMath.ageText(1).equals("1 Tag") && RbxPlayerMath.ageText(45).equals("45 Tage") && RbxPlayerMath.ageText(200).equals("6 Monate") && RbxPlayerMath.ageText(1100).equals("3,0 Jahre") && RbxPlayerMath.ageDays(now - 3 * 86400000L - 5, now) == 3);

        // ---- Giveaways
        List<String> pl = GiveawayMath.parseList("@Max\nlena, Tom; max\n- Jonas\n• Mia\n\n  ");
        check("Teilnehmer aus eingefügtem Text: Zeilen, Komma, Semikolon, Aufzählungszeichen, @ weg, Doppelte (Groß/Klein) einmal", pl.equals(Arrays.asList("Max", "lena", "Tom", "Jonas", "Mia")));
        JSONArray people = new JSONArray(Arrays.asList("Max", "Lena"));
        check("Zusammenführen: nur neue kommen dazu", GiveawayMath.merge(people, Arrays.asList("LENA", "Tom", "Jonas")) == 2 && people.length() == 4);
        Random r = new Random(42);
        List<String> pool = Arrays.asList("A", "B", "C", "D", "E");
        List<String> w = GiveawayMath.draw(pool, Arrays.asList("c"), 3, r);
        check("Ziehung: 3 verschiedene Gewinner, Ausgeschlossene (Groß/Klein egal) nie dabei", w.size() == 3 && new HashSet<String>(w).size() == 3 && !w.contains("C"));
        check("Mehr Gewinner gewünscht als Teilnehmer: alle gewinnen, keiner doppelt; 0 Teilnehmer: keine", GiveawayMath.draw(Arrays.asList("A", "B"), new ArrayList<String>(), 5, r).size() == 2 && GiveawayMath.draw(new ArrayList<String>(), new ArrayList<String>(), 3, r).isEmpty());
        int[] hits = new int[5]; Random fr = new Random(7);
        for (int i = 0; i < 100000; i++) for (String s : GiveawayMath.draw(pool, new ArrayList<String>(), 2, fr)) hits[s.charAt(0) - 'A']++;
        boolean fair = true; for (int h : hits) if (Math.abs(h - 40000) > 1200) fair = false;
        check("Fairness: 100.000 Ziehungen (2 aus 5) – jeder gewinnt ~40.000 Mal (±3 %): " + Arrays.toString(hits), fair);
        JSONObject g = new JSONObject().put("prize", "1.000 Robux").put("count", 2).put("end", now + 86400000L).put("people", new JSONArray(Arrays.asList("A", "B", "C"))).put("winners", new JSONArray());
        JSONObject ann = GiveawayMath.discord(g, false, now);
        check("Discord-Ankündigung: Titel, Gewinnerzahl, Ende als Discord-Zeitstempel, pingt niemanden an", ann.getJSONArray("embeds").getJSONObject(0).getString("title").equals("🎁 Giveaway: 1.000 Robux") && ann.toString().contains("<t:" + (now + 86400000L) / 1000 + ":R>") && ann.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        g.put("winners", new JSONArray(Arrays.asList("B*ob", "C")));
        JSONObject win = GiveawayMath.discord(g, true, now);
        check("Discord-Gewinner: Medaillen, fett, * im Namen entschärft, Teilnehmerzahl", win.getJSONArray("embeds").getJSONObject(0).getString("description").equals("🥇 **Bob**\n🥈 **C**") && win.toString().contains("\"value\":\"3\""));

        // ---- Regelwerk
        JSONObject rs = RulesMath.ruleset("r1", "GrenzRP", now);
        check("Regel hinzufügen: Nummer am Anfang ('3.', '§ 4)') wird entfernt; leer abgelehnt; Version steigt", RulesMath.add(rs, "3. Kein RDM", now) == null && RulesMath.add(rs, "§ 4) Kein VDM", now) == null && RulesMath.add(rs, "  ", now) != null && RulesMath.rules(rs).equals(Arrays.asList("Kein RDM", "Kein VDM")) && rs.optInt("version") == 3);
        check("Mehrere Zeilen auf einmal (leere übersprungen)", RulesMath.addMany(rs, "Regel A\n\n2. Regel B\n", now) == 2 && RulesMath.rules(rs).size() == 4);
        check("Verschieben, Bearbeiten, Löschen; Grenzen werden geprüft", RulesMath.move(rs, 1, -1, now) && RulesMath.rules(rs).get(0).equals("Kein VDM") && !RulesMath.move(rs, 0, -1, now) && RulesMath.edit(rs, 0, "Kein VDM (Fahrzeuge)", now) && !RulesMath.edit(rs, 0, "Kein VDM (Fahrzeuge)", now) && RulesMath.remove(rs, 3, now) && !RulesMath.remove(rs, 9, now) && RulesMath.rules(rs).size() == 3);
        check("Vorlagen: Discord 7 Regeln, RP 9 Regeln (RDM, VDM, Metagaming, Powergaming, Combat-Logging)", RulesMath.TEMPLATES[0].length == 7 && RulesMath.TEMPLATES[1].length == 9 && String.join(" ", RulesMath.TEMPLATES[1]).contains("RDM") && String.join(" ", RulesMath.TEMPLATES[1]).contains("Combat-Logging"));
        java.time.ZoneId z = java.time.ZoneId.of("Europe/Berlin");
        List<JSONObject> m1 = RulesMath.discord(rs, 0x3DDC84, z);
        check("Discord kurz: 1 Nachricht, Titel, nummeriert '**§1**', Stand und Version im Footer, keine Pings", m1.size() == 1 && m1.get(0).getJSONArray("embeds").getJSONObject(0).getString("title").equals("📜 GrenzRP") && m1.get(0).getJSONArray("embeds").getJSONObject(0).getString("description").startsWith("**§1** Kein VDM (Fahrzeuge)") && m1.get(0).getJSONArray("embeds").getJSONObject(0).getJSONObject("footer").getString("text").contains("Version " + rs.optInt("version")) && m1.get(0).getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        JSONObject big = RulesMath.ruleset("r2", "Groß", now);
        StringBuilder sb = new StringBuilder(); for (int i = 0; i < 100; i++) sb.append("Regel ").append(i + 1).append(": ").append("x".repeat(400)).append(" @everyone\n");
        RulesMath.addMany(big, sb.toString(), now);
        List<JSONObject> mb = RulesMath.discord(big, 0x3DDC84, z);
        boolean ok = true; int count = 0; String all = "";
        for (JSONObject m : mb) { if (RulesMath.chars(m) > 6000 || m.getJSONArray("embeds").length() > 10) ok = false; for (int i = 0; i < m.getJSONArray("embeds").length(); i++) { String d = m.getJSONArray("embeds").getJSONObject(i).getString("description"); if (d.length() > 4096) ok = false; all += d + "\n"; } }
        for (int i = 1; i <= 100; i++) if (all.contains("**§" + i + "** ")) count++;
        check("Discord lang (100 Regeln à 400 Zeichen): " + mb.size() + " Nachrichten, alle unter Discords Grenzen (6.000 je Nachricht, 4.096 je Embed, max. 10 Embeds), alle 100 Regeln drin, @everyone entschärft", ok && count == 100 && mb.size() > 1 && !all.contains("@everyone") && mb.get(0).getJSONArray("embeds").getJSONObject(0).has("title") && mb.get(mb.size() - 1).getJSONArray("embeds").getJSONObject(mb.get(mb.size() - 1).getJSONArray("embeds").length() - 1).has("footer"));
        check("Höchstens 100 Regeln", RulesMath.add(big, "eine zu viel", now) != null);
        check("Als Text teilen: Titel, §-Nummern, Stand", RulesMath.text(rs, z).startsWith("📜 GrenzRP\n\n§1 Kein VDM (Fahrzeuge)") && RulesMath.text(rs, z).contains("Stand: "));
        System.out.println(fails == 0 ? "ALLE " + total + " TESTS BESTANDEN" : fails + " VON " + total + " FEHLGESCHLAGEN");
        System.exit(fails == 0 ? 0 : 1);
    }
}

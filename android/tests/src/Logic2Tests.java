package de.skycat223.screentime;

import java.nio.file.*;
import java.util.*;
import org.json.*;

public class Logic2Tests {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok) { total++; System.out.println((ok ? "OK     " : "FEHLER ") + n); if (!ok) fails++; }
    static String f(String n) throws Exception { return new String(Files.readAllBytes(Paths.get("tests/fixtures/" + n)), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        // ---- Roblox-Gruppen
        check("Gruppen-ID aus Links: /communities/, /groups/, mit Sprachpfad /de/, web.roblox.com, mit Namen und ?-Zusatz, reine Zahl",
            RbxGroupMath.groupId("https://www.roblox.com/communities/1234567/Emergency-Emden#!/about") == 1234567 && RbxGroupMath.groupId("roblox.com/groups/7/Roblox") == 7 && RbxGroupMath.groupId("https://www.roblox.com/de/communities/99?x=1") == 99 && RbxGroupMath.groupId("https://web.roblox.com/groups/5") == 5 && RbxGroupMath.groupId(" 32808 ") == 32808);
        check("Keine Gruppe: Spiel-Link, fremde Seite, Text, 0, leer", RbxGroupMath.groupId("https://www.roblox.com/games/123/x") == -1 && RbxGroupMath.groupId("https://evil.de/roblox.com/groups/5") == -1 && RbxGroupMath.groupId("abc") == -1 && RbxGroupMath.groupId("0") == -1 && RbxGroupMath.groupId(null) == -1);
        JSONObject g = RbxGroupMath.parse(f("group_ok.json"));
        check("Echte Roblox-Antwort (Gruppe 7): Name, Mitglieder > 13 Mio., Besitzer 'Games', offen, ohne Ruf", g != null && g.optString("name").equals("Roblox") && g.optLong("members") > 13_000_000 && g.optString("owner").equals("Games") && g.optBoolean("public") && !g.has("shout"));
        check("Echte Fehlerantwort (Gruppe gibt es nicht): keine Auswertung, verständliche Meldung", RbxGroupMath.parse(f("group_bad.json")) == null && RbxGroupMath.error(400).contains("gibt es nicht"));
        JSONObject sh = RbxGroupMath.parse("{\"name\":\"EE RP\",\"memberCount\":5000,\"owner\":null,\"hasVerifiedBadge\":true,\"shout\":{\"body\":\"RP-Abend heute 20 Uhr!\",\"poster\":{\"username\":\"SkyCat223\"},\"updated\":\"2026-11-16T18:30:00.123Z\"}}");
        check("Gruppen-Ruf: Text, Absender, Zeitpunkt; Gruppe ohne Besitzer; verifiziert", sh.getJSONObject("shout").getString("text").equals("RP-Abend heute 20 Uhr!") && sh.getJSONObject("shout").getString("by").equals("SkyCat223") && sh.getJSONObject("shout").getLong("at") == java.time.Instant.parse("2026-11-16T18:30:00.123Z").toEpochMilli() && sh.optString("owner").isEmpty() && sh.optBoolean("verified"));
        check("Leerer Ruf zählt nicht als Ruf", !RbxGroupMath.parse("{\"name\":\"x\",\"memberCount\":1,\"shout\":{\"body\":\"  \"}}").has("shout"));
        check("Gruppen-Symbol aus der echten Thumbnail-Antwort (nur https, nur fertig)", RbxGroupMath.iconFrom(f("group_icon.json")).startsWith("https://tr.rbxcdn.com/") && RbxGroupMath.iconFrom("{\"data\":[{\"state\":\"Pending\",\"imageUrl\":\"https://x\"}]}") == null && RbxGroupMath.iconFrom("kaputt") == null);
        long now = 2_000_000_000_000L;
        check("Zeitangaben: vor 5 Min, vor 3 Std, vor 1 Tag, vor 4 Tagen", RbxGroupMath.ago(now - 5 * 60000, now).equals("vor 5 Min") && RbxGroupMath.ago(now - 3 * 3600000L, now).equals("vor 3 Std") && RbxGroupMath.ago(now - 26 * 3600000L, now).equals("vor 1 Tag") && RbxGroupMath.ago(now - 4 * 86400000L, now).equals("vor 4 Tagen") && RbxGroupMath.ago(0, now).isEmpty());
        check("Neuer Ruf wird erkannt (anderer Fingerabdruck), gleicher nicht", !RbxGroupMath.shoutKey(sh.getJSONObject("shout")).equals(RbxGroupMath.shoutKey(new JSONObject().put("text", "anders").put("at", 5))) && RbxGroupMath.shoutKey(sh.getJSONObject("shout")).equals(RbxGroupMath.shoutKey(new JSONObject(sh.getJSONObject("shout").toString()))));

        // ---- Bewerbungen
        long t0 = 1_700_000_000_000L, D = 86_400_000L;
        List<JSONObject> l = new ArrayList<JSONObject>();
        JSONObject a1 = ApplyMath.entry("a1", t0, "@MaxRP", "Polizei", "max#1", "Erfahrung bei EZRP"), a2 = ApplyMath.entry("a2", t0 + D, "Lena", "Moderator", "", ""), a3 = ApplyMath.entry("a3", t0 + 2 * D, "Tom", "Polizei", "", "");
        l.add(a1); l.add(a2); l.add(a3);
        check("Bewerbung: @ weg, Status neu mit Verlauf; ohne Namen keine", a1.getString("name").equals("MaxRP") && a1.getString("status").equals("neu") && a1.getJSONArray("hist").length() == 1 && ApplyMath.entry("x", t0, " ", "a", "", "") == null);
        check("Status ändern: mit Verlauf; gleicher oder unbekannter Status wird abgelehnt", ApplyMath.setStatus(a2, "gespraech", t0 + 3 * D) && a2.getJSONArray("hist").length() == 2 && !ApplyMath.setStatus(a2, "gespraech", t0 + 4 * D) && !ApplyMath.setStatus(a2, "quatsch", t0));
        check("Doppelte offene Bewerbung (gleiche Person + Rolle, Groß/Klein egal) erkannt; andere Rolle nicht", ApplyMath.duplicate(l, "maxrp", "polizei") && !ApplyMath.duplicate(l, "MaxRP", "Moderator"));
        ApplyMath.setStatus(a3, "abgelehnt", t0 + 5 * D);
        check("Nach Entscheidung keine Doppel-Warnung mehr", !ApplyMath.duplicate(l, "Tom", "Polizei"));
        List<JSONObject> v = ApplyMath.view(l, "", "");
        check("Ansicht: offene zuerst (wer am längsten wartet oben: MaxRP, Lena), dann erledigte", v.get(0).optString("id").equals("a1") && v.get(1).optString("id").equals("a2") && v.get(2).optString("id").equals("a3"));
        check("Filter und Suche (Rolle, Notizen)", ApplyMath.view(l, "abgelehnt", "").size() == 1 && ApplyMath.view(l, "", "polizei").size() == 2 && ApplyMath.view(l, "", "ezrp").size() == 1);
        Map<String, Integer> c = ApplyMath.counts(l);
        check("Zählung je Status", c.get("neu") == 1 && c.get("gespraech") == 1 && c.get("abgelehnt") == 1 && c.get("angenommen") == 0);
        check("Wartezeit in Tagen", ApplyMath.daysWaiting(a1, t0 + 10 * D + 5) == 10);
        ApplyMath.setStatus(a1, "angenommen", t0 + 6 * D);
        JSONObject m = ApplyMath.discord(a1, "Willkommen im Team!", t0);
        JSONObject em = m.getJSONArray("embeds").getJSONObject(0);
        check("Discord: grünes 'angenommen' mit Bewerber, Rolle, Nachricht; pingt niemanden an", em.getString("title").equals("✅ Bewerbung angenommen") && em.getInt("color") == 0x57F287 && em.getJSONArray("fields").length() == 3 && em.getJSONArray("fields").getJSONObject(2).getString("value").equals("Willkommen im Team!") && m.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        check("Discord ohne Nachricht und Rolle: nur Bewerber-Feld", ApplyMath.discord(ApplyMath.entry("z", t0, "Q", "", "", ""), "", t0).getJSONArray("embeds").getJSONObject(0).getJSONArray("fields").length() == 1);

        // ---- Event-Planer
        long n0 = 2_000_000_000_000L, H = 3_600_000L;
        check("Countdown: Minuten, Stunden mit Minuten, Tage mit Stunden, läuft, vorbei", EventMath.countdown(n0 + 45 * 60000, n0).equals("in 45 Min") && EventMath.countdown(n0 + 2 * H + 15 * 60000, n0).equals("in 2 Std 15 Min") && EventMath.countdown(n0 + 50 * H, n0).equals("in 2 Tagen 2 Std") && EventMath.countdown(n0 + 24 * H, n0).equals("in 1 Tag") && EventMath.countdown(n0 - H, n0).equals("🔴 läuft gerade") && EventMath.countdown(n0 - 4 * H, n0).equals("vorbei"));
        JSONObject e = EventMath.event("ev1", "RP-Abend", n0 + 48 * H, "GrenzRP", "Großeinsatz", 2);
        check("Event: ohne Titel oder Zeit keins; Max begrenzt auf 200", e != null && EventMath.event("x", " ", n0, "", "", 0) == null && EventMath.event("x", "a", 0, "", "", 0) == null && EventMath.event("x", "a", n0, "", "", 999).optInt("max") == 200);
        check("Zusagen: doppelt (Groß/Klein, @) abgelehnt, voll bei Max 2, Austragen", EventMath.join(e, "Max") == null && EventMath.join(e, "@max") != null && EventMath.join(e, "Lena") == null && EventMath.join(e, "Tom").contains("voll") && EventMath.leave(e, "MAX") && EventMath.people(e).equals(Arrays.asList("Lena")) && EventMath.join(e, "") != null);
        List<JSONObject> evs = Arrays.asList(EventMath.event("p", "Alt", n0 - 10 * H, "", "", 0), EventMath.event("b", "Später", n0 + 72 * H, "", "", 0), EventMath.event("a", "Bald", n0 + H, "", "", 0), EventMath.event("r", "Läuft", n0 - H, "", "", 0));
        List<JSONObject> s = EventMath.sorted(evs, n0);
        check("Sortierung: laufend/kommend nach Zeit (Läuft, Bald, Später), Vergangene ans Ende", s.get(0).optString("id").equals("r") && s.get(1).optString("id").equals("a") && s.get(2).optString("id").equals("b") && s.get(3).optString("id").equals("p"));
        JSONObject dr = EventMath.draft(e, "announce");
        long unix = (n0 + 48 * H) / 1000;
        check("Ankündigung: Titel, Beschreibung, Discord-Zeitstempel (eigene Zeitzone + Countdown), Ort, Teilnehmer 1/2", dr.getString("title").equals("🎉 RP-Abend") && dr.getString("description").equals("Großeinsatz") && dr.getJSONArray("fields").getJSONObject(0).getString("value").equals("<t:" + unix + ":F> (<t:" + unix + ":R>)") && dr.getJSONArray("fields").getJSONObject(1).getString("value").equals("GrenzRP") && dr.getJSONArray("fields").getJSONObject(2).getString("value").equals("1 / 2"));
        List<String> errs = new ArrayList<String>();
        JSONObject built = DiscordMath.build(EventMath.draft(e, "remind"), errs, n0);
        check("Erinnerung läuft fehlerfrei durch den ECHTEN Prüfer des Webhook-Studios (gelbes Embed, Footer, 3 Felder)", errs.isEmpty() && built.getJSONArray("embeds").getJSONObject(0).getString("title").equals("⏰ Gleich geht's los: RP-Abend") && built.getJSONArray("embeds").getJSONObject(0).getInt("color") == 0xFEE75C && built.getJSONArray("embeds").getJSONObject(0).getJSONObject("footer").getString("text").equals("SkyManager Event-Planer") && built.getJSONArray("embeds").getJSONObject(0).getJSONArray("fields").length() == 3);
        JSONObject r = EventMath.reminder(e, "h1", "#events", "s1", n0);
        check("Geplante Erinnerung: 1 Std vor Beginn, im Format der geplanten Nachrichten (geplant, Webhook, Entwurf, Event-Bezug)", r.getLong("at") == n0 + 47 * H && r.getString("status").equals(SchedMath.PLANNED) && r.getString("hookId").equals("h1") && r.getString("evId").equals("ev1") && r.getJSONObject("draft").getString("title").startsWith("⏰"));
        check("Erinnerung zu spät (Event in 30 Min) wird nicht geplant", EventMath.reminder(EventMath.event("x", "Gleich", n0 + 30 * 60000, "", "", 0), "h1", "", "s", n0) == null);
        check("Der echte Hintergrund-Versand erkennt sie: wartet bis 1 Std vorher, dann fällig", SchedMath.state(r, n0) == SchedMath.WAIT && SchedMath.state(r, n0 + 47 * H) == SchedMath.DUE && SchedMath.nextAt(Arrays.asList(r), n0) == n0 + 47 * H);
        List<JSONObject> sch = new ArrayList<JSONObject>(Arrays.asList(r, new JSONObject().put("evId", "ev1").put("status", "gesendet"), new JSONObject().put("evId", "anderes").put("status", SchedMath.PLANNED)));
        check("Event löschen: nur dessen noch geplante Erinnerung fällt weg (gesendete und fremde bleiben)", EventMath.reminderPlanned(sch, "ev1") && EventMath.dropReminders(sch, "ev1") == 1 && sch.size() == 2 && !EventMath.reminderPlanned(sch, "ev1"));
        System.out.println(fails == 0 ? "ALLE " + total + " TESTS BESTANDEN" : fails + " VON " + total + " FEHLGESCHLAGEN");
        System.exit(fails == 0 ? 0 : 1);
    }
}

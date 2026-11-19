package de.skycat223.screentime;

import java.nio.file.*;
import java.util.*;
import org.json.*;

public class LogicTests {
    static int fails = 0, total = 0;
    static void check(String n, boolean ok) { total++; System.out.println((ok ? "OK     " : "FEHLER ") + n); if (!ok) fails++; }

    public static void main(String[] args) throws Exception {
        // ---- Discord-Tracker
        check("Einladung aus Link: discord.gg, discord.com/invite, discordapp.com, mit https/www/?event, nur Code",
            "abc123".equals(DcTrackMath.inviteCode("https://discord.gg/abc123")) && "Ab-C9".equals(DcTrackMath.inviteCode("discord.com/invite/Ab-C9/")) && "xyz".equals(DcTrackMath.inviteCode("https://www.discordapp.com/invite/xyz?event=1")) && "grenzrp".equals(DcTrackMath.inviteCode("  grenzrp ")));
        check("Keine Einladung: fremde Seite, Leerzeichen, leer, zu kurz", DcTrackMath.inviteCode("https://evil.de/discord.gg/abc") == null && DcTrackMath.inviteCode("ab cd") == null && DcTrackMath.inviteCode("") == null && DcTrackMath.inviteCode("a") == null && DcTrackMath.inviteCode(null) == null);
        check("Abfrage-Adresse mit Zählern", DcTrackMath.apiUrl("abc").equals("https://discord.com/api/v10/invites/abc?with_counts=true"));
        JSONObject o = DcTrackMath.parse(new String(Files.readAllBytes(Paths.get("tests/fixtures/invite_ok.json")), "UTF-8"));
        check("Echte Discord-Antwort: Name, Server-ID, Mitglieder 311.618, online 86.775, 56 Boosts, kein Ablauf", o != null && o.optString("name").equals("Discord Developers") && o.optString("gid").equals("613425648685547541") && o.optLong("members") == 311618 && o.optLong("online") == 86775 && o.optLong("boosts") == 56 && !o.optBoolean("expires"));
        check("Ungültige Einladung (echte 404-Antwort): keine Auswertung, verständliche Meldung", DcTrackMath.parse(new String(Files.readAllBytes(Paths.get("tests/fixtures/invite_bad.json")), "UTF-8")) == null && DcTrackMath.error(404, "").contains("ungültig") && DcTrackMath.error(429, "").contains("bremst") && DcTrackMath.parse("kaputt") == null);
        check("Ablaufende Einladung wird erkannt", DcTrackMath.parse("{\"expires_at\":\"2026-11-20T10:00:00+00:00\",\"approximate_member_count\":5,\"approximate_presence_count\":1,\"guild\":{\"id\":\"1\",\"name\":\"X\",\"icon\":null}}").optBoolean("expires"));
        check("Symbol-Adresse (auch animiertes a_-Symbol als PNG), ohne Symbol null", "https://cdn.discordapp.com/icons/1/a_x.png?size=128".equals(DcTrackMath.iconUrl("1", "a_x")) && DcTrackMath.iconUrl("1", "") == null);
        JSONArray h = new JSONArray(); long t = 1_000_000_000_000L, H = 3_600_000L;
        boolean r1 = DcTrackMath.record(h, t, 100, 10), r2 = DcTrackMath.record(h, t + 5 * 60000, 100, 10), r3 = DcTrackMath.record(h, t + 6 * 60000, 101, 10), r4 = DcTrackMath.record(h, t + 36 * 60000, 101, 10), r5 = DcTrackMath.record(h, t + 36 * 60000 + 30000, 105, 9);
        check("Verlauf: erster Punkt ja; gleiche Werte nach 5 Min nein; Änderung nach 6 Min ja; 30 Min nach dem letzten Punkt ja; Änderung schon 30 s später nein", r1 && !r2 && r3 && r4 && !r5 && h.length() == 3);
        JSONArray g = new JSONArray();
        for (int i = 0; i <= 8 * 24; i++) DcTrackMath.record(g, t + i * H, 1000 + i * 2, 50);
        long now = t + 8 * 24 * H;
        check("Wachstum: 24 h = +48, 7 Tage = +336; ohne Verlauf unbekannt (–)", DcTrackMath.growth(g, now, 24 * H) == 48 && DcTrackMath.growth(g, now, 7 * 24 * H) == 336 && DcTrackMath.growth(new JSONArray(), now, H) == Long.MIN_VALUE && DcTrackMath.signed(Long.MIN_VALUE).equals("–") && DcTrackMath.signed(1234).equals("+1.234") && DcTrackMath.signed(-5).equals("-5"));
        JSONArray big = new JSONArray(); for (int i = 0; i < 400; i++) DcTrackMath.record(big, t + i * H, i, 1);
        check("Verlauf begrenzt auf 300 Punkte (älteste fallen raus)", big.length() == 300 && big.optJSONObject(0).optLong("m") == 100);
        check("Online-Anteil und Spitzenwert", DcTrackMath.onlinePercent(311618, 86775) == 28 && DcTrackMath.onlinePercent(0, 5) == 0 && DcTrackMath.peakOnline(new JSONArray().put(new JSONObject().put("o", 3)).put(new JSONObject().put("o", 9))) == 9);
        check("Doppelte Einladung erkannt (Groß/Klein egal)", DcTrackMath.known(Arrays.asList(new JSONObject().put("code", "GrenzRP")), "grenzrp") && !DcTrackMath.known(new ArrayList<JSONObject>(), "x"));

        // ---- Mod-Log
        List<JSONObject> log = new ArrayList<JSONObject>();
        long d = 1_700_000_000_000L;
        log.add(ModLogMath.entry("1", d, "@Raider123", "warn", "Spam", "Sky", "GrenzRP"));
        log.add(ModLogMath.entry("2", d + 1, "raider123", "warn", "Beleidigung", "Sky", ""));
        check("Eintrag: @ weg, Name gekürzt; ohne Namen oder mit unbekannter Aktion kein Eintrag", log.get(0).optString("user").equals("Raider123") && ModLogMath.entry("x", d, "  ", "warn", "", "", "") == null && ModLogMath.entry("x", d, "a", "hack", "", "", "") == null && ModLogMath.entry("x", d, "y".repeat(80), "note", "", "", "").optString("user").length() == 40);
        check("2 Verwarnungen (Groß/Klein und @ egal): noch kein Hinweis", ModLogMath.activeWarns(log, "RAIDER123") == 2 && ModLogMath.hint(2) == null);
        log.add(ModLogMath.entry("3", d + 2, "Raider123", "warn", "Wieder Spam", "Max", ""));
        check("3. Verwarnung: Hinweis auf Kick/Mute", ModLogMath.activeWarns(log, "raider123") == 3 && ModLogMath.hint(3).contains("3. Verwarnung"));
        log.add(ModLogMath.entry("4", d + 3, "Raider123", "ban", "Zu viele Verwarnungen", "Sky", ""));
        check("Ban: Nutzer gilt als gebannt", ModLogMath.banned(log, "raider123") && !ModLogMath.banned(log, "jemand"));
        log.add(ModLogMath.entry("5", d + 4, "Raider123", "unban", "Zweite Chance", "Sky", ""));
        check("Entbannt: nicht mehr gebannt, Verwarnungen zurück auf 0", !ModLogMath.banned(log, "raider123") && ModLogMath.activeWarns(log, "raider123") == 0);
        check("Hinweis ab 5 Verwarnungen: Ban vorschlagen", ModLogMath.hint(5).contains("Ban"));
        log.add(ModLogMath.entry("6", d + 5, "Gast", "note", "Fragt nach Rang", "Max", "SkyCommunity"));
        check("Suche: nach Grund, Moderator, Server; Filter nach Aktion; neueste zuerst", ModLogMath.search(log, "spam", null).size() == 2 && ModLogMath.search(log, "max", null).size() == 2 && ModLogMath.search(log, "skycommunity", null).size() == 1 && ModLogMath.search(log, "", "warn").size() == 3 && ModLogMath.search(log, "", null).get(0).optString("id").equals("6") && ModLogMath.search(log, "@raider", "ban").size() == 1);
        Map<String, Integer> c = ModLogMath.counts(log);
        check("Zählung je Aktion", c.get("warn") == 3 && c.get("ban") == 1 && c.get("unban") == 1 && c.get("note") == 1 && c.get("kick") == 0);
        JSONObject msg = ModLogMath.discord(log.get(3), 3, d);
        JSONObject emb = msg.getJSONArray("embeds").getJSONObject(0);
        check("Discord-Nachricht: rotes Ban-Embed mit Grund, Moderator, Verwarnungen; pingt niemanden an", emb.getString("title").equals("🔨 Ban: Raider123") && emb.getInt("color") == 0xED4245 && emb.getJSONArray("fields").length() == 3 && emb.getJSONArray("fields").getJSONObject(0).getString("value").equals("Zu viele Verwarnungen") && msg.getJSONObject("allowed_mentions").getJSONArray("parse").length() == 0);
        check("Leerer Grund wird zu „–“ (Discord lehnt leere Felder ab)", ModLogMath.discord(ModLogMath.entry("z", d, "a", "kick", "", "", ""), 0, d).getJSONArray("embeds").getJSONObject(0).getJSONArray("fields").getJSONObject(0).getString("value").equals("–"));
        String txt = ModLogMath.shareText(log, 3, java.time.ZoneId.of("Europe/Berlin"));
        check("Teilen-Text: neueste zuerst, höchstens 3, lesbar", txt.startsWith("Mod-Log (SkyManager)") && txt.split("\n").length == 5 && txt.contains("Notiz: Gast – Fragt nach Rang (Max)"));

        // ---- Widget
        List<String[]> ls = Arrays.asList(new String[]{"1", "Mathe"}, new String[]{"3", "Deutsch"});
        check("Widget-Stunde: vor Schulbeginn die nächste, während der Stunde 'Jetzt', in der Pause die nächste, danach geschafft, frei = nichts",
            "⏭ 07:45: Mathe".equals(WidgetMath.lessonLine(ls, 400)) && "▶ Jetzt: 1. Mathe (bis 08:30)".equals(WidgetMath.lessonLine(ls, 470)) && "⏭ 09:40: Deutsch".equals(WidgetMath.lessonLine(ls, 520)) && "✅ Schule für heute geschafft".equals(WidgetMath.lessonLine(ls, 700)) && WidgetMath.lessonLine(new ArrayList<String[]>(), 500) == null);
        List<List<JSONObject>> hw = new ArrayList<List<JSONObject>>();
        hw.add(Arrays.asList(new JSONObject())); hw.add(Arrays.asList(new JSONObject(), new JSONObject())); hw.add(new ArrayList<JSONObject>());
        List<List<JSONObject>> none = new ArrayList<List<JSONObject>>(); for (int i = 0; i < 3; i++) none.add(new ArrayList<JSONObject>());
        check("Widget-Aufgaben: '📝 2 heute fällig · 1 überfällig', ohne Aufgaben nichts", "📝 2 heute fällig · 1 überfällig".equals(WidgetMath.homeworkLine(hw)) && WidgetMath.homeworkLine(none) == null);
        check("Widget-Titel", WidgetMath.title(java.time.LocalDate.of(2026, 11, 16)).equals("☀️ Mo, 16.11."));
        System.out.println(fails == 0 ? "ALLE " + total + " TESTS BESTANDEN" : fails + " VON " + total + " FEHLGESCHLAGEN");
        System.exit(fails == 0 ? 0 : 1);
    }
}

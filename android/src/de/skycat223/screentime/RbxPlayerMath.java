package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Roblox-Spieler-Check: Eingabe lesen, Antworten auswerten, Zweitkonto-Einschaetzung. Reine Logik. */
final class RbxPlayerMath {
    private RbxPlayerMath() {}

    static final int OK = 0, CHECK = 1, ALT = 2;
    static final String[] LEVEL = {"🟢 Unauffällig", "🟡 Genauer prüfen", "🔴 Zweitkonto-Verdacht"};

    /** Profil-Link -> ID (>0), sonst 0. */
    static long linkId(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(?:https?://)?(?:www\\.|web\\.)?roblox\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?users/(\\d{1,15})(?:[/?#].*)?$").matcher(s.trim());
        if (!m.find()) return 0;
        try { return Long.parseLong(m.group(1)); } catch (Exception e) { return 0; }
    }

    /** Gueltiger Roblox-Benutzername (3-20, Buchstaben/Ziffern/_, hoechstens ein _, nicht am Rand)? Mit @ davor erlaubt. */
    static String username(String s) {
        if (s == null) return null;
        String t = s.trim().replaceFirst("^@", "");
        if (!t.matches("[A-Za-z0-9_]{3,20}") || t.startsWith("_") || t.endsWith("_") || t.indexOf('_') != t.lastIndexOf('_')) return null;
        return t;
    }

    static String lookupBody(String name) {
        try { return new JSONObject().put("usernames", new JSONArray().put(name)).put("excludeBannedUsers", false).toString(); } catch (Exception e) { return "{}"; }
    }

    static long idFromLookup(String body) {
        try { JSONArray d = new JSONObject(body).optJSONArray("data"); return d == null || d.length() == 0 ? 0 : d.optJSONObject(0).optLong("id"); } catch (Exception e) { return 0; }
    }

    /** id, name, display, created (ms), banned, verified, desc. null bei Fehler. */
    static JSONObject parseUser(String body) {
        try {
            JSONObject j = new JSONObject(body);
            if (!j.has("id") || !j.has("name")) return null;
            return new JSONObject().put("id", j.optLong("id")).put("name", j.optString("name")).put("display", CashMath.clean(j.optString("displayName"), 40))
                .put("created", RbxGroupMath.isoMillis(j.optString("created"))).put("banned", j.optBoolean("isBanned")).put("verified", j.optBoolean("hasVerifiedBadge"))
                .put("desc", CashMath.clean(j.isNull("description") ? "" : j.optString("description"), 300));
        } catch (Exception e) { return null; }
    }

    static long count(String body) { try { return Math.max(0, new JSONObject(body).optLong("count", -1)); } catch (Exception e) { return -1; } }

    /** Gruppen: [{gid, name, members, role, rank}]. */
    static JSONArray parseRoles(String body) {
        JSONArray out = new JSONArray();
        try {
            JSONArray d = new JSONObject(body).optJSONArray("data");
            for (int i = 0; d != null && i < d.length(); i++) {
                JSONObject x = d.optJSONObject(i), g = x.optJSONObject("group"), r = x.optJSONObject("role");
                if (g == null || r == null) continue;
                out.put(new JSONObject().put("gid", g.optLong("id")).put("name", CashMath.clean(g.optString("name"), 60)).put("members", g.optLong("memberCount")).put("role", CashMath.clean(r.optString("name"), 40)).put("rank", r.optInt("rank")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Gruppen des Spielers, die auch in "meinen" (beobachteten) Gruppen sind. */
    static List<JSONObject> inMyGroups(JSONArray roles, List<JSONObject> myGroups) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (int i = 0; roles != null && i < roles.length(); i++) {
            JSONObject r = roles.optJSONObject(i);
            for (JSONObject g : myGroups) if (g.optLong("gid") == r.optLong("gid")) { out.add(r); break; }
        }
        return out;
    }

    static long ageDays(long created, long now) { return created <= 0 ? -1 : Math.max(0, (now - created) / 86_400_000L); }

    /**
     * Einschaetzung mit Gruenden. Nur ein Hinweis, kein Beweis.
     * Punkte: Konto < 7 Tage +3, < 30 Tage +2, < 90 Tage +1; nur bei Konten unter 1 Jahr: keine Freunde +2, < 3 Freunde +1, keine Gruppe +1.
     * ab 4 Punkten Verdacht, ab 2 pruefen.
     */
    static int risk(long ageDays, long friends, int groups, List<String> why) {
        int p = 0;
        if (ageDays >= 0 && ageDays < 7) { p += 3; why.add("Konto erst " + ageDays + (ageDays == 1 ? " Tag" : " Tage") + " alt"); }
        else if (ageDays >= 0 && ageDays < 30) { p += 2; why.add("Konto erst " + ageDays + " Tage alt"); }
        else if (ageDays >= 0 && ageDays < 90) { p += 1; why.add("Konto jünger als 3 Monate"); }
        boolean young = ageDays < 0 || ageDays < 365;
        if (young && friends == 0) { p += 2; why.add("keine Freunde"); }
        else if (young && friends > 0 && friends < 3) { p += 1; why.add("nur " + friends + (friends == 1 ? " Freund" : " Freunde")); }
        if (young && groups == 0) { p += 1; why.add("in keiner Gruppe"); }
        return p >= 4 ? ALT : p >= 2 ? CHECK : OK;
    }

    static String ageText(long days) {
        if (days < 0) return "unbekannt";
        if (days < 60) return days + (days == 1 ? " Tag" : " Tage");
        if (days < 730) return (days / 30) + " Monate";
        return String.format(java.util.Locale.GERMAN, "%.1f Jahre", days / 365.25);
    }

    static String profileUrl(long id) { return "https://www.roblox.com/users/" + id + "/profile"; }
}

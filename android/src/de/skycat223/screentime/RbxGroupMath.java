package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

/** Roblox-Gruppen (Communities): Link lesen, Antwort auswerten. Verlauf/Wachstum ueber DcTrackMath. Reine Logik. */
final class RbxGroupMath {
    private RbxGroupMath() {}

    /** Gruppen-ID aus Link oder Zahl. -1 = ungueltig. */
    static long groupId(String s) {
        if (s == null) return -1;
        String t = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(?:https?://)?(?:www\\.|web\\.)?roblox\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:groups|communities)/(\\d{1,15})(?:[/?#].*)?$").matcher(t);
        String d = m.find() ? m.group(1) : t.matches("\\d{1,15}") ? t : null;
        if (d == null) return -1;
        try { long v = Long.parseLong(d); return v > 0 ? v : -1; } catch (Exception e) { return -1; }
    }

    static String apiUrl(long id) { return "https://groups.roblox.com/v1/groups/" + id; }
    static String iconApi(long id) { return "https://thumbnails.roblox.com/v1/groups/icons?groupIds=" + id + "&size=150x150&format=Png&isCircular=false"; }
    static String pageUrl(long id) { return "https://www.roblox.com/communities/" + id; }

    /** name, members, owner, verified, desc, public, shout{text, by, at}. null bei Fehler. */
    static JSONObject parse(String body) {
        try {
            JSONObject j = new JSONObject(body);
            if (!j.has("memberCount") || !j.has("name")) return null;
            JSONObject o = new JSONObject();
            o.put("name", CashMath.clean(j.optString("name"), 60));
            o.put("members", Math.max(0, j.optLong("memberCount")));
            JSONObject ow = j.optJSONObject("owner");
            o.put("owner", ow == null ? "" : CashMath.clean(ow.optString("username"), 40));
            o.put("verified", j.optBoolean("hasVerifiedBadge"));
            o.put("desc", j.isNull("description") ? "" : CashMath.clean(j.optString("description"), 300));
            o.put("public", j.optBoolean("publicEntryAllowed"));
            JSONObject sh = j.optJSONObject("shout");
            if (sh != null && !sh.optString("body").trim().isEmpty()) {
                JSONObject p = sh.optJSONObject("poster");
                o.put("shout", new JSONObject().put("text", CashMath.clean(sh.optString("body"), 255)).put("by", p == null ? "" : CashMath.clean(p.optString("username"), 40)).put("at", isoMillis(sh.optString("updated"))));
            }
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    static long isoMillis(String iso) {
        try { return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli(); } catch (Exception e) {
            try { return java.time.Instant.parse(iso).toEpochMilli(); } catch (Exception e2) { return 0; }
        }
    }

    /** Bild-Adresse aus der Thumbnail-Antwort. */
    static String iconFrom(String body) {
        try {
            JSONArray d = new JSONObject(body).optJSONArray("data");
            JSONObject x = d == null ? null : d.optJSONObject(0);
            return x != null && "Completed".equals(x.optString("state")) && x.optString("imageUrl").startsWith("https://") ? x.optString("imageUrl") : null;
        } catch (Exception e) { return null; }
    }

    static String error(int status) {
        if (status == 400 || status == 404) return "Diese Gruppe gibt es nicht. Prüf den Link.";
        if (status == 429) return "Roblox bremst gerade. Versuch es in einer Minute nochmal.";
        if (status >= 500) return "Roblox ist gerade nicht erreichbar.";
        return "Fehler " + status;
    }

    /** "vor 5 Min", "vor 3 Std", "vor 2 Tagen", "am 12.11.2026". */
    static String ago(long t, long now) {
        if (t <= 0) return "";
        long m = Math.max(0, (now - t) / 60000);
        if (m < 60) return "vor " + m + " Min";
        if (m < 24 * 60) return "vor " + (m / 60) + " Std";
        if (m < 14 * 24 * 60) return "vor " + (m / 1440) + (m / 1440 == 1 ? " Tag" : " Tagen");
        java.time.LocalDate d = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return "am " + d.getDayOfMonth() + "." + d.getMonthValue() + "." + d.getYear();
    }

    /** Fingerabdruck des Rufs, um neue Rufe zu erkennen. */
    static String shoutKey(JSONObject shout) { return shout == null ? "" : shout.optLong("at") + "|" + shout.optString("text").hashCode(); }
}

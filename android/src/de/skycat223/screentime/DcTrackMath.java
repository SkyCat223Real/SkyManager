package de.skycat223.screentime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Discord-Server-Tracker: Einladung lesen, Antwort auswerten, Verlauf und Wachstum. Reine Logik. */
final class DcTrackMath {
    private DcTrackMath() {}

    static final int MAX_HIST = 300;
    static final long MIN_GAP = 30 * 60_000L;

    /** Einladungscode aus Link oder Code. null = ungueltig. */
    static String inviteCode(String s) {
        if (s == null) return null;
        String t = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(?:https?://)?(?:www\\.)?(?:discord\\.gg|discord(?:app)?\\.com/invite)/([A-Za-z0-9-]{2,32})/?(?:\\?.*)?$").matcher(t);
        if (m.find()) return m.group(1);
        return t.matches("[A-Za-z0-9-]{2,32}") ? t : null;
    }

    static String apiUrl(String code) { return "https://discord.com/api/v10/invites/" + code + "?with_counts=true"; }

    /** Auswertung der Antwort: name, gid, icon, members, online, boosts, desc, expires. null bei Fehler. */
    static JSONObject parse(String body) {
        try {
            JSONObject j = new JSONObject(body);
            JSONObject g = j.optJSONObject("guild");
            if (g == null || !j.has("approximate_member_count")) return null;
            JSONObject o = new JSONObject();
            o.put("name", CashMath.clean(g.optString("name"), 60));
            o.put("gid", g.optString("id"));
            o.put("icon", g.isNull("icon") ? "" : g.optString("icon"));
            o.put("members", Math.max(0, j.optLong("approximate_member_count")));
            o.put("online", Math.max(0, j.optLong("approximate_presence_count")));
            o.put("boosts", Math.max(0, g.optLong("premium_subscription_count")));
            o.put("desc", g.isNull("description") ? "" : CashMath.clean(g.optString("description"), 200));
            o.put("expires", !j.isNull("expires_at") && !j.optString("expires_at").isEmpty());
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** Verstaendliche Fehlermeldung zur Antwort. */
    static String error(int status, String body) {
        if (status == 404) return "Einladung ungültig oder abgelaufen. Nimm eine Einladung ohne Ablaufdatum.";
        if (status == 429) return "Discord bremst gerade. Versuch es in einer Minute nochmal.";
        if (status >= 500) return "Discord ist gerade nicht erreichbar.";
        return "Fehler " + status;
    }

    static String iconUrl(String gid, String icon) {
        if (gid == null || gid.isEmpty() || icon == null || icon.isEmpty()) return null;
        return "https://cdn.discordapp.com/icons/" + gid + "/" + icon + ".png?size=128";
    }

    /** Neuen Messpunkt anhaengen, wenn genug Zeit vergangen ist oder sich etwas geaendert hat. @return true wenn angehaengt. */
    static boolean record(JSONArray hist, long t, long members, long online) {
        try {
            int n = hist.length();
            if (n > 0) {
                JSONObject last = hist.optJSONObject(n - 1);
                boolean same = last.optLong("m") == members && last.optLong("o") == online;
                if (t - last.optLong("t") < MIN_GAP && (same || t - last.optLong("t") < 60_000L)) return false;
            }
            hist.put(new JSONObject().put("t", t).put("m", members).put("o", online));
            while (hist.length() > MAX_HIST) hist.remove(0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Mitglieder-Veraenderung seit "ago" ms (Vergleich mit dem letzten Punkt davor). Long.MIN_VALUE = unbekannt. */
    static long growth(JSONArray hist, long now, long ago) {
        if (hist == null || hist.length() < 2) return Long.MIN_VALUE;
        JSONObject last = hist.optJSONObject(hist.length() - 1);
        JSONObject base = null;
        for (int i = hist.length() - 1; i >= 0; i--) { JSONObject p = hist.optJSONObject(i); if (p.optLong("t") <= now - ago) { base = p; break; } }
        if (base == null) return Long.MIN_VALUE;
        return last.optLong("m") - base.optLong("m");
    }

    static String signed(long v) { return v == Long.MIN_VALUE ? "–" : (v > 0 ? "+" : "") + RobloxMath.num(v); }

    static int onlinePercent(long members, long online) { return members <= 0 ? 0 : (int) Math.round(online * 100.0 / members); }

    /** Hoechster gemessener Online-Wert im Verlauf. */
    static long peakOnline(JSONArray hist) {
        long p = 0;
        for (int i = 0; hist != null && i < hist.length(); i++) p = Math.max(p, hist.optJSONObject(i).optLong("o"));
        return p;
    }

    static boolean known(List<JSONObject> list, String code) {
        for (JSONObject x : list) if (x.optString("code").equalsIgnoreCase(code)) return true;
        return false;
    }
}

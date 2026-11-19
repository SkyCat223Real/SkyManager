package de.skycat223.screentime;
public class LiveDc {
    public static void main(String[] a) throws Exception {
        org.json.JSONObject o = DcTracker.fetch(DcTrackMath.inviteCode("https://discord.gg/discord-developers"));
        System.out.println("Echter App-Code gegen Discord: " + o.optString("name") + " | " + RobloxMath.num(o.optLong("members")) + " Mitglieder | " + RobloxMath.num(o.optLong("online")) + " online | Boosts " + o.optLong("boosts"));
        org.json.JSONObject s = new org.json.JSONObject().put("code", "discord-developers");
        DcTracker.apply(s, o, System.currentTimeMillis());
        System.out.println("Gespeicherter Eintrag hat Verlauf: " + s.optJSONArray("hist").length() + " Punkt, aktualisiert: " + (s.optLong("updated") > 0));
        try { DcTracker.fetch("gibtsnichtxyz123"); } catch (Exception e) { System.out.println("Ungültige Einladung -> \"" + e.getMessage() + "\""); }
    }
}

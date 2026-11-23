package de.skycat223.screentime;
public class LivePlayer {
    public static void main(String[] a) throws Exception {
        for (String q : new String[]{"@Roblox", "https://www.roblox.com/users/156/profile"}) {
            org.json.JSONObject o = RbxPlayerCheck.check(q);
            java.util.List<String> why = new java.util.ArrayList<String>();
            int r = RbxPlayerMath.risk(RbxPlayerMath.ageDays(o.optLong("created"), System.currentTimeMillis()), o.optLong("friends"), o.optJSONArray("groups").length(), why);
            System.out.println(q + " -> @" + o.optString("name") + " | Konto " + RbxPlayerMath.ageText(RbxPlayerMath.ageDays(o.optLong("created"), System.currentTimeMillis())) + " | Freunde " + o.optLong("friends") + " | Follower " + RobloxMath.num(o.optLong("followers")) + " | Gruppen " + o.optJSONArray("groups").length() + " | " + RbxPlayerMath.LEVEL[r] + " | Bild: " + o.optString("icon").startsWith("https://"));
        }
        try { RbxPlayerCheck.check("gibtsnichtxyz98765"); } catch (Exception e) { System.out.println("Unbekannter Name -> " + e.getMessage()); }
    }
}

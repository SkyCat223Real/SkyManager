package de.skycat223.screentime;
public class LiveRbx {
    public static void main(String[] a) throws Exception {
        org.json.JSONObject o = RbxGroups.fetch(RbxGroupMath.groupId("https://www.roblox.com/communities/7/Roblox"));
        System.out.println("Echter App-Code gegen Roblox: " + o.optString("name") + " | " + RobloxMath.num(o.optLong("members")) + " Mitglieder | Besitzer " + o.optString("owner") + " | Symbol: " + o.optString("icon").startsWith("https://tr.rbxcdn.com/"));
        try { RbxGroups.fetch(999999999999L); } catch (Exception e) { System.out.println("Ungültige Gruppe -> \"" + e.getMessage() + "\""); }
    }
}

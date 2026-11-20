package de.skycat223.screentime;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Roblox-Spieler-Check: Konto, Alter, Freunde, Gruppen (auch in deinen Gruppen), Zweitkonto-Einschaetzung. */
final class RbxPlayerCheck {
    private RbxPlayerCheck() {}

    static final String KEY = "rbxchecks";
    static JSONObject current = null;

    static String get(String url) throws Exception {
        String[] r = WebhookPage.http("GET", url, null);
        if (!"200".equals(r[0])) throw new Exception(r[0].equals("429") ? "Roblox bremst gerade. Versuch es gleich nochmal." : "Roblox antwortet nicht (" + r[0] + ")");
        return r[1];
    }

    /** Kompletter Check. Wirft verstaendliche Fehler. */
    static JSONObject check(String input) throws Exception {
        long id = RbxPlayerMath.linkId(input);
        if (id <= 0) {
            String name = RbxPlayerMath.username(input);
            if (name == null) {
                try { id = Long.parseLong(input.trim()); } catch (Exception e) { throw new Exception("Das ist kein gültiger Roblox-Name"); }
            } else {
                String[] r = WebhookPage.http("POST", "https://users.roblox.com/v1/usernames/users", RbxPlayerMath.lookupBody(name));
                if (!"200".equals(r[0])) throw new Exception("Roblox antwortet nicht (" + r[0] + ")");
                id = RbxPlayerMath.idFromLookup(r[1]);
                if (id <= 0) throw new Exception("Spieler „" + name + "“ gibt es nicht");
            }
        }
        String[] u = WebhookPage.http("GET", "https://users.roblox.com/v1/users/" + id, null);
        if ("404".equals(u[0]) || "400".equals(u[0])) throw new Exception("Diesen Spieler gibt es nicht");
        if (!"200".equals(u[0])) throw new Exception("Roblox antwortet nicht (" + u[0] + ")");
        JSONObject o = RbxPlayerMath.parseUser(u[1]);
        if (o == null) throw new Exception("Unerwartete Antwort von Roblox");
        long friends = -1, followers = -1;
        try { friends = RbxPlayerMath.count(get("https://friends.roblox.com/v1/users/" + id + "/friends/count")); } catch (Exception ignored) { }
        try { followers = RbxPlayerMath.count(get("https://friends.roblox.com/v1/users/" + id + "/followers/count")); } catch (Exception ignored) { }
        JSONArray roles = new JSONArray();
        try { roles = RbxPlayerMath.parseRoles(get("https://groups.roblox.com/v2/users/" + id + "/groups/roles")); } catch (Exception ignored) { }
        o.put("friends", friends).put("followers", followers).put("groups", roles).put("checked", System.currentTimeMillis());
        try { String ic = RbxGroupMath.iconFrom(get("https://thumbnails.roblox.com/v1/users/avatar-headshot?userIds=" + id + "&size=150x150&format=Png")); if (ic != null) o.put("icon", ic); } catch (Exception ignored) { }
        return o;
    }

    static void remember(MainActivity a, JSONObject o) {
        List<JSONObject> l = Db.list(a, KEY);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).optLong("id") == o.optLong("id")) l.remove(i);
        try { l.add(0, new JSONObject().put("id", o.optLong("id")).put("name", o.optString("name")).put("checked", o.optLong("checked"))); } catch (Exception ignored) { }
        while (l.size() > 20) l.remove(l.size() - 1);
        Db.save(a, KEY, l);
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Spieler-Check", "Roblox-Konten prüfen");
        final long now = System.currentTimeMillis();
        LinearLayout in = U.card(a, "🔎 Spieler suchen");
        final EditText f = SchoolUi.field(a, "", "Roblox-Name oder Profil-Link", false);
        U.add(in, f, 4);
        final TextView st = NX.text(a, "", 12, C.DIM, false);
        View.OnClickListener go = new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String q = f.getText().toString();
                if (q.trim().isEmpty()) { NX.toast(a, "Bitte einen Namen eingeben"); return; }
                CloudPage.bg(a, st, new CloudPage.Job() { @Override public String run() throws Exception { JSONObject o = check(q); current = o; remember(a, o); return ""; } });
            }
        };
        U.add(in, NX.button(a, "Prüfen", go), 6);
        in.addView(st);
        U.add(p[1], in, 12);

        if (current != null) {
            final JSONObject o = current;
            LinearLayout c = U.card(a, null);
            LinearLayout head = U.hbox(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            final ImageView iv = new ImageView(a);
            iv.setBackground(U.round(C.CARD_HI, 30));
            head.addView(iv, new LinearLayout.LayoutParams(U.dp(56), U.dp(56)));
            final String url = o.optString("icon");
            if (url.startsWith("https://")) new Thread(new Runnable() { @Override public void run() { final Bitmap b = CommunityPages.image(url); if (b != null) a.runOnUiThread(new Runnable() { @Override public void run() { iv.setImageBitmap(b); } }); } }).start();
            LinearLayout t = U.vbox(a);
            t.setPadding(U.dp(12), 0, 0, 0);
            t.addView(NX.text(a, o.optString("display") + (o.optBoolean("verified") ? " ✔️" : ""), 17, C.TEXT, true));
            t.addView(NX.text(a, "@" + o.optString("name") + " · ID " + o.optLong("id"), 12, C.DIM, false));
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            c.addView(head);
            if (o.optBoolean("banned")) U.add(c, NX.text(a, "⛔ Dieses Konto ist von Roblox gesperrt", 14, 0xFFFF6B6B, true), 8);
            long age = RbxPlayerMath.ageDays(o.optLong("created"), now);
            JSONArray g = o.optJSONArray("groups");
            List<String> why = new ArrayList<String>();
            int risk = RbxPlayerMath.risk(age, o.optLong("friends", -1), g == null ? 0 : g.length(), why);
            U.add(c, NX.text(a, "📅 Konto seit " + KeyLogMath.stamp(o.optLong("created"), ZoneId.systemDefault()).replaceAll(" \\d{1,2}:\\d{2}$", "") + " (" + RbxPlayerMath.ageText(age) + ")", 14, C.TEXT, false), 8);
            U.add(c, NX.text(a, "👥 " + (o.optLong("friends") < 0 ? "?" : RobloxMath.num(o.optLong("friends"))) + " Freunde · " + (o.optLong("followers") < 0 ? "?" : RobloxMath.num(o.optLong("followers"))) + " Follower · " + (g == null ? 0 : g.length()) + " Gruppen", 14, C.TEXT, false), 4);
            LinearLayout rb = U.vbox(a);
            rb.setBackground(U.round(C.CARD_HI, 12));
            rb.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
            rb.addView(NX.text(a, RbxPlayerMath.LEVEL[risk], 15, risk == RbxPlayerMath.ALT ? 0xFFFF6B6B : risk == RbxPlayerMath.CHECK ? 0xFFFFB74D : C.ACCENT, true));
            rb.addView(NX.text(a, why.isEmpty() ? "Altes Konto mit Freunden und Gruppen." : MyDayMath.join(why), 12, C.DIM, false));
            rb.addView(NX.text(a, "Nur ein Hinweis, kein Beweis.", 11, C.DIM, false));
            U.add(c, rb, 8);
            List<JSONObject> mine = RbxPlayerMath.inMyGroups(g, Db.list(a, RbxGroups.KEY));
            if (!mine.isEmpty()) for (JSONObject r : mine) U.add(c, NX.text(a, "⭐ In deiner Gruppe „" + r.optString("name") + "“: " + r.optString("role") + " (Rang " + r.optInt("rank") + ")", 13, C.ACCENT, true), 6);
            else if (!Db.list(a, RbxGroups.KEY).isEmpty()) U.add(c, NX.text(a, "In keiner deiner beobachteten Gruppen.", 12, C.DIM, false), 6);
            if (g != null && g.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(8, g.length()); i++) sb.append(i > 0 ? "\n" : "").append("• ").append(g.optJSONObject(i).optString("name")).append(" – ").append(g.optJSONObject(i).optString("role"));
                if (g.length() > 8) sb.append("\n… und ").append(g.length() - 8).append(" weitere");
                U.add(c, NX.text(a, sb.toString(), 12, C.DIM, false), 6);
            }
            if (!o.optString("desc").isEmpty()) U.add(c, NX.text(a, "„" + o.optString("desc") + "“", 12, C.DIM, false), 6);
            LinearLayout br = U.hbox(a);
            TextView open = NX.text(a, "Profil öffnen", 13, C.ACCENT, true), note = NX.text(a, "📝 Ins Mod-Log", 13, C.ACCENT, true);
            open.setPadding(0, U.dp(10), U.dp(18), 0); note.setPadding(0, U.dp(10), 0, 0);
            open.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RbxPlayerMath.profileUrl(o.optLong("id"))))); } catch (Exception e) { NX.toast(a, "Kein Browser gefunden"); } } });
            final String summary = "Spieler-Check: " + RbxPlayerMath.LEVEL[risk].substring(2).trim() + ", Konto " + RbxPlayerMath.ageText(age) + " alt" + (why.isEmpty() ? "" : " (" + MyDayMath.join(why) + ")");
            note.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    JSONObject e = ModLogMath.entry(Db.id(), System.currentTimeMillis(), o.optString("name"), "note", summary, Db.obj(a, ModLog.CFG).optString("mod"), Db.obj(a, ModLog.CFG).optString("server"));
                    List<JSONObject> all = Db.list(a, ModLog.KEY); all.add(e); Db.save(a, ModLog.KEY, all);
                    NX.toast(a, "Als Notiz im Mod-Log gespeichert ✓");
                }
            });
            br.addView(open); br.addView(note);
            c.addView(br);
            U.add(p[1], c, 12);
        }

        List<JSONObject> hist = Db.list(a, KEY);
        if (!hist.isEmpty()) {
            LinearLayout hc = U.card(a, "🕘 Zuletzt geprüft");
            for (final JSONObject h : hist) {
                TextView tv = NX.text(a, "@" + h.optString("name") + "  ·  " + RbxGroupMath.ago(h.optLong("checked"), now), 13, C.TEXT, false);
                tv.setPadding(0, U.dp(6), 0, U.dp(6));
                tv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { JSONObject o = check(String.valueOf(h.optLong("id"))); current = o; remember(a, o); return ""; } }); } });
                hc.addView(tv);
            }
            U.add(p[1], hc, 12);
        }
        U.add(p[1], NX.text(a, "Die Daten kommen öffentlich von Roblox. Die Einschätzung zählt Kontoalter, Freunde und Gruppen. Ein neues Konto kann auch einfach ein neuer Spieler sein.", 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

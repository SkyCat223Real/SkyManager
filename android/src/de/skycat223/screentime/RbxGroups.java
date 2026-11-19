package de.skycat223.screentime;

import android.content.Context;
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

import java.util.List;

/** Roblox-Gruppen-Tracker: Mitglieder, Wachstum, Gruppen-Ruf. */
final class RbxGroups {
    private RbxGroups() {}

    static final String KEY = "rbxgroups";
    static long lastAuto = 0;

    static JSONObject fetch(long id) throws Exception {
        String[] r = WebhookPage.http("GET", RbxGroupMath.apiUrl(id), null);
        int st = Integer.parseInt(r[0]);
        if (st != 200) throw new Exception(RbxGroupMath.error(st));
        JSONObject o = RbxGroupMath.parse(r[1]);
        if (o == null) throw new Exception("Unerwartete Antwort von Roblox");
        try { String[] ic = WebhookPage.http("GET", RbxGroupMath.iconApi(id), null); if ("200".equals(ic[0])) { String u = RbxGroupMath.iconFrom(ic[1]); if (u != null) o.put("icon", u); } } catch (Exception ignored) { }
        return o;
    }

    static void apply(JSONObject s, JSONObject o, long now) throws Exception {
        for (String k : new String[]{"name", "members", "owner", "verified", "desc", "public"}) s.put(k, o.get(k));
        if (o.has("icon")) s.put("icon", o.optString("icon"));
        if (o.has("shout")) {
            JSONObject sh = o.optJSONObject("shout");
            if (!RbxGroupMath.shoutKey(sh).equals(RbxGroupMath.shoutKey(s.optJSONObject("shout"))) && s.has("seenShout")) s.put("newShout", true);
            s.put("shout", sh);
        } else s.remove("shout");
        JSONArray h = s.optJSONArray("hist");
        if (h == null) { h = new JSONArray(); s.put("hist", h); }
        DcTrackMath.record(h, now, o.optLong("members"), 0);
        s.put("updated", now);
        s.remove("err");
    }

    static int refreshAll(Context c) {
        List<JSONObject> l = Db.list(c, KEY);
        int ok = 0; long now = System.currentTimeMillis();
        for (JSONObject s : l) { try { apply(s, fetch(s.optLong("gid")), now); ok++; } catch (Exception e) { try { s.put("err", e.getMessage()); } catch (Exception ignored) { } } }
        Db.save(c, KEY, l);
        return ok;
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Roblox-Gruppen", "Mitglieder & Gruppen-Ruf");
        final List<JSONObject> l = Db.list(a, KEY);
        final long now = System.currentTimeMillis();
        boolean due = false;
        for (JSONObject s : l) if (now - s.optLong("updated") > DcTracker.AUTO) due = true;
        if (due && now - lastAuto > DcTracker.AUTO) {
            lastAuto = now;
            new Thread(new Runnable() { @Override public void run() { refreshAll(a); a.runOnUiThread(new Runnable() { @Override public void run() { a.render(); } }); } }).start();
        }
        LinearLayout hero = SchoolUi.hero(a);
        long total = 0; for (JSONObject s : l) total += s.optLong("members");
        hero.addView(NX.text(a, l.isEmpty() ? "Noch keine Gruppen" : RobloxMath.num(total) + " Mitglieder", 24, C.TEXT, true));
        U.add(hero, NX.text(a, l.isEmpty() ? "Füge unten deine Roblox-Gruppe hinzu" : l.size() + (l.size() == 1 ? " Gruppe" : " Gruppen") + " im Blick", 13, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        boolean changed = false;
        for (final JSONObject s : l) {
            LinearLayout c = U.card(a, null);
            LinearLayout head = U.hbox(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            final ImageView iv = new ImageView(a);
            iv.setBackground(U.round(C.CARD_HI, 12));
            head.addView(iv, new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
            final String url = s.optString("icon");
            if (url.startsWith("https://")) new Thread(new Runnable() { @Override public void run() { final Bitmap b = CommunityPages.image(url); if (b != null) a.runOnUiThread(new Runnable() { @Override public void run() { iv.setImageBitmap(b); } }); } }).start();
            LinearLayout t = U.vbox(a);
            t.setPadding(U.dp(10), 0, 0, 0);
            t.addView(NX.text(a, s.optString("name") + (s.optBoolean("verified") ? " ✔️" : ""), 16, C.TEXT, true));
            t.addView(NX.text(a, (s.optString("owner").isEmpty() ? "ohne Besitzer" : "👑 " + s.optString("owner")) + (s.optBoolean("public") ? " · offen für alle" : " · auf Anfrage"), 11, C.DIM, false));
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            c.addView(head);
            JSONArray h = s.optJSONArray("hist");
            U.add(c, NX.text(a, "👥 " + RobloxMath.num(s.optLong("members")) + " Mitglieder", 15, C.TEXT, true), 8);
            U.add(c, NX.text(a, "📈 24 h: " + DcTrackMath.signed(DcTrackMath.growth(h, now, 86_400_000L)) + " · 7 Tage: " + DcTrackMath.signed(DcTrackMath.growth(h, now, 7 * 86_400_000L)), 13, C.DIM, false), 4);
            if (h != null && h.length() >= 2) {
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, U.dp(60));
                cl.topMargin = U.dp(8);
                c.addView(new DcTracker.Chart(a, h), cl);
            }
            JSONObject sh = s.optJSONObject("shout");
            if (sh != null) {
                LinearLayout sb = U.vbox(a);
                sb.setBackground(U.round(C.CARD_HI, 12));
                sb.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
                sb.addView(NX.text(a, (s.optBoolean("newShout") ? "🆕 " : "📣 ") + "Gruppen-Ruf" + (sh.optString("by").isEmpty() ? "" : " von " + sh.optString("by")) + " · " + RbxGroupMath.ago(sh.optLong("at"), now), 12, s.optBoolean("newShout") ? C.ACCENT : C.DIM, true));
                sb.addView(NX.text(a, sh.optString("text"), 13, C.TEXT, false));
                U.add(c, sb, 8);
            }
            if (!s.optString("err").isEmpty()) U.add(c, NX.text(a, "Letzte Aktualisierung fehlgeschlagen: " + s.optString("err"), 11, 0xFFFF6B6B, false), 6);
            LinearLayout br = U.hbox(a);
            TextView open = NX.text(a, "In Roblox öffnen", 13, C.ACCENT, true), del = NX.text(a, "Entfernen", 13, C.DIM, false);
            open.setPadding(0, U.dp(10), U.dp(18), 0); del.setPadding(0, U.dp(10), 0, 0);
            open.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RbxGroupMath.pageUrl(s.optLong("gid"))))); } catch (Exception e) { NX.toast(a, "Kein Browser gefunden"); } } });
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    List<JSONObject> all = Db.list(a, KEY);
                    for (int k = 0; k < all.size(); k++) if (all.get(k).optString("id").equals(s.optString("id"))) { all.remove(k); break; }
                    Db.save(a, KEY, all); a.render();
                }
            });
            br.addView(open); br.addView(del);
            c.addView(br);
            U.add(p[1], c, 12);
            try { if (s.optBoolean("newShout") || !s.has("seenShout")) { s.remove("newShout"); s.put("seenShout", true); changed = true; } } catch (Exception ignored) { }
        }
        if (changed) Db.save(a, KEY, l);

        LinearLayout add = U.card(a, "➕ Gruppe hinzufügen");
        U.add(add, NX.text(a, "Link der Gruppe aus dem Browser oder der Roblox-App (Teilen → Link kopieren), z. B. roblox.com/communities/1234567. Die Gruppen-ID geht auch.", 12, C.DIM, false), 2);
        final EditText f = SchoolUi.field(a, "", "roblox.com/communities/…", false);
        U.add(add, f, 8);
        final TextView st = NX.text(a, "", 12, C.DIM, false);
        U.add(add, NX.button(a, "Hinzufügen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final long id = RbxGroupMath.groupId(f.getText().toString());
                if (id <= 0) { NX.toast(a, "Das ist kein Roblox-Gruppenlink"); return; }
                for (JSONObject x : Db.list(a, KEY)) if (x.optLong("gid") == id) { NX.toast(a, "Diese Gruppe ist schon drin"); return; }
                CloudPage.bg(a, st, new CloudPage.Job() {
                    @Override public String run() throws Exception {
                        JSONObject o = fetch(id);
                        JSONObject s = Db.o("id", Db.id(), "gid", id, "added", System.currentTimeMillis());
                        apply(s, o, System.currentTimeMillis());
                        List<JSONObject> all = Db.list(a, KEY);
                        all.add(s); Db.save(a, KEY, all);
                        return "✓ " + o.optString("name") + " hinzugefügt";
                    }
                });
            }
        }), 6);
        add.addView(st);
        U.add(p[1], add, 12);
        if (!l.isEmpty()) U.add(p[1], NX.button(a, "↻ Jetzt aktualisieren", new View.OnClickListener() {
            @Override public void onClick(View v) { CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() { int n = refreshAll(a); return n + " von " + Db.list(a, KEY).size() + " aktualisiert"; } }); }
        }), 4);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

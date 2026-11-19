package de.skycat223.screentime;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bewerbungen fuer Team und Fraktionen. */
final class Applications {
    private Applications() {}

    static final String KEY = "applications";
    static String filter = "", query = "", openId = "";

    static void save(MainActivity a, JSONObject e) {
        List<JSONObject> all = Db.list(a, KEY);
        for (int i = 0; i < all.size(); i++) if (all.get(i).optString("id").equals(e.optString("id"))) { all.set(i, e); Db.save(a, KEY, all); return; }
        all.add(e); Db.save(a, KEY, all);
    }

    static String post(MainActivity a, JSONObject e, String hookId, String msg) throws Exception {
        for (JSONObject h : Db.list(a, WebhookPage.HOOKS)) {
            if (!h.optString("id").equals(hookId)) continue;
            String[] w = DiscordMath.parseWebhook(h.optString("url"));
            if (w == null) return "Webhook ungültig";
            String[] r = WebhookPage.http("POST", DiscordMath.apiUrl(w), ApplyMath.discord(e, msg, System.currentTimeMillis()).toString());
            return DiscordMath.explain(Integer.parseInt(r[0]), r[1]);
        }
        return "Webhook nicht gefunden";
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Bewerbungen", "Team & Fraktionen");
        final List<JSONObject> list = Db.list(a, KEY);
        final List<JSONObject> hooks = Db.list(a, WebhookPage.HOOKS);
        final JSONObject cfg = Db.obj(a, KEY + "_cfg");
        final long now = System.currentTimeMillis();
        Map<String, Integer> cnt = ApplyMath.counts(list);
        LinearLayout hero = SchoolUi.hero(a);
        int open = cnt.get("neu") + cnt.get("gespraech");
        hero.addView(NX.text(a, open == 0 ? "Keine offenen Bewerbungen" : open + (open == 1 ? " offene Bewerbung" : " offene Bewerbungen"), 20, C.TEXT, true));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ApplyMath.STATUS.length; i++) sb.append(i > 0 ? "  " : "").append(ApplyMath.EMOJI[i]).append(" ").append(cnt.get(ApplyMath.STATUS[i]));
        U.add(hero, NX.text(a, sb.toString(), 14, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        LinearLayout nc = U.card(a, "➕ Neue Bewerbung");
        final EditText name = SchoolUi.field(a, "", "Name (Discord/Roblox)", false), role = SchoolUi.field(a, cfg.optString("role"), "Rolle (z. B. Polizei, Moderator)", false), contact = SchoolUi.field(a, "", "Kontakt (optional)", false), notes = SchoolUi.field(a, "", "Notizen (optional)", false);
        notes.setSingleLine(false); notes.setMaxLines(5);
        U.add(nc, name, 4); U.add(nc, role, 4); U.add(nc, contact, 4); U.add(nc, notes, 4);
        U.add(nc, NX.button(a, "Speichern", new View.OnClickListener() {
            @Override public void onClick(View v) {
                JSONObject e = ApplyMath.entry(Db.id(), System.currentTimeMillis(), name.getText().toString(), role.getText().toString(), contact.getText().toString(), notes.getText().toString());
                if (e == null) { NX.toast(a, "Bitte einen Namen eingeben"); return; }
                boolean dup = ApplyMath.duplicate(Db.list(a, KEY), e.optString("name"), e.optString("role"));
                try { cfg.put("role", e.optString("role")); } catch (Exception ignored) { }
                Db.saveObj(a, KEY + "_cfg", cfg);
                save(a, e);
                openId = e.optString("id"); filter = ""; query = "";
                NX.toast(a, dup ? "Gespeichert – Achtung: " + e.optString("name") + " hat schon eine offene Bewerbung als " + e.optString("role") : "Gespeichert ✓");
                a.render();
            }
        }), 8);
        U.add(p[1], nc, 12);

        LinearLayout lc = U.card(a, "📋 Bewerbungen");
        LinearLayout sr = U.hbox(a);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        final EditText q = SchoolUi.field(a, query, "Suchen", false);
        sr.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = NX.text(a, "Suchen", 13, C.ACCENT, true);
        go.setPadding(U.dp(10), U.dp(8), U.dp(4), U.dp(8));
        go.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { query = q.getText().toString().trim(); a.render(); } });
        sr.addView(go);
        lc.addView(sr);
        List<TextView> fc = new ArrayList<TextView>();
        fc.add(SchoolUi.chip(a, "Alle", filter.isEmpty(), new View.OnClickListener() { @Override public void onClick(View v) { filter = ""; a.render(); } }));
        for (int i = 0; i < ApplyMath.STATUS.length; i++) { final String s = ApplyMath.STATUS[i]; fc.add(SchoolUi.chip(a, ApplyMath.EMOJI[i] + " " + ApplyMath.NAMES[i], s.equals(filter), new View.OnClickListener() { @Override public void onClick(View v) { filter = s; a.render(); } })); }
        LinearLayout fr = U.vbox(a);
        SchoolUi.addRows(a, fr, fc, 3);
        U.add(lc, fr, 6);
        List<JSONObject> v = ApplyMath.view(list, filter, query);
        if (v.isEmpty()) U.add(lc, NX.text(a, list.isEmpty() ? "Noch keine Bewerbungen." : "Nichts gefunden.", 13, C.DIM, false), 8);
        for (int k = 0; k < Math.min(150, v.size()); k++) {
            final JSONObject e = v.get(k);
            int i = Math.max(0, ApplyMath.index(e.optString("status")));
            final boolean isOpen = e.optString("id").equals(openId);
            LinearLayout row = U.vbox(a);
            row.setBackground(U.round(C.CARD_HI, 12));
            row.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
            long w = ApplyMath.daysWaiting(e, now);
            TextView tt = NX.text(a, ApplyMath.EMOJI[i] + " " + e.optString("name") + (e.optString("role").isEmpty() ? "" : " · " + e.optString("role")), 14, C.TEXT, true);
            row.addView(tt);
            row.addView(NX.text(a, ApplyMath.NAMES[i] + " · " + KeyLogMath.stamp(e.optLong("ts"), ZoneId.systemDefault()) + (ApplyMath.open(e) && w >= 3 ? " · ⏳ wartet seit " + w + " Tagen" : ""), 11, ApplyMath.open(e) && w >= 7 ? 0xFFFFB74D : C.DIM, false));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vv) { openId = isOpen ? "" : e.optString("id"); a.render(); } });
            if (isOpen) {
                if (!e.optString("contact").isEmpty()) U.add(row, NX.text(a, "📇 " + e.optString("contact"), 13, C.TEXT, false), 6);
                if (!e.optString("notes").isEmpty()) U.add(row, NX.text(a, e.optString("notes"), 13, C.TEXT, false), 4);
                List<TextView> sc = new ArrayList<TextView>();
                for (int s = 0; s < ApplyMath.STATUS.length; s++) {
                    final String st = ApplyMath.STATUS[s];
                    sc.add(SchoolUi.chip(a, ApplyMath.EMOJI[s] + " " + ApplyMath.NAMES[s], st.equals(e.optString("status")), new View.OnClickListener() {
                        @Override public void onClick(View vv) { if (ApplyMath.setStatus(e, st, System.currentTimeMillis())) { save(a, e); NX.toast(a, "Status: " + ApplyMath.NAMES[ApplyMath.index(st)]); } a.render(); }
                    }));
                }
                LinearLayout srow = U.vbox(a);
                SchoolUi.addRows(a, srow, sc, 2);
                U.add(row, srow, 6);
                if (!hooks.isEmpty()) {
                    final String hookSel = cfg.optString("hook").isEmpty() ? hooks.get(0).optString("id") : cfg.optString("hook");
                    List<TextView> hc = new ArrayList<TextView>();
                    for (final JSONObject h : hooks) hc.add(SchoolUi.chip(a, "📣 " + h.optString("name"), hookSel.equals(h.optString("id")), new View.OnClickListener() { @Override public void onClick(View vv) { try { cfg.put("hook", h.optString("id")); } catch (Exception ignored) { } Db.saveObj(a, KEY + "_cfg", cfg); a.render(); } }));
                    LinearLayout hr = U.vbox(a);
                    SchoolUi.addRows(a, hr, hc, 2);
                    U.add(row, hr, 6);
                    final EditText msg = SchoolUi.field(a, "", "Nachricht dazu (optional)", false);
                    U.add(row, msg, 4);
                    U.add(row, NX.button(a, "Entscheidung in Discord posten", new View.OnClickListener() {
                        @Override public void onClick(View vv) { final String m = msg.getText().toString(); CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = post(a, e, hookSel, m); return err == null ? "In Discord gepostet ✓" : "Discord: " + err; } }); }
                    }), 6);
                }
                TextView del = NX.text(a, "Bewerbung löschen", 12, C.DIM, false);
                del.setPadding(0, U.dp(10), 0, 0);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View vv) {
                        List<JSONObject> all = Db.list(a, KEY);
                        for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(e.optString("id"))) { all.remove(x); break; }
                        Db.save(a, KEY, all); openId = ""; a.render();
                    }
                });
                row.addView(del);
            }
            U.add(lc, row, 6);
        }
        U.add(p[1], lc, 12);
        U.add(p[1], NX.text(a, "Tippe auf eine Bewerbung für Details, Status und Discord. Offene Bewerbungen stehen oben, wer am längsten wartet zuerst." + (hooks.isEmpty() ? " Mit einem Webhook aus dem Webhook-Studio kannst du Entscheidungen direkt in Discord posten." : ""), 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

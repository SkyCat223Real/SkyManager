package de.skycat223.screentime;

import android.content.Intent;
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

/** Mod-Log: Strafen und Notizen fuer deine Community festhalten, auf Wunsch in Discord posten. */
final class ModLog {
    private ModLog() {}

    static final String KEY = "modlog", CFG = "modlog_cfg";
    static String action = "warn", query = "", filter = "";

    static String hookName(MainActivity a, String id) {
        for (JSONObject h : Db.list(a, WebhookPage.HOOKS)) if (h.optString("id").equals(id)) return h.optString("name");
        return "";
    }

    /** Postet einen Eintrag in den gewaehlten Discord-Webhook. @return Fehlermeldung oder null. */
    static String post(MainActivity a, JSONObject e, String hookId) throws Exception {
        for (JSONObject h : Db.list(a, WebhookPage.HOOKS)) {
            if (!h.optString("id").equals(hookId)) continue;
            String[] w = DiscordMath.parseWebhook(h.optString("url"));
            if (w == null) return "Webhook ungültig";
            int warns = ModLogMath.activeWarns(Db.list(a, KEY), e.optString("user"));
            String[] r = WebhookPage.http("POST", DiscordMath.apiUrl(w), ModLogMath.discord(e, warns, System.currentTimeMillis()).toString());
            return DiscordMath.explain(Integer.parseInt(r[0]), r[1]);
        }
        return "Webhook nicht gefunden";
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Mod-Log", "Verwarnungen, Kicks & Bans");
        final List<JSONObject> log = Db.list(a, KEY);
        final JSONObject cfg = Db.obj(a, CFG);
        final List<JSONObject> hooks = Db.list(a, WebhookPage.HOOKS);

        LinearLayout hero = SchoolUi.hero(a);
        Map<String, Integer> cnt = ModLogMath.counts(log);
        hero.addView(NX.text(a, log.size() + (log.size() == 1 ? " Eintrag" : " Einträge"), 22, C.TEXT, true));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(i > 0 ? "  " : "").append(ModLogMath.EMOJI[i]).append(" ").append(cnt.get(ModLogMath.ACTIONS[i]));
        U.add(hero, NX.text(a, sb.toString(), 14, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        LinearLayout nc = U.card(a, "➕ Neuer Eintrag");
        final EditText user = SchoolUi.field(a, "", "Nutzer (z. B. Discord- oder Roblox-Name)", false);
        U.add(nc, user, 4);
        List<TextView> ac = new ArrayList<TextView>();
        for (int i = 0; i < ModLogMath.ACTIONS.length; i++) {
            final String act = ModLogMath.ACTIONS[i];
            ac.add(SchoolUi.chip(a, ModLogMath.EMOJI[i] + " " + ModLogMath.NAMES[i], act.equals(action), new View.OnClickListener() { @Override public void onClick(View v) { action = act; a.render(); } }));
        }
        LinearLayout ar = U.vbox(a);
        SchoolUi.addRows(a, ar, ac, 3);
        U.add(nc, ar, 6);
        final EditText reason = SchoolUi.field(a, "", "Grund", false);
        reason.setSingleLine(false);
        reason.setMaxLines(4);
        U.add(nc, reason, 6);
        final EditText server = SchoolUi.field(a, cfg.optString("server"), "Server/Community (optional)", false);
        U.add(nc, server, 4);
        final EditText mod = SchoolUi.field(a, cfg.optString("mod"), "Moderator (dein Name)", false);
        U.add(nc, mod, 4);
        final String hookSel = cfg.optString("hook");
        if (!hooks.isEmpty()) {
            List<TextView> hc = new ArrayList<TextView>();
            hc.add(SchoolUi.chip(a, "📣 Discord: aus", hookSel.isEmpty(), new View.OnClickListener() { @Override public void onClick(View v) { setCfg(a, "hook", ""); a.render(); } }));
            for (final JSONObject h : hooks) hc.add(SchoolUi.chip(a, "📣 " + h.optString("name"), hookSel.equals(h.optString("id")), new View.OnClickListener() { @Override public void onClick(View v) { setCfg(a, "hook", h.optString("id")); a.render(); } }));
            LinearLayout hr = U.vbox(a);
            SchoolUi.addRows(a, hr, hc, 2);
            U.add(nc, hr, 6);
        }
        U.add(nc, NX.button(a, "Speichern", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final JSONObject e = ModLogMath.entry(Db.id(), System.currentTimeMillis(), user.getText().toString(), action, reason.getText().toString(), mod.getText().toString(), server.getText().toString());
                if (e == null) { NX.toast(a, "Bitte einen Nutzer eingeben"); return; }
                setCfg(a, "mod", e.optString("mod"));
                setCfg(a, "server", e.optString("server"));
                List<JSONObject> all = Db.list(a, KEY);
                all.add(e);
                while (all.size() > ModLogMath.MAX_ENTRIES) all.remove(0);
                Db.save(a, KEY, all);
                String hint = "warn".equals(action) ? ModLogMath.hint(ModLogMath.activeWarns(all, e.optString("user"))) : null;
                query = e.optString("user");
                if (!hookSel.isEmpty()) {
                    final String h0 = hint;
                    CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = post(a, e, hookSel); return err == null ? "Gespeichert und in Discord gepostet ✓" + (h0 != null ? "\n" + h0 : "") : "Gespeichert, aber Discord: " + err; } });
                } else { NX.toast(a, hint != null ? hint : "Gespeichert ✓"); a.render(); }
            }
        }), 8);
        U.add(p[1], nc, 12);

        if (!query.isEmpty()) {
            int w = ModLogMath.activeWarns(log, query);
            boolean ban = ModLogMath.banned(log, query);
            int total = ModLogMath.search(log, query, null).size();
            LinearLayout uc = U.card(a, "👤 " + query);
            U.add(uc, NX.text(a, (ban ? "🔨 Gebannt" : "Nicht gebannt") + " · " + w + " aktive Verwarnung" + (w == 1 ? "" : "en") + " · " + total + " Einträge", 14, ban ? 0xFFFF6B6B : C.TEXT, true), 2);
            String h = ModLogMath.hint(w);
            if (h != null && !ban) U.add(uc, NX.text(a, h, 13, 0xFFFFB74D, true), 6);
            TextView clr = NX.text(a, "Filter aufheben", 13, C.ACCENT, true);
            clr.setPadding(0, U.dp(8), 0, 0);
            clr.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { query = ""; a.render(); } });
            uc.addView(clr);
            U.add(p[1], uc, 12);
        }

        LinearLayout lc = U.card(a, "📜 Verlauf");
        LinearLayout sr = U.hbox(a);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        final EditText q = SchoolUi.field(a, query, "Suchen (Name, Grund …)", false);
        sr.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = NX.text(a, "Suchen", 13, C.ACCENT, true);
        go.setPadding(U.dp(10), U.dp(8), U.dp(4), U.dp(8));
        go.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { query = q.getText().toString().trim(); a.render(); } });
        sr.addView(go);
        lc.addView(sr);
        List<TextView> fc = new ArrayList<TextView>();
        fc.add(SchoolUi.chip(a, "Alle", filter.isEmpty(), new View.OnClickListener() { @Override public void onClick(View v) { filter = ""; a.render(); } }));
        for (int i = 0; i < ModLogMath.ACTIONS.length; i++) { final String act = ModLogMath.ACTIONS[i]; fc.add(SchoolUi.chip(a, ModLogMath.EMOJI[i], act.equals(filter), new View.OnClickListener() { @Override public void onClick(View v) { filter = act; a.render(); } })); }
        LinearLayout fr = U.vbox(a);
        SchoolUi.addRows(a, fr, fc, 7);
        U.add(lc, fr, 6);
        List<JSONObject> res = ModLogMath.search(log, query, filter);
        if (res.isEmpty()) U.add(lc, NX.text(a, log.isEmpty() ? "Noch keine Einträge." : "Nichts gefunden.", 13, C.DIM, false), 8);
        for (int k = 0; k < Math.min(100, res.size()); k++) {
            final JSONObject e = res.get(k);
            int i = Math.max(0, ModLogMath.index(e.optString("action")));
            LinearLayout row = U.vbox(a);
            row.setBackground(U.round(C.CARD_HI, 12));
            row.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
            TextView tt = NX.text(a, ModLogMath.EMOJI[i] + " " + ModLogMath.NAMES[i] + ": " + e.optString("user"), 14, C.TEXT, true);
            tt.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { query = e.optString("user"); a.render(); } });
            row.addView(tt);
            if (!e.optString("reason").isEmpty()) row.addView(NX.text(a, e.optString("reason"), 13, C.TEXT, false));
            row.addView(NX.text(a, KeyLogMath.stamp(e.optLong("ts"), ZoneId.systemDefault()) + (e.optString("mod").isEmpty() ? "" : " · " + e.optString("mod")) + (e.optString("server").isEmpty() ? "" : " · " + e.optString("server")), 11, C.DIM, false));
            LinearLayout act = U.hbox(a);
            if (!hooks.isEmpty() && !cfg.optString("hook").isEmpty()) {
                TextView send = NX.text(a, "📣 Posten", 12, C.ACCENT, true);
                send.setPadding(0, U.dp(6), U.dp(16), 0);
                send.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = post(a, e, cfg.optString("hook")); return err == null ? "In Discord gepostet ✓" : "Discord: " + err; } }); } });
                act.addView(send);
            }
            TextView del = NX.text(a, "Löschen", 12, C.DIM, false);
            del.setPadding(0, U.dp(6), 0, 0);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    List<JSONObject> all = Db.list(a, KEY);
                    for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(e.optString("id"))) { all.remove(x); break; }
                    Db.save(a, KEY, all); a.render();
                }
            });
            act.addView(del);
            row.addView(act);
            U.add(lc, row, 6);
        }
        U.add(p[1], lc, 12);
        if (!log.isEmpty()) U.add(p[1], NX.button(a, "Mod-Log teilen (Text)", new View.OnClickListener() {
            @Override public void onClick(View v) { Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ModLogMath.shareText(Db.list(a, KEY), 50, ZoneId.systemDefault())); a.startActivity(Intent.createChooser(i, "Mod-Log teilen")); }
        }), 4);
        if (hooks.isEmpty()) U.add(p[1], NX.text(a, "Tipp: Speichere im Webhook-Studio einen Webhook, dann kannst du Einträge direkt in einen Discord-Kanal posten.", 11, C.DIM, false), 8);
        SchoolUi.reveal(p[1]);
        return p[0];
    }

    static void setCfg(MainActivity a, String k, String v) {
        JSONObject o = Db.obj(a, CFG);
        try { o.put(k, v); } catch (Exception ignored) { }
        Db.saveObj(a, CFG, o);
    }
}

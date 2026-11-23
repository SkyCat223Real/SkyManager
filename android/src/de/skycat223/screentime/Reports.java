package de.skycat223.screentime;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Einsatzberichte fuer RP-Einsaetze. */
final class Reports {
    private Reports() {}

    static final String KEY = "reports", CFG = "reports_cfg";
    static String type = "Verkehrsunfall", result = "", filter = "", query = "", openId = "", fines = "";

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Einsatzberichte", "Berichte schreiben & posten");
        final List<JSONObject> list = Db.list(a, KEY);
        final JSONObject cfg = Db.obj(a, CFG);
        final ZoneId z = ZoneId.systemDefault();
        long month = LocalDate.now().withDayOfMonth(1).atStartOfDay(z).toInstant().toEpochMilli();
        Map<String, Integer> st = ReportMath.stats(list, month);
        int sum = 0; for (int v : st.values()) sum += v;
        LinearLayout hero = SchoolUi.hero(a);
        hero.addView(NX.text(a, sum + (sum == 1 ? " Einsatz" : " Einsätze") + " diesen Monat", 20, C.TEXT, true));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : st.entrySet()) sb.append(sb.length() > 0 ? "  " : "").append(ReportMath.EMOJI[ReportMath.type(e.getKey())]).append(" ").append(e.getValue());
        U.add(hero, NX.text(a, sb.length() == 0 ? list.size() + " Berichte insgesamt" : sb.toString(), 14, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        LinearLayout nc = U.card(a, "📝 Neuer Bericht · " + ReportMath.nextNumber(list, LocalDate.now().getYear()));
        List<TextView> tc = new ArrayList<TextView>();
        for (int i = 0; i < ReportMath.TYPES.length; i++) { final String t = ReportMath.TYPES[i]; tc.add(SchoolUi.chip(a, ReportMath.EMOJI[i] + " " + t, t.equals(type), new View.OnClickListener() { @Override public void onClick(View v) { type = t; a.render(); } })); }
        LinearLayout tr = U.vbox(a);
        SchoolUi.addRows(a, tr, tc, 2);
        U.add(nc, tr, 4);
        final EditText place = SchoolUi.field(a, "", "Ort", false), units = SchoolUi.field(a, cfg.optString("units"), "Einheiten (z. B. Streife 12, RTW 3)", false), people = SchoolUi.field(a, "", "Beteiligte (Täter, Opfer, Zeugen)", false), text = SchoolUi.field(a, "", "Was ist passiert?", false), fine = SchoolUi.field(a, fines, "Strafe (optional)", false), author = SchoolUi.field(a, cfg.optString("author"), "Bericht von", false);
        text.setSingleLine(false); text.setMaxLines(12); text.setMinLines(3);
        fine.setSingleLine(false); fine.setMaxLines(6);
        U.add(nc, place, 6); U.add(nc, units, 4); U.add(nc, people, 4); U.add(nc, text, 4);
        List<TextView> rc = new ArrayList<TextView>();
        for (final String r : ReportMath.RESULTS) rc.add(SchoolUi.chip(a, r, r.equals(result), new View.OnClickListener() { @Override public void onClick(View v) { result = r.equals(result) ? "" : r; a.render(); } }));
        LinearLayout rr = U.vbox(a);
        SchoolUi.addRows(a, rr, rc, 2);
        U.add(nc, rr, 6);
        U.add(nc, fine, 4);
        final JSONObject last = Db.obj(a, Fines.LAST);
        if (!last.optString("text").isEmpty() && System.currentTimeMillis() - last.optLong("ts") < 12 * 3_600_000L) {
            TextView imp = NX.text(a, "⚖️ Letzte Berechnung aus dem Bußgeldkatalog übernehmen", 13, C.ACCENT, true);
            imp.setPadding(0, U.dp(6), 0, U.dp(6));
            imp.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { fine.setText(last.optString("text")); fines = last.optString("text"); } });
            nc.addView(imp);
        }
        U.add(nc, author, 4);
        U.add(nc, NX.button(a, "Bericht speichern", new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<JSONObject> all = Db.list(a, KEY);
                JSONObject r = ReportMath.report(Db.id(), ReportMath.nextNumber(all, LocalDate.now().getYear()), System.currentTimeMillis(), type, place.getText().toString(), units.getText().toString(), people.getText().toString(), text.getText().toString(), result, fine.getText().toString().trim(), author.getText().toString());
                if (r == null) { NX.toast(a, "Bitte beschreiben, was passiert ist"); return; }
                try { cfg.put("units", r.optString("units")).put("author", r.optString("author")); } catch (Exception ignored) { }
                Db.saveObj(a, CFG, cfg);
                all.add(r); Db.save(a, KEY, all);
                openId = r.optString("id"); result = ""; fines = "";
                NX.toast(a, "Bericht " + r.optString("nr") + " gespeichert ✓"); a.render();
            }
        }), 8);
        U.add(p[1], nc, 12);

        LinearLayout lc = U.card(a, "📂 Berichte");
        LinearLayout sr = U.hbox(a);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        final EditText q = SchoolUi.field(a, query, "Suchen (Nr., Ort, Name …)", false);
        sr.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = NX.text(a, "Suchen", 13, C.ACCENT, true);
        go.setPadding(U.dp(10), U.dp(8), U.dp(4), U.dp(8));
        go.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { query = q.getText().toString().trim(); a.render(); } });
        sr.addView(go);
        lc.addView(sr);
        List<TextView> fc = new ArrayList<TextView>();
        fc.add(SchoolUi.chip(a, "Alle", filter.isEmpty(), new View.OnClickListener() { @Override public void onClick(View v) { filter = ""; a.render(); } }));
        for (int i = 0; i < ReportMath.TYPES.length; i++) { final String t = ReportMath.TYPES[i]; fc.add(SchoolUi.chip(a, ReportMath.EMOJI[i], t.equals(filter), new View.OnClickListener() { @Override public void onClick(View v) { filter = t; a.render(); } })); }
        LinearLayout fr = U.vbox(a);
        SchoolUi.addRows(a, fr, fc, 6);
        U.add(lc, fr, 6);
        List<JSONObject> res = ReportMath.search(list, filter, query);
        if (res.isEmpty()) U.add(lc, NX.text(a, list.isEmpty() ? "Noch keine Berichte." : "Nichts gefunden.", 13, C.DIM, false), 8);
        for (int k = 0; k < Math.min(100, res.size()); k++) {
            final JSONObject r = res.get(k);
            final boolean open = r.optString("id").equals(openId);
            LinearLayout row = U.vbox(a);
            row.setBackground(U.round(C.CARD_HI, 12));
            row.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
            row.addView(NX.text(a, ReportMath.EMOJI[ReportMath.type(r.optString("type"))] + " " + r.optString("nr") + " · " + r.optString("type"), 14, C.TEXT, true));
            row.addView(NX.text(a, EventMath.when(r.optLong("at"), z) + (r.optString("place").isEmpty() ? "" : " · " + r.optString("place")) + (r.optString("result").isEmpty() ? "" : " · " + r.optString("result")), 11, C.DIM, false));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openId = open ? "" : r.optString("id"); a.render(); } });
            if (open) {
                U.add(row, NX.text(a, ReportMath.text(r, z), 13, C.TEXT, false), 8);
                LinearLayout hr = HookPost.chooser(a, CFG);
                if (hr != null) {
                    U.add(row, hr, 8);
                    U.add(row, NX.button(a, "📣 In Discord posten", new View.OnClickListener() {
                        @Override public void onClick(View v) { CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = HookPost.send(a, HookPost.selected(a, CFG), ReportMath.discord(r, ZoneId.systemDefault())); return err == null ? "Bericht gepostet ✓" : "Discord: " + err; } }); }
                    }), 6);
                }
                LinearLayout br = U.hbox(a);
                TextView share = NX.text(a, "Als Text teilen", 12, C.ACCENT, true), del = NX.text(a, "Löschen", 12, C.DIM, false);
                share.setPadding(0, U.dp(10), U.dp(18), 0); del.setPadding(0, U.dp(10), 0, 0);
                share.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ReportMath.text(r, ZoneId.systemDefault())); a.startActivity(Intent.createChooser(i, "Bericht teilen")); } });
                del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(r.optString("id"))) { all.remove(x); break; } Db.save(a, KEY, all); openId = ""; a.render(); } });
                br.addView(share); br.addView(del);
                row.addView(br);
            }
            U.add(lc, row, 6);
        }
        U.add(p[1], lc, 12);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

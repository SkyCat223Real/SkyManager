package de.skycat223.screentime;

import android.app.DatePickerDialog;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Team-Verwaltung. */
final class Team {
    private Team() {}

    static final String KEY = "team", CFG = "team_cfg";
    static String openId = "";
    static boolean editRanks = false;

    static void save(MainActivity a, JSONObject m) {
        List<JSONObject> all = Db.list(a, KEY);
        for (int i = 0; i < all.size(); i++) if (all.get(i).optString("id").equals(m.optString("id"))) { all.set(i, m); Db.save(a, KEY, all); return; }
        all.add(m); Db.save(a, KEY, all);
    }

    /** Aus Bewerbungen: angenommenen Bewerber ins Team. @return Meldung. */
    static String adopt(MainActivity a, JSONObject app) {
        List<JSONObject> team = Db.list(a, KEY);
        if (TeamMath.exists(team, app.optString("name"))) return app.optString("name") + " ist schon im Team";
        if (team.size() >= TeamMath.MAX_MEMBERS) return "Höchstens " + TeamMath.MAX_MEMBERS + " Mitglieder";
        List<String> ranks = TeamMath.ranks(Db.obj(a, CFG));
        String rank = "";
        for (String r : ranks) if (r.equalsIgnoreCase(app.optString("role"))) rank = r;
        if (rank.isEmpty()) rank = ranks.isEmpty() ? "" : ranks.get(ranks.size() - 1);
        JSONObject m = TeamMath.member(Db.id(), System.currentTimeMillis(), app.optString("name"), rank, app.optString("contact"));
        save(a, m);
        return "👥 " + m.optString("name") + " ist jetzt im Team" + (rank.isEmpty() ? "" : " (" + rank + ")");
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Team-Verwaltung", "Ränge & Mitglieder");
        final JSONObject cfg = Db.obj(a, CFG);
        final List<String> ranks = TeamMath.ranks(cfg);
        final long now = System.currentTimeMillis(), today = LocalDate.now().toEpochDay();
        final List<JSONObject> team = TeamMath.sorted(Db.list(a, KEY), ranks);
        int away = 0; for (JSONObject m : team) if (TeamMath.away(m, today)) away++;
        LinearLayout hero = SchoolUi.hero(a);
        hero.addView(NX.text(a, team.size() + (team.size() == 1 ? " Mitglied" : " Mitglieder"), 22, C.TEXT, true));
        U.add(hero, NX.text(a, away > 0 ? "🌴 " + away + " abwesend" : "Alle da", 13, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        LinearLayout nc = U.card(a, "➕ Mitglied hinzufügen");
        final EditText name = SchoolUi.field(a, "", "Name", false), contact = SchoolUi.field(a, "", "Kontakt (optional)", false);
        U.add(nc, name, 4); U.add(nc, contact, 4);
        final String[] rk = {cfg.optString("newRank", ranks.isEmpty() ? "" : ranks.get(ranks.size() - 1))};
        List<TextView> rc = new ArrayList<TextView>();
        for (final String r : ranks) rc.add(SchoolUi.chip(a, r, r.equals(rk[0]), new View.OnClickListener() { @Override public void onClick(View v) { try { cfg.put("newRank", r); } catch (Exception ignored) { } Db.saveObj(a, CFG, cfg); a.render(); } }));
        LinearLayout rr = U.vbox(a);
        SchoolUi.addRows(a, rr, rc, 3);
        U.add(nc, rr, 6);
        U.add(nc, NX.button(a, "Hinzufügen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<JSONObject> all = Db.list(a, KEY);
                if (TeamMath.exists(all, name.getText().toString())) { NX.toast(a, "Ist schon im Team"); return; }
                JSONObject m = TeamMath.member(Db.id(), System.currentTimeMillis(), name.getText().toString(), rk[0], contact.getText().toString());
                if (m == null) { NX.toast(a, "Bitte einen Namen eingeben"); return; }
                if (all.size() >= TeamMath.MAX_MEMBERS) { NX.toast(a, "Höchstens " + TeamMath.MAX_MEMBERS + " Mitglieder"); return; }
                save(a, m); openId = m.optString("id"); a.render();
            }
        }), 8);
        U.add(p[1], nc, 12);

        for (final JSONObject m : team) {
            final boolean open = m.optString("id").equals(openId);
            LinearLayout c = U.card(a, null);
            boolean aw = TeamMath.away(m, today);
            c.addView(NX.text(a, (aw ? "🌴 " : "👤 ") + m.optString("name"), 15, C.TEXT, true));
            U.add(c, NX.text(a, (m.optString("rank").isEmpty() ? "ohne Rang" : m.optString("rank")) + " · seit " + TeamMath.days(m, now) + " Tagen im Team" + (aw ? " · abwesend bis " + LocalDate.ofEpochDay(m.optLong("awayUntil")).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.")) : ""), 12, aw ? 0xFFFFB74D : C.DIM, false), 2);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openId = open ? "" : m.optString("id"); a.render(); } });
            if (open) {
                if (!m.optString("contact").isEmpty()) U.add(c, NX.text(a, "📇 " + m.optString("contact"), 13, C.TEXT, false), 6);
                LinearLayout act = U.hbox(a);
                String[] lab = {"⬆️ Befördern", "⬇️ Herabstufen"};
                for (int k = 0; k < 2; k++) {
                    final int dir = k == 0 ? -1 : 1;
                    TextView b = NX.text(a, lab[k], 13, C.ACCENT, true);
                    b.setPadding(0, U.dp(8), U.dp(18), 0);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            final String nr = TeamMath.move(m, ranks, dir, System.currentTimeMillis());
                            if (nr == null) { NX.toast(a, dir < 0 ? "Schon im höchsten Rang" : "Schon im niedrigsten Rang"); return; }
                            save(a, m); NX.toast(a, m.optString("name") + " ist jetzt " + nr);
                            a.render();
                        }
                    });
                    act.addView(b);
                }
                c.addView(act);
                TextView awb = NX.text(a, aw ? "🌴 Wieder da" : "🌴 Abwesend bis …", 13, C.ACCENT, true);
                awb.setPadding(0, U.dp(8), 0, 0);
                awb.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (TeamMath.away(m, LocalDate.now().toEpochDay())) { m.remove("awayUntil"); TeamMath.log(m, System.currentTimeMillis(), "Wieder da"); save(a, m); a.render(); return; }
                        Calendar cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_MONTH, 7);
                        new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
                            @Override public void onDateSet(DatePicker dp, int y, int mo, int d) { LocalDate until = LocalDate.of(y, mo + 1, d); try { m.put("awayUntil", until.toEpochDay()); } catch (Exception ignored) { } TeamMath.log(m, System.currentTimeMillis(), "Abwesend bis " + until.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))); save(a, m); a.render(); }
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
                    }
                });
                c.addView(awb);
                final EditText notes = SchoolUi.field(a, m.optString("notes"), "Notizen", false);
                notes.setSingleLine(false); notes.setMaxLines(5);
                U.add(c, notes, 8);
                TextView sv = NX.text(a, "Notiz speichern", 12, C.ACCENT, true);
                sv.setPadding(0, U.dp(6), 0, 0);
                sv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { m.put("notes", CashMath.clean(notes.getText().toString(), 1000)); } catch (Exception ignored) { } save(a, m); NX.toast(a, "Gespeichert ✓"); } });
                c.addView(sv);
                JSONArray h = m.optJSONArray("hist");
                if (h != null && h.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = h.length() - 1; i >= Math.max(0, h.length() - 8); i--) sb.append(sb.length() > 0 ? "\n" : "").append(KeyLogMath.stamp(h.optJSONObject(i).optLong("t"), ZoneId.systemDefault()).replaceAll(" \\d{1,2}:\\d{2}$", "")).append("  ").append(h.optJSONObject(i).optString("text"));
                    U.add(c, NX.text(a, sb.toString(), 11, C.DIM, false), 8);
                }
                LinearLayout hr = HookPost.chooser(a, CFG);
                if (hr != null) {
                    U.add(c, hr, 8);
                    U.add(c, NX.button(a, "📣 Rang in Discord bekanntgeben", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            JSONArray hh = m.optJSONArray("hist");
                            final boolean up = hh == null || hh.length() == 0 || !hh.optJSONObject(hh.length() - 1).optString("text").startsWith("⬇️");
                            CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = HookPost.send(a, HookPost.selected(a, CFG), TeamMath.discordPromotion(m, up, System.currentTimeMillis())); return err == null ? "Bekanntgegeben ✓" : "Discord: " + err; } });
                        }
                    }), 6);
                }
                TextView del = NX.text(a, "Aus dem Team entfernen", 12, C.DIM, false);
                del.setPadding(0, U.dp(10), 0, 0);
                del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(m.optString("id"))) { all.remove(x); break; } Db.save(a, KEY, all); openId = ""; a.render(); } });
                c.addView(del);
            }
            U.add(p[1], c, 8);
        }

        LinearLayout tc = U.card(a, "📣 Teamliste & Ränge");
        LinearLayout hr = HookPost.chooser(a, CFG);
        final EditText title = SchoolUi.field(a, cfg.optString("title"), "Titel der Teamliste (z. B. Team von GrenzRP)", false);
        U.add(tc, title, 4);
        if (hr != null) {
            U.add(tc, hr, 6);
            U.add(tc, NX.button(a, "Teamliste in Discord posten", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { cfg.put("title", CashMath.clean(title.getText().toString(), 100)); } catch (Exception ignored) { }
                    Db.saveObj(a, CFG, cfg);
                    CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = HookPost.send(a, HookPost.selected(a, CFG), TeamMath.discordList(Db.list(a, KEY), ranks, cfg.optString("title"), LocalDate.now().toEpochDay(), System.currentTimeMillis())); return err == null ? "Teamliste gepostet ✓" : "Discord: " + err; } });
                }
            }), 6);
        } else U.add(tc, NX.text(a, "Speichere im Webhook-Studio einen Webhook, um die Teamliste in Discord zu posten.", 11, C.DIM, false), 6);
        TextView er = NX.text(a, editRanks ? "Ränge schließen" : "✏️ Ränge bearbeiten (" + MyDayMath.join(ranks) + ")", 13, C.ACCENT, true);
        er.setPadding(0, U.dp(12), 0, 0);
        er.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { editRanks = !editRanks; a.render(); } });
        tc.addView(er);
        if (editRanks) {
            StringBuilder sb = new StringBuilder(); for (String r : ranks) sb.append(sb.length() > 0 ? "\n" : "").append(r);
            final EditText re = SchoolUi.field(a, sb.toString(), "Ein Rang pro Zeile, oben der höchste", false);
            re.setSingleLine(false); re.setMaxLines(12);
            U.add(tc, re, 6);
            U.add(tc, NX.button(a, "Ränge speichern", new View.OnClickListener() {
                @Override public void onClick(View v) { List<String> l = TeamMath.parseRanks(re.getText().toString()); if (l.isEmpty()) { NX.toast(a, "Mindestens ein Rang"); return; } try { cfg.put("ranks", new JSONArray(l)); } catch (Exception ignored) { } Db.saveObj(a, CFG, cfg); editRanks = false; a.render(); }
            }), 6);
        }
        U.add(p[1], tc, 12);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

package de.skycat223.screentime;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Giveaways: Teilnehmer sammeln, fair auslosen, in Discord posten. */
final class Giveaways {
    private Giveaways() {}

    static final String KEY = "giveaways", CFG = "giveaways_cfg";
    static String openId = "";
    static long pickEnd = 0;

    static void save(MainActivity a, JSONObject g) {
        List<JSONObject> all = Db.list(a, KEY);
        for (int i = 0; i < all.size(); i++) if (all.get(i).optString("id").equals(g.optString("id"))) { all.set(i, g); Db.save(a, KEY, all); return; }
        all.add(0, g); Db.save(a, KEY, all);
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Giveaways", "Fair auslosen");
        final List<JSONObject> list = Db.list(a, KEY);
        final long now = System.currentTimeMillis();
        LinearLayout nc = U.card(a, "🎁 Neues Giveaway");
        final EditText prize = SchoolUi.field(a, "", "Preis (z. B. 1.000 Robux, VIP-Rang)", false), info = SchoolUi.field(a, "", "Infos / Teilnahme (optional)", false), count = SchoolUi.field(a, "1", "Anzahl Gewinner", true);
        U.add(nc, prize, 4); U.add(nc, info, 4); U.add(nc, count, 4);
        final TextView end = NX.text(a, pickEnd > now ? "⏰ Endet " + EventMath.when(pickEnd, ZoneId.systemDefault()) : "⏰ Ende wählen (optional)", 14, C.ACCENT, true);
        end.setPadding(0, U.dp(10), 0, U.dp(10));
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final Calendar cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_MONTH, 1);
                new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker dp, final int y, final int m, final int d) {
                        new TimePickerDialog(a, new TimePickerDialog.OnTimeSetListener() {
                            @Override public void onTimeSet(TimePicker tp, int h, int mi) { Calendar c = Calendar.getInstance(); c.set(y, m, d, h, mi, 0); c.set(Calendar.MILLISECOND, 0); pickEnd = c.getTimeInMillis(); end.setText("⏰ Endet " + EventMath.when(pickEnd, ZoneId.systemDefault())); }
                        }, 20, 0, true).show();
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        nc.addView(end);
        U.add(nc, NX.button(a, "Erstellen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String pr = CashMath.clean(prize.getText().toString(), 100);
                if (pr.isEmpty()) { NX.toast(a, "Bitte einen Preis eingeben"); return; }
                int k = 1; try { k = Math.max(1, Math.min(GiveawayMath.MAX_WINNERS, Integer.parseInt(count.getText().toString().trim()))); } catch (Exception ignored) { }
                JSONObject g = Db.o("id", Db.id(), "prize", pr, "info", CashMath.clean(info.getText().toString(), 500), "count", k, "end", pickEnd > System.currentTimeMillis() ? pickEnd : 0, "created", System.currentTimeMillis());
                try { g.put("people", new JSONArray()).put("winners", new JSONArray()); } catch (Exception ignored) { }
                save(a, g); openId = g.optString("id"); pickEnd = 0; a.render();
            }
        }), 6);
        U.add(p[1], nc, 12);

        for (final JSONObject g : list) {
            final boolean open = g.optString("id").equals(openId);
            List<String> ppl = GiveawayMath.list(g.optJSONArray("people")), win = GiveawayMath.list(g.optJSONArray("winners"));
            LinearLayout c = U.card(a, null);
            c.addView(NX.text(a, (win.isEmpty() ? "🎁 " : "🏆 ") + g.optString("prize"), 16, C.TEXT, true));
            U.add(c, NX.text(a, ppl.size() + " Teilnehmer · " + g.optInt("count", 1) + (g.optInt("count", 1) == 1 ? " Gewinner" : " Gewinner") + (g.optLong("end") > 0 ? " · " + (g.optLong("end") > now ? "endet " + EventMath.countdown(g.optLong("end"), now) : "beendet") : ""), 12, C.DIM, false), 2);
            if (!win.isEmpty()) U.add(c, NX.text(a, "🏆 " + MyDayMath.join(win), 14, C.ACCENT, true), 6);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openId = open ? "" : g.optString("id"); a.render(); } });
            if (open) {
                final EditText add = SchoolUi.field(a, "", "Teilnehmer einfügen (einer pro Zeile oder mit Komma)", false);
                add.setSingleLine(false); add.setMaxLines(6);
                U.add(c, add, 8);
                U.add(c, NX.button(a, "Hinzufügen", new View.OnClickListener() {
                    @Override public void onClick(View v) { int n = GiveawayMath.merge(g.optJSONArray("people"), GiveawayMath.parseList(add.getText().toString())); save(a, g); NX.toast(a, n + " neu dabei"); a.render(); }
                }), 4);
                if (!ppl.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(30, ppl.size()); i++) sb.append(i > 0 ? ", " : "").append(ppl.get(i));
                    if (ppl.size() > 30) sb.append(" … (+").append(ppl.size() - 30).append(")");
                    U.add(c, NX.text(a, sb.toString(), 12, C.DIM, false), 6);
                }
                U.add(c, NX.button(a, win.isEmpty() ? "🎲 Auslosen" : "🎲 Komplett neu auslosen", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        List<String> pool = GiveawayMath.list(g.optJSONArray("people"));
                        if (pool.isEmpty()) { NX.toast(a, "Noch keine Teilnehmer"); return; }
                        List<String> w = GiveawayMath.draw(pool, new ArrayList<String>(), g.optInt("count", 1), GiveawayMath.random());
                        try { g.put("winners", new JSONArray(w)).put("drawn", System.currentTimeMillis()); } catch (Exception ignored) { }
                        save(a, g); NX.toast(a, "🏆 " + MyDayMath.join(w)); a.render();
                    }
                }), 6);
                if (!win.isEmpty() && ppl.size() > win.size()) {
                    for (final String w : win) {
                        TextView rr = NX.text(a, "↻ " + w + " neu ziehen (z. B. nicht gemeldet)", 12, C.ACCENT, true);
                        rr.setPadding(0, U.dp(6), 0, U.dp(2));
                        rr.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                List<String> cur = GiveawayMath.list(g.optJSONArray("winners")), ex = new ArrayList<String>(cur);
                                JSONArray rerolled = g.optJSONArray("rerolled"); for (String s : GiveawayMath.list(rerolled)) ex.add(s);
                                List<String> n = GiveawayMath.draw(GiveawayMath.list(g.optJSONArray("people")), ex, 1, GiveawayMath.random());
                                if (n.isEmpty()) { NX.toast(a, "Niemand mehr übrig"); return; }
                                cur.set(cur.indexOf(w), n.get(0));
                                try { if (rerolled == null) { rerolled = new JSONArray(); g.put("rerolled", rerolled); } rerolled.put(w); g.put("winners", new JSONArray(cur)); } catch (Exception ignored) { }
                                save(a, g); NX.toast(a, "Neu: " + n.get(0)); a.render();
                            }
                        });
                        c.addView(rr);
                    }
                }
                LinearLayout hr = HookPost.chooser(a, CFG);
                if (hr != null) {
                    U.add(c, hr, 8);
                    U.add(c, NX.button(a, win.isEmpty() ? "📣 Giveaway in Discord ankündigen" : "📣 Gewinner in Discord posten", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            final boolean w = !GiveawayMath.list(g.optJSONArray("winners")).isEmpty();
                            CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = HookPost.send(a, HookPost.selected(a, CFG), GiveawayMath.discord(g, w, System.currentTimeMillis())); return err == null ? "In Discord gepostet ✓" : "Discord: " + err; } });
                        }
                    }), 6);
                }
                TextView del = NX.text(a, "Giveaway löschen", 12, C.DIM, false);
                del.setPadding(0, U.dp(10), 0, 0);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(g.optString("id"))) { all.remove(x); break; } Db.save(a, KEY, all); openId = ""; a.render(); }
                });
                c.addView(del);
            }
            U.add(p[1], c, 10);
        }
        U.add(p[1], NX.text(a, "Die Ziehung nutzt den sicheren Zufallsgenerator von Android: Jeder Teilnehmer hat die gleiche Chance, doppelte Namen zählen nur einmal.", 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

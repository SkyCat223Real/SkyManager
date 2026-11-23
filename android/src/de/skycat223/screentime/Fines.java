package de.skycat223.screentime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Bussgeldkatalog fuer RP mit Rechner. */
final class Fines {
    private Fines() {}

    static final String KEY = "fines", CFG = "fines_cfg", LAST = "fines_last";
    static JSONObject sel = new JSONObject();
    static String cat = "", who = "";
    static boolean edit = false;

    static List<JSONObject> catalog(MainActivity a) {
        List<JSONObject> l = Db.list(a, KEY);
        if (l.isEmpty() && !Db.obj(a, CFG).optBoolean("init")) {
            l = FineMath.template(System.currentTimeMillis());
            Db.save(a, KEY, l);
            JSONObject c = Db.obj(a, CFG); try { c.put("init", true); } catch (Exception ignored) { } Db.saveObj(a, CFG, c);
        }
        return l;
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Bußgeldkatalog", "Strafen schnell berechnen");
        final List<JSONObject> list = catalog(a);
        final JSONObject cfg = Db.obj(a, CFG);
        final long maxJail = cfg.optLong("maxJail", 0);
        long[] t = FineMath.total(list, sel, maxJail);
        LinearLayout hero = SchoolUi.hero(a);
        hero.addView(NX.text(a, FineMath.euro(t[0]), 26, C.TEXT, true));
        U.add(hero, NX.text(a, t[3] == 0 ? "Hake unten Vergehen an" : t[3] + (t[3] == 1 ? " Vergehen" : " Vergehen") + (t[1] > 0 ? " · " + t[1] + " Hafteinheiten" : "") + (t[2] == 1 ? " · Führerscheinentzug" : ""), 13, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        if (t[3] > 0) {
            LinearLayout rc = U.card(a, "⚖️ Strafe");
            final EditText w = SchoolUi.field(a, who, "Name des Täters (optional)", false);
            U.add(rc, w, 4);
            U.add(rc, NX.text(a, FineMath.summary(list, sel, maxJail, null), 13, C.TEXT, false), 6);
            LinearLayout br = U.hbox(a);
            TextView copy = NX.text(a, "📋 Kopieren", 13, C.ACCENT, true), clear = NX.text(a, "Zurücksetzen", 13, C.DIM, false);
            copy.setPadding(0, U.dp(10), U.dp(18), 0); clear.setPadding(0, U.dp(10), 0, 0);
            copy.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    who = w.getText().toString();
                    String s = FineMath.summary(list, sel, maxJail, who);
                    ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Strafe", s));
                    remember(a, s); NX.toast(a, "Kopiert ✓ (auch für Einsatzberichte gemerkt)");
                }
            });
            clear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sel = new JSONObject(); who = ""; a.render(); } });
            br.addView(copy); br.addView(clear);
            rc.addView(br);
            LinearLayout hr = HookPost.chooser(a, CFG);
            if (hr != null) {
                U.add(rc, hr, 8);
                U.add(rc, NX.button(a, "📣 In Discord posten", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        who = w.getText().toString();
                        remember(a, FineMath.summary(list, sel, maxJail, who));
                        final JSONObject msg = FineMath.discord(list, sel, maxJail, who, cfg.optString("officer"), System.currentTimeMillis());
                        CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = HookPost.send(a, HookPost.selected(a, CFG), msg); return err == null ? "Gepostet ✓" : "Discord: " + err; } });
                    }
                }), 6);
            }
            U.add(p[1], rc, 12);
        }

        List<String> cats = FineMath.categories(list);
        List<TextView> cc = new ArrayList<TextView>();
        cc.add(SchoolUi.chip(a, "Alle", cat.isEmpty(), new View.OnClickListener() { @Override public void onClick(View v) { cat = ""; a.render(); } }));
        for (final String c : cats) cc.add(SchoolUi.chip(a, c, c.equals(cat), new View.OnClickListener() { @Override public void onClick(View v) { cat = c; a.render(); } }));
        LinearLayout cr = U.vbox(a);
        SchoolUi.addRows(a, cr, cc, 4);
        U.add(p[1], cr, 4);
        LinearLayout lc = U.card(a, null);
        for (final JSONObject e : list) {
            if (!cat.isEmpty() && !cat.equals(e.optString("cat"))) continue;
            final String id = e.optString("id");
            int n = sel.optInt(id, 0);
            LinearLayout row = U.hbox(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(U.dp(6), U.dp(8), U.dp(6), U.dp(8));
            if (n > 0) row.setBackground(U.round(C.CARD_HI, 12));
            LinearLayout tx = U.vbox(a);
            tx.addView(NX.text(a, (n > 0 ? "✅ " + (n > 1 ? n + "× " : "") : "") + e.optString("name"), 14, C.TEXT, n > 0));
            tx.addView(NX.text(a, FineMath.euro(e.optLong("fine")) + (e.optInt("jail") > 0 ? " · " + e.optInt("jail") + " HE" : "") + (e.optBoolean("lic") ? " · Führerschein" : ""), 12, C.DIM, false));
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
            if (edit) {
                TextView del = NX.text(a, "Löschen", 12, C.DIM, false);
                del.setPadding(U.dp(10), U.dp(6), U.dp(4), U.dp(6));
                del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(id)) { all.remove(x); break; } Db.save(a, KEY, all); sel.remove(id); a.render(); } });
                row.addView(del);
            } else {
                TextView minus = NX.text(a, "−", 20, n > 0 ? C.ACCENT : C.DIM, true), plus = NX.text(a, "+", 20, C.ACCENT, true);
                minus.setPadding(U.dp(12), 0, U.dp(12), 0); plus.setPadding(U.dp(12), 0, U.dp(6), 0);
                minus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { int k = sel.optInt(id, 0) - 1; try { if (k <= 0) sel.remove(id); else sel.put(id, k); } catch (Exception ignored) { } a.render(); } });
                plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { sel.put(id, Math.min(FineMath.MAX_COUNT, sel.optInt(id, 0) + 1)); } catch (Exception ignored) { } a.render(); } });
                row.addView(minus); row.addView(plus);
            }
            lc.addView(row);
        }
        U.add(p[1], lc, 8);

        LinearLayout ec = U.card(a, "✏️ Katalog anpassen");
        TextView te = NX.text(a, edit ? "Bearbeiten beenden" : "Einträge löschen / hinzufügen", 13, C.ACCENT, true);
        te.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit = !edit; a.render(); } });
        ec.addView(te);
        if (edit) {
            final EditText nm = SchoolUi.field(a, "", "Vergehen", false), ct = SchoolUi.field(a, cat, "Kategorie (z. B. Verkehr)", false), fn = SchoolUi.field(a, "", "Bußgeld in €", true), jl = SchoolUi.field(a, "", "Haft in Hafteinheiten (optional)", true);
            U.add(ec, nm, 6); U.add(ec, ct, 4); U.add(ec, fn, 4); U.add(ec, jl, 4);
            final boolean[] lic = {false};
            final TextView lt = SchoolUi.chip(a, "Führerscheinentzug", false, null);
            lt.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { lic[0] = !lic[0]; lt.setText((lic[0] ? "✓ " : "") + "Führerscheinentzug"); } });
            U.add(ec, lt, 6);
            U.add(ec, NX.button(a, "Hinzufügen", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    long f = 0; int j = 0;
                    try { f = Long.parseLong(fn.getText().toString().trim()); } catch (Exception ignored) { }
                    try { j = Integer.parseInt(jl.getText().toString().trim()); } catch (Exception ignored) { }
                    JSONObject e = FineMath.entry(Db.id(), ct.getText().toString(), nm.getText().toString(), f, j, lic[0]);
                    if (e == null) { NX.toast(a, "Bitte Vergehen und Betrag eingeben"); return; }
                    List<JSONObject> all = Db.list(a, KEY);
                    if (all.size() >= FineMath.MAX_ENTRIES) { NX.toast(a, "Höchstens " + FineMath.MAX_ENTRIES + " Einträge"); return; }
                    all.add(e); Db.save(a, KEY, all); a.render();
                }
            }), 6);
            final EditText mj = SchoolUi.field(a, maxJail > 0 ? String.valueOf(maxJail) : "", "Höchsthaft in HE (leer = keine Grenze)", true);
            final EditText of = SchoolUi.field(a, cfg.optString("officer"), "Dein Name für Discord (optional)", false);
            U.add(ec, mj, 10); U.add(ec, of, 4);
            U.add(ec, NX.button(a, "Einstellungen speichern", new View.OnClickListener() {
                @Override public void onClick(View v) { long m = 0; try { m = Math.max(0, Long.parseLong(mj.getText().toString().trim())); } catch (Exception ignored) { } try { cfg.put("maxJail", m).put("officer", CashMath.clean(of.getText().toString(), 40)); } catch (Exception ignored) { } Db.saveObj(a, CFG, cfg); NX.toast(a, "Gespeichert ✓"); a.render(); }
            }), 6);
            U.add(ec, NX.button(a, "Vorlage zusätzlich laden", new View.OnClickListener() {
                @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (JSONObject e : FineMath.template(System.currentTimeMillis())) if (all.size() < FineMath.MAX_ENTRIES) all.add(e); Db.save(a, KEY, all); NX.toast(a, "Vorlage geladen"); a.render(); }
            }), 6);
        }
        U.add(p[1], ec, 12);
        U.add(p[1], NX.text(a, "Die Vorlage ist ein Vorschlag für Roleplay. Passe Beträge und Vergehen an die Regeln deines Servers an.", 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }

    static void remember(MainActivity a, String s) {
        if (s == null || s.isEmpty()) return;
        try { Db.saveObj(a, LAST, new JSONObject().put("text", s).put("ts", System.currentTimeMillis())); } catch (Exception ignored) { }
    }
}

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

/** Regelwerk fuer Discord- und RP-Server, mit Vorlagen und Discord-Post. */
final class Rules {
    private Rules() {}

    static final String KEY = "rulesets", CFG = "rules_cfg";
    static String openId = "";
    static int editIdx = -1;

    static void save(MainActivity a, JSONObject rs) {
        List<JSONObject> all = Db.list(a, KEY);
        for (int i = 0; i < all.size(); i++) if (all.get(i).optString("id").equals(rs.optString("id"))) { all.set(i, rs); Db.save(a, KEY, all); return; }
        all.add(rs); Db.save(a, KEY, all);
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Regelwerk", "Regeln für deine Server");
        final List<JSONObject> sets = Db.list(a, KEY);
        final ZoneId z = ZoneId.systemDefault();

        LinearLayout nc = U.card(a, "📜 Neues Regelwerk");
        final EditText name = SchoolUi.field(a, "", "Name (z. B. Discord, GrenzRP)", false);
        U.add(nc, name, 4);
        List<TextView> tc = new ArrayList<TextView>();
        tc.add(SchoolUi.chip(a, "Leer", false, new View.OnClickListener() { @Override public void onClick(View v) { create(a, name.getText().toString(), -1); } }));
        for (int i = 0; i < RulesMath.TEMPLATE_NAMES.length; i++) { final int t = i; tc.add(SchoolUi.chip(a, "Vorlage: " + RulesMath.TEMPLATE_NAMES[i], false, new View.OnClickListener() { @Override public void onClick(View v) { create(a, name.getText().toString().trim().isEmpty() ? RulesMath.TEMPLATE_NAMES[t] : name.getText().toString(), t); } })); }
        LinearLayout tr = U.vbox(a);
        SchoolUi.addRows(a, tr, tc, 2);
        U.add(nc, tr, 6);
        U.add(p[1], nc, 12);

        for (final JSONObject rs : sets) {
            final boolean open = rs.optString("id").equals(openId);
            final List<String> rules = RulesMath.rules(rs);
            LinearLayout c = U.card(a, null);
            c.addView(NX.text(a, "📜 " + rs.optString("name"), 16, C.TEXT, true));
            U.add(c, NX.text(a, rules.size() + (rules.size() == 1 ? " Regel · " : " Regeln · ") + RulesMath.stand(rs, z), 12, C.DIM, false), 2);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openId = open ? "" : rs.optString("id"); editIdx = -1; a.render(); } });
            if (open) {
                for (int i = 0; i < rules.size(); i++) {
                    final int idx = i;
                    LinearLayout row = U.vbox(a);
                    row.setBackground(U.round(C.CARD_HI, 12));
                    row.setPadding(U.dp(10), U.dp(8), U.dp(10), U.dp(8));
                    if (editIdx == i) {
                        final EditText ed = SchoolUi.field(a, rules.get(i), "Regel", false);
                        ed.setSingleLine(false); ed.setMaxLines(8);
                        row.addView(ed);
                        TextView ok = NX.text(a, "Speichern", 13, C.ACCENT, true);
                        ok.setPadding(0, U.dp(8), 0, 0);
                        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { RulesMath.edit(rs, idx, ed.getText().toString(), System.currentTimeMillis()); save(a, rs); editIdx = -1; a.render(); } });
                        row.addView(ok);
                    } else {
                        row.addView(NX.text(a, "§" + (i + 1) + "  " + rules.get(i), 14, C.TEXT, false));
                        LinearLayout act = U.hbox(a);
                        act.setGravity(Gravity.CENTER_VERTICAL);
                        String[] lab = {"▲", "▼", "Bearbeiten", "Löschen"};
                        for (int k = 0; k < lab.length; k++) {
                            final int kk = k;
                            TextView b = NX.text(a, lab[k], 12, k == 3 ? C.DIM : C.ACCENT, true);
                            b.setPadding(0, U.dp(6), U.dp(16), 0);
                            b.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    long now = System.currentTimeMillis();
                                    if (kk == 0) RulesMath.move(rs, idx, -1, now); else if (kk == 1) RulesMath.move(rs, idx, 1, now); else if (kk == 2) { editIdx = idx; a.render(); return; } else RulesMath.remove(rs, idx, now);
                                    save(a, rs); a.render();
                                }
                            });
                            act.addView(b);
                        }
                        row.addView(act);
                    }
                    U.add(c, row, 6);
                }
                final EditText nr = SchoolUi.field(a, "", "Neue Regel(n), eine pro Zeile", false);
                nr.setSingleLine(false); nr.setMaxLines(8);
                U.add(c, nr, 10);
                U.add(c, NX.button(a, "Hinzufügen", new View.OnClickListener() {
                    @Override public void onClick(View v) { int n = RulesMath.addMany(rs, nr.getText().toString(), System.currentTimeMillis()); if (n == 0) { NX.toast(a, "Bitte einen Regeltext eingeben"); return; } save(a, rs); a.render(); }
                }), 4);
                LinearLayout hr = HookPost.chooser(a, CFG);
                if (hr != null) {
                    U.add(c, hr, 10);
                    U.add(c, NX.button(a, "📣 In Discord posten", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (RulesMath.rules(rs).isEmpty()) { NX.toast(a, "Noch keine Regeln"); return; }
                            CloudPage.bg(a, null, new CloudPage.Job() {
                                @Override public String run() throws Exception {
                                    List<JSONObject> msgs = RulesMath.discord(rs, 0x3DDC84, ZoneId.systemDefault());
                                    for (int i = 0; i < msgs.size(); i++) { String err = HookPost.send(a, HookPost.selected(a, CFG), msgs.get(i)); if (err != null) return "Discord (Teil " + (i + 1) + "): " + err; if (i < msgs.size() - 1) Thread.sleep(1200); }
                                    return "Regelwerk gepostet ✓" + (msgs.size() > 1 ? " (" + msgs.size() + " Nachrichten)" : "");
                                }
                            });
                        }
                    }), 6);
                }
                LinearLayout br = U.hbox(a);
                TextView share = NX.text(a, "Als Text teilen", 13, C.ACCENT, true), del = NX.text(a, "Regelwerk löschen", 13, C.DIM, false);
                share.setPadding(0, U.dp(10), U.dp(18), 0); del.setPadding(0, U.dp(10), 0, 0);
                share.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RulesMath.text(rs, ZoneId.systemDefault())); a.startActivity(Intent.createChooser(i, "Regelwerk teilen")); } });
                del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { List<JSONObject> all = Db.list(a, KEY); for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(rs.optString("id"))) { all.remove(x); break; } Db.save(a, KEY, all); openId = ""; a.render(); } });
                br.addView(share); br.addView(del);
                c.addView(br);
            }
            U.add(p[1], c, 10);
        }
        if (Db.list(a, WebhookPage.HOOKS).isEmpty()) U.add(p[1], NX.text(a, "Tipp: Mit einem Webhook aus dem Webhook-Studio postest du das Regelwerk direkt in einen Discord-Kanal.", 11, C.DIM, false), 6);
        U.add(p[1], NX.text(a, "Jede Änderung erhöht die Version, Stand und Version stehen unter dem Regelwerk. Die Vorlagen sind Vorschläge, pass sie für deinen Server an.", 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }

    static void create(MainActivity a, String name, int template) {
        JSONObject rs = RulesMath.ruleset(Db.id(), name, System.currentTimeMillis());
        if (template >= 0) { List<String> l = new ArrayList<String>(); for (String s : RulesMath.TEMPLATES[template]) l.add(s); RulesMath.setRules(rs, l); }
        save(a, rs); openId = rs.optString("id"); editIdx = -1; a.render();
    }
}

package de.skycat223.screentime;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Event-Planer fuer Community-Events mit Discord-Ankuendigung und Erinnerung. */
final class EventPlanner {
    private EventPlanner() {}

    static final String KEY = "events";
    static long pickAt = 0;
    static String openId = "";

    static void save(MainActivity a, JSONObject e) {
        List<JSONObject> all = Db.list(a, KEY);
        for (int i = 0; i < all.size(); i++) if (all.get(i).optString("id").equals(e.optString("id"))) { all.set(i, e); Db.save(a, KEY, all); return; }
        all.add(e); Db.save(a, KEY, all);
    }

    static JSONObject hook(MainActivity a, String id) {
        for (JSONObject h : Db.list(a, WebhookPage.HOOKS)) if (h.optString("id").equals(id)) return h;
        return null;
    }

    static String announce(MainActivity a, JSONObject e, JSONObject h) throws Exception {
        String[] w = DiscordMath.parseWebhook(h.optString("url"));
        if (w == null) return "Webhook ungültig";
        List<String> errs = new ArrayList<String>();
        JSONObject msg = DiscordMath.build(EventMath.draft(e, "announce"), errs, System.currentTimeMillis());
        if (!errs.isEmpty()) return errs.get(0);
        String[] r = WebhookPage.http("POST", DiscordMath.apiUrl(w), msg.toString());
        return DiscordMath.explain(Integer.parseInt(r[0]), r[1]);
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Event-Planer", "Events für deine Community");
        final long now = System.currentTimeMillis();
        final List<JSONObject> events = EventMath.sorted(Db.list(a, KEY), now);
        final List<JSONObject> hooks = Db.list(a, WebhookPage.HOOKS);
        final List<JSONObject> sched = Db.list(a, Watcher.SCHED);
        JSONObject next = null;
        for (JSONObject e : events) if (!EventMath.past(e, now)) { next = e; break; }
        LinearLayout hero = SchoolUi.hero(a);
        hero.addView(NX.text(a, next == null ? "Kein Event geplant" : "🎉 " + next.optString("title"), 20, C.TEXT, true));
        U.add(hero, NX.text(a, next == null ? "Plane unten dein nächstes Event" : EventMath.countdown(next.optLong("at"), now) + " · " + EventMath.people(next).size() + " dabei", 14, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        LinearLayout nc = U.card(a, "➕ Neues Event");
        final EditText title = SchoolUi.field(a, "", "Titel (z. B. RP-Abend, Server-Event)", false), place = SchoolUi.field(a, "", "Wo (Server, Spiel, Kanal)", false), desc = SchoolUi.field(a, "", "Beschreibung (optional)", false), max = SchoolUi.field(a, "", "Max. Teilnehmer (leer = unbegrenzt)", true);
        desc.setSingleLine(false); desc.setMaxLines(5);
        U.add(nc, title, 4);
        final TextView when = NX.text(a, pickAt > now ? "🕒 " + EventMath.when(pickAt, ZoneId.systemDefault()) : "🕒 Datum & Uhrzeit wählen", 14, pickAt > now ? C.TEXT : C.ACCENT, true);
        when.setPadding(0, U.dp(10), 0, U.dp(10));
        when.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final Calendar cal = Calendar.getInstance();
                if (pickAt > 0) cal.setTimeInMillis(pickAt); else cal.add(Calendar.DAY_OF_MONTH, 1);
                new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker dp, final int y, final int m, final int d) {
                        new TimePickerDialog(a, new TimePickerDialog.OnTimeSetListener() {
                            @Override public void onTimeSet(TimePicker tp, int h, int min) {
                                Calendar c = Calendar.getInstance(); c.set(y, m, d, h, min, 0); c.set(Calendar.MILLISECOND, 0);
                                pickAt = c.getTimeInMillis();
                                when.setText("🕒 " + EventMath.when(pickAt, ZoneId.systemDefault()));
                                when.setTextColor(C.TEXT);
                            }
                        }, pickAt > 0 ? cal.get(Calendar.HOUR_OF_DAY) : 20, pickAt > 0 ? cal.get(Calendar.MINUTE) : 0, true).show();
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        nc.addView(when);
        U.add(nc, place, 4); U.add(nc, desc, 4); U.add(nc, max, 4);
        U.add(nc, NX.button(a, "Event erstellen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (pickAt <= System.currentTimeMillis()) { NX.toast(a, "Bitte einen Zeitpunkt in der Zukunft wählen"); return; }
                int mx = 0; try { mx = Integer.parseInt(max.getText().toString().trim()); } catch (Exception ignored) { }
                JSONObject e = EventMath.event(Db.id(), title.getText().toString(), pickAt, place.getText().toString(), desc.getText().toString(), mx);
                if (e == null) { NX.toast(a, "Bitte einen Titel eingeben"); return; }
                save(a, e); pickAt = 0; openId = e.optString("id");
                NX.toast(a, "Event erstellt ✓"); a.render();
            }
        }), 8);
        U.add(p[1], nc, 12);

        for (final JSONObject e : events) {
            final boolean past = EventMath.past(e, now), isOpen = e.optString("id").equals(openId);
            LinearLayout c = U.card(a, null);
            TextView tt = NX.text(a, (past ? "✔️ " : "🎉 ") + e.optString("title"), 16, past ? C.DIM : C.TEXT, true);
            c.addView(tt);
            List<String> ppl = EventMath.people(e);
            int mx = e.optInt("max", 0);
            U.add(c, NX.text(a, EventMath.when(e.optLong("at"), ZoneId.systemDefault()) + (e.optString("place").isEmpty() ? "" : " · 📍 " + e.optString("place")), 12, C.DIM, false), 2);
            U.add(c, NX.text(a, (past ? "vorbei" : EventMath.countdown(e.optLong("at"), now)) + " · 👥 " + ppl.size() + (mx > 0 ? " / " + mx : "") + (EventMath.reminderPlanned(sched, e.optString("id")) ? " · ⏰ Erinnerung geplant" : ""), 13, past ? C.DIM : C.ACCENT, true), 4);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openId = isOpen ? "" : e.optString("id"); a.render(); } });
            if (isOpen) {
                if (!e.optString("desc").isEmpty()) U.add(c, NX.text(a, e.optString("desc"), 13, C.TEXT, false), 8);
                LinearLayout pl = U.vbox(a);
                for (final String n : ppl) {
                    LinearLayout r = U.hbox(a);
                    r.setGravity(Gravity.CENTER_VERTICAL);
                    r.addView(NX.text(a, "• " + n, 14, C.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView x = NX.text(a, "✕", 14, C.DIM, true);
                    x.setPadding(U.dp(12), U.dp(4), U.dp(4), U.dp(4));
                    x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { EventMath.leave(e, n); save(a, e); a.render(); } });
                    r.addView(x);
                    pl.addView(r);
                }
                U.add(c, pl, 8);
                if (!past) {
                    LinearLayout jr = U.hbox(a);
                    jr.setGravity(Gravity.CENTER_VERTICAL);
                    final EditText who = SchoolUi.field(a, "", "Teilnehmer hinzufügen", false);
                    jr.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView add = NX.text(a, "Dazu", 13, C.ACCENT, true);
                    add.setPadding(U.dp(10), U.dp(8), U.dp(4), U.dp(8));
                    add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { String err = EventMath.join(e, who.getText().toString()); if (err != null) { NX.toast(a, err); return; } save(a, e); a.render(); } });
                    jr.addView(add);
                    U.add(c, jr, 4);
                    if (!hooks.isEmpty()) {
                        final String hookSel = e.optString("hook").isEmpty() ? hooks.get(0).optString("id") : e.optString("hook");
                        List<TextView> hc = new ArrayList<TextView>();
                        for (final JSONObject h : hooks) hc.add(SchoolUi.chip(a, "📣 " + h.optString("name"), hookSel.equals(h.optString("id")), new View.OnClickListener() { @Override public void onClick(View v) { try { e.put("hook", h.optString("id")); } catch (Exception ignored) { } save(a, e); a.render(); } }));
                        LinearLayout hr = U.vbox(a);
                        SchoolUi.addRows(a, hr, hc, 2);
                        U.add(c, hr, 8);
                        U.add(c, NX.button(a, "📣 Jetzt in Discord ankündigen", new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                final JSONObject h = hook(a, hookSel);
                                if (h == null) { NX.toast(a, "Webhook nicht gefunden"); return; }
                                CloudPage.bg(a, null, new CloudPage.Job() { @Override public String run() throws Exception { String err = announce(a, e, h); return err == null ? "Angekündigt ✓" : "Discord: " + err; } });
                            }
                        }), 6);
                        final boolean planned = EventMath.reminderPlanned(sched, e.optString("id"));
                        U.add(c, NX.button(a, planned ? "Erinnerung abbrechen" : "⏰ Erinnerung 1 Std vorher planen", new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                List<JSONObject> s = Db.list(a, Watcher.SCHED);
                                if (planned) { EventMath.dropReminders(s, e.optString("id")); Db.save(a, Watcher.SCHED, s); Watcher.schedule(a); NX.toast(a, "Erinnerung abgebrochen"); a.render(); return; }
                                JSONObject h = hook(a, hookSel);
                                JSONObject r = h == null ? null : EventMath.reminder(e, hookSel, h.optString("name"), Db.id(), System.currentTimeMillis());
                                if (r == null) { NX.toast(a, h == null ? "Webhook nicht gefunden" : "Zu spät: Das Event beginnt in weniger als einer Stunde"); return; }
                                s.add(r); Db.save(a, Watcher.SCHED, s); Watcher.schedule(a);
                                NX.toast(a, "⏰ Erinnerung kommt " + EventMath.when(r.optLong("at"), ZoneId.systemDefault()));
                                a.render();
                            }
                        }), 6);
                    } else U.add(c, NX.text(a, "Speichere im Webhook-Studio einen Webhook, um Events in Discord anzukündigen.", 11, C.DIM, false), 8);
                }
                TextView del = NX.text(a, "Event löschen", 12, C.DIM, false);
                del.setPadding(0, U.dp(10), 0, 0);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        List<JSONObject> all = Db.list(a, KEY);
                        for (int x = 0; x < all.size(); x++) if (all.get(x).optString("id").equals(e.optString("id"))) { all.remove(x); break; }
                        Db.save(a, KEY, all);
                        List<JSONObject> s = Db.list(a, Watcher.SCHED);
                        if (EventMath.dropReminders(s, e.optString("id")) > 0) { Db.save(a, Watcher.SCHED, s); Watcher.schedule(a); }
                        openId = ""; a.render();
                    }
                });
                c.addView(del);
            }
            U.add(p[1], c, 10);
        }
        U.add(p[1], NX.text(a, "Tippe auf ein Event für Teilnehmer und Discord. Die Zeit erscheint in Discord bei jedem in seiner eigenen Zeitzone, mit Countdown.", 11, C.DIM, false), 6);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

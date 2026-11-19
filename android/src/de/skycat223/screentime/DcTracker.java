package de.skycat223.screentime;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Discord-Server-Tracker: Mitglieder und Online-Zahl ueber oeffentliche Einladungen, mit Verlauf. */
final class DcTracker {
    private DcTracker() {}

    static final String KEY = "dcservers";
    static final long AUTO = 10 * 60_000L;
    static long lastAuto = 0;

    static final class Chart extends View {
        final JSONArray h; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), f = new Paint(Paint.ANTI_ALIAS_FLAG);
        Chart(Context c, JSONArray h) { super(c); this.h = h; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(U.dp(2)); p.setColor(C.ACCENT); f.setColor((C.ACCENT & 0x00FFFFFF) | 0x33000000); }
        @Override protected void onDraw(Canvas cv) {
            int n = h.length();
            if (n < 2) return;
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (int i = 0; i < n; i++) { long m = h.optJSONObject(i).optLong("m"); min = Math.min(min, m); max = Math.max(max, m); }
            if (max == min) { max++; min--; }
            float w = getWidth(), ht = getHeight() - U.dp(4);
            Path line = new Path(), fill = new Path();
            for (int i = 0; i < n; i++) {
                float x = w * i / (n - 1), y = ht - ht * (h.optJSONObject(i).optLong("m") - min) / (float) (max - min) + U.dp(2);
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, getHeight()); fill.lineTo(x, y); } else { line.lineTo(x, y); fill.lineTo(x, y); }
            }
            fill.lineTo(w, getHeight()); fill.close();
            cv.drawPath(fill, f); cv.drawPath(line, p);
        }
    }

    /** Laedt eine Einladung. @return Auswertung, wirft verstaendliche Fehler. */
    static JSONObject fetch(String code) throws Exception {
        String[] r = WebhookPage.http("GET", DcTrackMath.apiUrl(code), null);
        int st = Integer.parseInt(r[0]);
        if (st != 200) throw new Exception(DcTrackMath.error(st, r[1]));
        JSONObject o = DcTrackMath.parse(r[1]);
        if (o == null) throw new Exception("Unerwartete Antwort von Discord");
        return o;
    }

    /** Uebertraegt eine frische Messung in den gespeicherten Eintrag. */
    static void apply(JSONObject s, JSONObject o, long now) throws Exception {
        for (String k : new String[]{"name", "gid", "icon", "members", "online", "boosts", "desc", "expires"}) s.put(k, o.get(k));
        JSONArray h = s.optJSONArray("hist");
        if (h == null) { h = new JSONArray(); s.put("hist", h); }
        DcTrackMath.record(h, now, o.optLong("members"), o.optLong("online"));
        s.put("updated", now);
        s.remove("err");
    }

    /** Alle aktualisieren. @return Zahl der erfolgreichen. */
    static int refreshAll(Context c) {
        List<JSONObject> l = Db.list(c, KEY);
        int ok = 0;
        long now = System.currentTimeMillis();
        for (JSONObject s : l) {
            try { apply(s, fetch(s.optString("code")), now); ok++; } catch (Exception e) { try { s.put("err", e.getMessage()); } catch (Exception ignored) { } }
        }
        Db.save(c, KEY, l);
        return ok;
    }

    static View page(final MainActivity a) {
        LinearLayout[] p = NX.page(a, "Discord-Tracker", "Mitglieder & Online deiner Server");
        final List<JSONObject> l = Db.list(a, KEY);
        long now = System.currentTimeMillis();
        boolean due = false;
        for (JSONObject s : l) if (now - s.optLong("updated") > AUTO) due = true;
        if (due && now - lastAuto > AUTO) {
            lastAuto = now;
            new Thread(new Runnable() { @Override public void run() { refreshAll(a); a.runOnUiThread(new Runnable() { @Override public void run() { a.render(); } }); } }).start();
        }
        LinearLayout hero = SchoolUi.hero(a);
        long total = 0, on = 0;
        for (JSONObject s : l) { total += s.optLong("members"); on += s.optLong("online"); }
        hero.addView(NX.text(a, l.isEmpty() ? "Noch keine Server" : RobloxMath.num(total) + " Mitglieder", 24, C.TEXT, true));
        U.add(hero, NX.text(a, l.isEmpty() ? "Füge unten eine Einladung hinzu" : "🟢 " + RobloxMath.num(on) + " online · " + l.size() + (l.size() == 1 ? " Server" : " Server"), 13, C.ACCENT, true), 4);
        U.add(p[1], hero, 12);

        for (final JSONObject s : l) {
            LinearLayout c = U.card(a, null);
            LinearLayout head = U.hbox(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            final ImageView iv = new ImageView(a);
            iv.setBackground(U.round(C.CARD_HI, 14));
            head.addView(iv, new LinearLayout.LayoutParams(U.dp(44), U.dp(44)));
            final String url = DcTrackMath.iconUrl(s.optString("gid"), s.optString("icon"));
            if (url != null) new Thread(new Runnable() { @Override public void run() { final Bitmap b = CommunityPages.image(url); if (b != null) a.runOnUiThread(new Runnable() { @Override public void run() { iv.setImageBitmap(b); } }); } }).start();
            LinearLayout t = U.vbox(a);
            t.setPadding(U.dp(10), 0, 0, 0);
            t.addView(NX.text(a, s.optString("name"), 16, C.TEXT, true));
            t.addView(NX.text(a, "discord.gg/" + s.optString("code"), 11, C.DIM, false));
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            c.addView(head);
            long m = s.optLong("members"), o = s.optLong("online");
            U.add(c, NX.text(a, "👥 " + RobloxMath.num(m) + " Mitglieder · 🟢 " + RobloxMath.num(o) + " online (" + DcTrackMath.onlinePercent(m, o) + " %)", 14, C.TEXT, false), 8);
            JSONArray h = s.optJSONArray("hist");
            U.add(c, NX.text(a, "📈 24 h: " + DcTrackMath.signed(DcTrackMath.growth(h, now, 86_400_000L)) + " · 7 Tage: " + DcTrackMath.signed(DcTrackMath.growth(h, now, 7 * 86_400_000L)) + (s.optLong("boosts") > 0 ? " · 💎 " + s.optLong("boosts") + " Boosts" : ""), 13, C.DIM, false), 4);
            if (h != null && h.length() >= 2) {
                U.add(c, NX.text(a, "Höchster Online-Wert: " + RobloxMath.num(DcTrackMath.peakOnline(h)), 12, C.DIM, false), 4);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, U.dp(70));
                cl.topMargin = U.dp(8);
                c.addView(new Chart(a, h), cl);
            } else U.add(c, NX.text(a, "Der Verlauf füllt sich mit jeder Aktualisierung (höchstens alle 30 Minuten ein Messpunkt).", 11, C.DIM, false), 6);
            if (s.optBoolean("expires")) U.add(c, NX.text(a, "⚠️ Diese Einladung läuft ab. Nimm eine ohne Ablaufdatum, sonst bricht die Verfolgung ab.", 11, 0xFFFFB74D, true), 6);
            if (!s.optString("err").isEmpty()) U.add(c, NX.text(a, "Letzte Aktualisierung fehlgeschlagen: " + s.optString("err"), 11, 0xFFFF6B6B, false), 6);
            LinearLayout br = U.hbox(a);
            TextView share = NX.text(a, "Teilen", 13, C.ACCENT, true), del = NX.text(a, "Entfernen", 13, C.DIM, false);
            share.setPadding(0, U.dp(10), U.dp(18), 0); del.setPadding(0, U.dp(10), 0, 0);
            share.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.optString("name") + ": " + RobloxMath.num(s.optLong("members")) + " Mitglieder, " + RobloxMath.num(s.optLong("online")) + " online\nhttps://discord.gg/" + s.optString("code"));
                    a.startActivity(Intent.createChooser(i, "Teilen"));
                }
            });
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    List<JSONObject> all = Db.list(a, KEY);
                    for (int k = 0; k < all.size(); k++) if (all.get(k).optString("id").equals(s.optString("id"))) { all.remove(k); break; }
                    Db.save(a, KEY, all); a.render();
                }
            });
            br.addView(share); br.addView(del);
            c.addView(br);
            U.add(p[1], c, 12);
        }

        LinearLayout add = U.card(a, "➕ Server hinzufügen");
        U.add(add, NX.text(a, "Einladungslink deines Servers einfügen, z. B. discord.gg/abc123. Am besten eine Einladung ohne Ablaufdatum. Der Server muss keinen Bot haben.", 12, C.DIM, false), 2);
        final EditText f = SchoolUi.field(a, "", "discord.gg/…", false);
        U.add(add, f, 8);
        final TextView st = NX.text(a, "", 12, C.DIM, false);
        U.add(add, NX.button(a, "Hinzufügen", new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String code = DcTrackMath.inviteCode(f.getText().toString());
                if (code == null) { NX.toast(a, "Das ist kein Discord-Einladungslink"); return; }
                if (DcTrackMath.known(Db.list(a, KEY), code)) { NX.toast(a, "Diese Einladung ist schon drin"); return; }
                CloudPage.bg(a, st, new CloudPage.Job() {
                    @Override public String run() throws Exception {
                        JSONObject o = fetch(code);
                        JSONObject s = Db.o("id", Db.id(), "code", code, "added", System.currentTimeMillis());
                        apply(s, o, System.currentTimeMillis());
                        List<JSONObject> all = Db.list(a, KEY);
                        all.add(s);
                        Db.save(a, KEY, all);
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
        U.add(p[1], NX.text(a, "Die Zahlen kommen direkt von Discord (ungefähre Werte, wie Discord sie bei Einladungen anzeigt).", 11, C.DIM, false), 8);
        SchoolUi.reveal(p[1]);
        return p[0];
    }
}

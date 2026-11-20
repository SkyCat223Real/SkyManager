package de.skycat223.screentime;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Gemeinsam: Webhook waehlen und fertige Nachricht senden. */
final class HookPost {
    private HookPost() {}

    /** @return null = gesendet, sonst Fehlermeldung. */
    static String send(MainActivity a, String hookId, JSONObject msg) throws Exception {
        for (JSONObject h : Db.list(a, WebhookPage.HOOKS)) {
            if (!h.optString("id").equals(hookId)) continue;
            String[] w = DiscordMath.parseWebhook(h.optString("url"));
            if (w == null) return "Webhook ungültig";
            String[] r = WebhookPage.http("POST", DiscordMath.apiUrl(w), msg.toString());
            return DiscordMath.explain(Integer.parseInt(r[0]), r[1]);
        }
        return "Webhook nicht gefunden";
    }

    /** Gewaehlter Webhook aus der Einstellung (Standard: erster). "" wenn keiner gespeichert. */
    static String selected(MainActivity a, String cfgKey) {
        List<JSONObject> hooks = Db.list(a, WebhookPage.HOOKS);
        if (hooks.isEmpty()) return "";
        String s = Db.obj(a, cfgKey).optString("hook");
        for (JSONObject h : hooks) if (h.optString("id").equals(s)) return s;
        return hooks.get(0).optString("id");
    }

    /** Auswahl-Chips; null, wenn keine Webhooks gespeichert sind. */
    static LinearLayout chooser(final MainActivity a, final String cfgKey) {
        List<JSONObject> hooks = Db.list(a, WebhookPage.HOOKS);
        if (hooks.isEmpty()) return null;
        String sel = selected(a, cfgKey);
        List<TextView> hc = new ArrayList<TextView>();
        for (final JSONObject h : hooks) hc.add(SchoolUi.chip(a, "📣 " + h.optString("name"), sel.equals(h.optString("id")), new View.OnClickListener() {
            @Override public void onClick(View v) { JSONObject o = Db.obj(a, cfgKey); try { o.put("hook", h.optString("id")); } catch (Exception ignored) { } Db.saveObj(a, cfgKey, o); a.render(); }
        }));
        LinearLayout r = U.vbox(a);
        SchoolUi.addRows(a, r, hc, 2);
        return r;
    }
}

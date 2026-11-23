package de.skycat223.screentime;

import android.view.View;
import android.widget.LinearLayout;

/** Menue: neue Werkzeuge ab 9.44 vorne, alles andere unveraendert aus NewToolsBase (9.43). */
final class NewTools {
    private NewTools() {}

    static final Object[][] MINE = {
            {"team", "Team-Verwaltung", Integer.valueOf(R.drawable.ic_team)},
            {"fines", "Bußgeldkatalog", Integer.valueOf(R.drawable.ic_money)},
            {"reports", "Einsatzberichte", Integer.valueOf(R.drawable.ic_sos)},
            {"rbxcheck", "Spieler-Check", Integer.valueOf(R.drawable.ic_shield)},
            {"giveaway", "Giveaways", Integer.valueOf(R.drawable.ic_gift)},
            {"rules", "Regelwerk", Integer.valueOf(R.drawable.ic_report)},
            {"rbxgroups", "Roblox-Gruppen", Integer.valueOf(R.drawable.ic_team)},
            {"apply", "Bewerbungen", Integer.valueOf(R.drawable.ic_report)},
            {"events", "Event-Planer", Integer.valueOf(R.drawable.ic_star)},
            {"dctrack", "Discord-Tracker", Integer.valueOf(R.drawable.ic_social)},
            {"modlog", "Mod-Log", Integer.valueOf(R.drawable.ic_shield)},
            {"widget", "Homescreen-Widget", Integer.valueOf(R.drawable.ic_today)},
    };
    static final String[][] MY_SECTIONS = {{"Neu in 9.53-9.55", "team", "fines", "reports"}, {"Neu in 9.50-9.52", "rbxcheck", "giveaway", "rules"}, {"Neu in 9.47-9.49", "rbxgroups", "apply", "events"}, {"Neu in 9.44-9.46", "dctrack", "modlog", "widget"}};

    static final Object[][] ENTRIES = concat(MINE, NewToolsBase.ENTRIES);
    static final String[][] SECTION_DEFS = concatS(MY_SECTIONS, NewToolsBase.SECTION_DEFS);

    static Object[][] concat(Object[][] a, Object[][] b) { Object[][] r = new Object[a.length + b.length][]; System.arraycopy(a, 0, r, 0, a.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }
    static String[][] concatS(String[][] a, String[][] b) { String[][] r = new String[a.length + b.length][]; System.arraycopy(a, 0, r, 0, a.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }

    static Object[][] grid(Object[][] base) { return concat(base, ENTRIES); }

    static String[][] sections(String[][] base) { return concatS(SECTION_DEFS, base); }

    static boolean mine(String id) { for (Object[] e : MINE) if (e[0].equals(id)) return true; return false; }

    static boolean isNew(String id) { for (Object[] e : ENTRIES) if (e[0].equals(id)) return true; return false; }

    static View build(MainActivity a, String id) {
        if (id == null || !mine(id)) return NewToolsBase.build(a, id);
        try { if (a != null) DQ.tool(a, id); } catch (Throwable ignored) { }
        try {
            if ("dctrack".equals(id)) return DcTracker.page(a);
            if ("modlog".equals(id)) return ModLog.page(a);
            if ("rbxgroups".equals(id)) return RbxGroups.page(a);
            if ("apply".equals(id)) return Applications.page(a);
            if ("events".equals(id)) return EventPlanner.page(a);
            if ("rbxcheck".equals(id)) return RbxPlayerCheck.page(a);
            if ("giveaway".equals(id)) return Giveaways.page(a);
            if ("rules".equals(id)) return Rules.page(a);
            if ("team".equals(id)) return Team.page(a);
            if ("fines".equals(id)) return Fines.page(a);
            if ("reports".equals(id)) return Reports.page(a);
            return MyDayWidget.page(a);
        } catch (Throwable t) {
            LinearLayout[] p = NX.page(a, "Fehler", "Diese Seite konnte nicht geöffnet werden");
            LinearLayout c = U.card(a, "Das ist nicht deine Schuld");
            U.add(c, NX.text(a, id + ": " + t, 13, C.DIM, false), 4);
            U.add(p[1], c, 14);
            return p[0];
        }
    }
}

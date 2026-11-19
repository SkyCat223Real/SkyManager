package de.skycat223.screentime;

import org.json.JSONObject;

import java.util.List;

/** Homescreen-Widget "Mein Tag": Zeilen fuer die Anzeige. Reine Logik. */
final class WidgetMath {
    private WidgetMath() {}

    static final String[] WD = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    /** Aktuelle oder naechste Stunde: "Jetzt: 3. Mathe (bis 10:25)" / "Als Nächstes 10:40: Deutsch" / null. */
    static String lessonLine(List<String[]> lessons, int nowMin) {
        for (String[] l : lessons) {
            int i = Integer.parseInt(l[0]) - 1;
            if (i < 0 || i >= MyDayMath.START.length) continue;
            if (nowMin >= MyDayMath.START[i] && nowMin < MyDayMath.END[i]) return "▶ Jetzt: " + l[0] + ". " + l[1] + " (bis " + MyDayMath.hm(MyDayMath.END[i]) + ")";
            if (nowMin < MyDayMath.START[i]) return "⏭ " + MyDayMath.hm(MyDayMath.START[i]) + ": " + l[1];
        }
        return lessons.isEmpty() ? null : "✅ Schule für heute geschafft";
    }

    /** Aufgaben-Zeile: "📝 2 heute fällig · 1 überfällig" oder null. */
    static String homeworkLine(List<List<JSONObject>> hw) {
        int over = hw.get(0).size(), today = hw.get(1).size(), tom = hw.get(2).size();
        StringBuilder b = new StringBuilder();
        if (today > 0) b.append(today).append(" heute fällig");
        if (over > 0) b.append(b.length() > 0 ? " · " : "").append(over).append(" überfällig");
        if (tom > 0) b.append(b.length() > 0 ? " · " : "").append(tom).append(" morgen");
        return b.length() == 0 ? null : "📝 " + b;
    }

    static String title(java.time.LocalDate d) {
        return "☀️ " + WD[d.getDayOfWeek().getValue() - 1] + ", " + d.getDayOfMonth() + "." + d.getMonthValue() + ".";
    }
}

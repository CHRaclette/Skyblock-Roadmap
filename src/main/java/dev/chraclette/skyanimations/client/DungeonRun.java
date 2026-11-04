package dev.chraclette.skyanimations.client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers which Catacombs floor you ran and the score, from the end-of-run summary
 * ("Master Mode The Catacombs - Floor VII" ... " Team Score: 317 (S+)", same pattern as Skyblocker)
 * and from the Croesus menu title ("Master Catacombs - Floor II").
 */
final class DungeonRun {

    private static final Pattern FLOOR = Pattern.compile("^\\s*(Master(?: Mode)?\\s+)?(?:The\\s+)?Catacombs\\s*-\\s*Floor\\s+([IVX]+|\\d)\\s*$");
    private static final Pattern SCORE = Pattern.compile("^\\s+Team Score: ([0-9]+) \\(([A-z+]+)\\)\\s*$");

    private static String floor = "";   // "M7", "F3"
    private static String label = "";   // "Master Mode Floor VII"
    private static String score = "";   // "317 (S+)"
    private static long at;

    private DungeonRun() {
    }

    static void onChat(String raw) {
        String line = raw.replaceAll("§.", "");
        Matcher m = FLOOR.matcher(line);
        if (m.matches()) {
            setFloor(m.group(1) != null, m.group(2));
            return;
        }
        m = SCORE.matcher(line);
        if (m.matches()) {
            score = m.group(1) + " (" + m.group(2) + ")";
            at = System.currentTimeMillis();
        }
    }

    /** Croesus shows the run as the inventory title. */
    static void onTitle(String title) {
        Matcher m = FLOOR.matcher(title.replaceAll("§.", ""));
        if (m.matches()) {
            setFloor(m.group(1) != null, m.group(2));
            score = "";
        }
    }

    private static void setFloor(boolean master, String num) {
        int n = num.matches("\\d") ? Integer.parseInt(num) : roman(num);
        floor = (master ? "M" : "F") + n;
        label = (master ? "Master Mode " : "") + "Floor " + toRoman(n);
        at = System.currentTimeMillis();
    }

    /** "M7" etc., or "" if we don't know (older than 30 minutes). */
    static String floor() {
        return System.currentTimeMillis() - at < 30 * 60_000L ? floor : "";
    }

    static String subtitle() {
        if (floor().isEmpty()) return "";
        return label + (score.isEmpty() ? "" : "  ·  Score " + score);
    }

    /** "Catacombs - Floor 7" / "Master Mode Catacombs - Floor VII" -> M7 / F7. */
    static String keyOf(String source) {
        String s = source.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("floor\\s+([ivx]+|\\d)").matcher(s);
        if (!m.find()) return "";
        String num = m.group(1).toUpperCase(Locale.ROOT);
        int n = num.matches("\\d") ? Integer.parseInt(num) : roman(num);
        return (s.contains("master") ? "M" : "F") + n;
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4;
            case "V" -> 5; case "VI" -> 6; case "VII" -> 7; default -> 0;
        };
    }

    private static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII"};
        return n > 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}

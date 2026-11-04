package dev.chraclette.skyanimations.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden messages that are not drops: Crop Fever starting/ending, pests spawning, pests killed.
 *
 * @param kind   what happened
 * @param amount pests spawned / items received (1 if not given)
 * @param item   item received for a pest kill, else ""
 * @param pest   pest name for kills ("Locust"), else ""
 * @param where  plot for spawns ("Plot - 14", "The Barn"), else ""
 */
public record FarmEvent(Kind kind, int amount, String item, String pest, String where) {

    public enum Kind { FEVER_START, FEVER_END, PEST_SPAWN, PEST_KILL }

    // WOAH! You caught a case of the CROP FEVER for 60 seconds!
    private static final Pattern FEVER_START = Pattern.compile(
            "^\\w+! You caught a case of the CROP FEVER for (\\d+) seconds!$");
    // GONE! Your CROP FEVER has been cured!
    private static final Pattern FEVER_END = Pattern.compile("^\\w+! Your CROP FEVER has been cured!$");
    // GROSS! A  Pest has appeared in Plot - 4!   /  YUCK! 4  Pests have spawned in Plot - 14!
    private static final Pattern ONE_PEST = Pattern.compile(
            "^[A-Z]+! A \\W*\\s*Pest has appeared in (Plot - .+|The Barn)!$");
    private static final Pattern MANY_PESTS = Pattern.compile(
            "^[A-Z]+! (\\d+) \\W*\\s*Pests? have spawned in (Plot - .+|The Barn)!$");
    private static final Pattern OFFLINE_PESTS = Pattern.compile(
            "^[A-Z]+! While you were offline, \\W*\\s*Pests? spawned in (Plots .+)!$");
    // You received 7x Enchanted Potato for killing a Locust!
    private static final Pattern PEST_KILL = Pattern.compile(
            "^You received ([\\d,]+)x (.+?) for killing an? (.+?)!$");

    public static FarmEvent parse(String line) {
        String text = DropParser.strip(line).replaceAll("\\s+", " ");
        if (text.isEmpty() || text.contains(":")) {
            return null;
        }
        Matcher m;
        if ((m = FEVER_START.matcher(text)).matches()) {
            return new FarmEvent(Kind.FEVER_START, Integer.parseInt(m.group(1)), "", "", "");
        }
        if (FEVER_END.matcher(text).matches()) {
            return new FarmEvent(Kind.FEVER_END, 1, "", "", "");
        }
        if ((m = ONE_PEST.matcher(text)).matches()) {
            return new FarmEvent(Kind.PEST_SPAWN, 1, "", "", m.group(1));
        }
        if ((m = MANY_PESTS.matcher(text)).matches()) {
            return new FarmEvent(Kind.PEST_SPAWN, Integer.parseInt(m.group(1)), "", "", m.group(2));
        }
        if ((m = OFFLINE_PESTS.matcher(text)).matches()) {
            return new FarmEvent(Kind.PEST_SPAWN, 0, "", "", m.group(1));
        }
        if ((m = PEST_KILL.matcher(text)).matches()) {
            int count;
            try {
                count = Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                count = 1;
            }
            return new FarmEvent(Kind.PEST_KILL, count, m.group(2).trim(), m.group(3).trim(), "");
        }
        return null;
    }
}

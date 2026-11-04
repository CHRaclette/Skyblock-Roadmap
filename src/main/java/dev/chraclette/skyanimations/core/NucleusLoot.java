package dev.chraclette.skyanimations.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Crystal Nucleus loot message:
 * <pre>
 *   CRYSTAL NUCLEUS LOOT BUNDLE
 *   REWARDS
 *     Flawless Jade Gemstone x2
 *     Gemstone Powder x1,500
 *     ...
 * ▬▬▬▬▬▬▬▬ (64x)
 * </pre>
 * Rewards are indented with 4 spaces (same rule SkyHanni uses).
 */
public final class NucleusLoot {

    public record Item(String name, int count, boolean powder) {
    }

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern SUFFIX = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern PREFIX = Pattern.compile("^([\\d,]+)x (.+)$");

    private boolean collecting;
    private long startedAt;
    private final List<Item> items = new ArrayList<>();

    /** Feeds one chat line. Returns the finished loot when the bundle ends, else null. */
    public List<Item> feed(String line, long nowMillis) {
        String text = FORMATTING.matcher(line).replaceAll("").replace(' ', ' ');
        String trimmed = text.trim();
        if (trimmed.contains(":")) return null; // player chat
        if (trimmed.startsWith("CRYSTAL NUCLEUS LOOT BUNDLE")) {
            collecting = true;
            startedAt = nowMillis;
            items.clear();
            return null;
        }
        if (!collecting) return null;
        if (trimmed.length() >= 20 && trimmed.chars().allMatch(ch -> ch == '▬')) {
            collecting = false;
            return List.copyOf(items);
        }
        if (!text.startsWith("    ") || trimmed.isEmpty()) return null; // "REWARDS" header etc.
        Item item = parseItem(trimmed);
        if (item != null) items.add(item);
        return null;
    }

    /** If the end line never comes (changed format), give up after a few seconds and use what we have. */
    public List<Item> timeout(long nowMillis) {
        if (collecting && nowMillis - startedAt > 3000) {
            collecting = false;
            return items.isEmpty() ? null : List.copyOf(items);
        }
        return null;
    }

    static Item parseItem(String s) {
        int count = 1;
        String name = s;
        Matcher m = SUFFIX.matcher(s);
        if (m.matches()) {
            name = m.group(1);
            count = parseCount(m.group(2));
        } else if ((m = PREFIX.matcher(s)).matches()) {
            count = parseCount(m.group(1));
            name = m.group(2);
        }
        name = name.trim();
        if (name.isEmpty()) return null;
        return new Item(name, count, name.endsWith(" Powder"));
    }

    private static int parseCount(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

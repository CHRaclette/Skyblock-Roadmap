package dev.chraclette.butcher.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Hypixel SkyBlock chat lines into {@link Drop}s. Pure Java so it can be tested
 * without Minecraft. All patterns run on text with formatting codes removed.
 */
public final class DropParser {

    private DropParser() {
    }

    private static final Pattern FORMATTING = Pattern.compile("§.");

    // "RARE DROP! Foo (+254% ✯ Magic Find)". Lines with ':' are player chat, so they are skipped.
    private static final Pattern DROP = Pattern.compile(
            "^(UNCOMMON|RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|PET) DROP!\\s+(.+?)\\s*$");

    private static final Pattern CROP = Pattern.compile(
            "^(RARE|VERY RARE) CROP!\\s+(.+?)\\s*$");

    // "RARE REWARD! [MVP+] Name found a Necron's Handle in their Bedrock Chest!"
    private static final Pattern REWARD = Pattern.compile(
            "^RARE REWARD!\\s+(?:\\[[^\\]]+\\]\\s*)?(\\w{1,16}) found (?:an? )?(.+?) in (?:their|the) .+?!?\\s*$");

    private static final Pattern CROP_FEVER = Pattern.compile("\\bCROP FEVER\\b");

    private static final Pattern BONUS = Pattern.compile(
            "\\s*\\((\\+[^()]*)\\)(?:\\s*\\(automatically donated\\))?\\s*$");

    private static final Pattern WRAPPER = Pattern.compile(
            "^(?:You dug out (?:an? )?|You dropped (?:an? )?|You found (?:an? )?)(.+?)!?$");

    private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+)\\)$");

    private static final Pattern COUNT_PREFIX = Pattern.compile("^([\\d,]+)x (.+)$");
    private static final Pattern COUNT_SUFFIX = Pattern.compile("^(.+?) x([\\d,]+)$");

    public static String strip(String text) {
        return FORMATTING.matcher(text).replaceAll("").replace(' ', ' ').trim();
    }

    /**
     * @param line      a chat line (formatting codes allowed)
     * @param ownName   the player's own name (for RARE REWARD lines), may be null
     * @param context   category hinted by recent chat (slayer boss slain, Diana dig...), may be null
     * @return the drop, or null if the line is not a drop
     */
    public static Drop parse(String line, String ownName, Category context) {
        String text = strip(line);
        if (text.isEmpty() || text.contains(":")) {
            return null;
        }

        DropTier tier;
        String rest;

        Matcher m = DROP.matcher(text);
        if (m.matches()) {
            tier = DropTier.fromDropWord(m.group(1));
            rest = m.group(2);
        } else if ((m = CROP.matcher(text)).matches()) {
            tier = m.group(1).equals("RARE") ? DropTier.RARE_CROP : DropTier.VERY_RARE_CROP;
            rest = m.group(2);
            if (context == null) {
                context = Category.FARMING;
            }
        } else if ((m = REWARD.matcher(text)).matches()) {
            if (ownName != null && !ownName.isEmpty() && !m.group(1).equalsIgnoreCase(ownName)) {
                return null;
            }
            tier = DropTier.REWARD;
            rest = m.group(2);
        } else if (CROP_FEVER.matcher(text).find() && !text.contains("DROP!")) {
            return build(DropTier.CROP_FEVER, "Crop Fever", 1, "", false, Category.FARMING, text);
        } else {
            return null;
        }

        // Trailing "(+254% ✯ Magic Find)" / "(+12 ☘ Farming Fortune)" etc. Keep the first one.
        String bonus = "";
        Matcher b;
        while ((b = BONUS.matcher(rest)).find()) {
            if (bonus.isEmpty()) {
                bonus = b.group(1).trim();
            }
            rest = rest.substring(0, b.start()).trim();
        }

        Matcher w = WRAPPER.matcher(rest);
        if (w.matches()) {
            rest = w.group(1).trim();
        }

        rest = unwrap(rest);

        boolean book = false;
        Matcher bk = BOOK.matcher(rest);
        if (bk.matches()) {
            book = true;
            rest = bk.group(1).trim();
        }

        int count = 1;
        Matcher c = COUNT_PREFIX.matcher(rest);
        if (c.matches()) {
            count = parseCount(c.group(1));
            rest = c.group(2).trim();
        } else if ((c = COUNT_SUFFIX.matcher(rest)).matches()) {
            count = parseCount(c.group(2));
            rest = c.group(1).trim();
        }

        rest = unwrap(rest);
        while (rest.endsWith("!")) {
            rest = rest.substring(0, rest.length() - 1).trim();
        }
        if (rest.isEmpty()) {
            return null;
        }

        Category category = Themes.category(rest, context);
        if (tier == DropTier.RARE_CROP || tier == DropTier.VERY_RARE_CROP) {
            if (category != Category.PEST) {
                category = Category.FARMING;
            }
        }
        return build(tier, rest, count, bonus, book, category, text);
    }

    private static Drop build(DropTier tier, String name, int count, String bonus, boolean book,
                              Category category, String raw) {
        Effect effect = Themes.effect(name, book, tier, category);
        int color = Themes.color(name, effect);
        return new Drop(tier, name, Math.max(1, count), bonus, book, category, effect, color, raw);
    }

    private static String unwrap(String s) {
        s = s.trim();
        while (s.length() > 2 && s.startsWith("(") && s.endsWith(")") && balanced(s.substring(1, s.length() - 1))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        for (char ch : s.toCharArray()) {
            if (ch == '(') depth++;
            if (ch == ')' && --depth < 0) return false;
        }
        return depth == 0;
    }

    private static int parseCount(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Context lines: messages that tell us what the next drop probably came from. */
    public static Category contextFrom(String line) {
        String text = strip(line);
        if (text.contains(":")) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("SLAYER BOSS SLAIN") || upper.contains("SLAYER QUEST")) {
            return Category.GENERIC; // the item name decides which slayer
        }
        if (text.matches("^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out .+!$")
                || text.startsWith("You dug out")) {
            return Category.DIANA;
        }
        if (text.contains("The Catacombs - Floor") || text.contains("Master Mode The Catacombs")) {
            return Category.DUNGEON;
        }
        if (text.startsWith("YUCK! You spawned") || text.contains("Pest") && text.contains("spawned")) {
            return Category.PEST;
        }
        return null;
    }
}

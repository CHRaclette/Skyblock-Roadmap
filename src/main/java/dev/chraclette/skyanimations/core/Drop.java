package dev.chraclette.skyanimations.core;

/**
 * One parsed drop.
 *
 * @param tier       what kind of message it was
 * @param name       clean item name, e.g. "Chimera I"
 * @param count      stack size (1 if the message had none)
 * @param bonus      the "+254% ✯ Magic Find" part, or "" if absent
 * @param book       true when Hypixel called it an Enchanted Book
 * @param category   where it came from
 * @param effect     which particle show to play
 * @param color      main colour of the effect (dye colour for dyes)
 * @param rawMessage the chat line it came from, without formatting codes
 * @param fever      true for Crop Fever rolls ("You dropped 10x ...")
 * @param pest       pest that dropped it ("Beetle"), or "" if unknown / not a pest
 */
public record Drop(
        DropTier tier,
        String name,
        int count,
        String bonus,
        boolean book,
        Category category,
        Effect effect,
        int color,
        String rawMessage,
        boolean fever,
        String pest
) {
    public String displayName() {
        return count > 1 ? count + "x " + name : name;
    }

    public Drop withCount(int newCount) {
        return new Drop(tier, name, newCount, bonus, book, category, effect, color, rawMessage, fever, pest);
    }

    /** Re-labels the drop as coming from a pest (category PEST keeps its own effect). */
    public Drop withPest(String pestName) {
        Effect e = effect == category.defaultEffect ? Category.PEST.defaultEffect : effect;
        return new Drop(tier, name, count, bonus, book, Category.PEST, e, Themes.color(name, e), rawMessage, fever, pestName);
    }

    /** Where it came from, for the info line. */
    public String source() {
        if (fever) return "Crop Fever";
        if (!pest.isEmpty()) return pest + " · Pest";
        return category.displayName;
    }
}

package dev.chraclette.butcher.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Picks category, effect and colour for a drop from its name (plus recent chat context). */
public final class Themes {

    private Themes() {
    }

    // Checked in this order, so specific slayer items win over broad words like "wither".
    private static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.REVENANT, List.of(
                "revenant", "scythe blade", "beheaded horror", "snake rune", "warden heart",
                "shard of the shredded", "smite vii", "undead catalyst", "foul flesh", "matcha dye",
                "pestilence rune", "severed hand", "reaper"));
        KEYWORDS.put(Category.TARANTULA, List.of(
                "tarantula", "digested mosquito", "fly swatter", "bite rune", "spider catalyst",
                "toxic arrow poison", "bane of arthropods vi", "vial of venom", "shriveled wasp",
                "ensnared snail", "primordial eye", "brick red dye", "spider", "fang-tastic"));
        KEYWORDS.put(Category.SVEN, List.of(
                "overflux", "grizzly bait", "red claw egg", "couture rune", "hamster wheel",
                "critical vi", "celeste dye", "sven", "wolf"));
        KEYWORDS.put(Category.VOIDGLOOM, List.of(
                "judgement core", "ender slayer vii", "ender artifact upgrader", "void conqueror",
                "pocket espresso", "handy blood chalice", "etherwarp", "transmission tuner",
                "sinful dice", "summoning eye", "byzantium dye", "enchant rune", "voidgloom",
                "null ovoid", "endersnake rune"));
        KEYWORDS.put(Category.INFERNO, List.of(
                "archfiend", "kelvin inverter", "subzero inverter", "wilson's engineering",
                "fire aspect iii", "duplex", "flawed opal", "lava tears", "fiery burst rune",
                "inferno", "blaze", "demonlord", "flame dye", "scorched books", "magma arrow"));
        KEYWORDS.put(Category.RIFTSTALKER, List.of(
                "mcgrubber", "unfanged vampire", "bubba blister", "vampire", "riftstalker",
                "the one iv", "full-jaw fanging kit", "luscious healing melon"));
        KEYWORDS.put(Category.DIANA, List.of(
                "chimera", "minos", "daedalus", "griffin", "shelmet", "antique remedies",
                "crochet tiger", "shimmering wool", "mythological", "crown of greed",
                "washed-up souvenir", "manti-core", "fateful stinger", "brain food", "dwarf turtle",
                "inquisitor", "sphinx", "king minos", "fabled", "hilt of revelations"));
        KEYWORDS.put(Category.PEST, List.of(
                "pest", "vinyl", "squeaky mousemat", "atmospheric filter", "burrowing spores", "chirping stereo", "beady eyes",
                "clipped wings", "bookworm's favorite book", "overclocker", "tasty cheese",
                "squeaky", "fire in a bottle", "vermin vaporizer", "mantid claw", "wriggling larva",
                "pesthunting", "sunder vi", "dung", "honey jar", "plant matter", "compost", "jelly"));
        KEYWORDS.put(Category.FARMING, List.of(
                "cropie", "squash", "fermento", "helianthus", "crop", "seed", "wart", "melon",
                "pumpkin", "wheat", "carrot", "potato", "cactus", "cocoa", "mushroom", "sugar cane",
                "sunflower", "moonflower", "wild rose", "harvest", "turbo-", "fruit"));
        KEYWORDS.put(Category.FISHING, List.of(
                "megalodon", "flying fish", "lucky clover core", "deep sea orb", "shark",
                "radioactive vial", "magma lord", "thunder", "jawbus", "carmine dye",
                "aquamarine dye", "tiki mask", "titanoboa", "hydra", "great white", "emperor",
                "baby yeti", "flash", "squid", "kraken", "tentacle dye", "bobbin"));
        KEYWORDS.put(Category.DUNGEON, List.of(
                "necron", "wither", "handle", "shadow warp", "implosion", "giant's sword",
                "precursor", "bonzo", "scarf", "livid", "sadan", "thorn", "spirit", "dungeon",
                "master skull", "master star", "necromancer", "phantom", "dark claymore",
                "shadow assassin", "last breath", "storm", "goldor", "maxor", "terminator",
                "recombobulator", "summoning ring", "hot potato"));
    }

    private static final Map<String, Integer> DYES = new LinkedHashMap<>();

    static {
        DYES.put("mythological", 0xC4D93A);
        DYES.put("matcha", 0x74A12E);
        DYES.put("celeste", 0x87CEEB);
        DYES.put("byzantium", 0x9B3FB5);
        DYES.put("brick red", 0xCB4154);
        DYES.put("necron", 0xE7413C);
        DYES.put("holly", 0x3C9B5A);
        DYES.put("dung", 0x8A6A3A);
        DYES.put("carmine", 0xC4002B);
        DYES.put("jade", 0x00A86B);
        DYES.put("aquamarine", 0x7FFFD4);
        DYES.put("flame", 0xE25822);
        DYES.put("nadeshiko", 0xF6ADC6);
        DYES.put("pure black", 0x3A3A3A);
        DYES.put("pure white", 0xF8F8F8);
        DYES.put("emerald", 0x50C878);
        DYES.put("bone", 0xE3DAC9);
        DYES.put("mango", 0xFDBE02);
        DYES.put("iceberg", 0x71A6D2);
        DYES.put("cyclamen", 0xF56FA1);
        DYES.put("wild strawberry", 0xFF43A4);
        DYES.put("sangria", 0xD40808);
        DYES.put("pelt", 0x8B6F7A);
        DYES.put("livid", 0xCEB7AA);
        DYES.put("tentacle", 0x4A73A6);
        DYES.put("dark purple", 0x6A2C9C);
        DYES.put("frostbitten", 0x09D8EB);
        DYES.put("copper", 0xB87333);
        DYES.put("chocolate", 0x9B5A1A);
        DYES.put("treasure", 0xFCD12A);
        DYES.put("midnight", 0x7A3FB0);
        DYES.put("periwinkle", 0xCCCCFF);
        DYES.put("warden", 0x22A0A6);
        DYES.put("fossil", 0xB0952A);
        DYES.put("lava", 0xFF5A1F);
        DYES.put("aurora", 0x7CFFB2);
        DYES.put("celadon", 0xACE1AF);
        DYES.put("sunset", 0xFF8C42);
    }

    private static final Pattern ROMAN_SUFFIX =
            Pattern.compile(".* (I|II|III|IV|V|VI|VII|VIII|IX|X)$");

    /** True for names that look like an enchantment ("Chimera I", "Smite VII"). */
    public static boolean looksLikeEnchant(String name) {
        return ROMAN_SUFFIX.matcher(name).matches();
    }

    public static Category category(String name, Category context) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (var entry : KEYWORDS.entrySet()) {
            for (String word : entry.getValue()) {
                if (lower.contains(word)) {
                    return entry.getKey();
                }
            }
        }
        return context == null ? Category.GENERIC : context;
    }

    public static Effect effect(String name, boolean book, DropTier tier, Category category) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" dye") || lower.contains(" dye ")) {
            return Effect.DYE;
        }
        if (book || lower.contains("book") || lower.contains(" rune") || looksLikeEnchant(name)) {
            return Effect.ARCANE;
        }
        if (lower.contains("wool") || lower.contains("golden") || lower.contains("gold ")
                || lower.contains("crown") || lower.contains("shimmering") || lower.contains("coin")) {
            return Effect.GOLDEN;
        }
        if (tier == DropTier.PET) {
            return Effect.STARS;
        }
        return category.defaultEffect;
    }

    public static int color(String name, Effect effect) {
        if (effect == Effect.DYE) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (var entry : DYES.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            // Unknown dye: derive a stable, saturated colour from the name.
            float hue = (Math.floorMod(lower.hashCode(), 360)) / 360f;
            return hsv(hue, 0.7f, 0.95f);
        }
        return effect.primary;
    }

    public static int hsv(float h, float s, float v) {
        int i = (int) Math.floor(h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6);
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}

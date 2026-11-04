package dev.chraclette.skyanimations.core;

import java.util.Locale;

/** The kinds of drop messages Hypixel SkyBlock sends, ordered by how exciting they are. */
public enum DropTier {
    UNCOMMON("UNCOMMON DROP!", 0x55FF55, 1),
    RARE_CROP("RARE CROP!", 0xFFAA00, 1),
    RARE("RARE DROP!", 0xFFAA00, 2),
    VERY_RARE_CROP("VERY RARE CROP!", 0x5555FF, 2),
    VERY_RARE("VERY RARE DROP!", 0x5555FF, 3),
    PET("PET DROP!", 0xFFAA00, 3),
    REWARD("RARE REWARD!", 0xFFAA00, 3),
    CRAZY_RARE("CRAZY RARE DROP!", 0xFF55FF, 4),
    INSANE("INSANE DROP!", 0xFF5555, 5),
    RNGESUS("PRAY TO RNGESUS DROP!", 0xFF55FF, 6);

    public final String label;
    public final int color;
    /** 1 = small, 6 = once-in-a-lifetime. Drives the size of effects and sounds. */
    public final int rank;

    DropTier(String label, int color, int rank) {
        this.label = label;
        this.color = color;
        this.rank = rank;
    }

    public boolean rainbow() {
        return this == RNGESUS || this == INSANE;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DropTier fromKey(String key) {
        for (DropTier tier : values()) {
            if (tier.key().equalsIgnoreCase(key) || tier.name().equalsIgnoreCase(key)) {
                return tier;
            }
        }
        return null;
    }

    /** Maps the words Hypixel puts in front of "DROP!". */
    public static DropTier fromDropWord(String word) {
        return switch (word.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> UNCOMMON;
            case "RARE" -> RARE;
            case "VERY RARE" -> VERY_RARE;
            case "CRAZY RARE" -> CRAZY_RARE;
            case "INSANE" -> INSANE;
            case "PRAY TO RNGESUS" -> RNGESUS;
            case "PET" -> PET;
            default -> RARE;
        };
    }
}

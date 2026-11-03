package dev.chraclette.butcher.core;

/** What bursts out of the chopped head. Colours are ARGB without alpha. */
public enum Effect {
    /** Enchanted books, runes: purple beam and floating enchanting-table glyphs. */
    ARCANE(0xB04CFF, 0xE8B8FF),
    /** Wool, crowns, gold: golden threads and a star constellation. */
    GOLDEN(0xFFC93C, 0xFFF1A8),
    /** Dyes: a spiral of the dye's colour. The colour is replaced per drop. */
    DYE(0xC4D93A, 0xF2FF9E),
    /** Zombie / wolf / vampire slayers: red burst and shockwave. */
    BLOOD(0xD6283A, 0xFF8A8A),
    /** Voidgloom: purple and black particles pulled inwards. */
    VOID(0x8A2BE2, 0x1A0B2E),
    /** Tarantula: toxic green droplets and web strands. */
    VENOM(0x7CD63A, 0xE6FFD0),
    /** Blaze: rising flames. */
    INFERNO(0xFF7A1A, 0xFFE15A),
    /** Dungeons: bone shards and blue soul flames. */
    BONE(0xE9E4D4, 0x5CE1FF),
    /** Garden: leaves and golden seeds. */
    HARVEST(0x6FCF3B, 0xFFD24A),
    /** Fishing: bubbles and a wave of cyan. */
    OCEAN(0x2EC4D6, 0xBDF6FF),
    /** Everything else: sparkles and little fireworks. */
    STARS(0xFFD84A, 0xFFFFFF);

    public final int primary;
    public final int secondary;

    Effect(int primary, int secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }
}

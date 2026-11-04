package dev.chraclette.skyanimations.core;

/** Where a drop came from. Decides which head gets chopped and the default effect. */
public enum Category {
    GENERIC("Rare drop", Effect.STARS),
    REVENANT("Revenant Horror", Effect.BLOOD),
    TARANTULA("Tarantula Broodfather", Effect.VENOM),
    SVEN("Sven Packmaster", Effect.BLOOD),
    VOIDGLOOM("Voidgloom Seraph", Effect.VOID),
    INFERNO("Inferno Demonlord", Effect.INFERNO),
    RIFTSTALKER("Riftstalker Bloodfiend", Effect.BLOOD),
    DUNGEON("The Catacombs", Effect.BONE),
    DIANA("Mythological Ritual", Effect.GOLDEN),
    FARMING("The Garden", Effect.HARVEST),
    PEST("Pest", Effect.HARVEST),
    FISHING("Sea Creature", Effect.OCEAN);

    public final String displayName;
    public final Effect defaultEffect;

    Category(String displayName, Effect defaultEffect) {
        this.displayName = displayName;
        this.defaultEffect = defaultEffect;
    }
}

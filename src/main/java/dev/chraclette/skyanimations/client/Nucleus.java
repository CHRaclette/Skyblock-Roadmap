package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.NucleusLoot;
import dev.chraclette.skyanimations.render.RunScene;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Crystal Nucleus runs: reads the loot bundle and plays the hotbar + CS2 case scene. */
final class Nucleus {

    /**
     * Items on the Crystal Nucleus RNG Meter (Hypixel changelog, 20 Aug 2024). These fill the CS2
     * reel and always count as rare.
     */
    static final List<String> RNG_METER = new ArrayList<>(List.of(
            "Pickonimbus 2000", "Divan Fragment", "Prehistoric Egg", "Helix", "Ruby Crystal", "Jasper Crystal",
            "Jaderald", "Gemstone Mixture", "Fortune IV", "Precious Pearl", "Claw Fossil", "Recall Potion",
            "Quick Claw", "Divan's Alloy"));

    private static final NucleusLoot PARSER = new NucleusLoot();

    private Nucleus() {
    }

    static void onChat(String raw, boolean test) {
        List<NucleusLoot.Item> loot = PARSER.feed(raw, System.currentTimeMillis());
        if (loot != null && !loot.isEmpty()) play(loot, test);
    }

    static void tick() {
        List<NucleusLoot.Item> loot = PARSER.timeout(System.currentTimeMillis());
        if (loot != null) play(loot, false);
    }

    static void play(List<NucleusLoot.Item> items, boolean test) {
        if (!Config.get().nucleus) return;
        Prices.refreshIfNeeded();
        List<RunScene.Loot> loot = new ArrayList<>();
        for (NucleusLoot.Item it : items) {
            if (it.powder()) {
                loot.add(new RunScene.Loot(it.name(), it.count(), null, powderColor(it.name()), 0, true));
            } else {
                loot.add(Runs.loot(it.name(), it.count(), ItemStack.EMPTY));
            }
        }
        int rareIdx = Runs.rarest(loot, RNG_METER, Config.get().nucleusRareCoins);
        if (rareIdx < 0 && Config.get().nucleusOnlyRare && !test) return;
        if (rareIdx >= 0 && !test) Runs.record(loot.get(rareIdx));
        CutsceneManager.play(new RunScene(RunScene.Style.nucleus(), loot, rareIdx, Runs.pool(RNG_METER), Runs.hooks(), test));
    }

    private static int powderColor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("gemstone")) return 0xFF55FF;
        if (n.contains("mithril")) return 0x00AA00;
        if (n.contains("glacite")) return 0x55FFFF;
        return 0xAAAAAA;
    }

    /** /sa test nucleus [common]: a realistic loot bundle. */
    static void test(boolean withRare) {
        String[] lines = {
                "  CRYSTAL NUCLEUS LOOT BUNDLE",
                "  REWARDS",
                "    Gemstone Powder x2,400",
                "    Mithril Powder x1,500",
                "    Flawless Jade Gemstone x2",
                "    Fine Amber Gemstone x8",
                "    Fine Topaz Gemstone x6",
                "    Flawless Sapphire Gemstone",
                "    Gemstone Chamber",
                "    Wishing Compass x3",
                "    Goblin Egg x2",
                withRare ? "    Divan Fragment" : "    Fine Amethyst Gemstone x4",
                "▬".repeat(64)};
        for (String l : lines) onChat(l, true);
    }
}

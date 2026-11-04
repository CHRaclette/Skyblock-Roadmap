package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.NucleusLoot;
import dev.chraclette.skyanimations.render.Cutscene;
import dev.chraclette.skyanimations.render.NucleusScene;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Crystal Nucleus runs: reads the loot bundle and plays the hotbar + CS2 case scene. */
final class Nucleus {

    /** Items on the Crystal Nucleus RNG Meter. These fill the CS2 reel and always count as rare. */
    static final List<String> RNG_METER = new ArrayList<>(List.of(
            "Jungle Heart", "Divan Fragment", "Treasurite", "Gemstone Mixture", "Jaderald",
            "Precursor Apparatus", "Prehistoric Egg", "Pickonimbus 2000", "Ruby Crystal", "Jasper Crystal",
            "Bal", "Scatha", "Goblin Egg", "Recall Potion", "Wishing Compass"));

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

    private static boolean onMeter(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("^\\[lvl \\d+] ", "");
        for (String m : RNG_METER) {
            if (n.equals(m.toLowerCase(Locale.ROOT)) || n.startsWith(m.toLowerCase(Locale.ROOT) + " ")) return true;
        }
        return false;
    }

    static void play(List<NucleusLoot.Item> items, boolean test) {
        if (!Config.get().nucleus) return;
        Prices.refreshIfNeeded();
        List<NucleusScene.Loot> loot = new ArrayList<>();
        int rareIdx = -1;
        long bestScore = Long.MIN_VALUE;
        for (NucleusLoot.Item it : items) {
            if (it.powder()) {
                loot.add(new NucleusScene.Loot(it.name(), it.count(), null, powderColor(it.name()), 0, true));
                continue;
            }
            Drop d = DropParser.parse("RARE DROP! " + it.name(), null, null);
            ItemStack icon = d == null ? ItemStack.EMPTY : ItemLookup.icon(d, ItemStack.EMPTY);
            ItemCatalog.Info info = ItemCatalog.byName(it.name());
            String tier = info == null ? (it.name().startsWith("[Lvl") ? "LEGENDARY" : "COMMON") : info.tier;
            Cutscene.Price price = d == null ? null : Prices.lookup(d.withCount(it.count()), "");
            long coins = price == null ? 0 : price.coins();
            loot.add(new NucleusScene.Loot(it.name(), it.count(), icon, ItemCatalog.tierColor(tier), coins, false));

            boolean meter = onMeter(it.name());
            boolean rare = meter || ItemCatalog.tierRank(tier) >= 3 || coins >= Config.get().nucleusRareCoins;
            if (rare) {
                long score = (meter ? 1L << 50 : 0) + ((long) ItemCatalog.tierRank(tier) << 40) + coins;
                if (score > bestScore) {
                    bestScore = score;
                    rareIdx = loot.size() - 1;
                }
            }
        }
        if (rareIdx < 0 && Config.get().nucleusOnlyRare && !test) return;
        if (rareIdx >= 0 && !test) {
            Drop d = DropParser.parse("RARE DROP! " + loot.get(rareIdx).name(), null, null);
            if (d != null) LedgerStore.record(d);
        }
        CutsceneManager.play(new NucleusScene(loot, rareIdx, reelPool(),
                (cue, pitch, volume) -> Sounds.play(cue, dev.chraclette.skyanimations.core.Effect.STARS, pitch, volume), test));
    }

    private static List<NucleusScene.ReelItem> reelPool() {
        List<NucleusScene.ReelItem> pool = new ArrayList<>();
        for (String name : RNG_METER) {
            Drop d = DropParser.parse("RARE DROP! " + name, null, null);
            if (d == null) continue;
            ItemCatalog.Info info = ItemCatalog.byName(name);
            int color = info == null ? 0xAA00AA : ItemCatalog.tierColor(info.tier);
            pool.add(new NucleusScene.ReelItem(name, ItemLookup.icon(d, ItemStack.EMPTY), color));
        }
        return pool;
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
                withRare ? "    Jaderald" : "    Fine Amethyst Gemstone x4",
                "▬".repeat(64)};
        for (String l : lines) onChat(l, true);
    }
}

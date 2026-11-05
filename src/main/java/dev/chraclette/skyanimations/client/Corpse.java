package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.core.NucleusLoot;
import dev.chraclette.skyanimations.render.CorpseScene;
import dev.chraclette.skyanimations.render.RunScene;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Glacite Mineshaft corpses: "  VANGUARD CORPSE LOOT!" -> the frozen-corpse scene. */
final class Corpse {

    /** Always the big finish. */
    static final List<String> RARE = List.of("Shattered Locket", "Frostbitten Dye");
    private static final long RARE_COINS = 5_000_000L;
    private static final NucleusLoot PARSER = NucleusLoot.corpses();

    private Corpse() {
    }

    static void onChat(String raw, boolean test) {
        List<NucleusLoot.Item> loot = PARSER.feed(raw, System.currentTimeMillis());
        if (loot == null) return;
        boolean played = !loot.isEmpty() && play(PARSER.type(), loot, test);
        if (!test) {
            ChatHider.bundleDone(played);
            LedgerStore.corpse();
        }
    }

    static void tick() {
        List<NucleusLoot.Item> loot = PARSER.timeout(System.currentTimeMillis());
        if (loot != null) ChatHider.bundleDone(play(PARSER.type(), loot, false));
    }

    static boolean play(String type, List<NucleusLoot.Item> items, boolean test) {
        if (!Config.get().corpse) return false;
        Prices.refreshIfNeeded();
        List<RunScene.Loot> loot = new ArrayList<>();
        for (NucleusLoot.Item it : items) {
            if (it.powder()) continue; // powder has no item to show
            loot.add(Runs.loot(it.name(), it.count(), ItemStack.EMPTY));
        }
        if (loot.isEmpty()) return false;
        while (loot.size() > 9) loot.remove(0);
        int rareIdx = Runs.rarest(loot, RARE, RARE_COINS);
        List<String> lines = new ArrayList<>();
        int[] dye = null;
        if (rareIdx >= 0) {
            RunScene.Loot r = loot.get(rareIdx);
            if (!test) Runs.record(r);
            var e = LedgerStore.get().entry(r.name());
            long corpses = Math.max(1, LedgerStore.get().runs("corpse") + (test ? 0 : 1));
            int times = e == null ? 1 : Math.max(1, e.times);
            lines.add("1 in " + Math.max(1, corpses / times) + " corpses");
            lines.add("Your " + Runs.ordinal(times) + " ever " + r.name());
            if (r.name().toLowerCase(Locale.ROOT).endsWith(" dye")) {
                dye = NeuRepo.dyeColors(r.name());
                if (dye == null) dye = new int[]{dev.chraclette.skyanimations.core.Themes.color(r.name(), Effect.DYE)};
            }
        }
        CutsceneManager.play(new CorpseScene(type, loot, rareIdx, new ItemStack(Items.IRON_PICKAXE), lines, dye,
                (cue, pitch, volume) -> Sounds.play(cue, Effect.STARS, pitch, volume)));
        return true;
    }

    /** /sa test corpse [dye] */
    static void test(boolean dye) {
        String[] lines = {"▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "  VANGUARD CORPSE LOOT! ", "  REWARDS",
                "    Glacite Amalgamation", "    Suspicious Scrap x16", "    Refined Umber", "    Blue Goblin Egg",
                "    ☠ Flawless Onyx Gemstone", dye ? "    Frostbitten Dye" : "    Shattered Locket",
                "    Glacite Powder x1,200", "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"};
        for (String l : lines) onChat(l, true);
    }
}

package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.Cutscene;
import dev.chraclette.skyanimations.render.RunScene;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared bits for the run scenes (Crystal Nucleus, dungeon chests, RNG Meter). */
final class Runs {

    private Runs() {
    }

    /** An item for the hotbar: icon, rarity colour and price. stack may be EMPTY (then it is looked up by name). */
    static RunScene.Loot loot(String name, int count, ItemStack stack) {
        Drop d = DropParser.parse("RARE DROP! " + name, null, null);
        ItemStack icon = stack != null && !stack.isEmpty() ? stack.copy()
                : d == null ? ItemStack.EMPTY : ItemLookup.icon(d, ItemStack.EMPTY);
        String tier = tier(name);
        int color = stack != null && !stack.isEmpty() ? ItemLookup.nameColor(stack) : ItemCatalog.tierColor(tier);
        if (color < 0) color = ItemCatalog.tierColor(tier);
        String id = stack != null && !stack.isEmpty() ? ItemLookup.skyblockId(stack) : "";
        Cutscene.Price price = d == null ? null : Prices.lookup(d.withCount(Math.max(1, count)), id == null ? "" : id);
        return new RunScene.Loot(name, count, icon, color, price == null ? 0 : price.coins(), false);
    }

    static String tier(String name) {
        ItemCatalog.Info info = ItemCatalog.byName(name);
        if (info != null && info.tier != null) return info.tier;
        return name.startsWith("[Lvl") ? "LEGENDARY" : "COMMON";
    }

    static boolean listed(List<String> list, String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("^\\[lvl \\d+] ", "");
        for (String m : list) {
            String ml = m.toLowerCase(Locale.ROOT);
            if (n.equals(ml) || n.startsWith(ml + " ") || n.contains("(" + ml + ")")) return true;
        }
        return false;
    }

    /** Picks the rarest item: on the meter list first, then rarity, then price. -1 if nothing is rare. */
    static int rarest(List<RunScene.Loot> loot, List<String> meter, long rareCoins) {
        int idx = -1;
        long best = Long.MIN_VALUE;
        for (int i = 0; i < loot.size(); i++) {
            RunScene.Loot l = loot.get(i);
            if (l.powder()) continue;
            boolean m = listed(meter, l.name());
            int rank = ItemCatalog.tierRank(tier(l.name()));
            if (!(m || rank >= 3 || l.coins() >= rareCoins)) continue;
            long score = (m ? 1L << 50 : 0) + ((long) rank << 40) + l.coins();
            if (score > best) {
                best = score;
                idx = i;
            }
        }
        return idx;
    }

    static List<RunScene.ReelItem> pool(List<String> names) {
        List<RunScene.ReelItem> pool = new ArrayList<>();
        java.util.Map<String, RunScene.ReelItem> made = new java.util.HashMap<>();
        for (String name : names) {
            RunScene.ReelItem item = made.computeIfAbsent(name, n -> {
                Drop d = DropParser.parse("RARE DROP! " + n, null, null);
                if (d == null) return null;
                return new RunScene.ReelItem(n, ItemLookup.icon(d, ItemStack.EMPTY), ItemCatalog.tierColor(tier(n)));
            });
            if (item != null) pool.add(item);
        }
        return pool;
    }

    /**
     * Names for a CS2 reel from NEU RNG-Meter data: items that need a lower meter score (= drop more
     * often) appear more often on the reel, like the common skins in a CS2 case.
     */
    static List<String> weighted(List<NeuRepo.RngItem> items) {
        long max = 1;
        for (NeuRepo.RngItem it : items) max = Math.max(max, it.score());
        List<String> out = new ArrayList<>();
        for (NeuRepo.RngItem it : items) {
            if (it.id().startsWith("ATTRIBUTE_SHARD")) continue;
            int w = (int) Math.max(1, Math.min(6, Math.round(Math.sqrt(max / (double) Math.max(1, it.score())))));
            String name = NeuRepo.name(it.id());
            for (int i = 0; i < w; i++) out.add(name);
        }
        return out;
    }

    /** Unique names of a weighted list (for "is this a meter item?" checks). */
    static List<String> unique(List<String> names) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(names));
    }

    /** 1st, 2nd, 3rd, 4th ... */
    static String ordinal(long n) {
        long m100 = n % 100, m10 = n % 10;
        String suf = (m100 >= 11 && m100 <= 13) ? "th" : m10 == 1 ? "st" : m10 == 2 ? "nd" : m10 == 3 ? "rd" : "th";
        return n + suf;
    }

    static RunScene.Hooks hooks() {
        return (cue, pitch, volume) -> Sounds.play(cue, Effect.STARS, pitch, volume);
    }

    /** The rare item also earns a sticker. */
    static void record(RunScene.Loot rare) {
        Drop d = DropParser.parse("RARE DROP! " + rare.name(), null, null);
        if (d != null) LedgerStore.record(d.withCount(Math.max(1, rare.count())));
    }
}

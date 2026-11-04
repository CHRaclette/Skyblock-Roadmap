package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Ledger;
import dev.chraclette.skyanimations.render.RunScene;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RNG Meter drops. Hypixel sends this right after your selected drop arrives:
 * "RNG METER! Reselected the Judgement Core for Voidgloom Seraph! CLICK HERE to select a new drop!"
 * Instead of the normal cutscene, the meter fills to 100 % and a CS2 case rolls onto the item.
 */
final class RngMeter {

    static final Pattern LINE = Pattern.compile("^RNG METER! Reselected the (.+?) for (.+?)! CLICK HERE.*$");

    /** Big-ticket dungeon loot for the dungeon reels. */
    static final List<String> DUNGEON_REEL = List.of(
            "Necron's Handle", "Implosion", "Shadow Warp", "Wither Shield", "Giant's Sword", "Dark Claymore",
            "Spirit Wing", "Bonzo's Staff", "Scarf's Studies", "Recombobulator 3000", "Fifth Master Star",
            "Master Skull - Tier 5", "Wither Chestplate", "Wither Helmet", "Precursor Eye", "Shadow Fury",
            "Last Breath", "Livid Dagger", "Shadow Assassin Chestplate", "Necron Dye", "Dark Orb",
            "Ice Spray Wand", "Wither Catalyst", "Auto Recombobulator", "Giant's Sword", "Warden Helmet");

    private RngMeter() {
    }

    static void onChat(String raw, boolean test) {
        if (!Config.get().rngMeter) return;
        String line = raw.replaceAll("§.", "").trim();
        Matcher m = LINE.matcher(line);
        if (!m.matches()) return;
        play(m.group(1).trim(), m.group(2).trim(), test);
    }

    static List<String> poolFor(String source) {
        String s = source.toLowerCase(Locale.ROOT);
        if (s.contains("nucleus")) return Nucleus.RNG_METER;
        if (s.contains("catacombs") || s.contains("floor")) return DUNGEON_REEL;
        List<String> out = new ArrayList<>(Ledger.KNOWN.get(Ledger.Chapter.SLAYER));
        if (out.isEmpty()) out.addAll(DUNGEON_REEL);
        return out;
    }

    static void play(String item, String source, boolean test) {
        Prices.refreshIfNeeded();
        List<RunScene.Loot> loot = List.of(Runs.loot(item, 1, ItemStack.EMPTY));
        RunScene scene = new RunScene(RunScene.Style.meter(source), loot, 0, Runs.pool(poolFor(source)), Runs.hooks(), test);
        // the RARE DROP cutscene for the same item is replaced by this one
        CutsceneManager.replace(item, scene);
    }

    /** /sa test rngmeter */
    static void test() {
        onChat("RNG METER! Reselected the Judgement Core for Voidgloom Seraph! CLICK HERE to select a new drop!", true);
    }
}

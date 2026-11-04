package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.render.RunScene;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon reward chests (Wood ... Bedrock, also opened through Croesus). While the chest GUI is
 * open we remember what is inside; when you click "Open Reward Chest" and the GUI closes, the loot
 * drops into the bar and the best item rolls in a CS2 case. Obsidian and Bedrock chests always roll.
 * Inventory reading works like SkyHanni's InstanceChestAPI (title = chest type, slot 31 = open button).
 */
final class DungeonChest {

    private static final Pattern TITLE = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$");
    private static final Set<String> BUTTONS = Set.of("open reward chest", "close", "go back", "reroll chest", "");

    private record Item(String name, int count, ItemStack stack) {
    }

    private static String chest;
    private static List<Item> items = List.of();
    private static boolean claimed;
    private static Field hoveredSlot;

    private DungeonChest() {
    }

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            String title = strip(screen.getTitle().getString());
            Matcher m = TITLE.matcher(title);
            if (!m.matches() || "off".equals(Config.get().dungeonChests)) return;
            if (!screen.equals(open)) {
                open = screen;
                chest = m.group(1);
                items = List.of();
                claimed = false;
            }
            ScreenEvents.afterTick(screen).register(s -> snapshot(acs));
            ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) -> {
                Slot slot = hovered(acs);
                if (slot != null && slot.hasItem() && strip(slot.getItem().getHoverName().getString()).equalsIgnoreCase("Open Reward Chest")) {
                    claimed = true;
                }
                return consumed;
            });
            ScreenEvents.remove(screen).register(s -> closed(s));
        });
    }

    private static Object open;

    private static String strip(String s) {
        return s.replaceAll("§.", "").trim();
    }

    private static void snapshot(AbstractContainerScreen<?> screen) {
        List<Item> now = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            String name = strip(stack.getHoverName().getString());
            if (BUTTONS.contains(name.toLowerCase(Locale.ROOT))) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (id.endsWith("glass_pane")) continue;
            now.add(new Item(name, stack.getCount(), stack.copy()));
        }
        if (!now.isEmpty()) items = now;
    }

    private static Slot hovered(AbstractContainerScreen<?> screen) {
        try {
            if (hoveredSlot == null) {
                hoveredSlot = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
                hoveredSlot.setAccessible(true);
            }
            return (Slot) hoveredSlot.get(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void closed(Object screen) {
        if (screen != open) return;
        open = null;
        if (claimed && !items.isEmpty()) play(chest, items, false);
        claimed = false;
    }

    private static void play(String chest, List<Item> items, boolean test) {
        Prices.refreshIfNeeded();
        List<RunScene.Loot> loot = new ArrayList<>();
        for (Item it : items) loot.add(Runs.loot(it.name, it.count, it.stack));
        int rareIdx = Runs.rarest(loot, RngMeter.DUNGEON_REEL, Config.get().nucleusRareCoins);
        boolean big = chest.equals("Obsidian") || chest.equals("Bedrock");
        if (rareIdx < 0 && big) {
            // the big chests always roll: take the most valuable item
            long best = -1;
            for (int i = 0; i < loot.size(); i++) {
                if (loot.get(i).coins() > best) {
                    best = loot.get(i).coins();
                    rareIdx = i;
                }
            }
        }
        if (rareIdx < 0 && "rare".equals(Config.get().dungeonChests) && !test) return;
        List<String> reel = new ArrayList<>(RngMeter.DUNGEON_REEL);
        for (Item it : items) if (!reel.contains(it.name)) reel.add(it.name);
        String floor = HypixelInfo.area();
        RunScene.Style style = RunScene.Style.chest(chest + " Chest", floor == null ? "" : floor, RunScene.chestColor(chest));
        CutsceneManager.play(new RunScene(style, loot, rareIdx, Runs.pool(reel), Runs.hooks(), test));
    }

    /** /sa test chest: an Obsidian chest from Master Mode Floor 7. */
    static void test() {
        play("Obsidian", List.of(
                new Item("Wither Essence", 12, ItemStack.EMPTY),
                new Item("Undead Essence", 40, ItemStack.EMPTY),
                new Item("Enchanted Book (Ultimate Wise I)", 1, ItemStack.EMPTY),
                new Item("Wither Blood", 1, ItemStack.EMPTY),
                new Item("Necron's Handle", 1, ItemStack.EMPTY)), true);
    }
}

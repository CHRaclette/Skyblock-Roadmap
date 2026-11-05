package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Category;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.Ledger;
import net.minecraft.client.Minecraft;

/** Hooks for recording the demo video (used by the automated client test). */
public final class Demo {

    private Demo() {
    }

    public static void start() {
        Clock.manual(true);
        Config.get().onlyOnHypixel = false;
        Prices.refreshIfNeeded();
    }

    public static void step(float seconds) {
        Clock.step(seconds);
    }

    public static boolean ready() {
        return ItemCatalog.loaded();
    }

    /** Plays a chat line as if Hypixel had sent it. */
    public static void chat(String line) {
        SkyAnimationsClient.handle(line, true);
    }

    /** A rare drop from a pest that was just vacuumed. */
    public static void pestDrop(String line, String pest) {
        Drop d = DropParser.parse(line, "You", null);
        if (d != null) CutsceneManager.enqueue(d.withPest(pest), true);
    }

    public static void fever() {
        Farming.testFever();
    }

    public static void pests() {
        Farming.testPests();
    }

    public static void stickerPop(Ledger.Level before, Ledger.Level after, String itemLine) {
        Drop d = DropParser.parse(itemLine, "You", null);
        Farming.stickerLater(before, after, d.name(), ItemLookup.icon(d, net.minecraft.world.item.ItemStack.EMPTY), after.points, 0);
    }

    public static void badgePop(int badgeIndex, dev.chraclette.skyanimations.core.Badges.Tier tier) {
        var b = dev.chraclette.skyanimations.core.Badges.ALL.get(badgeIndex);
        Farming.badgeLater(new dev.chraclette.skyanimations.core.Badges.Earned(b, tier), BookScreen.badgeIcon(b.icon()), 0);
    }

    /** Fills the sticker book with a believable collection. */
    public static void fillBook() {
        Ledger l = LedgerStore.get();
        long now = System.currentTimeMillis();
        String[][] drops = {
                {"RARE DROP! Griffin Feather (+120% ✯ Magic Find)", "DIANA", "180"},
                {"RARE DROP! Daedalus Stick (+180% ✯ Magic Find)", "DIANA", "64"},
                {"RARE DROP! Enchanted Book (Chimera I) (+254% ✯ Magic Find)", "DIANA", "27"},
                {"RARE DROP! You dug out a Shimmering Wool!", "DIANA", "11"},
                {"RARE DROP! Crochet Tiger Plushie", "DIANA", "4"},
                {"RARE DROP! Dwarf Turtle Shelmet", "DIANA", "2"},
                {"CRAZY RARE DROP! (Mythological Dye) (+312% ✯ Magic Find)", "DIANA", "1"},
                {"RARE DROP! Minos Relic", "DIANA", "1"},
                {"VERY RARE DROP! (Judgement Core) (+250% ✯ Magic Find)", "VOIDGLOOM", "3"},
                {"CRAZY RARE DROP! (Overflux Capacitor) (+180% ✯ Magic Find)", "SVEN", "1"},
                {"RARE CROP! Cropie (+23 ☘ Farming Fortune)", "FARMING", "40"},
                {"RARE DROP! Beady Eyes (+12% ✯ Magic Find)", "PEST", "6"},
        };
        for (String[] d : drops) {
            Drop drop = DropParser.parse(d[0], "You", Category.valueOf(d[1]));
            if (drop == null) continue;
            if (d[1].equals("PEST")) drop = drop.withPest("Fly");
            for (int i = 0; i < Integer.parseInt(d[2]); i++) l.record(drop, 1_250_000, now - 3L * 86_400_000);
        }
        for (Ledger.Entry e : l.stickers.values()) e.lastAt = now - (2 * 3_600_000L + 14 * 60_000L);
        l.pestsVacuumed = 1234;
        l.fevers = 12;
        dev.chraclette.skyanimations.core.Badges.check(l);
    }

    public static void nucleus(boolean rare) {
        Nucleus.test(rare);
    }

    public static void wow(String raw) {
        Specials.onWow(raw, "You", true);
    }

    public static void dungeonChest() {
        DungeonChest.test();
    }

    public static void rngMeter() {
        RngMeter.test();
    }

    /** What /sa backup prints, plus the restore notice after a reinstall. */
    public static void backupInfo() {
        ChatHider.add(net.minecraft.network.chat.Component.literal("§6[SkyAnimations] §7Sticker book restored from your backup (§f"
                + LedgerStore.get().size() + "§7 stickers)."));
        ChatHider.add(net.minecraft.network.chat.Component.literal("§7Backed up outside Minecraft: §f%APPDATA%\\SkyAnimations"));
    }

    public static void openBook() {
        Minecraft.getInstance().gui.setScreen(new BookScreen());
    }

    public static void showBadges() {
        if (Minecraft.getInstance().gui.screen() instanceof BookScreen s) s.badges();
    }

    public static void closeBook() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    /** Moves the mouse-hover position used by the book, as fractions of the screen (for tooltips). */
    public static void hover(float fx, float fy) {
        BookScreen.demoMouse(fx, fy);
    }

    /** A short Crop Fever for the video: intro, a few rolls, a pest alert, cured, summary. */
    public static void shortFever() {
        Farming.scheduleDemo(new float[]{0f, 3.0f, 3.9f, 4.6f, 5.3f, 6.2f, 9.0f}, new String[]{
                "WOAH! You caught a case of the CROP FEVER for 9 seconds!",
                "UNCOMMON DROP! You dropped 6x Enchanted Wheat!",
                "RARE DROP! You dropped 10x Enchanted Wheat!",
                "UNCOMMON DROP! You dropped 6x Enchanted Wheat!",
                "YUCK! 3 \uE07F Pests have spawned in Plot - 14!",
                "You received 7x Enchanted Potato for killing a Locust!",
                "GONE! Your CROP FEVER has been cured!"});
    }
}

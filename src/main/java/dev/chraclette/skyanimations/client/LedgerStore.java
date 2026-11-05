package dev.chraclette.skyanimations.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.Ledger;
import dev.chraclette.skyanimations.render.Cutscene;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves the sticker book (config/skyanimations-stickers.json). */
public final class LedgerStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Ledger ledger = new Ledger();

    private LedgerStore() {
    }

    public static Ledger get() {
        return ledger;
    }

    /** One sticker book per SkyBlock profile, like SkyHanni keeps its data per profile. */
    private static String profile = "";

    private static Path legacyFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyanimations-stickers.json");
    }

    private static Path file() {
        if (profile.isEmpty()) return legacyFile();
        String safe = profile.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return FabricLoader.getInstance().getConfigDir().resolve("skyanimations").resolve("stickers-" + safe + ".json");
    }

    public static String profile() {
        return profile;
    }

    /** Called when the tab list / chat tells us the profile. Saves this book and opens that one. */
    static void switchProfile(String name) {
        if (name.equalsIgnoreCase(profile)) return;
        if (!profile.isEmpty()) save();
        profile = name;
        Config.get().lastProfile = name;
        Config.save();
        // The very first profile we see inherits the book collected before profiles were known.
        if (!Files.exists(file()) && Files.exists(legacyFile())) {
            try {
                Files.createDirectories(file().getParent());
                Files.move(legacyFile(), file());
            } catch (IOException e) {
                SkyAnimationsClient.LOG.warn("Could not move the old sticker book", e);
            }
        }
        ledger = new Ledger();
        load();
    }

    static void load() {
        if (profile.isEmpty() && Config.get().lastProfile != null) profile = Config.get().lastProfile;
        if (!profile.isEmpty() && !Files.exists(file()) && Files.exists(legacyFile())) {
            try {
                Files.createDirectories(file().getParent());
                Files.move(legacyFile(), file());
            } catch (IOException e) {
                SkyAnimationsClient.LOG.warn("Could not move the old sticker book", e);
            }
        }
        try {
            if (Files.exists(file())) {
                Ledger loaded = GSON.fromJson(Files.readString(file()), Ledger.class);
                if (loaded != null && loaded.stickers != null) ledger = loaded;
                if (ledger.badgeTiers == null) ledger.badgeTiers = new java.util.LinkedHashMap<>();
            }
        } catch (Exception e) {
            SkyAnimationsClient.LOG.warn("Could not read the sticker book, starting a new one", e);
        }
        dev.chraclette.skyanimations.core.Badges.check(ledger); // sync silently, only announce new ones later
    }

    private static void announceBadges(float delay) {
        for (var earned : dev.chraclette.skyanimations.core.Badges.check(ledger)) {
            Farming.badgeLater(earned, BookScreen.badgeIcon(earned.badge().icon()), delay);
        }
    }

    static void save() {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling(file().getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(ledger));
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SkyAnimationsClient.LOG.warn("Could not save the sticker book", e);
        }
    }

    /** A real drop happened: stick it in the book and announce new/upgraded stickers. */
    static void record(Drop drop) {
        Cutscene.Price p = Prices.lookup(drop, "");
        Ledger.Result r = ledger.record(drop, p == null ? 0 : p.coins(), System.currentTimeMillis());
        if (r == null) return;
        float delay = drop.fever() && drop.tier().rank < Config.get().feverCutsceneFromRank ? 0.8f : 6.5f;
        if (!Config.get().stickerToasts) {
            dev.chraclette.skyanimations.core.Badges.check(ledger);
            save();
            return;
        }
        announceBadges(delay + 0.2f);
        save();
        Object icon = ItemLookup.icon(drop, net.minecraft.world.item.ItemStack.EMPTY);
        if (r.isNew() || r.upgraded()) {
            Farming.stickerLater(r.isNew() ? null : r.before(), r.after(), r.entry().name, icon, r.entry().times, delay);
        }
    }

    /** /sa give: adds a test sticker and shows its pop-up. */
    static void giveTest(String name, Ledger.Level level, boolean pop, float delay) {
        Ledger.Result r = ledger.give(name, level, System.currentTimeMillis());
        if (pop && (r.isNew() || r.upgraded())) {
            Drop d = dev.chraclette.skyanimations.core.DropParser.parse("RARE DROP! " + name, "You", null);
            Object icon = d == null ? null : ItemLookup.icon(d, net.minecraft.world.item.ItemStack.EMPTY);
            Farming.stickerLater(r.isNew() ? null : r.before(), r.after(), r.entry().name, icon, r.entry().times, delay);
        }
    }

    /** After a batch of /sa give: check badges (with pop-ups) and save. */
    static void finishGive(float delay) {
        announceBadges(delay);
        save();
    }

    /** /sa give reset: removes the test stickers again. */
    static int clearTest() {
        int n = ledger.removeTest();
        dev.chraclette.skyanimations.core.Badges.check(ledger); // re-sync silently
        save();
        return n;
    }

    /** A Glacite Mineshaft corpse was looted. */
    static void corpse() {
        ledger.addRun("corpse");
        save();
    }

    /** A Crystal Nucleus run finished (loot bundle seen). */
    static void nucleusRun() {
        ledger.addRun("nucleus");
        announceBadges(9f);
        save();
    }

    /** A Catacombs run finished ("Team Score" line), key = "F7" / "M7". */
    static void dungeonRun(String floorKey) {
        if (floorKey == null || floorKey.isEmpty()) return;
        ledger.addRun(floorKey);
        announceBadges(1f);
        save();
    }

    /** Run totals read from the SkyBlock profile (replace our own counting). */
    static void profileRuns(java.util.Map<String, Long> runs) {
        ledger.runs = new java.util.LinkedHashMap<>(runs);
        ledger.runsFromProfileAt = System.currentTimeMillis();
        dev.chraclette.skyanimations.core.Badges.check(ledger);
        save();
    }

    static void pestVacuumed() {
        ledger.pestsVacuumed++;
        announceBadges(0.5f);
        save();
    }

    static void feverCaught() {
        ledger.fevers++;
        announceBadges(3.5f);
        save();
    }
}

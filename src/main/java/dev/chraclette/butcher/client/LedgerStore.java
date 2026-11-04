package dev.chraclette.butcher.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.core.Ledger;
import dev.chraclette.butcher.render.Cutscene;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and saves the sticker book (config/butcherdrops-stickers.json). */
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
        return FabricLoader.getInstance().getConfigDir().resolve("butcherdrops-stickers.json");
    }

    private static Path file() {
        if (profile.isEmpty()) return legacyFile();
        String safe = profile.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return FabricLoader.getInstance().getConfigDir().resolve("butcherdrops").resolve("stickers-" + safe + ".json");
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
                ButcherClient.LOG.warn("Could not move the old sticker book", e);
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
                ButcherClient.LOG.warn("Could not move the old sticker book", e);
            }
        }
        try {
            if (Files.exists(file())) {
                Ledger loaded = GSON.fromJson(Files.readString(file()), Ledger.class);
                if (loaded != null && loaded.stickers != null) ledger = loaded;
                if (ledger.badgeTiers == null) ledger.badgeTiers = new java.util.LinkedHashMap<>();
            }
        } catch (Exception e) {
            ButcherClient.LOG.warn("Could not read the sticker book, starting a new one", e);
        }
        dev.chraclette.butcher.core.Badges.check(ledger); // sync silently, only announce new ones later
    }

    private static void announceBadges(float delay) {
        for (var earned : dev.chraclette.butcher.core.Badges.check(ledger)) {
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
            ButcherClient.LOG.warn("Could not save the sticker book", e);
        }
    }

    /** A real drop happened: stick it in the book and announce new/upgraded stickers. */
    static void record(Drop drop) {
        Cutscene.Price p = Prices.lookup(drop, "");
        Ledger.Result r = ledger.record(drop, p == null ? 0 : p.coins(), System.currentTimeMillis());
        if (r == null) return;
        float delay = drop.fever() && drop.tier().rank < Config.get().feverCutsceneFromRank ? 0.8f : 6.5f;
        if (!Config.get().stickerToasts) {
            dev.chraclette.butcher.core.Badges.check(ledger);
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

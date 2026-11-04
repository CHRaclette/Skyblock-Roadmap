package dev.chraclette.skyanimations.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.chraclette.skyanimations.core.DropTier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Settings, stored in config/skyanimations.json. Changed with /sa commands. */
public final class Config {

    public enum Mode { FULL, COMPACT, OFF }

    public static final class Data {
        public boolean enabled = true;
        public boolean sound = true;
        public boolean music = true;
        public boolean prices = true;
        public boolean onlyOnHypixel = true;
        public float volume = 1.0f;
        public float speed = 1.0f;
        /** Last SkyBlock profile seen, so the right sticker book opens before the tab list loads. */
        public String lastProfile = "";

        /** Pop-ups for new / upgraded stickers. */
        public boolean stickerToasts = true;

        // Garden
        public boolean feverIntro = true;
        public boolean feverTimer = true;
        public boolean feverSummary = true;
        public boolean feverToasts = true;
        /** Crop Fever rolls at or above this rank get a full cutscene (4 = CRAZY RARE). */
        public int feverCutsceneFromRank = 4;
        public boolean pestSpawn = true;
        public boolean pestKills = true;
        /** Vacuum scene for pest drops (false = chopping board like everything else). */
        public boolean vacuum = true;

        /** Per tier: FULL cutscene, COMPACT (reveal only) or OFF. */
        public Map<String, Mode> modes = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private Config() {
    }

    public static Data get() {
        return data;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyanimations.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) {
                Data loaded = GSON.fromJson(Files.readString(file()), Data.class);
                if (loaded != null) data = loaded;
            }
        } catch (Exception e) {
            SkyAnimationsClient.LOG.warn("Could not read config, using defaults", e);
        }
        if (data.modes == null) data.modes = new LinkedHashMap<>();
        for (DropTier tier : DropTier.values()) {
            data.modes.putIfAbsent(tier.key(), defaultMode(tier));
        }
        save();
    }

    private static Mode defaultMode(DropTier tier) {
        return switch (tier) {
            case UNCOMMON, RARE_CROP -> Mode.COMPACT;
            default -> Mode.FULL;
        };
    }

    public static Mode mode(DropTier tier) {
        Mode m = data.modes.get(tier.key());
        return m == null ? defaultMode(tier) : m;
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data));
        } catch (IOException e) {
            SkyAnimationsClient.LOG.warn("Could not save config", e);
        }
    }
}

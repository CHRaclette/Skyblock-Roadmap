package dev.chraclette.skyanimations.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The mod used to be called "Butcher Drops": move its old settings and sticker books over once. */
final class Migration {

    private Migration() {
    }

    static void run() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        move(dir.resolve("butcherdrops.json"), dir.resolve("skyanimations.json"));
        move(dir.resolve("butcherdrops-stickers.json"), dir.resolve("skyanimations-stickers.json"));
        move(dir.resolve("butcherdrops-items.json"), dir.resolve("skyanimations-items.json"));
        move(dir.resolve("butcherdrops"), dir.resolve("skyanimations"));
    }

    private static void move(Path from, Path to) {
        try {
            if (Files.exists(from) && !Files.exists(to)) {
                Files.move(from, to);
            }
        } catch (IOException e) {
            System.err.println("[SkyAnimations] Could not move " + from + ": " + e);
        }
    }
}

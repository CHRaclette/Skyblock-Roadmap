package dev.chraclette.skyanimations.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hypixel sends ~900 SkyBlock items as paper with an item model from its official SkyBlock resource
 * pack. Without that pack (declined, or not on Hypixel) they look like paper. /sa pack downloads the
 * official pack straight from Hypixel (resources API, hash checked) into your resourcepacks folder for
 * your own use, and switches it on. Nothing of the pack is shipped with this mod.
 */
final class HypixelPack {

    private static final String API = "https://api.hypixel.net/v2/resources/packs";
    static final String FILE = "Hypixel-SkyBlock-Pack.zip";
    private static boolean hinted;

    private HypixelPack() {
    }

    /** One hint per session when we're in SkyBlock and the pack is missing. */
    static void hintIfMissing() {
        if (hinted || !HypixelInfo.inSkyBlock() || ItemCatalog.packLoaded()) return;
        hinted = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§6[SkyAnimations] §7Hypixel's SkyBlock pack isn't loaded, so many items have no texture. §f/sa pack §7gets it."), false);
        }
    }

    static void download(Consumer<String> report) {
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            String msg;
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                String body = http.send(HttpRequest.newBuilder(URI.create(API)).header("User-Agent", "SkyAnimations-Mod").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
                JsonObject best = null;
                for (JsonElement p : JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("packs")) {
                    if (!p.getAsJsonObject().get("id").getAsString().equalsIgnoreCase("SkyBlock")) continue;
                    for (JsonElement v : p.getAsJsonObject().getAsJsonArray("versions")) {
                        JsonObject o = v.getAsJsonObject();
                        if (best == null || o.get("packFormat").getAsInt() > best.get("packFormat").getAsInt()) best = o;
                    }
                }
                if (best == null) throw new IllegalStateException("no SkyBlock pack listed");
                byte[] zip = http.send(HttpRequest.newBuilder(URI.create(best.get("url").getAsString()))
                                .timeout(Duration.ofMinutes(2)).header("User-Agent", "SkyAnimations-Mod").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
                String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
                if (best.has("hash") && !sha1.equalsIgnoreCase(best.get("hash").getAsString())) {
                    throw new IllegalStateException("download damaged (hash mismatch)");
                }
                Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
                Files.createDirectories(dir);
                Files.write(dir.resolve(FILE), zip);
                msg = "§aHypixel SkyBlock pack downloaded §7(" + zip.length / 1024 / 1024 + " MB).";
            } catch (Exception e) {
                SkyAnimationsClient.LOG.warn("Could not download the Hypixel pack", e);
                String m = "§cCould not download the pack: §7" + e.getMessage();
                mc.execute(() -> report.accept(m));
                return;
            }
            String done = msg;
            mc.execute(() -> {
                report.accept(done);
                report.accept(enable() ? "§7Switched it on, reloading textures..."
                        : "§7Turn it on in §fOptions → Resource Packs§7 (" + FILE + ").");
            });
        }, "skyanimations-pack");
        t.setDaemon(true);
        t.start();
    }

    /** Selects the downloaded pack and reloads. Done by reflection so a renamed method can't break the build. */
    @SuppressWarnings("unchecked")
    private static boolean enable() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Object repo = Minecraft.class.getMethod("getResourcePackRepository").invoke(mc);
            repo.getClass().getMethod("reload").invoke(repo);
            Collection<String> selected = (Collection<String>) repo.getClass().getMethod("getSelectedIds").invoke(repo);
            String id = "file/" + FILE;
            List<String> ids = new ArrayList<>(selected);
            if (!ids.contains(id)) ids.add(id);
            repo.getClass().getMethod("setSelected", Collection.class).invoke(repo, ids);
            for (var m : mc.options.getClass().getMethods()) {
                if (m.getName().equals("updateResourcePacks") && m.getParameterCount() == 1) {
                    m.invoke(mc.options, repo);
                    break;
                }
            }
            Minecraft.class.getMethod("reloadResourcePacks").invoke(mc);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAnimationsClient.LOG.debug("Could not switch the pack on", e);
            return false;
        }
    }
}

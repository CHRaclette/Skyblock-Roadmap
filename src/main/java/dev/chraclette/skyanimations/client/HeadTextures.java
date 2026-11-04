package dev.chraclette.skyanimations.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.chraclette.skyanimations.render.Props;
import dev.chraclette.skyanimations.render.Skins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Head skins for the 3D head models (Props). The item's skin value points at a texture on
 * textures.minecraft.net (the same place Minecraft loads every head from); we load it once
 * and register it as skyanimations:heads/<hash>.
 */
final class HeadTextures {

    private static final Map<String, String> READY = new ConcurrentHashMap<>();   // texture value -> texture path
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final Map<String, String> EXTRA = new ConcurrentHashMap<>();   // name -> texture value (pets etc.)

    private HeadTextures() {
    }

    static void register() {
        Props.headSkins = HeadTextures::skinFor;
    }

    /** Heads that are not in the item list (e.g. pets). */
    static void put(String name, String textureValue) {
        EXTRA.put(name.toLowerCase(Locale.ROOT), textureValue);
    }

    private static Skins.CubeSkin skinFor(String name) {
        String value = EXTRA.get(name.toLowerCase(Locale.ROOT).replaceAll("^\\[lvl \\d+] ", ""));
        if (value == null) {
            ItemCatalog.Info info = ItemCatalog.byName(name);
            if (info == null || info.skin == null || info.skin.isEmpty()) return null;
            value = info.skin;
        }
        String path = READY.get(value);
        if (path != null) return Skins.head(path);
        load(value);
        return null; // the normal icon is shown until the skin is there
    }

    private static void load(String value) {
        if (LOADING.putIfAbsent(value, true) != null) return;
        Thread t = new Thread(() -> {
            try {
                String json = new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
                String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                byte[] png = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
                String hash = url.substring(url.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage img = NativeImage.read(png);
                        Identifier id = Identifier.fromNamespaceAndPath(SkyAnimationsClient.MOD_ID, "heads/" + hash);
                        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "skyanimations head " + hash, img));
                        READY.put(value, id.toString());
                    } catch (Exception e) {
                        SkyAnimationsClient.LOG.debug("Could not use head skin", e);
                    }
                });
            } catch (Exception e) {
                SkyAnimationsClient.LOG.debug("Could not load head skin", e);
            }
        }, "skyanimations-head");
        t.setDaemon(true);
        t.start();
    }
}

package dev.chraclette.skyanimations.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.render.Cutscene.Price;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prices from the public Hypixel bazaar endpoint (no API key needed) and Moulberry's lowest-BIN
 * list. Fetched in the background every 10 minutes, never on the render thread.
 */
final class Prices {

    private static final String BAZAAR = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String LOWEST_BIN = "https://moulberry.codes/lowestbin.json";
    private static final long REFRESH_MS = 10 * 60_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private static volatile Map<String, Double> bazaar = Map.of();
    private static volatile Map<String, Double> bins = Map.of();
    private static volatile long fetchedAt;

    private Prices() {
    }

    static void refreshIfNeeded() {
        if (!Config.get().prices) return;
        if (System.currentTimeMillis() - fetchedAt < REFRESH_MS || !BUSY.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try {
                Map<String, Double> bz = new HashMap<>();
                JsonObject products = get(BAZAAR).getAsJsonObject().getAsJsonObject("products");
                for (var e : products.entrySet()) {
                    JsonObject quick = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
                    if (quick != null && quick.has("sellPrice")) {
                        bz.put(e.getKey(), quick.get("sellPrice").getAsDouble());
                    }
                }
                bazaar = bz;
            } catch (Exception e) {
                SkyAnimationsClient.LOG.debug("Bazaar fetch failed", e);
            }
            try {
                Map<String, Double> lb = new HashMap<>();
                for (var e : get(LOWEST_BIN).getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonPrimitive()) lb.put(e.getKey(), e.getValue().getAsDouble());
                }
                bins = lb;
            } catch (Exception e) {
                SkyAnimationsClient.LOG.debug("Lowest BIN fetch failed", e);
            }
            fetchedAt = System.currentTimeMillis();
            BUSY.set(false);
        }, "skyanimations-prices");
        thread.setDaemon(true);
        thread.start();
    }

    private static JsonElement get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SkyAnimations-Mod")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body());
    }

    /** Price for the drop, or null if unknown. Uses the item's SkyBlock id when we have the stack. */
    static Price lookup(Drop drop, String skyblockId) {
        if (!Config.get().prices) return null;
        Map<String, Double> bz = bazaar, lb = bins;
        if (bz.isEmpty() && lb.isEmpty()) return null;
        if (skyblockId == null || skyblockId.isEmpty()) skyblockId = ItemCatalog.idOf(drop.name());
        int count = Math.max(1, drop.count());

        for (String id : candidates(drop, skyblockId)) {
            Double p = bz.get(id);
            if (p != null && p > 0) return new Price(Math.round(p * count), "bazaar instant sell");
            p = lb.get(id);
            if (p != null && p > 0) return new Price(Math.round(p * count), "lowest BIN on the auction house");
        }

        // Enchantments: "Chimera I" -> ENCHANTMENT_ULTIMATE_CHIMERA_1 / ULTIMATE_CHIMERA;1
        String name = drop.name();
        int space = name.lastIndexOf(' ');
        if (space > 0) {
            int level = roman(name.substring(space + 1));
            if (level > 0) {
                String base = normalize(name.substring(0, space));
                for (var e : bz.entrySet()) {
                    if (e.getKey().startsWith("ENCHANTMENT_") && e.getKey().endsWith("_" + base + "_" + level)
                            || e.getKey().equals("ENCHANTMENT_" + base + "_" + level)) {
                        return new Price(Math.round(e.getValue() * count), "bazaar instant sell");
                    }
                }
                for (var e : lb.entrySet()) {
                    if (e.getKey().endsWith(base + ";" + level)) {
                        return new Price(Math.round(e.getValue() * count), "lowest BIN on the auction house");
                    }
                }
            }
        }
        // Pets: "ENDER_DRAGON;4" etc. Take the highest rarity listed.
        String base = normalize(name);
        double best = 0;
        for (var e : lb.entrySet()) {
            if (e.getKey().startsWith(base + ";")) best = Math.max(best, e.getValue());
        }
        return best > 0 ? new Price(Math.round(best * count), "lowest BIN on the auction house") : null;
    }

    private static String[] candidates(Drop drop, String skyblockId) {
        String n = normalize(drop.name());
        return new String[]{skyblockId, n, n.replace("S_", "_"), "ENCHANTED_" + n};
    }

    static String normalize(String name) {
        return name.toUpperCase(Locale.ROOT)
                .replace("'", "")
                .replace("-", "_")
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static int roman(String s) {
        return switch (s) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
            default -> 0;
        };
    }
}

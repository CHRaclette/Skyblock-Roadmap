package dev.chraclette.skyanimations.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reads run totals from your SkyBlock profile with the official Hypixel API.
 *
 * The profile endpoint needs an API key. SkyBlock Profile Viewer (meowdding/skyblock-pv) solves that
 * with its own server: the client proves who it is through Mojang's session server (joinServer),
 * gets a token, and their server asks Hypixel with the server's app key. That server and key belong
 * to them, so SkyAnimations instead uses a personal key you create on developer.hypixel.net
 * (/sa apikey <key>). The numbers are read the same way SkyBlockPv does:
 *   Nucleus runs = the lowest "total_placed" of the five Nucleus crystals (mining_core.crystals)
 *   Catacombs runs = dungeons.dungeon_types.(master_)catacombs.tier_completions per floor
 * Without a key the mod simply counts runs in game from now on.
 */
final class ProfileApi {

    private static final String URL = "https://api.hypixel.net/v2/skyblock/profiles?uuid=";
    private static final String[] NUCLEUS_CRYSTALS = {"jade_crystal", "amethyst_crystal", "topaz_crystal", "sapphire_crystal", "amber_crystal"};
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static long lastTry;

    private ProfileApi() {
    }

    static boolean hasKey() {
        String k = Config.get().apiKey;
        return k != null && !k.isBlank();
    }

    /** Refreshes if a key is set and the numbers are older than 10 minutes. */
    static void refreshIfOld() {
        if (!hasKey()) return;
        long now = System.currentTimeMillis();
        if (now - LedgerStore.get().runsFromProfileAt < 10 * 60_000L || now - lastTry < 60_000L) return;
        refresh(msg -> { });
    }

    /** Fetches in the background; report gets a short status line (on the client thread). */
    static void refresh(Consumer<String> report) {
        if (!hasKey()) {
            report.accept("§cNo API key. §7Create a personal key on §fdeveloper.hypixel.net§7, then §f/sa apikey <key>");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        lastTry = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = mc.getUser().getProfileId();
        String wantedProfile = HypixelInfo.profile();
        Thread t = new Thread(() -> {
            String msg;
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(URL + uuid.toString().replace("-", "")))
                                .timeout(Duration.ofSeconds(20)).header("API-Key", Config.get().apiKey.trim())
                                .header("User-Agent", "SkyAnimations-Mod").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 403) throw new IllegalStateException("the API key was rejected (403)");
                if (res.statusCode() == 429) throw new IllegalStateException("too many requests, try again in a minute");
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
                Map<String, Long> runs = parse(JsonParser.parseString(res.body()).getAsJsonObject(),
                        uuid.toString().replace("-", ""), wantedProfile);
                mc.execute(() -> LedgerStore.profileRuns(runs));
                long dungeons = runs.entrySet().stream().filter(e -> e.getKey().matches("[FM][1-7]")).mapToLong(Map.Entry::getValue).sum();
                msg = "§7Profile read: §f" + runs.getOrDefault("nucleus", 0L) + "§7 Nucleus runs, §f" + dungeons + "§7 Catacombs runs.";
            } catch (Exception e) {
                SkyAnimationsClient.LOG.warn("Could not read the SkyBlock profile", e);
                msg = "§cCould not read your profile: §7" + e.getMessage();
            } finally {
                BUSY.set(false);
            }
            String m = msg;
            mc.execute(() -> report.accept(m));
        }, "skyanimations-profile");
        t.setDaemon(true);
        t.start();
    }

    static Map<String, Long> parse(JsonObject root, String uuid, String wantedProfile) {
        JsonObject member = null;
        for (JsonElement el : root.getAsJsonArray("profiles")) {
            JsonObject p = el.getAsJsonObject();
            String name = p.has("cute_name") ? p.get("cute_name").getAsString() : "";
            boolean match = wantedProfile != null && !wantedProfile.isEmpty() && name.equalsIgnoreCase(wantedProfile);
            boolean selected = p.has("selected") && p.get("selected").getAsBoolean();
            if (match || (member == null && selected)) {
                JsonObject members = p.getAsJsonObject("members");
                if (members != null && members.has(uuid)) member = members.getAsJsonObject(uuid);
                if (match) break;
            }
        }
        if (member == null) throw new IllegalStateException("profile not found");
        Map<String, Long> runs = new LinkedHashMap<>();
        // Nucleus: each run places all five crystals, so the lowest count is the number of runs
        JsonObject crystals = obj(obj(member, "mining_core"), "crystals");
        if (crystals != null) {
            long min = Long.MAX_VALUE;
            for (String c : NUCLEUS_CRYSTALS) {
                JsonObject cr = obj(crystals, c);
                long placed = cr != null && cr.has("total_placed") ? cr.get("total_placed").getAsLong() : 0;
                min = Math.min(min, placed);
            }
            runs.put("nucleus", min == Long.MAX_VALUE ? 0 : min);
        }
        JsonObject types = obj(obj(member, "dungeons"), "dungeon_types");
        readFloors(obj(types, "catacombs"), "F", runs);
        readFloors(obj(types, "master_catacombs"), "M", runs);
        return runs;
    }

    private static void readFloors(JsonObject type, String prefix, Map<String, Long> out) {
        JsonObject tc = obj(type, "tier_completions");
        if (tc == null) return;
        for (var e : tc.entrySet()) {
            try {
                int floor = (int) Double.parseDouble(e.getKey());
                if (floor >= 1 && floor <= 7) out.put(prefix + floor, (long) e.getValue().getAsDouble());
            } catch (RuntimeException ignored) {
                // "total" and other keys
            }
        }
    }

    private static JsonObject obj(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) return null;
        return o.getAsJsonObject(key);
    }

    static String mask(String key) {
        if (key == null || key.length() < 8) return "****";
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4).toLowerCase(Locale.ROOT);
    }
}

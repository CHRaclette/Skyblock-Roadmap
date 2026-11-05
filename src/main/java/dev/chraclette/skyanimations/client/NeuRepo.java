package dev.chraclette.skyanimations.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Data from the NotEnoughUpdates repo (MIT), the same source Skyblocker and other mods use:
 * constants/dyes.json   exact hex colours of every dye (animated dyes = a list of colours)
 * constants/rngscore.json every RNG-Meter item per Catacombs floor and per slayer, with its score
 * Cached in config/skyanimations/ and refreshed once a day.
 */
final class NeuRepo {

    private static final String BASE = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/";
    private static final long MAX_AGE = Duration.ofHours(24).toMillis();

    private static volatile Map<String, int[]> dyes = Map.of();
    private static volatile JsonObject rng = new JsonObject();

    record RngItem(String id, long score) {
    }

    private NeuRepo() {
    }

    static void init() {
        Thread t = new Thread(() -> {
            load("dyes.json", NeuRepo::indexDyes);
            load("rngscore.json", o -> rng = o);
        }, "skyanimations-neu");
        t.setDaemon(true);
        t.start();
    }

    private static final java.util.Map<String, Boolean> PET_ASKED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Finds a pet's head skin in the NEU item repo (items/ENDER_DRAGON;4.json -> SkullOwner texture) and hands it
     * to the 3D head renderer. Cached in config/skyanimations/neu-pets.json. rarityIndex 0 = common ... 5 = mythic.
     */
    static void petSkin(String petName, int rarityIndex) {
        String key = petName.toLowerCase(java.util.Locale.ROOT).replaceAll("^\\[lvl \\d+] ", "").trim();
        if (PET_ASKED.putIfAbsent(key, true) != null) return;
        Thread t = new Thread(() -> {
            Path cache = dir().resolve("neu-pets.json");
            try {
                JsonObject known = Files.exists(cache) ? JsonParser.parseString(Files.readString(cache)).getAsJsonObject() : new JsonObject();
                if (known.has(key)) {
                    HeadTextures.put(key, known.get(key).getAsString());
                    return;
                }
                String id = key.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                java.util.LinkedHashSet<Integer> order = new java.util.LinkedHashSet<>(java.util.List.of(Math.max(0, Math.min(5, rarityIndex)), 4, 3, 5, 2, 1, 0));
                for (int r : order) {
                    String url = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/" + id + "%3B" + r + ".json";
                    HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "SkyAnimations-Mod").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (res.statusCode() != 200) continue;
                    JsonObject item = JsonParser.parseString(res.body()).getAsJsonObject();
                    String nbt = item.has("nbttag") ? item.get("nbttag").getAsString() : "";
                    var m = java.util.regex.Pattern.compile("Value:\\\\?\"?([A-Za-z0-9+/=]{40,})").matcher(nbt);
                    if (!m.find()) continue;
                    String value = m.group(1);
                    HeadTextures.put(key, value);
                    known.addProperty(key, value);
                    Files.createDirectories(dir());
                    Files.writeString(cache, known.toString());
                    return;
                }
            } catch (Exception e) {
                SkyAnimationsClient.LOG.debug("Could not find pet skin for {}", petName, e);
            }
        }, "skyanimations-pet");
        t.setDaemon(true);
        t.start();
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyanimations");
    }

    private static void load(String name, java.util.function.Consumer<JsonObject> use) {
        Path f = dir().resolve("neu-" + name);
        try {
            if (Files.exists(f)) use.accept(JsonParser.parseString(Files.readString(f)).getAsJsonObject());
            boolean old = !Files.exists(f) || System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() > MAX_AGE;
            if (!old) return;
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(BASE + name))
                            .timeout(Duration.ofSeconds(30)).header("User-Agent", "SkyAnimations-Mod").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            use.accept(o);
            Files.createDirectories(dir());
            Files.writeString(f, res.body());
        } catch (Exception e) {
            SkyAnimationsClient.LOG.warn("Could not load NEU {}", name, e);
        }
    }

    private static void indexDyes(JsonObject o) {
        Map<String, int[]> out = new HashMap<>();
        if (o.has("static")) {
            for (Entry<String, JsonElement> e : o.getAsJsonObject("static").entrySet()) {
                out.put(e.getKey(), new int[]{hex(e.getValue().getAsString())});
            }
        }
        if (o.has("animated")) {
            for (Entry<String, JsonElement> e : o.getAsJsonObject("animated").entrySet()) {
                var arr = e.getValue().getAsJsonArray();
                int[] c = new int[arr.size()];
                for (int i = 0; i < c.length; i++) c[i] = hex(arr.get(i).getAsString());
                if (c.length > 0) out.put(e.getKey(), c);
            }
        }
        dyes = out;
    }

    private static int hex(String s) {
        return Integer.parseInt(s.replace("#", "").trim(), 16) & 0xFFFFFF;
    }

    /** "Necron Dye" -> DYE_NECRON. */
    static String dyeId(String name) {
        String base = name.replaceAll("(?i)\\s*dye\\s*$", "").trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return "DYE_" + base;
    }

    /** Exact colours of a dye (1 = static, more = animated), or null if unknown. */
    static int[] dyeColors(String name) {
        int[] c = dyes.get(dyeId(name));
        if (c == null && name.toLowerCase(Locale.ROOT).startsWith("tentacle")) c = dyes.get("TENTACLE_DYE");
        return c;
    }

    /**
     * RNG-Meter items for a source: "F7", "M3", or a slayer boss name ("Voidgloom Seraph").
     * Empty if unknown.
     */
    static List<RngItem> rngItems(String key) {
        List<RngItem> out = new ArrayList<>();
        if (key == null || key.isEmpty()) return out;
        JsonObject group = null;
        if (key.matches("[FM][1-7]") && rng.has("catacombs")) {
            JsonObject cata = rng.getAsJsonObject("catacombs");
            if (cata.has(key)) group = cata.getAsJsonObject(key);
        } else if (rng.has("slayer")) {
            for (Entry<String, JsonElement> e : rng.getAsJsonObject("slayer").entrySet()) {
                String boss = e.getKey().toLowerCase(Locale.ROOT);
                String k = key.toLowerCase(Locale.ROOT);
                if (k.contains(boss) || boss.contains(k) || k.contains(boss.split(" ")[0])) {
                    group = e.getValue().getAsJsonObject();
                    break;
                }
            }
        }
        if (group == null) return out;
        for (Entry<String, JsonElement> e : group.entrySet()) {
            out.add(new RngItem(e.getKey(), e.getValue().getAsLong()));
        }
        return out;
    }

    /** Readable name for a NEU id: the Hypixel item name, or "Ultimate Wise II" for enchant books. */
    static String name(String id) {
        ItemCatalog.Info info = ItemCatalog.byId(id);
        if (info != null && info.name != null) return info.name;
        String base = id;
        String level = "";
        int semi = id.indexOf(';');
        if (semi > 0) {
            base = id.substring(0, semi);
            level = " " + roman(Integer.parseInt(id.substring(semi + 1)));
        }
        StringBuilder sb = new StringBuilder();
        for (String w : base.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        String n = sb + level;
        if (semi > 0 && !id.startsWith("ATTRIBUTE")) n = "Enchanted Book (" + n + ")";
        return n;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}

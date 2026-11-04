package dev.chraclette.skyanimations.client;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.Materials;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What every SkyBlock item really looks like, from Hypixel's public item list
 * (api.hypixel.net/v2/resources/skyblock/items, no API key needed): base material, head texture,
 * leather colour, enchant glint and the model in Hypixel's SkyBlock resource pack.
 */
public final class ItemCatalog {

    /** The few fields we need, kept small on disk. */
    public static final class Info {
        public String id;
        public String name;
        public String material;
        public int durability;
        public String skin;
        public String model;
        public String color;
        public boolean glowing;
        public String tier;
        public double npc;
    }

    /** Rarity order used by Hypixel. */
    public static int tierRank(String tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case "UNCOMMON" -> 1; case "RARE" -> 2; case "EPIC" -> 3; case "LEGENDARY" -> 4; case "MYTHIC" -> 5;
            case "DIVINE" -> 6; case "SPECIAL" -> 7; case "VERY_SPECIAL" -> 8; case "ULTIMATE" -> 9; default -> 0;
        };
    }

    /** Hypixel rarity colour. */
    public static int tierColor(String tier) {
        if (tier == null) return 0xFFFFFF;
        return switch (tier) {
            case "UNCOMMON" -> 0x55FF55; case "RARE" -> 0x5555FF; case "EPIC" -> 0xAA00AA; case "LEGENDARY" -> 0xFFAA00;
            case "MYTHIC" -> 0xFF55FF; case "DIVINE" -> 0x55FFFF; case "SPECIAL", "VERY_SPECIAL" -> 0xFF5555;
            case "ULTIMATE" -> 0xAA0000; default -> 0xFFFFFF;
        };
    }

    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final long MAX_AGE = Duration.ofHours(24).toMillis();
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private static volatile Map<String, Info> byName = Map.of();
    private static volatile Map<String, Info> byId = Map.of();
    private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MODEL_OK = new ConcurrentHashMap<>();
    private static long modelCheckedAt;

    private ItemCatalog() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyanimations-items-v2.json");
    }

    /** Loads the cached list, and refreshes it in the background when it is old. */
    static void init() {
        Thread t = new Thread(() -> {
            try {
                Path f = file();
                if (Files.exists(f)) {
                    index(GSON.fromJson(Files.readString(f), Info[].class));
                }
                boolean old = !Files.exists(f) || System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() > MAX_AGE;
                if (old) refresh();
            } catch (Exception e) {
                SkyAnimationsClient.LOG.warn("Could not load the SkyBlock item list", e);
            }
        }, "skyanimations-items");
        t.setDaemon(true);
        t.start();
    }

    static void refresh() {
        if (!BUSY.compareAndSet(false, true)) return;
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(30)).header("User-Agent", "SkyAnimations-Mod").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
            Info[] list = new Info[items.size()];
            for (int i = 0; i < items.size(); i++) {
                JsonObject o = items.get(i).getAsJsonObject();
                Info info = new Info();
                info.id = str(o, "id");
                info.name = DropParser.strip(str(o, "name"));
                info.material = str(o, "material");
                info.durability = o.has("durability") ? o.get("durability").getAsInt() : 0;
                info.model = str(o, "item_model");
                info.color = str(o, "color");
                info.glowing = o.has("glowing") && o.get("glowing").getAsBoolean();
                info.tier = str(o, "tier");
                info.npc = o.has("npc_sell_price") ? o.get("npc_sell_price").getAsDouble() : 0;
                if (o.has("skin") && o.get("skin").isJsonObject()) {
                    info.skin = str(o.getAsJsonObject("skin"), "value");
                }
                list[i] = info;
            }
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(list));
            index(list);
            SkyAnimationsClient.LOG.info("SkyBlock item list updated ({} items)", list.length);
        } catch (Exception e) {
            SkyAnimationsClient.LOG.warn("Could not download the SkyBlock item list", e);
        } finally {
            BUSY.set(false);
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static void index(Info[] list) {
        if (list == null) return;
        Map<String, Info> n = new HashMap<>(), d = new HashMap<>();
        for (Info i : list) {
            if (i == null || i.id == null) continue;
            d.put(i.id, i);
            if (i.name != null && !i.name.isEmpty()) n.putIfAbsent(i.name.toLowerCase(Locale.ROOT), i);
        }
        byName = n;
        byId = d;
        STACKS.clear();
    }

    public static Info byName(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT).trim());
    }

    public static Info byId(String id) {
        return byId.get(id);
    }

    /** SkyBlock id for an item name ("Griffin Feather" -> "GRIFFIN_FEATHER"), or "". */
    public static String idOf(String name) {
        Info i = byName(name);
        return i == null ? "" : i.id;
    }

    /** The item as it looks in game, or EMPTY if we don't know it. */
    public static ItemStack stack(String name) {
        Info info = byName(name);
        if (info == null) return ItemStack.EMPTY;
        ItemStack cached = STACKS.get(info.id);
        if (cached != null && modelStillValid(info)) return cached;
        ItemStack built = build(info);
        STACKS.put(info.id, built);
        return built;
    }

    private static boolean modelStillValid(Info info) {
        // the server pack can be loaded after we first built the stack: re-check now and then
        return info.model == null || info.model.isEmpty() || System.currentTimeMillis() - modelCheckedAt < 15_000;
    }

    /** A player head with a texture (base64 "textures" value). */
    static ItemStack head(String key, String texture) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        try {
            var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
            var profile = new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), "skull", properties);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        } catch (RuntimeException e) {
            SkyAnimationsClient.LOG.debug("Could not build head {}", key, e);
        }
        return stack;
    }

    private static ItemStack build(Info info) {
        String modern = Materials.modern(info.material, info.durability);
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(modern));
        if (item == null || item == Items.AIR) item = Items.NETHER_STAR;
        ItemStack stack = new ItemStack(item);
        try {
            if (info.skin != null && !info.skin.isEmpty() && item == Items.PLAYER_HEAD) {
                var properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", info.skin)));
                var profile = new GameProfile(UUID.nameUUIDFromBytes(info.id.getBytes(StandardCharsets.UTF_8)), "skull", properties);
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            }
            if (info.color != null && !info.color.isEmpty()) {
                String[] p = info.color.split(",");
                if (p.length == 3) {
                    int rgb = (Integer.parseInt(p[0].trim()) << 16) | (Integer.parseInt(p[1].trim()) << 8) | Integer.parseInt(p[2].trim());
                    stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
                }
            }
            if (info.glowing) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            if (info.model != null && !info.model.isEmpty() && modelLoaded(info.model)) {
                stack.set(DataComponents.ITEM_MODEL, Identifier.parse(info.model));
            }
        } catch (RuntimeException e) {
            SkyAnimationsClient.LOG.debug("Could not style {}", info.id, e);
        }
        return stack;
    }

    /** True if the item model exists in the loaded resource packs (Hypixel's SkyBlock pack). */
    private static boolean modelLoaded(String model) {
        if (System.currentTimeMillis() - modelCheckedAt > 15_000) {
            MODEL_OK.clear();
            modelCheckedAt = System.currentTimeMillis();
        }
        return MODEL_OK.computeIfAbsent(model, m -> {
            try {
                Identifier id = Identifier.parse(m);
                Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json");
                return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    public static boolean loaded() {
        return !byId.isEmpty();
    }

    public static int size() {
        return byId.size();
    }

    static List<String> debug(String name) {
        Info i = byName(name);
        if (i == null) return List.of("not in the item list");
        return List.of("id " + i.id, "material " + i.material + ":" + i.durability + " -> " + Materials.modern(i.material, i.durability),
                "model " + (i.model == null || i.model.isEmpty() ? "-" : i.model + (modelLoaded(i.model) ? " (pack loaded)" : " (pack NOT loaded)")),
                "head texture " + (i.skin != null && !i.skin.isEmpty()), "glint " + i.glowing);
    }
}

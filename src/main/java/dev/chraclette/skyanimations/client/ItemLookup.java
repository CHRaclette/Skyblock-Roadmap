package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.core.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/** Finds the real dropped item in the inventory (with Hypixel's texture), or picks a stand-in. */
final class ItemLookup {

    private ItemLookup() {
    }

    /** The real stack from the inventory, or EMPTY. */
    static ItemStack findInInventory(Drop drop) {
        var player = Minecraft.getInstance().player;
        if (player == null || drop.book()) return ItemStack.EMPTY;
        String want = drop.name().toLowerCase(Locale.ROOT);
        var inv = player.getInventory();
        ItemStack partial = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String name = DropParser.strip(s.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (name.equals(want)) return s.copy();
            if (partial.isEmpty() && want.length() >= 5 && name.contains(want)) partial = s.copy();
        }
        return partial;
    }

    static ItemStack icon(Drop drop, ItemStack found) {
        if (!found.isEmpty()) return found;
        if (!drop.book()) {
            ItemStack real = ItemCatalog.stack(drop.name());
            if (!real.isEmpty()) return real;
        }
        ItemStack stack = new ItemStack(fallback(drop));
        String n = drop.name().toLowerCase(Locale.ROOT);
        boolean fancy = n.startsWith("enchanted") || n.startsWith("mutant") || n.startsWith("compacted")
                || n.startsWith("polished") || n.startsWith("box of");
        if (drop.book() || drop.tier().rank >= 3 || fancy) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (drop.book()) {
            tagBook(stack, drop.name());
        } else {
            // unknown to the item list: still give it the SkyBlock id/name so packs can match it
            String id = ItemCatalog.idOf(drop.name());
            ItemCatalog.tag(stack, id != null && !id.isEmpty() ? id : drop.name().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"),
                    drop.name(), drop.color(), null);
        }
        return stack;
    }

    private static final java.util.Set<String> ULTIMATES = java.util.Set.of("chimera", "combo", "duplex", "fatal tempo",
            "flash", "habanero tactics", "inferno", "last stand", "legion", "no pain no gain", "one for all", "rend",
            "refrigerate", "soul eater", "swarm", "the one", "bobbin time", "bank", "jerry", "wise", "wisdom", "reiterate", "flowstate");

    /**
     * Enchanted books like SkyBlock sends them: id ENCHANTED_BOOK + enchantments {ultimate_chimera: 1},
     * so packs that texture books per enchantment (e.g. a Chimera book) show it.
     */
    static void tagBook(ItemStack stack, String name) {
        String n = name.replaceAll("(?i)^enchanted book \\(|\\)$", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s+([IVX]+)$").matcher(n);
        String ench = n;
        int level = 1;
        if (m.matches()) {
            ench = m.group(1);
            level = roman(m.group(2));
        }
        String key = ench.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        String plain = key.replace('_', ' ');
        if (plain.startsWith("ultimate ")) key = "ultimate_" + key.substring(9);
        else if (ULTIMATES.contains(plain)) key = "ultimate_" + key;
        try {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", "ENCHANTED_BOOK");
            net.minecraft.nbt.CompoundTag ench2 = new net.minecraft.nbt.CompoundTag();
            ench2.putInt(key, level);
            tag.put("enchantments", ench2);
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Enchanted Book").withStyle(st -> st.withColor(0x5555FF).withItalic(false)));
        } catch (RuntimeException e) {
            SkyAnimationsClient.LOG.debug("Could not tag book {}", name, e);
        }
    }

    private static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return Math.max(1, total);
    }

    static String skyblockId(ItemStack stack) {
        if (stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("id", "");
    }

    /** Rarity colour of an item name, or -1. Handles both styled text and legacy § codes. */
    static int nameColor(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        Component name = stack.getHoverName();
        int c = color(name);
        if (c >= 0) return c;
        for (Component sib : name.getSiblings()) {
            c = color(sib);
            if (c >= 0) return c;
        }
        String raw = name.getString();
        int idx = raw.indexOf('§');
        while (idx >= 0 && idx + 1 < raw.length()) {
            int legacy = legacyColor(raw.charAt(idx + 1));
            if (legacy >= 0) return legacy;
            idx = raw.indexOf('§', idx + 1);
        }
        return -1;
    }

    static int color(Component component) {
        TextColor tc = component.getStyle().getColor();
        return tc == null ? -1 : tc.getValue();
    }

    private static int legacyColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0x000000; case '1' -> 0x0000AA; case '2' -> 0x00AA00; case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000; case '5' -> 0xAA00AA; case '6' -> 0xFFAA00; case '7' -> 0xAAAAAA;
            case '8' -> 0x555555; case '9' -> 0x5555FF; case 'a' -> 0x55FF55; case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555; case 'd' -> 0xFF55FF; case 'e' -> 0xFFFF55; case 'f' -> 0xFFFFFF;
            default -> -1;
        };
    }

    private static Item fallback(Drop drop) {
        String n = drop.name().toLowerCase(Locale.ROOT);
        if (drop.effect() == Effect.DYE) return dye(drop.color());
        if (drop.book() || Themes.looksLikeEnchant(drop.name()) || n.contains("book")) return Items.ENCHANTED_BOOK;
        // Hypixel sends ~900 items as plain paper + a pack model; without the pack, pick something close
        if (n.contains("amethyst")) return Items.AMETHYST_SHARD;
        if (n.contains("ruby")) return Items.REDSTONE;
        if (n.contains("sapphire")) return Items.LAPIS_LAZULI;
        if (n.contains("amber")) return Items.HONEYCOMB;
        if (n.contains("topaz") || n.contains("citrine")) return Items.GLOWSTONE_DUST;
        if (n.contains("jasper")) return byId("pink_dye", Items.REDSTONE);
        if (n.contains("jade") || n.contains("peridot")) return Items.EMERALD;
        if (n.contains("onyx")) return Items.COAL;
        if (n.contains("aquamarine")) return Items.PRISMARINE_CRYSTALS;
        if (n.contains("opal")) return Items.QUARTZ;
        if (n.contains("jungle heart") || n.contains(" core") || n.contains("heart")) return Items.HEART_OF_THE_SEA;
        if (n.contains("fragment")) return Items.PRISMARINE_SHARD;
        if (n.contains("alloy")) return Items.NETHERITE_INGOT;
        if (n.contains("egg")) return Items.EGG;
        if (n.contains("treasurite") || n.contains("blood")) return Items.REDSTONE;
        if (n.contains("pearl")) return Items.ENDER_PEARL;
        if (n.contains("potion")) return Items.POTION;
        if (n.contains("claw") || n.contains("fossil")) return Items.BONE;
        if (n.contains("mixture")) return Items.GUNPOWDER;
        if (n.contains("chamber")) return Items.CHEST;
        if (n.contains("helix")) return Items.NAUTILUS_SHELL;
        if (n.contains("apparatus") || n.contains("gear") || n.contains("chip")) return Items.CLOCK;
        if (n.contains("crystal")) return Items.AMETHYST_CLUSTER;
        if (n.contains("essence")) return Items.GHAST_TEAR;
        if (n.contains("star")) return Items.NETHER_STAR;
        if (n.contains("wool")) return byId("yellow_wool", Items.STRING);
        if (n.contains("vinyl") || n.contains("stereo")) return byId("music_disc_cat", Items.JUKEBOX);
        if (n.contains("hay")) return Items.HAY_BLOCK;
        if (n.contains("golden carrot")) return Items.GOLDEN_CARROT;
        if (n.contains("baked potato")) return Items.BAKED_POTATO;
        if (n.contains("sugar")) return Items.SUGAR;
        if (n.contains("cookie")) return Items.COOKIE;
        if (n.contains("sunflower")) return byId("sunflower", Items.DANDELION);
        if (n.contains("moonflower")) return byId("blue_orchid", Items.DANDELION);
        if (n.contains("rose")) return byId("rose_bush", Items.POPPY);
        if (n.contains("wings")) return Items.FEATHER;
        if (n.contains("larva") || n.contains("worm")) return Items.SLIME_BALL;
        if (n.contains("bottle")) return Items.GLASS_BOTTLE;
        if (n.contains("claw")) return Items.SHEARS;
        if (n.contains("chip")) return Items.REDSTONE;
        if (n.contains("compost")) return Items.BONE_MEAL;
        if (n.contains("honey")) return Items.HONEY_BOTTLE;
        if (n.contains("jelly")) return Items.SLIME_BALL;
        if (n.contains("plant matter")) return Items.KELP;
        if (n.contains("cropie") || n.contains("squash") || n.contains("fermento") || n.contains("helianthus")) return Items.WHEAT_SEEDS;
        if (n.contains("feather")) return Items.FEATHER;
        if (n.contains("rune")) return Items.MAGMA_CREAM;
        if (n.contains("core")) return Items.HEART_OF_THE_SEA;
        if (n.contains("heart")) return Items.REDSTONE;
        if (n.contains("eye")) return Items.ENDER_EYE;
        if (n.contains("egg")) return Items.EGG;
        if (n.contains("shard") || n.contains("tooth")) return Items.PRISMARINE_SHARD;
        if (n.contains("handle") || n.contains("stick")) return Items.STICK;
        if (n.contains("blade") || n.contains("sword")) return Items.IRON_SWORD;
        if (n.contains("bone")) return Items.BONE;
        if (n.contains("flesh")) return Items.ROTTEN_FLESH;
        if (n.contains("skull")) return Items.WITHER_SKELETON_SKULL;
        if (n.contains("dice")) return Items.PLAYER_HEAD;
        if (n.contains("seed") || n.contains("spore")) return Items.WHEAT_SEEDS;
        if (n.contains("melon")) return Items.MELON_SLICE;
        if (n.contains("pumpkin") || n.contains("squash")) return Items.PUMPKIN;
        if (n.contains("wart")) return Items.NETHER_WART;
        if (n.contains("cactus")) return Items.CACTUS;
        if (n.contains("carrot")) return Items.CARROT;
        if (n.contains("potato")) return Items.POTATO;
        if (n.contains("wheat") || n.contains("cropie")) return Items.WHEAT;
        if (n.contains("cocoa")) return Items.COCOA_BEANS;
        if (n.contains("mushroom") || n.contains("fermento")) return Items.RED_MUSHROOM;
        if (n.contains("cane")) return Items.SUGAR_CANE;
        if (n.contains("talisman") || n.contains("ring") || n.contains("artifact") || n.contains("relic")) return Items.NETHER_STAR;
        if (n.contains("gem") || n.contains("opal")) return Items.EMERALD;
        if (n.contains("filter")) return Items.PAPER;
        if (n.contains("cheese")) return byId("yellow_dye", Items.GOLD_NUGGET);
        if (n.contains("stereo") || n.contains("vinyl")) return Items.MUSIC_DISC_CAT;
        if (n.contains("plushie") || n.contains("shelmet")) return Items.TURTLE_HELMET;
        if (drop.tier() == dev.chraclette.skyanimations.core.DropTier.PET) return Items.BONE;
        return Items.NETHER_STAR;
    }

    /** Coloured items are looked up by id: their Items fields changed in 26.x. */
    static Item byIdPublic(String id, Item fallback) {
        return byId(id, fallback);
    }

    private static Item byId(String id, Item fallback) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
        return item == null || item == Items.AIR ? fallback : item;
    }

    private static Item dye(int rgb) {
        String[] dyes = {"white_dye", "orange_dye", "magenta_dye", "light_blue_dye", "yellow_dye", "lime_dye",
                "pink_dye", "gray_dye", "light_gray_dye", "cyan_dye", "purple_dye", "blue_dye", "brown_dye",
                "green_dye", "red_dye", "black_dye"};
        int[] colors = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52, 0x9D9D97,
                0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            int dr = ((rgb >> 16) & 255) - ((colors[i] >> 16) & 255);
            int dg = ((rgb >> 8) & 255) - ((colors[i] >> 8) & 255);
            int db = (rgb & 255) - (colors[i] & 255);
            double dist = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return byId(dyes[best], Items.GLOWSTONE_DUST);
    }
}

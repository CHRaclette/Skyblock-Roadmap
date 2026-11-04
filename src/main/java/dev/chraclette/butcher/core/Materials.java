package dev.chraclette.butcher.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hypixel's item API still uses 1.8 material names with damage values ("SKULL_ITEM" + 3,
 * "INK_SACK" + 10). This turns them into modern item ids ("player_head", "lime_dye").
 */
public final class Materials {

    private Materials() {
    }

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /** Dyes by 1.8 INK_SACK damage. */
    private static final String[] INK = {"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye",
            "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye",
            "magenta_dye", "orange_dye", "bone_meal"};

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private static final Map<String, String> RENAMED = new HashMap<>();

    static {
        String[][] r = {
                {"SKULL_ITEM", "player_head"}, {"SKULL", "player_head"}, {"WATCH", "clock"}, {"SULPHUR", "gunpowder"},
                {"SEEDS", "wheat_seeds"}, {"NETHER_STALK", "nether_wart"}, {"CARROT_ITEM", "carrot"},
                {"POTATO_ITEM", "potato"}, {"MELON", "melon_slice"}, {"MELON_BLOCK", "melon"},
                {"YELLOW_FLOWER", "dandelion"}, {"HUGE_MUSHROOM_1", "brown_mushroom_block"},
                {"HUGE_MUSHROOM_2", "red_mushroom_block"}, {"EYE_OF_ENDER", "ender_eye"}, {"IRON_FENCE", "iron_bars"},
                {"SNOW_BALL", "snowball"}, {"BOOK_AND_QUILL", "writable_book"}, {"ENCHANTMENT_TABLE", "enchanting_table"},
                {"RAW_BEEF", "beef"}, {"PORK", "porkchop"}, {"GRILLED_PORK", "cooked_porkchop"},
                {"MUSHROOM_SOUP", "mushroom_stew"}, {"SPECKLED_MELON", "glistering_melon_slice"},
                {"FIREBALL", "fire_charge"}, {"WEB", "cobweb"}, {"NETHER_BRICK_ITEM", "nether_brick"},
                {"EXP_BOTTLE", "experience_bottle"}, {"FIREWORK", "firework_rocket"}, {"FIREWORK_CHARGE", "firework_star"},
                {"GOLD_BARDING", "golden_horse_armor"}, {"IRON_BARDING", "iron_horse_armor"},
                {"DIAMOND_BARDING", "diamond_horse_armor"}, {"RAW_CHICKEN", "chicken"}, {"MONSTER_EGG", "zombie_spawn_egg"},
                {"WOOD_STEP", "oak_slab"}, {"STEP", "stone_slab"}, {"SMOOTH_BRICK", "stone_bricks"},
                {"ENDER_STONE", "end_stone"}, {"NETHER_FENCE", "nether_brick_fence"}, {"QUARTZ_ORE", "nether_quartz_ore"},
                {"GLOWING_REDSTONE_ORE", "redstone_ore"}, {"THIN_GLASS", "glass_pane"}, {"STAINED_CLAY", "terracotta"},
                {"HARD_CLAY", "terracotta"}, {"MYCEL", "mycelium"}, {"WATER_LILY", "lily_pad"}, {"LONG_GRASS", "short_grass"},
                {"SNOW_BLOCK", "snow_block"}, {"BED", "red_bed"}, {"CAKE", "cake"}, {"DIODE", "repeater"},
                {"REDSTONE_COMPARATOR", "comparator"}, {"REDSTONE_TORCH_ON", "redstone_torch"},
                {"WOOD_BUTTON", "oak_button"}, {"WOOD_PLATE", "oak_pressure_plate"}, {"WORKBENCH", "crafting_table"},
                {"PISTON_BASE", "piston"}, {"PISTON_STICKY_BASE", "sticky_piston"}, {"BOAT", "oak_boat"},
                {"MINECART", "minecart"}, {"COMMAND", "command_block"}, {"CARROT_STICK", "carrot_on_a_stick"},
                {"LEASH", "lead"}, {"RECORD_3", "music_disc_blocks"}, {"RECORD_4", "music_disc_chirp"},
                {"RECORD_5", "music_disc_far"}, {"RECORD_6", "music_disc_mall"}, {"RECORD_7", "music_disc_mellohi"},
                {"RECORD_8", "music_disc_stal"}, {"RECORD_9", "music_disc_strad"}, {"RECORD_10", "music_disc_ward"},
                {"RECORD_11", "music_disc_11"}, {"RECORD_12", "music_disc_wait"}, {"GOLD_RECORD", "music_disc_13"},
                {"GREEN_RECORD", "music_disc_cat"}, {"SKULL_BANNER", "white_banner"}, {"BANNER", "white_banner"},
                {"SIGN", "oak_sign"}, {"WOOD_DOOR", "oak_door"}, {"IRON_DOOR_BLOCK", "iron_door"},
                {"TRAP_DOOR", "oak_trapdoor"}, {"FENCE", "oak_fence"}, {"FENCE_GATE", "oak_fence_gate"},
                {"COBBLE_WALL", "cobblestone_wall"}, {"STONE_PLATE", "stone_pressure_plate"},
                {"GOLD_PLATE", "light_weighted_pressure_plate"}, {"IRON_PLATE", "heavy_weighted_pressure_plate"},
                {"SUGAR_CANE_BLOCK", "sugar_cane"}, {"CLAY_BRICK", "brick"}, {"COOKED_FISH", "cooked_cod"},
                {"NETHER_WARTS", "nether_wart"}, {"PRISMARINE_CRYSTALS", "prismarine_crystals"},
                {"ENDER_PORTAL_FRAME", "end_portal_frame"}, {"MOB_SPAWNER", "spawner"}, {"BREWING_STAND_ITEM", "brewing_stand"},
                {"CAULDRON_ITEM", "cauldron"}, {"FLOWER_POT_ITEM", "flower_pot"}, {"REDSTONE_LAMP_OFF", "redstone_lamp"},
                {"POTION", "potion"}, {"EMPTY_MAP", "map"}, {"MAP", "filled_map"}, {"SAPLING", "oak_sapling"},
                {"STORAGE_MINECART", "chest_minecart"}, {"POWERED_MINECART", "furnace_minecart"},
                {"EXPLOSIVE_MINECART", "tnt_minecart"}, {"HOPPER_MINECART", "hopper_minecart"},
                {"WOOD_STAIRS", "oak_stairs"}, {"SPRUCE_WOOD_STAIRS", "spruce_stairs"},
                {"BIRCH_WOOD_STAIRS", "birch_stairs"}, {"JUNGLE_WOOD_STAIRS", "jungle_stairs"},
                {"SMOOTH_STAIRS", "stone_brick_stairs"}, {"RAILS", "rail"}, {"GRASS", "grass_block"},
                {"IRON_SPADE", "iron_shovel"}, {"WOOD_SPADE", "wooden_shovel"}, {"STONE_SPADE", "stone_shovel"},
                {"DIAMOND_SPADE", "diamond_shovel"}, {"GOLD_SPADE", "golden_shovel"}, {"QUARTZ_BLOCK", "quartz_block"},
        };
        for (String[] e : r) RENAMED.put(e[0], e[1]);
    }

    /** Modern item id (without namespace) for a 1.8 material + damage. Never null. */
    public static String modern(String material, int damage) {
        if (material == null || material.isEmpty()) return "nether_star";
        String m = material.toUpperCase(Locale.ROOT);
        int d = Math.max(0, damage);
        String color = COLORS[d % 16];
        switch (m) {
            case "WOOL": return color + "_wool";
            case "CARPET": return color + "_carpet";
            case "STAINED_GLASS": return color + "_stained_glass";
            case "STAINED_GLASS_PANE": return color + "_stained_glass_pane";
            case "STAINED_CLAY": return d == 0 && damage < 0 ? "terracotta" : color + "_terracotta";
            case "INK_SACK": return INK[d % 16];
            case "WOOD": return WOODS[d % 6] + "_planks";
            case "LOG": return WOODS[d % 4] + "_log";
            case "LOG_2": return WOODS[4 + d % 2] + "_log";
            case "LEAVES": return WOODS[d % 4] + "_leaves";
            case "LEAVES_2": return WOODS[4 + d % 2] + "_leaves";
            case "SAPLING": return WOODS[d % 6] + "_sapling";
            case "RAW_FISH": return switch (d) { case 1 -> "salmon"; case 2 -> "tropical_fish"; case 3 -> "pufferfish"; default -> "cod"; };
            case "COOKED_FISH": return d == 1 ? "cooked_salmon" : "cooked_cod";
            case "RED_ROSE": return switch (d) {
                case 1 -> "blue_orchid"; case 2 -> "allium"; case 3 -> "azure_bluet"; case 4 -> "red_tulip";
                case 5 -> "orange_tulip"; case 6 -> "white_tulip"; case 7 -> "pink_tulip"; case 8 -> "oxeye_daisy";
                default -> "poppy"; };
            case "DOUBLE_PLANT": return switch (d) {
                case 1 -> "lilac"; case 2 -> "tall_grass"; case 3 -> "large_fern"; case 4 -> "rose_bush"; case 5 -> "peony";
                default -> "sunflower"; };
            case "SKULL_ITEM": return switch (d) {
                case 0 -> "skeleton_skull"; case 1 -> "wither_skeleton_skull"; case 2 -> "zombie_head";
                case 4 -> "creeper_head"; default -> "player_head"; };
            case "STONE": return switch (d) {
                case 1 -> "granite"; case 2 -> "polished_granite"; case 3 -> "diorite"; case 4 -> "polished_diorite";
                case 5 -> "andesite"; case 6 -> "polished_andesite"; default -> "stone"; };
            case "SAND": return d == 1 ? "red_sand" : "sand";
            case "DIRT": return d == 2 ? "podzol" : d == 1 ? "coarse_dirt" : "dirt";
            case "SPONGE": return d == 1 ? "wet_sponge" : "sponge";
            case "GOLDEN_APPLE": return d == 1 ? "enchanted_golden_apple" : "golden_apple";
            case "COAL": return d == 1 ? "charcoal" : "coal";
            case "PRISMARINE": return d == 1 ? "prismarine_bricks" : d == 2 ? "dark_prismarine" : "prismarine";
            case "QUARTZ_BLOCK": return d == 1 ? "chiseled_quartz_block" : d == 2 ? "quartz_pillar" : "quartz_block";
            case "SANDSTONE": return d == 1 ? "chiseled_sandstone" : d == 2 ? "smooth_sandstone" : "sandstone";
            default:
                break;
        }
        String renamed = RENAMED.get(m);
        if (renamed != null) return renamed;
        String id = m.toLowerCase(Locale.ROOT);
        boolean gear = id.matches(".*_(sword|axe|pickaxe|hoe|helmet|chestplate|leggings|boots)");
        if (gear && id.startsWith("gold_")) id = "golden_" + id.substring(5);
        if (gear && id.startsWith("wood_")) id = "wooden_" + id.substring(5);
        if (id.endsWith("_item")) id = id.substring(0, id.length() - 5);
        return id;
    }
}

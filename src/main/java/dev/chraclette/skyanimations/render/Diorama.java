package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;
import dev.chraclette.skyanimations.render.Skins.Face;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small 3D piece of the place a drop happened, built from real Minecraft blocks, behind a scene:
 * floor, back wall, accents (gem veins, lava, ice, graves ...) and a slowly drifting camera. The action
 * of the scene plays in front of it, so every scene looks like it happens somewhere in SkyBlock.
 */
public final class Diorama {

    public enum Theme {
        HOLLOWS, GLACITE, CRIMSON, GARDEN, CATACOMBS, END, SPIDER, GRAVEYARD, HOWL, RIFT, HUB, BEACH
    }

    private static Face b(String block) {
        return new Face("textures/block/" + block + ".png", 16, 16, 0, 0, 16, 16);
    }

    /** First frame of an animated block texture (n frames stacked vertically), or frame f. */
    private static Face anim(String block, int frames, int f) {
        return new Face("textures/block/" + block + ".png", 16, 16 * frames, 0, 16 * (f % frames), 16, 16);
    }

    private static CubeSkin cube(Face top, Face side, int flesh) {
        return new CubeSkin(top, top, side, side, side, side, null, 1, 1, 1, flesh, Fx.darken(flesh, 0.4f));
    }

    private static CubeSkin cube(String block, int flesh) {
        return cube(b(block), b(block), flesh);
    }

    private static CubeSkin cube(String top, String side, int flesh) {
        return cube(b(top), b(side), flesh);
    }

    /** Blocks of one place: floor, rare floor accent, wall, wall accent, light colour. */
    private record Palette(CubeSkin[] floor, CubeSkin[] wall, CubeSkin accent, float accentChance, int light, boolean animatedAccent,
                           String animTex, int animFrames) {
    }

    private static Palette palette(Theme t) {
        return switch (t) {
            case HOLLOWS -> new Palette(new CubeSkin[]{cube("stone", 0x7F7F7F), cube("andesite", 0x888888), cube("cobblestone", 0x777777)},
                    new CubeSkin[]{cube("stone", 0x7F7F7F), cube("granite", 0x9A6A55), cube("diorite", 0xBBBBBB)},
                    cube("purple_stained_glass", 0xB050FF), 0.12f, 0xB050FF, false, null, 0);
            case GLACITE -> new Palette(new CubeSkin[]{cube("packed_ice", 0x8DB4F0), cube("snow", 0xF0F8FF), cube("packed_ice", 0x8DB4F0)},
                    new CubeSkin[]{cube("packed_ice", 0x8DB4F0), cube("blue_ice", 0x74A8F5), cube("ice", 0xA8C8FF)},
                    cube("light_blue_stained_glass", 0x6ED0FF), 0.12f, 0x9AD8FF, false, null, 0);
            case CRIMSON -> new Palette(new CubeSkin[]{cube("netherrack", 0x6E2A2A), cube("blackstone", 0x2E2A30), cube("netherrack", 0x6E2A2A)},
                    new CubeSkin[]{cube("nether_bricks", 0x3A1C22), cube("basalt_top", "basalt_side", 0x4A4A50), cube("blackstone", 0x2E2A30)},
                    cube(anim("magma", 3, 0), anim("magma", 3, 0), 0xC04A10), 0.16f, 0xFF7A20, true, "magma", 3);
            case GARDEN -> new Palette(new CubeSkin[]{cube("moss_block", 0x5E7A2E), cube("farmland_moist", "dirt", 0x5A3A20), cube("moss_block", 0x5E7A2E)},
                    new CubeSkin[]{cube("oak_log_top", "oak_log", 0x6A5030), cube("oak_planks", 0xA0804A), cube("oak_planks", 0xA0804A)},
                    cube("hay_block_top", "hay_block_side", 0xD9B44A), 0.12f, 0xFFE070, false, null, 0);
            case CATACOMBS -> new Palette(new CubeSkin[]{cube("stone_bricks", 0x7A7A7A), cube("cracked_stone_bricks", 0x737373), cube("mossy_stone_bricks", 0x6E7A5E)},
                    new CubeSkin[]{cube("stone_bricks", 0x7A7A7A), cube("mossy_stone_bricks", 0x6E7A5E), cube("chiseled_stone_bricks", 0x777777)},
                    cube("bone_block_top", "bone_block_side", 0xE0DCC8), 0.08f, 0xE0E0FF, false, null, 0);
            case END -> new Palette(new CubeSkin[]{cube("end_stone", 0xDDE0A5), cube("end_stone_bricks", 0xD8DCA0), cube("end_stone", 0xDDE0A5)},
                    new CubeSkin[]{cube("obsidian", 0x1A1028), cube("purpur_block", 0xA57AA5), cube("obsidian", 0x1A1028)},
                    cube("crying_obsidian", 0x5A1A9A), 0.12f, 0xC040FF, false, null, 0);
            case SPIDER -> new Palette(new CubeSkin[]{cube("coarse_dirt", 0x6A4A30), cube("gravel", 0x7A746E), cube("dirt", 0x7A5236)},
                    new CubeSkin[]{cube("stone", 0x7F7F7F), cube("cobblestone", 0x777777), cube("mossy_cobblestone", 0x6E7A5E)},
                    cube("cobweb", 0xE0E0E0), 0.1f, 0x9AE040, false, null, 0);
            case GRAVEYARD -> new Palette(new CubeSkin[]{cube("podzol_top", "podzol_side", 0x5A3E20), cube("coarse_dirt", 0x6A4A30), cube("podzol_top", "podzol_side", 0x5A3E20)},
                    new CubeSkin[]{cube("cobblestone", 0x777777), cube("mossy_cobblestone", 0x6E7A5E), cube("stone_bricks", 0x7A7A7A)},
                    cube("soul_soil", 0x4A3A30), 0.12f, 0x60C060, false, null, 0);
            case HOWL -> new Palette(new CubeSkin[]{cube("snow", 0xF0F8FF), cube("podzol_top", "podzol_side", 0x5A3E20), cube("snow", 0xF0F8FF)},
                    new CubeSkin[]{cube("spruce_log_top", "spruce_log", 0x4A3420), cube("spruce_planks", 0x6A4E30), cube("stone", 0x7F7F7F)},
                    cube("packed_ice", 0x8DB4F0), 0.1f, 0xC8E0FF, false, null, 0);
            case RIFT -> new Palette(new CubeSkin[]{cube("mycelium_top", "mycelium_side", 0x6E5A6E), cube("purpur_block", 0xA57AA5), cube("mycelium_top", "mycelium_side", 0x6E5A6E)},
                    new CubeSkin[]{cube("purpur_pillar_top", "purpur_pillar", 0xA57AA5), cube("crying_obsidian", 0x5A1A9A), cube("purpur_block", 0xA57AA5)},
                    cube("amethyst_block", 0x9A5ACC), 0.14f, 0xD070FF, false, null, 0);
            case BEACH -> new Palette(new CubeSkin[]{cube("sand", 0xDBD3A0), cube("sand", 0xDBD3A0), cube("dark_prismarine", 0x335B4C)},
                    new CubeSkin[]{cube("sandstone_top", "sandstone", 0xD8CC90), cube("prismarine_bricks", 0x63AB9F), cube("dark_prismarine", 0x335B4C)},
                    cube("glowstone", 0xE8C878), 0.1f, 0x60D0E0, false, null, 0);
            default -> new Palette(new CubeSkin[]{cube("moss_block", 0x5E7A2E), cube("dirt", 0x7A5236), cube("moss_block", 0x5E7A2E)},
                    new CubeSkin[]{cube("oak_log_top", "oak_log", 0x6A5030), cube("stone_bricks", 0x7A7A7A), cube("oak_planks", 0xA0804A)},
                    cube("gold_block", 0xF2C230), 0.08f, 0xFFE070, false, null, 0);
        };
    }

    /** Current SkyBlock area (set by the client every tick), for picking the place. */
    public static volatile String area = "";

    private final Theme theme;
    private final Palette pal;
    private final Iso iso = new Iso();
    private final long seed;

    public Diorama(Theme theme, long seed) {
        this.theme = theme;
        this.pal = palette(theme);
        this.seed = seed;
    }

    public Theme theme() {
        return theme;
    }

    /** Colour of the place's light (for glows the scene may want to match). */
    public int light() {
        return pal.light;
    }

    /**
     * Draws the diorama: floor top surface at screen y {@code floorY}, centred on {@code cx}.
     * {@code dim} 0..1 darkens it so the action in front reads clearly.
     */
    public void draw(Canvas c, float t, float cx, float floorY, float k, float alpha, float dim) {
        if (alpha < 0.02f) return;
        float in = Fx.easeOut(Fx.clamp01(t / 0.6f));
        iso.scale = 22 * k;
        iso.yaw = 0.22f + (float) Math.sin(t * 0.25f) * 0.06f;
        iso.pitch = (float) Math.toRadians(22);
        iso.cx = cx;
        iso.cy = floorY + (1 - in) * 30 * k;
        iso.begin();
        // put the front edge of the floor's top surface at floorY
        float[] front = iso.screen(Pose.identity(), 0, 0, 1.5f);
        iso.cy += floorY + (1 - in) * 30 * k - front[1];
        Pose id = Pose.identity();
        Random r = new Random(seed);
        int frame = (int) (t * 4);

        List<float[]> boxes = new ArrayList<>(); // x, y, z, kind(0 floor 1 wall 2 accent), variant
        int half = (int) Math.ceil(c.width() / (2 * 22 * k)) + 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -1; z <= 1; z++) {
                boolean acc = r.nextFloat() < pal.accentChance * 0.6f;
                boxes.add(new float[]{x, -1, z, acc ? 2 : 0, r.nextInt(pal.floor.length)});
            }
            int height = 2 + (r.nextFloat() < 0.35f ? 1 : 0);
            for (int y = 0; y < height; y++) {
                boolean acc = r.nextFloat() < pal.accentChance;
                boxes.add(new float[]{x, y, -2, acc ? 2 : 1, r.nextInt(pal.wall.length)});
            }
        }
        float sinY = (float) Math.sin(iso.yaw), cosY = (float) Math.cos(iso.yaw);
        boxes.sort((p, q) -> {
            float dp = p[0] * sinY + p[2] * cosY - p[1] * 0.001f, dq = q[0] * sinY + q[2] * cosY - q[1] * 0.001f;
            return Float.compare(dp, dq);
        });
        for (float[] bx : boxes) {
            int kind = (int) bx[3];
            CubeSkin skin = kind == 0 ? pal.floor[(int) bx[4]] : kind == 1 ? pal.wall[(int) bx[4]] : accent(frame);
            iso.box(c, skin, Piece.WHOLE, id, bx[0], bx[1], bx[2], 1, 0, 0, 0);
            if (kind == 2) {
                float[] p = iso.screen(id, bx[0], bx[1] + 0.5f, bx[2] + 0.5f);
                Fx.glow(c, p[0], p[1], 14 * k, pal.light, 0.25f * alpha * (0.8f + 0.2f * (float) Math.sin(t * 3 + bx[0])), 2);
            }
        }
        // darken, then the place's light from the top and a soft vignette
        float d = Math.max(0, Math.min(1, dim)) * alpha;
        c.fill(0, 0, c.width(), c.height(), Fx.alpha(0x05060A, d));
        if (alpha < 0.98f) c.fill(0, 0, c.width(), c.height(), Fx.alpha(0x05060A, (1 - alpha) * 0.85f));
        // drifting motes in the place's colour
        Random m = new Random(seed * 7);
        for (int i = 0; i < 24; i++) {
            float mx = (m.nextFloat() * c.width() + t * (8 + m.nextFloat() * 14) * k) % c.width();
            float my = floorY - m.nextFloat() * c.height() * 0.6f + (float) Math.sin(t + i) * 4 * k;
            float ma = (0.25f + 0.35f * m.nextFloat()) * alpha * (0.6f + 0.4f * (float) Math.sin(t * 2 + i));
            int s = Math.max(1, Math.round(k * (m.nextFloat() < 0.3f ? 2 : 1)));
            c.fill(Math.round(mx), Math.round(my), Math.round(mx) + s, Math.round(my) + s, Fx.alpha(Fx.lerpColor(pal.light, 0xFFFFFF, 0.4f), ma));
        }
    }

    private CubeSkin accent(int frame) {
        if (!pal.animatedAccent) return pal.accent;
        Face f = anim(pal.animTex, pal.animFrames, frame);
        return cube(f, f, pal.accent.flesh());
    }

    /** The place for a drop category / effect / island name. */
    public static Theme forPlace(String category, String area) {
        String a = area == null ? "" : area.toLowerCase(java.util.Locale.ROOT);
        if (a.contains("glacite") || a.contains("tunnel")) return Theme.GLACITE;
        if (a.contains("hollows")) return Theme.HOLLOWS;
        if (a.contains("crimson")) return Theme.CRIMSON;
        if (a.contains("garden")) return Theme.GARDEN;
        if (a.contains("rift")) return Theme.RIFT;
        if (a.contains("end")) return Theme.END;
        String c = category == null ? "" : category;
        return switch (c) {
            case "REVENANT" -> Theme.GRAVEYARD;
            case "TARANTULA" -> Theme.SPIDER;
            case "SVEN" -> Theme.HOWL;
            case "VOIDGLOOM" -> Theme.END;
            case "INFERNO" -> Theme.CRIMSON;
            case "RIFTSTALKER" -> Theme.RIFT;
            case "DUNGEON" -> Theme.CATACOMBS;
            case "FARMING", "PEST" -> Theme.GARDEN;
            case "FISHING" -> Theme.BEACH;
            default -> Theme.HUB;
        };
    }
}

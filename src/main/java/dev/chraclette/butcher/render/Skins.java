package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Category;
import dev.chraclette.butcher.core.Drop;

import java.util.Locale;

/** The heads and blocks that get chopped, built from vanilla textures so no downloads are needed. */
public final class Skins {

    private Skins() {
    }

    /** One textured face: a w*h texel region at (u,v) of a texW*texH texture. */
    public record Face(String texture, int texW, int texH, int u, int v, int w, int h) {
    }

    /**
     * A box with six faces. sx/sy/sz are the box proportions (largest = 1).
     * Faces are named from the viewer's side: right = +x, left = -x, front = +z.
     */
    public record CubeSkin(
            Face top, Face bottom, Face front, Face back, Face right, Face left,
            CubeSkin overlay,
            float sx, float sy, float sz,
            int flesh, int fleshDark
    ) {
    }

    private static final int MEAT = 0xB3243A, MEAT_DARK = 0x7A1527;

    /** Minecraft model-box UV layout: texOffs (u,v), size w*h*d. */
    private static CubeSkin box(String tex, int texW, int texH, int u, int v, int w, int h, int d,
                                int flesh, int fleshDark, CubeSkin overlay) {
        float max = Math.max(w, Math.max(h, d));
        return new CubeSkin(
                new Face(tex, texW, texH, u + d, v, w, d),
                new Face(tex, texW, texH, u + d + w, v, w, d),
                new Face(tex, texW, texH, u + d, v + d, w, h),
                new Face(tex, texW, texH, u + 2 * d + w, v + d, w, h),
                new Face(tex, texW, texH, u + d + w, v + d, d, h),
                new Face(tex, texW, texH, u, v + d, d, h),
                overlay,
                w / max, h / max, d / max,
                flesh, fleshDark);
    }

    private static CubeSkin block(String top, String side, int flesh, int fleshDark) {
        Face t = new Face(top, 16, 16, 0, 0, 16, 16);
        Face s = new Face(side, 16, 16, 0, 0, 16, 16);
        return new CubeSkin(t, t, s, s, s, s, null, 1, 1, 1, flesh, fleshDark);
    }

    private static String e(String path) {
        return "textures/entity/" + path + ".png";
    }

    private static String b(String path) {
        return "textures/block/" + path + ".png";
    }

    public static final CubeSkin ZOMBIE = box(e("zombie/zombie"), 64, 64, 0, 0, 8, 8, 8, MEAT, MEAT_DARK, null);
    public static final CubeSkin STEVE = box(e("player/wide/steve"), 64, 64, 0, 0, 8, 8, 8, MEAT, MEAT_DARK,
            box(e("player/wide/steve"), 64, 64, 32, 0, 8, 8, 8, MEAT, MEAT_DARK, null));
    public static final CubeSkin SPIDER = box(e("spider/spider"), 64, 32, 32, 4, 8, 8, 8, 0x8DA33A, 0x4F6420, null);
    public static final CubeSkin WOLF = box(e("wolf/wolf"), 64, 32, 0, 0, 6, 6, 4, MEAT, MEAT_DARK, null);
    public static final CubeSkin ENDERMAN = box(e("enderman/enderman"), 64, 32, 0, 0, 8, 8, 8, 0x7B2FB0, 0x3C1060, null);
    public static final CubeSkin BLAZE = box(e("blaze/blaze"), 64, 32, 0, 0, 8, 8, 8, 0xFF9A1F, 0xC24A0A, null);
    public static final CubeSkin WITHER_SKELETON = box(e("skeleton/wither_skeleton"), 64, 32, 0, 0, 8, 8, 8, 0xD8D2C0, 0x8F8A79, null);
    public static final CubeSkin PIGLIN = box(e("piglin/piglin"), 64, 64, 0, 0, 10, 8, 8, MEAT, MEAT_DARK, null);
    public static final CubeSkin DROWNED = box(e("zombie/drowned"), 64, 64, 0, 0, 8, 8, 8, 0x3E9E8A, 0x22604F, null);
    public static final CubeSkin CREEPER = box(e("creeper/creeper"), 64, 32, 0, 0, 8, 8, 8, 0x6CC24A, 0x2F6B1F, null);

    public static final CubeSkin MELON = block(b("melon_top"), b("melon_side"), 0xE0453A, 0x2E1A12);
    public static final CubeSkin PUMPKIN = block(b("pumpkin_top"), b("pumpkin_side"), 0xF0A030, 0xF6D16B);
    public static final CubeSkin HAY = block(b("hay_block_top"), b("hay_block_side"), 0xD9B44A, 0x9C7A22);
    public static final CubeSkin CACTUS = block(b("cactus_top"), b("cactus_side"), 0xB5D67A, 0x5E8A2C);

    /** The chopping board. */
    public static final CubeSkin BOARD = block(b("stripped_spruce_log"), b("stripped_spruce_log"), 0, 0);

    public static CubeSkin forDrop(Drop drop) {
        Category category = drop.category();
        String lower = drop.name().toLowerCase(Locale.ROOT);
        return switch (category) {
            case REVENANT -> ZOMBIE;
            case TARANTULA -> SPIDER;
            case SVEN -> WOLF;
            case VOIDGLOOM -> ENDERMAN;
            case INFERNO -> BLAZE;
            case RIFTSTALKER -> STEVE;
            case DUNGEON -> WITHER_SKELETON;
            case DIANA -> PIGLIN;
            case FISHING -> DROWNED;
            case PEST -> lower.contains("melon") ? MELON : PUMPKIN;
            case FARMING -> lower.contains("melon") ? MELON
                    : lower.contains("pumpkin") || lower.contains("squash") ? PUMPKIN
                    : lower.contains("cactus") ? CACTUS
                    : HAY;
            case GENERIC -> drop.tier() == dev.chraclette.butcher.core.DropTier.PET ? CREEPER : ZOMBIE;
        };
    }
}

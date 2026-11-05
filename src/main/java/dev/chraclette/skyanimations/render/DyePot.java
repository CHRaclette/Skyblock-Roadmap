package dev.chraclette.skyanimations.render;

/**
 * The SkyBlock dye as it looks on Hypixel (a little paint pot with a black lid), drawn as 16x16 pixel art
 * in the dye's own colour, so every dye - also animated ones - looks right without any resource pack.
 * Draws like {@link Canvas#item}: the 16x16 area starting at (x, y).
 */
public final class DyePot {

    private DyePot() {
    }

    private static final String[] ART = {
            "................",
            "...LLLLLLLLLL...",
            "..LLkLLLLLLkLL..",
            "..OOOOOOOOOOOO..",
            ".OOBBBBBBBBBBOO.",
            ".OBBDDDDDDDDBBO.",
            ".OBDDDDDDDDDDBO.",
            ".OBDDDDDDWDDDBO.",
            ".OBDDDDDWDDDDBO.",
            ".OBDDDDDDDDDDBO.",
            ".OBDDDDDDDDDDBO.",
            ".OBBDDDDDDDDBBO.",
            ".SOBBBBBBBBBBOS.",
            "..SSBBBBBBBBSS..",
            "...SSSSSSSSSS...",
            "................",
    };

    /** @param shade 0 = normal, up to 1 = darker (back faces of the 3D pop) */
    public static void draw(Canvas c, int x, int y, int dye, float shade, float alpha) {
        int light = Fx.lerpColor(dye, 0xFFFFFF, 0.38f);
        int dark = Fx.darken(dye, 0.12f);
        int shadow = Fx.darken(dye, 0.35f);
        int white = Fx.lerpColor(dye, 0xFFFFFF, 0.75f);
        for (int r = 0; r < 16; r++) {
            String row = ART[r];
            int start = -1;
            char prev = '.';
            for (int col = 0; col <= 16; col++) {
                char ch = col < 16 ? row.charAt(col) : '.';
                if (ch != prev) {
                    if (prev != '.' && start >= 0) {
                        int rgb = switch (prev) {
                            case 'L' -> 0x15131A;
                            case 'k' -> 0x3A3842;
                            case 'O' -> light;
                            case 'B' -> dye;
                            case 'D' -> dark;
                            case 'W' -> white;
                            default -> shadow;
                        };
                        if (shade > 0) rgb = Fx.darken(rgb, 0.45f * shade);
                        c.fill(x + start, y + r, x + col, y + r + 1, Fx.alpha(rgb, alpha));
                    }
                    start = col;
                    prev = ch;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3D model

    // side of the pot, 12 wide x 11 high (front/back/left/right)
    private static final String[] SIDE = {
            "OOOOOOOOOOOO",
            "OBBBBBBBBBBO",
            "OBDDDDDDDDBO",
            "OBDDDDDDWDBO",
            "OBDDDDDWDDBO",
            "OBDDDDDDDDBO",
            "OBDDDDDDDDBO",
            "OBDDDDDDDDBO",
            "OBBDDDDDDBBO",
            "SOBBBBBBBBOS",
            "SSSSSSSSSSSS",
    };
    private static final String[] TOP = {
            "OOOOOOOOOOOO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OBBBBBBBBBBO",
            "OOOOOOOOOOOO",
    };
    private static final String[] LID_SIDE = {
            "kLLLLLLLLk",
            "LLLLLLLLLL",
    };
    private static final String[] LID_TOP = {
            "LLLLLLLLLL",
            "LkkkkkkkkL",
            "LkLLLLLLkL",
            "LkLLLLLLkL",
            "LkLLLLLLkL",
            "LkLLLLLLkL",
            "LkLLLLLLkL",
            "LkLLLLLLkL",
            "LkkkkkkkkL",
            "LLLLLLLLLL",
    };

    private static int[][] grid(String[] art, int dye) {
        int light = Fx.lerpColor(dye, 0xFFFFFF, 0.38f);
        int dark = Fx.darken(dye, 0.12f);
        int shadow = Fx.darken(dye, 0.35f);
        int white = Fx.lerpColor(dye, 0xFFFFFF, 0.75f);
        int[][] g = new int[art.length][art[0].length()];
        for (int r = 0; r < art.length; r++) {
            for (int col = 0; col < art[r].length(); col++) {
                g[r][col] = switch (art[r].charAt(col)) {
                    case 'L' -> 0x15131A;
                    case 'k' -> 0x3A3842;
                    case 'O' -> light;
                    case 'B' -> dye;
                    case 'D' -> dark;
                    case 'W' -> white;
                    case 'S' -> shadow;
                    default -> -1;
                };
            }
        }
        return g;
    }

    private static final Iso ISO = new Iso();

    /**
     * The pot as a real 3D model (body 12x11x12 texels, black lid 10x2x10 on top), centred on (cx, cy).
     * px = screen pixels per texel, yaw = turn around the vertical axis, roll = tilt (radians).
     */
    public static void draw3d(Canvas c, float cx, float cy, float px, float yaw, float roll, int dye, float alpha) {
        if (px <= 0.01f || alpha < 0.02f) return;
        Iso iso = ISO;
        iso.yaw = 0;
        iso.pitch = (float) Math.toRadians(22);
        iso.scale = px;
        iso.cx = cx;
        iso.cy = cy;
        iso.begin();
        Iso.Pose pose = Iso.Pose.identity().rotY(yaw).rollZ(roll);
        int[][] side = grid(SIDE, dye), top = grid(TOP, dye), bottom = grid(new String[]{"SSSSSSSSSSSS"}, dye);
        int[][] lidSide = grid(LID_SIDE, dye), lidTop = grid(LID_TOP, dye);
        box(c, iso, pose, -6, -5.5f, -6, 6, 5.5f, 6, side, top, bottom, alpha);
        box(c, iso, pose, -5, 5.5f, -5, 5, 7.5f, 5, lidSide, lidTop, lidTop, alpha);
    }

    private static void box(Canvas c, Iso iso, Iso.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                            int[][] side, int[][] top, int[][] bottom, float alpha) {
        // bottom and back faces are culled by facing; draw bottom first, then sides, then top
        iso.pixelQuad(c, pose, new float[]{x0, y0, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z0 - z1}, new float[]{0, -1, 0}, bottom, alpha);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, 1}, side, alpha);
        iso.pixelQuad(c, pose, new float[]{x1, y1, z1}, new float[]{0, 0, z0 - z1}, new float[]{0, y0 - y1, 0}, new float[]{1, 0, 0}, side, alpha);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z0}, new float[]{0, 0, z1 - z0}, new float[]{0, y0 - y1, 0}, new float[]{-1, 0, 0}, side, alpha);
        iso.pixelQuad(c, pose, new float[]{x1, y1, z0}, new float[]{x0 - x1, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, -1}, side, alpha);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z0}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z1 - z0}, new float[]{0, 1, 0}, top, alpha);
    }
}

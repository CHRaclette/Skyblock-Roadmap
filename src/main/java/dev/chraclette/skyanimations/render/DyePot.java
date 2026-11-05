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
}

package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Badges.Tier;
import dev.chraclette.butcher.core.Themes;

/** Badge medallions and mastery stars, in the book's paper-and-leather style. */
public final class Medal {

    private Medal() {
    }

    /** A round medal with a ribbon, item icon in the middle. Locked medals are dark and dull. */
    public static void draw(Canvas c, float cx, float cy, float r, Tier tier, Object icon, float time, boolean hover, long seed) {
        float grow = hover ? 1.1f : 1f;
        float phase = (seed & 0xFF) / 255f * 6.28f;
        c.push();
        c.translate(cx, cy);
        c.scale(grow, grow);
        boolean locked = tier == Tier.NONE;
        int metal = tier.color;
        // ribbon tails
        int ribbon = locked ? 0x5A4A3A : tier == Tier.GOLD ? 0xB0282E : tier == Tier.SILVER ? 0x2E4EA8 : 0x3A7A3A;
        c.fill(Math.round(-r * 0.55f), Math.round(r * 0.3f), Math.round(-r * 0.15f), Math.round(r * 1.25f), 0xFF000000 | ribbon);
        c.fill(Math.round(r * 0.15f), Math.round(r * 0.3f), Math.round(r * 0.55f), Math.round(r * 1.25f), 0xFF000000 | Fx.darken(ribbon, 0.2f));
        c.fill(Math.round(-r * 0.55f), Math.round(r * 1.25f), Math.round(-r * 0.35f), Math.round(r * 1.4f), 0xFF000000 | ribbon);
        c.fill(Math.round(r * 0.35f), Math.round(r * 1.25f), Math.round(r * 0.55f), Math.round(r * 1.4f), 0xFF000000 | Fx.darken(ribbon, 0.2f));
        // shadow, rim, face
        Fx.disc(c, 1.5f, 2, r, 1, 0x50000000);
        Fx.disc(c, 0, 0, r, 1, 0xFF000000 | Fx.darken(metal, 0.45f));
        Fx.disc(c, 0, 0, r - Math.max(1.5f, r * 0.12f), 1, 0xFF000000 | metal);
        Fx.disc(c, 0, 0, r * 0.7f, 1, 0xFF000000 | Fx.darken(metal, 0.12f));
        // notches round the rim
        int notches = 12;
        for (int i = 0; i < notches; i++) {
            double a = i * Math.PI * 2 / notches;
            int x = Math.round((float) Math.cos(a) * (r - 1)), y = Math.round((float) Math.sin(a) * (r - 1));
            c.fill(x, y, x + 1, y + 1, 0x60000000);
        }
        // icon
        float s = r * 1.05f / 16f;
        c.push();
        c.scale(s, s);
        if (icon != null) c.item(icon, -8, -8);
        c.pop();
        if (locked) {
            Fx.disc(c, 0, 0, r - 1, 1, 0xA0302820);
        }
        // gold shimmer
        if (tier == Tier.GOLD) {
            float k = ((time * 0.5f + phase) % 1.6f) / 1.6f;
            float x = -r + 2 * r * k;
            for (int y = Math.round(-r * 0.8f); y < Math.round(r * 0.8f); y++) {
                float half = (float) Math.sqrt(Math.max(0, r * r * 0.64f - y * y));
                float sx = x + y * 0.5f;
                if (Math.abs(sx) < half) c.fill(Math.round(sx), y, Math.round(sx) + 2, y + 1, 0x70FFFFFF);
            }
        }
        c.pop();
    }

    /** A row of 5 mastery stars, centred at (cx, y). */
    public static void stars(Canvas c, float cx, float y, int filled, boolean crown, float px, float time) {
        int s = Math.max(1, Math.round(px));
        int gap = s * 6;
        float x0 = cx - gap * 2;
        for (int i = 0; i < 5; i++) {
            boolean on = i < filled;
            int col = on ? (crown ? Themes.hsv(((time * 0.3f + i * 0.12f) % 1f), 0.45f, 1f) : 0xFFD23A) : 0xC9BBA0;
            star(c, x0 + i * gap, y, s, 0xFF000000 | col, on ? 0xFF7A5210 : 0xFFA89880);
        }
        if (crown) {
            crown(c, cx, y - s * 5, s, 0xFFFFD23A);
        }
    }

    private static void star(Canvas c, float x, float y, int s, int fill, int edge) {
        int ix = Math.round(x), iy = Math.round(y);
        // 5x5 pixel star scaled by s
        int[][] px = {{2, 0}, {1, 1}, {2, 1}, {3, 1}, {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {1, 3}, {2, 3}, {3, 3}, {0, 4}, {1, 4}, {3, 4}, {4, 4}};
        for (int[] p : px) {
            c.fill(ix + (p[0] - 2) * s, iy + (p[1] - 2) * s, ix + (p[0] - 1) * s, iy + (p[1] - 1) * s, fill);
        }
    }

    public static void crown(Canvas c, float cx, float y, int s, int argb) {
        int ix = Math.round(cx), iy = Math.round(y);
        int[][] px = {{0, 0}, {2, 0}, {4, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}};
        for (int[] p : px) {
            c.fill(ix + (p[0] - 2) * s, iy + (p[1] - 1) * s, ix + (p[0] - 1) * s, iy + p[1] * s, argb);
        }
        c.fill(ix - s / 2, iy, ix + s - s / 2, iy + s, 0xFFE8302A);
    }
}

package dev.chraclette.butcher.render;

import java.util.Random;

/** Drawing helpers for an old, worn leather-bound book with parchment pages. */
public final class OldBook {

    private OldBook() {
    }

    public static final int LEATHER = 0x4A2416, LEATHER_DARK = 0x2A120A, LEATHER_LIGHT = 0x6A3622;
    public static final int GOLD = 0xC9A24A, GOLD_DARK = 0x7A5A1E, GOLD_LIGHT = 0xF0D58A;
    public static final int PARCHMENT = 0xE9D9B4, PARCHMENT_EDGE = 0xC4A574, SEPIA = 0x3B2414, SEPIA_LIGHT = 0x7A5A3A;
    public static final int INK_RED = 0x8A2A1A;

    /** Mixes a bright colour with old brown, so it looks faded. */
    public static int aged(int rgb) {
        return Fx.lerpColor(rgb, 0x6B4A2A, 0.45f);
    }

    /** Leather cover with grain, gold tooled border, brass corners and a clasp. */
    public static void cover(Canvas c, int x, int y, int w, int h) {
        c.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF000000 | LEATHER_DARK);
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000 | LEATHER);
        // grain
        Random r = new Random(1234);
        for (int i = 0; i < (w * h) / 60; i++) {
            int gx = x + r.nextInt(w), gy = y + r.nextInt(h);
            c.fill(gx, gy, gx + 1 + r.nextInt(2), gy + 1, r.nextBoolean() ? 0x30000000 : 0x14FFE0C0);
        }
        // worn, lighter edges
        c.fill(x - 1, y - 1, x + w + 1, y + 1, 0x30FFD8B0);
        c.fill(x - 1, y + h - 1, x + w + 1, y + h + 1, 0x40000000);
        // gold tooling: double line just inside the edge
        rect(c, x + 1, y + 1, w - 2, h - 2, 0xA0000000 | GOLD_DARK);
        rect(c, x + 2, y + 2, w - 4, h - 4, 0xFF000000 | GOLD);
        // brass corner pieces
        corner(c, x - 3, y - 3, 1, 1);
        corner(c, x + w + 3, y - 3, -1, 1);
        corner(c, x - 3, y + h + 3, 1, -1);
        corner(c, x + w + 3, y + h + 3, -1, -1);
        // clasp on the right edge
        int cy = y + h / 2;
        c.fill(x + w, cy - 7, x + w + 9, cy + 7, 0xFF000000 | LEATHER_DARK);
        c.fill(x + w + 1, cy - 6, x + w + 8, cy + 6, 0xFF000000 | LEATHER_LIGHT);
        c.fill(x + w + 3, cy - 4, x + w + 9, cy + 4, 0xFF000000 | GOLD_DARK);
        c.fill(x + w + 4, cy - 3, x + w + 8, cy + 3, 0xFF000000 | GOLD);
        c.fill(x + w + 5, cy - 1, x + w + 7, cy + 1, 0xFF000000 | GOLD_DARK);
    }

    private static void corner(Canvas c, int x, int y, int dx, int dy) {
        int n = 11;
        for (int i = 0; i < n; i++) {
            int len = n - i;
            int x1 = dx > 0 ? x : x - len, x2 = dx > 0 ? x + len : x;
            int yy = dy > 0 ? y + i : y - i - 1;
            c.fill(x1, yy, x2, yy + 1, 0xFF000000 | (i == 0 || len == 1 ? GOLD_DARK : i < 3 ? GOLD_LIGHT : GOLD));
        }
        // rivet
        int rx = x + dx * 3 - (dx < 0 ? 1 : 0), ry = y + dy * 3 - (dy < 0 ? 1 : 0);
        c.fill(rx, ry, rx + 1, ry + 1, 0xFF000000 | GOLD_DARK);
    }

    /** One parchment page: yellowed, darker at the edges, stains and foxing, a stack of pages under it. */
    public static void page(Canvas c, int x, int y, int w, int h, boolean left, long seed) {
        // page stack peeking out at the outer edge
        for (int i = 1; i <= 3; i++) {
            int ex = left ? x - i : x + w + i - 1;
            c.fill(ex, y + i, ex + 1, y + h - i, 0xFF000000 | Fx.darken(PARCHMENT_EDGE, 0.1f * i));
        }
        c.fill(x, y, x + w, y + h, 0xFF000000 | PARCHMENT);
        // darker, burnt-looking edges (layered bands)
        for (int i = 0; i < 9; i++) {
            int a = (9 - i) * 7;
            int col = (a << 24) | 0x6B4A20;
            c.fill(x, y + i, x + w, y + i + 1, col);
            c.fill(x, y + h - i - 1, x + w, y + h - i, col);
            int ox = left ? x + i : x + w - i - 1;
            c.fill(ox, y, ox + 1, y + h, col);
        }
        // gutter shadow towards the spine
        for (int i = 0; i < 16; i++) {
            int a = (16 - i) * 5;
            int sx = left ? x + w - 1 - i : x + i;
            c.fill(sx, y, sx + 1, y + h, a << 24);
        }
        Random r = new Random(seed);
        // stains
        for (int i = 0; i < 3; i++) {
            float sx = x + 20 + r.nextFloat() * (w - 40), sy = y + 20 + r.nextFloat() * (h - 40);
            float rad = 8 + r.nextFloat() * 18;
            Fx.disc(c, sx, sy, rad, 2, 0x0E6B4A20);
            Fx.disc(c, sx + 2, sy - 1, rad * 0.7f, 2, 0x0A6B4A20);
            Fx.ring(c, sx, sy, rad, 1, 0x186B4A20);
        }
        // foxing: little brown age spots
        for (int i = 0; i < w * h / 450; i++) {
            int fx = x + r.nextInt(w), fy = y + r.nextInt(h);
            int s = r.nextInt(4) == 0 ? 2 : 1;
            c.fill(fx, fy, fx + s, fy + s, 0x228A5A2A);
        }
        // ink frame with corner ornaments
        int m = 6;
        rect(c, x + m, y + m, w - 2 * m, h - 2 * m, 0x90000000 | SEPIA);
        rect(c, x + m + 2, y + m + 2, w - 2 * m - 4, h - 2 * m - 4, 0x50000000 | SEPIA);
        flourish(c, x + m, y + m, 1, 1);
        flourish(c, x + w - m - 1, y + m, -1, 1);
        flourish(c, x + m, y + h - m - 1, 1, -1);
        flourish(c, x + w - m - 1, y + h - m - 1, -1, -1);
    }

    private static void flourish(Canvas c, int x, int y, int dx, int dy) {
        int col = 0xC0000000 | SEPIA;
        int[][] px = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {0, 1}, {0, 2}, {0, 3}, {2, 2}, {3, 2}, {2, 3}, {4, 1}, {1, 4}, {5, 1}, {1, 5}};
        for (int[] p : px) {
            int fx = x + p[0] * dx, fy = y + p[1] * dy;
            c.fill(fx, fy, fx + 1, fy + 1, col);
        }
    }

    /** "~ ◆ ~" divider under headings. */
    public static void divider(Canvas c, int x, int y, int w) {
        int mid = x + w / 2;
        int col = 0x90000000 | SEPIA;
        c.fill(x, y, mid - 5, y + 1, col);
        c.fill(mid + 5, y, x + w, y + 1, col);
        for (int i = 0; i < 3; i++) {
            c.fill(mid - i, y - 2 + i, mid + i + 1, y - 1 + i, 0xFF000000 | INK_RED);
            c.fill(mid - i, y + 2 - i, mid + i + 1, y + 3 - i, 0xFF000000 | INK_RED);
        }
        c.fill(mid - 4, y, mid - 3, y + 1, col);
        c.fill(mid + 3, y, mid + 4, y + 1, col);
    }

    /** Ink-drawn progress bar with a hatched fill. */
    public static void bar(Canvas c, int x, int y, int w, int h, float frac, int rgb) {
        rect(c, x - 1, y - 1, w + 2, h + 2, 0xB0000000 | SEPIA);
        c.fill(x, y, x + w, y + h, 0x30000000 | 0x8A6A3A);
        int fw = Math.round(w * Math.max(0, Math.min(1, frac)));
        c.fill(x, y, x + fw, y + h, 0xFF000000 | aged(rgb));
        for (int i = x; i < x + fw; i += 3) {
            c.fill(i, y, i + 1, y + h, 0x30000000);
        }
    }

    /** Cloth bookmark tab hanging over the top edge. */
    public static void tab(Canvas c, int x, int y, int w, int h, int rgb, boolean selected) {
        int col = selected ? Fx.lerpColor(rgb, 0x6B4A2A, 0.3f) : aged(Fx.darken(rgb, 0.15f));
        c.fill(x, y, x + w, y + h, 0xFF000000 | Fx.darken(col, 0.4f));
        c.fill(x + 1, y + 1, x + w - 1, y + h, 0xFF000000 | col);
        // woven texture + stitched edge
        for (int yy = y + 2; yy < y + h; yy += 2) {
            c.fill(x + 1, yy, x + w - 1, yy + 1, 0x14000000);
        }
        for (int xx = x + 3; xx < x + w - 3; xx += 4) {
            c.fill(xx, y + 2, xx + 2, y + 3, 0x50FFF0D0);
        }
    }

    /** Red silk ribbon hanging from the bottom of the book. */
    public static void ribbon(Canvas c, int x, int y, int len, float time) {
        float sway = (float) Math.sin(time * 1.3f) * 1.5f;
        for (int i = 0; i < len; i++) {
            int ox = Math.round(sway * i / (float) len);
            c.fill(x + ox, y + i, x + ox + 6, y + i + 1, 0xFF000000 | (i % 6 < 3 ? 0x8A1A1A : 0x7A1414));
            c.fill(x + ox, y + i, x + ox + 1, y + i + 1, 0x40000000);
        }
        int ex = x + Math.round(sway);
        // swallowtail end
        c.fill(ex, y + len, ex + 2, y + len + 3, 0xFF7A1414);
        c.fill(ex + 4, y + len, ex + 6, y + len + 3, 0xFF7A1414);
    }

    /** Leather label with gold border (for the level plaque). */
    public static void label(Canvas c, int x, int y, int w, int h) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000 | LEATHER_DARK);
        c.fill(x, y, x + w, y + h, 0xFF000000 | LEATHER);
        rect(c, x + 1, y + 1, w - 2, h - 2, 0xFF000000 | GOLD);
        Random r = new Random(77);
        for (int i = 0; i < w * h / 40; i++) {
            int gx = x + 2 + r.nextInt(Math.max(1, w - 4)), gy = y + 2 + r.nextInt(Math.max(1, h - 4));
            c.fill(gx, gy, gx + 1, gy + 1, 0x24000000);
        }
    }

    /** Parchment tooltip with an ink border. */
    public static void tooltip(Canvas c, int x, int y, int w, int h) {
        c.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
        c.fill(x, y, x + w, y + h, 0xFF000000 | 0xF0E2C0);
        for (int i = 0; i < 3; i++) {
            int a = (3 - i) * 20;
            rect(c, x + i, y + i, w - 2 * i, h - 2 * i, (a << 24) | 0x6B4A20);
        }
        rect(c, x, y, w, h, 0xFF000000 | SEPIA);
    }

    public static void rect(Canvas c, int x, int y, int w, int h, int argb) {
        c.fill(x, y, x + w, y + 1, argb);
        c.fill(x, y + h - 1, x + w, y + h, argb);
        c.fill(x, y + 1, x + 1, y + h - 1, argb);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }
}

package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Ledger.Level;
import dev.chraclette.butcher.core.Themes;

/** Draws one sticker. The higher the level, the more it shines. */
public final class Sticker {

    private Sticker() {
    }

    /**
     * @param cx,cy   centre of the sticker
     * @param size    side length in GUI pixels
     * @param level   null = locked ("???")
     * @param seed    per-item number, gives every sticker its own tilt and timing
     */
    public static void draw(Canvas c, float cx, float cy, float size, Level level, Object icon, int count,
                            float time, boolean hover, long seed) {
        float tilt = (float) Math.toRadians(((seed & 0xFF) / 255f - 0.5f) * 12);
        float phase = ((seed >> 8) & 0xFF) / 255f * 6.28f;
        float bob = 0, wobble = 0, grow = hover ? 1.12f : 1f;
        if (level == Level.DIAMOND || level == Level.HOLO) {
            bob = (float) Math.sin(time * 2.2f + phase) * size * 0.03f;
        }
        if (level == Level.HOLO) {
            wobble = (float) Math.sin(time * 1.7f + phase) * 0.06f;
        }
        if (hover) {
            tilt *= 0.3f;
        }
        int half = Math.round(size / 2);

        // Holo: rays and glow behind the sticker
        if (level == Level.HOLO) {
            int rgb = Themes.hsv((time * 0.15f + phase) % 1f, 0.6f, 1f);
            Fx.glow(c, cx, cy + bob, size * 0.95f, rgb, 0.8f, 2);
            Fx.rays(c, cx, cy + bob, size * 1.15f, 10, time * 0.6f + phase, rgb, 1.1f);
        } else if (level == Level.DIAMOND) {
            Fx.glow(c, cx, cy + bob, size * 0.8f, Level.DIAMOND.color, 0.55f, 2);
        } else if (level == Level.GOLD) {
            Fx.glow(c, cx, cy, size * 0.7f, Level.GOLD.color, 0.35f, 2);
        }

        c.push();
        c.translate(cx, cy + bob);
        c.rotate(tilt + wobble);
        c.scale(grow, grow);

        if (level == null) {
            lockedBody(c, half);
            c.pop();
            return;
        }

        // shadow, die-cut border, inner card
        roundRect(c, -half + 2, -half + 3, half * 2, half * 2, 4, 0x55000000);
        int border = level == Level.HOLO ? Themes.hsv((time * 0.4f + phase) % 1f, 0.55f, 1f) : level.color;
        roundRect(c, -half, -half, half * 2, half * 2, 4, 0xFF000000 | border);
        int in = Math.max(2, Math.round(size * 0.07f));
        background(c, -half + in, -half + in, half * 2 - 2 * in, half * 2 - 2 * in, level, time, phase);

        // item
        float iconScale = size * 0.6f / 16f;
        c.push();
        c.scale(iconScale, iconScale);
        if (icon != null) c.item(icon, -8, -8);
        c.pop();

        // shine sweep (silver and up)
        if (level.ordinal() >= Level.SILVER.ordinal()) {
            float period = level == Level.HOLO ? 1.6f : level == Level.DIAMOND ? 2.2f : 3.2f;
            float k = ((time + phase) % period) / period;
            shine(c, -half + in, -half + in, half * 2 - 2 * in, half * 2 - 2 * in, k,
                    level == Level.SILVER ? 0.35f : 0.5f);
        }

        // corner stars (bronze and up)
        int stars = Math.min(4, level.ordinal());
        for (int i = 0; i < stars; i++) {
            float sx = (i % 2 == 0 ? -1 : 1) * (half - in - 3);
            float sy = (i < 2 ? -1 : 1) * (half - in - 3);
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 4 + i * 1.7f + phase);
            star(c, sx, sy, level.ordinal() >= Level.GOLD.ordinal() ? 2 + Math.round(tw) : 1, Fx.alpha(0xFFFFFF, 0.6f + 0.4f * tw));
        }

        // count badge
        if (count > 1) {
            String label = "x" + count;
            float bs = Math.max(0.6f, size / 70f);
            int tw = c.textWidth(label);
            c.push();
            c.translate(half - 2, half - 2);
            c.scale(bs, bs);
            c.fill(-tw - 4, -6, 3, 5, 0xE0101018);
            c.fill(-tw - 4, -6, 3, -5, 0xFF000000 | border);
            c.text(label, -tw - 1, -4, 0xFFFFFFFF, false);
            c.pop();
        }
        c.pop();

        // sparkles orbiting diamond / holo stickers
        if (level.ordinal() >= Level.DIAMOND.ordinal()) {
            int n = level == Level.HOLO ? 6 : 3;
            for (int i = 0; i < n; i++) {
                float a = time * (level == Level.HOLO ? 1.4f : 0.9f) + i * 6.28f / n + phase;
                float r = size * 0.72f;
                float sx = cx + (float) Math.cos(a) * r, sy = cy + bob + (float) Math.sin(a) * r * 0.8f;
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 6 + i);
                int col = level == Level.HOLO ? Themes.hsv((i / (float) n + time * 0.2f) % 1f, 0.5f, 1f) : 0xDFFBFF;
                star(c, sx, sy, 1 + Math.round(tw * 2), Fx.alpha(col, 0.5f + 0.5f * tw));
            }
        }
    }

    private static void lockedBody(Canvas c, int half) {
        // dashed outline + dark card with a question mark
        c.fill(-half, -half, half, half, 0x30000000);
        int dash = Math.max(2, half / 5);
        for (int i = -half; i < half; i += dash * 2) {
            int e = Math.min(half, i + dash);
            c.fill(i, -half, e, -half + 1, 0x80000000);
            c.fill(i, half - 1, e, half, 0x80000000);
            c.fill(-half, i, -half + 1, e, 0x80000000);
            c.fill(half - 1, i, half, e, 0x80000000);
        }
        float s = Math.max(1, half / 9f);
        c.push();
        c.scale(s, s);
        c.text("?", -c.textWidth("?") / 2, -4, 0x90403020, false);
        c.pop();
    }

    private static void background(Canvas c, int x, int y, int w, int h, Level level, float time, float phase) {
        for (int i = 0; i < h; i += 2) {
            float k = i / (float) Math.max(1, h);
            int rgb = switch (level) {
                case PAPER -> Fx.lerpColor(0xFFFFFA, 0xEDE6D4, k);
                case BRONZE -> Fx.lerpColor(0xF6D6B4, 0xC8834A, k);
                case SILVER -> Fx.lerpColor(0xF4F7FA, 0xA9B4C0, k);
                case GOLD -> Fx.lerpColor(0xFFF2B0, 0xE8A920, k);
                case DIAMOND -> Fx.lerpColor(0xE8FFFF, 0x3FB8E0, k);
                case HOLO -> Themes.hsv(((time * 0.25f + k * 0.6f + phase) % 1f + 1f) % 1f, 0.35f, 1f);
            };
            c.fill(x, y + i, x + w, Math.min(y + h, y + i + 2), 0xFF000000 | rgb);
        }
        // fine pattern on fancy ones
        if (level.ordinal() >= Level.GOLD.ordinal()) {
            for (int yy = y + 2; yy < y + h - 1; yy += 4) {
                for (int xx = x + ((yy / 4) % 2) * 2 + 1; xx < x + w - 1; xx += 4) {
                    c.fill(xx, yy, xx + 1, yy + 1, 0x22FFFFFF);
                }
            }
        }
    }

    /** A diagonal light band moving across the card (k = 0..1). Drawn row by row, so no clipping needed. */
    private static void shine(Canvas c, int x, int y, int w, int h, float k, float strength) {
        float centre = x - h + (w + 2 * h) * k;
        for (int row = 0; row < h; row++) {
            float cxr = centre + row * 0.7f;
            int band = Math.max(2, w / 8);
            int x1 = Math.max(x, Math.round(cxr - band)), x2 = Math.min(x + w, Math.round(cxr + band));
            if (x2 > x1) c.fill(x1, y + row, x2, y + row + 1, Fx.alpha(0xFFFFFF, strength * 0.6f));
            int y1 = Math.max(x, Math.round(cxr - band * 0.35f)), y2 = Math.min(x + w, Math.round(cxr + band * 0.35f));
            if (y2 > y1) c.fill(y1, y + row, y2, y + row + 1, Fx.alpha(0xFFFFFF, strength));
        }
    }

    private static void star(Canvas c, float x, float y, int s, int argb) {
        int ix = Math.round(x), iy = Math.round(y);
        c.fill(ix - s, iy, ix + s + 1, iy + 1, argb);
        c.fill(ix, iy - s, ix + 1, iy + s + 1, argb);
    }

    /** Rectangle with chopped corners (pixel-art rounding). */
    public static void roundRect(Canvas c, int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        c.fill(x + r, y, x + w - r, y + h, argb);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
            c.fill(x + inset, y + i, x + r, y + i + 1, argb);
            c.fill(x + inset, y + h - i - 1, x + r, y + h - i, argb);
            c.fill(x + w - r, y + i, x + w - inset, y + i + 1, argb);
            c.fill(x + w - r, y + h - i - 1, x + w - inset, y + h - i, argb);
        }
        c.fill(x, y + r, x + r, y + h - r, argb);
        c.fill(x + w - r, y + r, x + w, y + h - r, argb);
    }
}

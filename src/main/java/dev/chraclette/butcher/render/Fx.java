package dev.chraclette.butcher.render;

/** Small drawing helpers built only from fills, so they work on any Canvas. */
public final class Fx {

    private Fx() {
    }

    public static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int brighten(int rgb, float amount) {
        return lerpColor(rgb, 0xFFFFFF, amount);
    }

    public static int darken(int rgb, float amount) {
        return lerpColor(rgb, 0x000000, amount);
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    public static float easeIn(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    /** Overshoots then settles; nice for pops. */
    public static float backOut(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    /** A bounce for things falling onto the board. */
    public static float bounceOut(float t) {
        t = clamp01(t);
        float n1 = 7.5625f, d1 = 2.75f;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) return n1 * (t -= 1.5f / d1) * t + 0.75f;
        if (t < 2.5 / d1) return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        return n1 * (t -= 2.625f / d1) * t + 0.984375f;
    }

    /** A blocky filled circle made of horizontal strips (pixel-art glow). */
    public static void disc(Canvas c, float cx, float cy, float r, int step, int argb) {
        if (r < 1 || (argb >>> 24) == 0) return;
        step = Math.max(1, step);
        for (float y = -r; y < r; y += step) {
            float mid = y + step / 2f;
            float half = (float) Math.sqrt(Math.max(0, r * r - mid * mid));
            if (half < 0.5f) continue;
            c.fill(Math.round(cx - half), Math.round(cy + y), Math.round(cx + half), Math.round(cy + y + step), argb);
        }
    }

    /** Soft glow: several stacked discs with low alpha. */
    public static void glow(Canvas c, float cx, float cy, float r, int rgb, float strength, int step) {
        for (int i = 0; i < 5; i++) {
            float rr = r * (1 - i * 0.18f);
            disc(c, cx, cy, rr, step, alpha(rgb, strength * 0.16f));
        }
    }

    /** Blocky circle outline. */
    public static void ring(Canvas c, float cx, float cy, float r, int thickness, int argb) {
        int n = Math.max(12, (int) (r * 0.9f));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = Math.round(cx + (float) Math.cos(a) * r), y = Math.round(cy + (float) Math.sin(a) * r);
            c.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, argb);
        }
    }

    /** A straight line of given thickness. */
    public static void line(Canvas c, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        c.push();
        c.translate(x1, y1);
        c.rotate((float) Math.atan2(dy, dx));
        int h = Math.max(1, Math.round(thickness));
        c.fill(0, -h / 2, Math.round(len), (h + 1) / 2, argb);
        c.pop();
    }

    /** Soft light rays spinning around a point (the reveal background). */
    public static void rays(Canvas c, float cx, float cy, float length, int count, float angle, int rgb, float a) {
        for (int i = 0; i < count; i++) {
            float base = angle + (float) (i * Math.PI * 2 / count);
            boolean wide = i % 2 == 0;
            float spread = wide ? 0.13f : 0.08f;
            int slices = 7;
            for (int k = 0; k < slices; k++) {
                float off = (k - (slices - 1) / 2f) / ((slices - 1) / 2f); // -1..1
                float edge = 1 - Math.abs(off);                            // 0 at edges, 1 in middle
                c.push();
                c.translate(cx, cy);
                c.rotate(base + off * spread);
                int w = Math.max(1, Math.round(length * 0.012f));
                int len = Math.round(length * (wide ? 1f : 0.8f) * (0.75f + 0.25f * edge));
                c.fill(Math.round(length * 0.1f), -w, len, w, alpha(rgb, a * (0.05f + 0.10f * edge)));
                c.pop();
            }
        }
    }

    /** Text drawn twice, one pixel apart, to look bold. */
    public static void bold(Canvas c, String s, int x, int y, int argb) {
        c.text(s, x, y, argb, true);
        c.text(s, x + 1, y, argb, false);
    }

    public static void centered(Canvas c, String s, float cx, float y, float scale, int argb, boolean bold) {
        c.push();
        c.translate(cx, y);
        c.scale(scale, scale);
        int w = c.textWidth(s);
        if (bold) {
            bold(c, s, -w / 2, 0, argb);
        } else {
            c.text(s, -w / 2, 0, argb, true);
        }
        c.pop();
    }

    /** Rainbow text, one colour per character, scrolling with time. */
    public static void rainbow(Canvas c, String s, float cx, float y, float scale, float time, float a, boolean bold) {
        c.push();
        c.translate(cx, y);
        c.scale(scale, scale);
        int x = -c.textWidth(s) / 2;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int rgb = dev.chraclette.butcher.core.Themes.hsv(((i * 0.07f + time * 0.5f) % 1 + 1) % 1, 0.65f, 1f);
            if (bold) bold(c, ch, x, 0, alpha(rgb, a));
            else c.text(ch, x, 0, alpha(rgb, a), true);
            x += c.textWidth(ch) + (bold ? 1 : 0);
        }
        c.pop();
    }
}

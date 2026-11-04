package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Ledger.Level;

/** A sticker slapped onto the screen when you earn or upgrade it. */
public final class StickerPop {

    public static final float DURATION = 3.6f;

    private final String name;
    private final Level level;
    private final Level before;
    private final Object icon;
    private final int count;
    private final float start;
    private final Particles fx = new Particles(99);
    private float lastT = -1;
    private boolean burst;

    /** @param before level before this drop (null = brand-new sticker) */
    public StickerPop(String name, Level before, Level level, Object icon, int count, float start) {
        this.name = name;
        this.before = before;
        this.level = level;
        this.icon = icon;
        this.count = count;
        this.start = start;
    }

    public boolean finished(float now) {
        return now - start > DURATION;
    }

    public void render(Canvas c, float now) {
        float t = now - start;
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        int w = c.width(), h = c.height();
        float cx = w * 0.80f, cy = h * 0.42f;
        float size = Math.min(h * 0.22f, 64);

        // flies in big and spinning, then slaps down
        float in = Fx.clamp01(t / 0.35f);
        float slap = in < 1 ? 2.4f - 1.4f * Fx.easeIn(in) : 1 + 0.12f * (float) Math.sin(Math.min(1, (t - 0.35f) / 0.25f) * Math.PI);
        float out = 1 - Fx.smooth((t - (DURATION - 0.4f)) / 0.4f);
        float a = Fx.clamp01(in * 3) * out;
        if (a <= 0.01f) return;

        if (t >= 0.35f && !burst) {
            burst = true;
            int col = level.color;
            int n = 14 + level.ordinal() * 8;
            fx.burst(cx, cy, n, 40, 110 + level.ordinal() * 20, 60, 0.9f, 2, 4, col, 0xFFFFFF, Fx.brighten(col, 0.4f));
            Particles.P ring = fx.add(cx, cy, 0, 0, 0.6f, size * 2.2f, col);
            ring.kind = Particles.Kind.RING;
        }
        fx.update(dt);
        fx.render(c, out);

        c.push();
        c.translate(cx, cy);
        c.scale(slap, slap);
        c.rotate((1 - Fx.easeOut(in)) * 1.4f);
        c.translate(-cx, -cy);
        Sticker.draw(c, cx, cy, size, level, icon, count, t, false, name.hashCode());
        c.pop();

        float textA = Fx.smooth((t - 0.4f) / 0.2f) * out;
        if (textA > 0.02f) {
            String head = before == null ? "NEW STICKER!" : level.title.toUpperCase() + "!";
            if (level == Level.HOLO) {
                Fx.rainbow(c, head, cx, cy + size * 0.62f + 4, 1.3f, t, textA, true);
            } else {
                Fx.centered(c, head, cx, cy + size * 0.62f + 4, 1.3f, Fx.alpha(level.color, textA), true);
            }
            String sub = before == null ? name : name + ": " + before.title + " → " + level.title;
            Fx.centered(c, sub, cx, cy + size * 0.62f + 18, 0.8f, Fx.alpha(0xFFFFFF, textA), false);
        }
    }
}

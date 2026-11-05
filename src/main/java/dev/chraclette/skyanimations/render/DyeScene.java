package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.util.Random;

/**
 * Dye drop: a paint bomb in exactly the dye's colour. The screen dims, a drop of paint falls,
 * bursts into rings and splats, the dye pops up big with its name, and paint drips down.
 */
public final class DyeScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);

        /** Minecraft's own "item pops in" animation (the totem one) for the dye; false if not available. */
        default boolean popIn(Object icon) {
            return false;
        }
    }

    /** How long Minecraft's pop-in plays (40 ticks); our own icon takes over after it. */
    private static final float POP_IN = 1.9f;
    private boolean popping;

    private static final float FALL = 0.55f, BURST = FALL, HOLD = 5.2f, END = BURST + HOLD;

    private final String name;
    private final Object icon;
    private final int dye;       // dye colour (first colour of an animated dye)
    private final int[] palette; // all colours; animated dyes cycle through them
    private final int rarity;    // rarity colour of the name
    private final String subtitle;
    private final Hooks hooks;
    private final Particles fx = new Particles(77);
    private final Random random;
    private final float[][] splats; // x, y (relative), radius, angle
    private float lastT = -1;
    private boolean burst, dripped;

    public DyeScene(String name, Object icon, int dye, int rarity, String subtitle, Hooks hooks) {
        this(name, icon, new int[]{dye}, rarity, subtitle, hooks);
    }

    public DyeScene(String name, Object icon, int[] palette, int rarity, String subtitle, Hooks hooks) {
        this.name = name;
        this.icon = icon;
        this.palette = palette == null || palette.length == 0 ? new int[]{0xC4D93A} : palette;
        this.dye = this.palette[0];
        this.rarity = rarity;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.hooks = hooks;
        this.random = new Random(name.hashCode());
        splats = new float[14][];
        for (int i = 0; i < splats.length; i++) {
            float ang = random.nextFloat() * 6.283f;
            float dist = 0.18f + random.nextFloat() * 0.35f;
            splats[i] = new float[]{(float) Math.cos(ang) * dist, (float) Math.sin(ang) * dist * 0.62f, 4 + random.nextFloat() * 9, ang};
        }
    }

    /** Colour at time t: animated dyes play their colour sequence (20 steps per second, like in game). */
    private int col(float t) {
        if (palette.length == 1) return dye;
        return palette[Math.floorMod((int) (t * 20), palette.length)];
    }

    @Override
    public boolean finished(float t) {
        return t >= END;
    }

    @Override
    public void render(Canvas c, float t) {
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        int w = c.width(), h = c.height();
        float k = Math.min(1.6f, Math.max(0.8f, h / 250f));
        float cx = w / 2f, cy = h * 0.42f;
        float fade = Fx.smooth(t / 0.3f) * (1 - Fx.smooth((t - (END - 0.6f)) / 0.6f));
        float popWindow = popping ? 1 - Fx.smooth((t - BURST - POP_IN + 0.3f) / 0.3f) : 0;
        float dim = 0.6f - 0.42f * (popping && t >= BURST ? popWindow : 0);
        c.fill(0, 0, w, h, Fx.alpha(Fx.lerpColor(0x05040A, Fx.darken(dye, 0.8f), 0.35f), dim * fade));

        if (t < FALL) {
            // a fat drop of paint falls from the top
            float p = Fx.easeIn(t / FALL);
            float y = -10 * k + (cy + 10 * k) * p;
            float r = 5 * k;
            Fx.disc(c, cx, y, r, 1, Fx.alpha(dye, 1));
            Fx.disc(c, cx, y - r * 1.2f, r * 0.6f, 1, Fx.alpha(dye, 1));
            c.fill(Math.round(cx - r * 0.4f), Math.round(y - r * 0.6f), Math.round(cx - r * 0.1f), Math.round(y - r * 0.2f), Fx.alpha(0xFFFFFF, 0.7f));
            if (t - dt <= 0.02f) hooks.sound(Cue.WHOOSH, 1.4f, 0.5f);
        } else if (!burst) {
            burst = true;
            popping = hooks.popIn(icon);
            hooks.sound(Cue.SPLASH, 1.0f, 1.0f);
            hooks.sound(Cue.FIREWORK, 1.1f, 0.7f);
            fx.burst(cx, cy, 90, 60 * k, 260 * k, 160 * k, 1.4f, 2 * k, 5 * k, dye, Fx.brighten(dye, 0.35f), Fx.darken(dye, 0.25f), 0xFFFFFF);
            for (int i = 0; i < 3; i++) {
                P ring = fx.add(cx, cy, 0, 0, 0.7f + i * 0.25f, (70 + i * 45) * k, i == 1 ? 0xFFFFFF : dye);
                ring.kind = Kind.RING;
            }
        }

        float bt = t - BURST;
        if (bt >= 0) {
            // shockwave flash
            float flash = 1 - Fx.smooth(bt / 0.35f);
            if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(Fx.brighten(dye, 0.5f), 0.45f * flash * fade));
            // paint splats on the "screen"
            for (int i = 0; i < splats.length; i++) {
                float[] s = splats[i];
                float grow = Fx.backOut(Fx.clamp01((bt - i * 0.015f) / 0.25f));
                float sx = cx + s[0] * w, sy = cy + s[1] * h;
                float r = s[2] * k * grow;
                int base = palette.length == 1 ? dye : palette[(i * palette.length / splats.length + (int) (t * 20)) % palette.length];
                int col = i % 3 == 0 ? Fx.darken(base, 0.15f) : i % 3 == 1 ? base : Fx.brighten(base, 0.2f);
                Fx.disc(c, sx, sy, r, 1, Fx.alpha(col, 0.9f * fade));
                Fx.disc(c, sx + (float) Math.cos(s[3]) * r * 1.3f, sy + (float) Math.sin(s[3]) * r * 0.8f, r * 0.35f, 1, Fx.alpha(col, 0.9f * fade));
                // drips run down slowly
                float drip = Fx.clamp01((bt - 0.5f) / 3f) * (8 + (i * 7) % 18) * k;
                if (drip > 0) {
                    c.fill(Math.round(sx - r * 0.18f), Math.round(sy), Math.round(sx + r * 0.18f), Math.round(sy + r * 0.6f + drip), Fx.alpha(col, 0.9f * fade));
                    Fx.disc(c, sx, sy + r * 0.6f + drip, r * 0.22f, 1, Fx.alpha(col, 0.9f * fade));
                }
            }
            if (!dripped && bt > 0.6f) {
                dripped = true;
                hooks.sound(Cue.TWINKLE, 1.3f, 0.6f);
            }
            // the dye itself, big, with a glow in its colour
            // with Minecraft's pop-in the dye flies in by itself; our big icon takes over afterwards
            float popStart = popping ? POP_IN - 0.15f : 0.1f;
            float pop = Fx.backOut(Fx.clamp01((bt - popStart) / 0.4f));
            float bob = (float) Math.sin(bt * 2.4f) * 2 * k;
            int now = col(t);
            Fx.glow(c, cx, cy + bob, 46 * k * pop, now, 0.9f * fade, 2);
            Fx.rays(c, cx, cy + bob, 90 * k * pop, 14, bt * 0.35f, Fx.brighten(now, 0.4f), 0.55f * fade);
            float sc = 3.2f * k * pop;
            if (sc > 0.05f && icon != null) {
                c.push();
                c.translate(cx, cy + bob);
                c.scale(sc, sc);
                c.item(icon, -8, -8);
                c.pop();
            }
            if (random.nextFloat() < 0.5f) {
                P sp = fx.add(cx + (random.nextFloat() - 0.5f) * 60 * k, cy + (random.nextFloat() - 0.5f) * 40 * k, 0, -20 * k, 0.8f, 2 * k, now);
                sp.kind = Kind.SPARKLE;
            }
            // title + name
            float ta = Fx.smooth((bt - 0.25f) / 0.3f) * fade;
            float tp = Fx.backOut(Fx.clamp01((bt - 0.25f) / 0.35f));
            Fx.centered(c, "DYE DROP!", cx, cy - 46 * k, 1.6f * k * tp, Fx.alpha(now, ta), true);
            Fx.centered(c, name, cx, cy + 34 * k, 1.5f * k * tp, Fx.alpha(rarity, ta), true);
            if (!subtitle.isEmpty()) Fx.centered(c, subtitle, cx, cy + 50 * k, 0.85f * k, Fx.alpha(0xD8D8E0, ta), false);
        }
        fx.update(dt);
        fx.render(c, fade);
    }
}

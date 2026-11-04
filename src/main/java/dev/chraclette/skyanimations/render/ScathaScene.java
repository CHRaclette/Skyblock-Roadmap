package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.util.Random;

/**
 * Scatha / Crystal Hollows pet drop: a strip of sand across the screen, something burrows through
 * it, the pet bursts out and slides across the screen CS-style, then snaps hard onto its spot with
 * a glow in its rarity colour.
 */
public final class ScathaScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final float DIG = 1.7f, SLIDE = 0.9f, SNAP = DIG + SLIDE, END = SNAP + 3.4f;
    private static final int[] SAND = {0xDBCF8E, 0xD3C27A, 0xE6DBA3, 0xC9B76E, 0xB8A660};

    private final String name;
    private final String rarityLabel;
    private final Object icon;
    private final int rarity;
    private final String subtitle;
    private final Hooks hooks;
    private final Particles fx = new Particles(99);
    private final Random random = new Random(7);
    private float lastT = -1;
    private int lastDigStep = -1;
    private boolean burst, snapped;

    public ScathaScene(String name, String rarityLabel, Object icon, int rarity, String subtitle, Hooks hooks) {
        this.name = name;
        this.rarityLabel = rarityLabel == null ? "" : rarityLabel;
        this.icon = icon;
        this.rarity = rarity;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.hooks = hooks;
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
        float fade = Fx.smooth(t / 0.3f) * (1 - Fx.smooth((t - (END - 0.6f)) / 0.6f));
        c.fill(0, 0, w, h, Fx.alpha(0x07050A, 0.55f * fade));

        // shake right after the snap
        float shake = snapped ? (1 - Fx.smooth((t - SNAP) / 0.35f)) * 3 * k : 0;
        float ox = shake > 0 ? (random.nextFloat() - 0.5f) * shake * 2 : 0;
        float oy = shake > 0 ? (random.nextFloat() - 0.5f) * shake * 2 : 0;
        c.push();
        c.translate(ox, oy);

        float cy = h * 0.5f;
        float bandH = 40 * k;
        float bandIn = Fx.easeOut(t / 0.35f);
        float bandA = fade * (1 - 0.5f * Fx.smooth((t - SNAP) / 0.5f));
        // sand band made of pixel blocks
        float bs = 5 * k;
        int cols = (int) (w / bs) + 1, rows = Math.max(2, Math.round(bandH / bs));
        float top = cy - rows * bs / 2;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                if (col > cols * bandIn) break;
                int shade = SAND[Math.floorMod(col * 31 + r * 17, SAND.length)];
                if (r == 0) shade = Fx.brighten(shade, 0.12f);
                if (r == rows - 1) shade = Fx.darken(shade, 0.2f);
                c.fill(Math.round(col * bs), Math.round(top + r * bs), Math.round((col + 1) * bs), Math.round(top + (r + 1) * bs), Fx.alpha(shade, bandA));
            }
        }
        // the burrowing tunnel: a dark trail that grows from left to right under the sand
        float digP = Fx.clamp01((t - 0.3f) / (DIG - 0.3f));
        float headX = w * (0.05f + 0.85f * Fx.easeInOut(digP));
        if (t > 0.3f) {
            float tunnelY = cy + (float) Math.sin(headX / (30 * k)) * 4 * k;
            for (float x = 0; x < headX; x += bs) {
                float ty = cy + (float) Math.sin(x / (30 * k)) * 4 * k;
                c.fill(Math.round(x), Math.round(ty - 3 * k), Math.round(x + bs), Math.round(ty + 3 * k), Fx.alpha(0x4A3B22, bandA * 0.85f));
            }
            if (t < DIG) {
                // dust kicked up at the digging head
                if (random.nextFloat() < 0.8f) {
                    P d = fx.add(headX, top, (random.nextFloat() - 0.7f) * 60 * k, -40 * k - random.nextFloat() * 50 * k, 0.6f, 2 * k + random.nextFloat() * 2 * k, SAND[random.nextInt(SAND.length)]);
                    d.ay = 150 * k;
                }
                int step = (int) (t * 7);
                if (step != lastDigStep) {
                    lastDigStep = step;
                    hooks.sound(Cue.DIG, 0.8f + random.nextFloat() * 0.3f, 0.6f);
                }
                Fx.glow(c, headX, tunnelY, 14 * k, 0xFFE8A0, 0.35f * fade, 2);
            }
        }

        // burst out at the right and slide to the centre (fast, decelerating, motion-blur ghosts)
        float slideP = Fx.clamp01((t - DIG) / SLIDE);
        if (t >= DIG && !burst) {
            burst = true;
            hooks.sound(Cue.WHOOSH, 0.9f, 1.0f);
            hooks.sound(Cue.DIG, 0.6f, 1.0f);
            fx.burst(headX, cy, 50, 40 * k, 160 * k, 200 * k, 0.9f, 2 * k, 4 * k, SAND);
        }
        if (t >= DIG) {
            float eased = 1 - (float) Math.pow(1 - slideP, 4);
            float x = headX + (w / 2f - headX) * eased;
            float y = cy - 6 * k - (float) Math.sin(Math.min(1, slideP * 1.4f) * Math.PI) * 10 * k * (1 - slideP);
            float sc = 2.8f * k;
            if (!snapped) {
                // speed streaks behind the sliding pet
                float len = 90 * k * (1 - slideP);
                for (int i = -3; i <= 3; i++) {
                    float sy = y + i * 4 * k;
                    float l = len * (1 - Math.abs(i) * 0.18f);
                    c.fill(Math.round(x + 8 * k), Math.round(sy), Math.round(x + 8 * k + l), Math.round(sy + Math.max(1, k)),
                            Fx.alpha(i == 0 ? 0xFFFFFF : rarity, 0.7f * fade * (1 - slideP)));
                }
            }
            if (slideP >= 1 && !snapped) {
                snapped = true;
                hooks.sound(Cue.SLAP, 0.8f, 1.0f);
                hooks.sound(Cue.REVEAL, 1.0f, 0.9f);
                hooks.sound(Cue.TOTEM, 1.3f, 0.5f);
                fx.burst(x, y, 70, 40 * k, 200 * k, 60 * k, 1.1f, 2 * k, 4 * k, rarity, 0xFFFFFF, Fx.brighten(rarity, 0.3f));
                P ring = fx.add(x, y, 0, 0, 0.7f, 70 * k, rarity);
                ring.kind = Kind.RING;
            }
            float st = t - SNAP;
            if (snapped) {
                float flash = 1 - Fx.smooth(st / 0.25f);
                if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(0xFFFFFF, 0.5f * flash * fade));
                Fx.glow(c, x, y, 40 * k, rarity, 0.9f * fade, 2);
                Fx.rays(c, x, y, 85 * k, 12, st * 0.4f, Fx.brighten(rarity, 0.4f), 0.6f * fade);
                float pulse = 1 + 0.04f * (float) Math.sin(st * 5);
                sc *= pulse * (1 + 0.25f * (1 - Fx.smooth(st / 0.2f)));
            }
            c.push();
            c.translate(x, y);
            c.scale(sc, sc);
            if (icon != null) c.item(icon, -8, -8);
            c.pop();
            if (snapped) {
                float ta = Fx.smooth((t - SNAP) / 0.25f) * fade;
                float tp = Fx.backOut(Fx.clamp01((t - SNAP) / 0.3f));
                Fx.centered(c, "PET DROP!", w / 2f, top - 34 * k, 1.6f * k * tp, Fx.alpha(0xFFAA00, ta), true);
                Fx.centered(c, (rarityLabel.isEmpty() ? "" : rarityLabel + " ") + name, w / 2f, top + rows * bs + 10 * k, 1.4f * k * tp, Fx.alpha(rarity, ta), true);
                if (!subtitle.isEmpty()) Fx.centered(c, subtitle, w / 2f, top + rows * bs + 26 * k, 0.85f * k, Fx.alpha(0xD8D8E0, ta), false);
                if (random.nextFloat() < 0.4f) {
                    P sp = fx.add(x + (random.nextFloat() - 0.5f) * 50 * k, y + (random.nextFloat() - 0.5f) * 40 * k, 0, -18 * k, 0.8f, 2 * k, rarity);
                    sp.kind = Kind.SPARKLE;
                }
            }
        }
        c.pop();
        fx.update(dt);
        fx.render(c, fade);
    }
}

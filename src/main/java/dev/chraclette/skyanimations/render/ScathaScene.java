package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;

import java.util.List;
import java.util.Random;

/**
 * Scatha pet drop: a worm of Scatha segments wriggles across the dark screen, a cleaver slices it
 * apart in a few quick cuts, the head is left, flies to the middle, grows, flashes, and sits in a
 * burst of purple light with PET DROP!, the rarity, a coin counter and how many you have.
 */
public final class ScathaScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final int SEGMENTS = 9;
    private static final float[] SLICES = {1.5f, 1.85f, 2.2f, 2.55f};
    private static final float ZOOM = 2.85f, REVEAL = 3.5f, END = 9.5f;

    private final String name;
    private final String rarityLabel;
    private final Object icon;
    private final int rarity;
    private final String countLine;
    private final long coins;
    private final Hooks hooks;
    private final Particles fx = new Particles(99);
    private final Random random = new Random(7);
    private final Iso iso = new Iso();
    private final RevealText text = new RevealText();
    private final Diorama place = new Diorama(Diorama.Theme.HOLLOWS, 4);
    private final float[] cutAt = new float[SEGMENTS];        // when a segment was cut off (-1 = attached)
    private final float[][] cutPos = new float[SEGMENTS][];
    private float lastT = -1;
    private int slicesDone;
    private boolean revealed;

    public ScathaScene(String name, String rarityLabel, Object icon, int rarity, String countLine, long coins, Hooks hooks) {
        this.name = name;
        this.rarityLabel = rarityLabel == null ? "" : rarityLabel;
        this.icon = icon;
        this.rarity = rarity;
        this.countLine = countLine == null ? "" : countLine;
        this.coins = coins;
        this.hooks = hooks;
        java.util.Arrays.fill(cutAt, -1);
    }

    @Override
    public boolean finished(float t) {
        return t >= END;
    }

    private float headX(int w, float k, float t) {
        return -30 * k + (w * 0.85f) * Fx.easeOut(Fx.clamp01(t / 2.4f));
    }

    private float segX(int j, int w, float k, float t) {
        return headX(w, k, t) - (j + 1) * 30 * k;
    }

    private float segY(float x, float cy, float k, float t) {
        return cy + (float) Math.sin(x / (60 * k) - t * 5) * 14 * k;
    }

    @Override
    public void render(Canvas c, float t) {
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        int w = c.width(), h = c.height();
        float k = Math.min(1.6f, Math.max(0.8f, h / 250f));
        float fade = Fx.smooth(t / 0.35f) * (1 - Fx.smooth((t - (END - 0.6f)) / 0.6f));
        float cy = h * 0.44f;
        c.fill(0, 0, w, h, Fx.alpha(0x07070C, 0.55f * fade));
        place.draw(c, t, w / 2f, cy + 16 * k, k, fade, 0.35f + 0.3f * Fx.smooth((t - REVEAL) / 0.4f));

        CubeSkin headSkin = Props.headSkins.apply(name);
        CubeSkin bodySkin = headSkin == null ? null : new CubeSkin(headSkin.back(), headSkin.back(), headSkin.back(),
                headSkin.back(), headSkin.back(), headSkin.back(), null, 1, 1, 1, 0x3B4054, 0x22242E);

        // ---- slices
        while (slicesDone < SLICES.length && t >= SLICES[slicesDone]) {
            int s = slicesDone++;
            hooks.sound(Cue.SWOOSH, 1.7f + s * 0.08f, 0.9f);
            hooks.sound(Cue.CHOP, 1.5f + s * 0.1f, 0.6f);
            hooks.sound(Cue.SWOOSH, 1.4f, 0.6f);
            // cut the tail-most attached segments off (2-3 per slice)
            int cut = 0;
            for (int j = SEGMENTS - 1; j >= 0 && cut < (s == SLICES.length - 1 ? SEGMENTS : 3); j--) {
                if (cutAt[j] >= 0) continue;
                cutAt[j] = t;
                float x = segX(j, w, k, t);
                cutPos[j] = new float[]{x, segY(x, cy, k, t), (random.nextFloat() - 0.5f) * 80 * k, -40 * k - random.nextFloat() * 50 * k, (random.nextFloat() - 0.5f) * 6};
                cut++;
            }
            fx.burst(segX(SEGMENTS / 2, w, k, t), cy, 18, 30 * k, 110 * k, 120 * k, 0.6f, k, 2.5f * k, 0x8C1A2A, 0x3B4054, 0xFFFFFF);
        }

        // ---- the worm (until the head flies to the middle)
        float zoom = Fx.easeInOut(Fx.clamp01((t - ZOOM) / (REVEAL - ZOOM)));
        if (t < REVEAL + 0.1f) {
            for (int j = SEGMENTS - 1; j >= 0; j--) {
                float x, y, rot = 0, a = fade;
                if (cutAt[j] >= 0) {
                    float ct = t - cutAt[j];
                    float[] p = cutPos[j];
                    x = p[0] + p[2] * ct;
                    y = p[1] + p[3] * ct + 160 * k * ct * ct;
                    rot = p[4] * ct;
                    a *= 1 - Fx.smooth(ct / 0.9f);
                } else {
                    x = segX(j, w, k, t);
                    y = segY(x, cy, k, t);
                }
                if (a > 0.03f) segment(c, bodySkin, false, x, y, 26 * k * (0.4f + 0.6f * a), rot, 0.6f, 1);
            }
            float hx = headX(w, k, t), hy = segY(hx, cy, k, t);
            float tx = w / 2f, ty = h * 0.40f;
            float x = hx + (tx - hx) * zoom, y = hy + (ty - hy) * zoom;
            float size = (28 + 17 * zoom) * k;
            if (t < REVEAL) segment(c, headSkin, true, x, y, size, 0, (1 - zoom) * 0.7f, fade);
        }

        // ---- slash lines (the cleaver)
        for (int s = 0; s < slicesDone; s++) {
            float st = t - SLICES[s];
            if (st > 0.35f) continue;
            float sx = w * (0.25f + 0.14f * s), a = 1 - st / 0.35f;
            float x1 = sx - 50 * k, y1 = cy - 80 * k, x2 = sx + 40 * k, y2 = cy + 80 * k;
            if (s % 2 == 1) {
                float tmp = x1;
                x1 = x2;
                x2 = tmp;
            }
            // the cut: a thin bright line that opens up behind the blade
            float p = Fx.clamp01(st / 0.12f);
            float bx = x1 + (x2 - x1) * p, by = y1 + (y2 - y1) * p;
            Fx.line(c, x1, y1, bx, by, 2.2f * k * a, Fx.alpha(0xFFFFFF, a * fade));
            Fx.line(c, x1, y1, bx, by, 5 * k * a, Fx.alpha(0xC8E8FF, 0.3f * a * fade));
            // the katana flies along the cut, with a motion trail
            float ang = (float) Math.atan2(y2 - y1, x2 - x1);
            if (st < 0.22f) {
                for (int g = 4; g >= 0; g--) {
                    float gp = Fx.clamp01((st - g * 0.012f) / 0.12f);
                    float gx = x1 + (x2 - x1) * gp, gy = y1 + (y2 - y1) * gp;
                    katana(c, gx, gy, k, ang + (s % 2 == 0 ? 1 : -1) * (0.5f - gp) * 0.6f, fade * (g == 0 ? 1f : 0.18f / g));
                }
            }
        }

        // ---- reveal
        if (t >= REVEAL) {
            float rt = t - REVEAL;
            if (!revealed) {
                revealed = true;
                hooks.sound(Cue.CASE_WIN, 1.0f, 1.0f);
                hooks.sound(Cue.TOTEM, 1.2f, 0.5f);
                fx.burst(w / 2f, h * 0.40f, 50, 40 * k, 180 * k, 40 * k, 1.1f, 2 * k, 4 * k, rarity, 0xFFFFFF, 0xC040E0);
            }
            float flash = 1 - Fx.smooth(rt / 0.3f);
            if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(0xFFFFFF, 0.55f * flash * fade));
            float x = w / 2f, y = h * 0.40f;
            Fx.glow(c, x, y, 80 * k, 0x6A1A9A, 0.8f * fade, 3);
            Fx.rays(c, x, y, 130 * k, 20, t * 0.25f, 0x9A3ACC, 0.55f * fade);
            float pulse = 1 + 0.03f * (float) Math.sin(rt * 4);
            if (headSkin != null || icon == null) {
                segment(c, headSkin, true, x, y, 45 * k * pulse, 0, (float) Math.sin(rt * 0.8f) * 0.25f, fade);
            } else {
                c.push();
                c.translate(x, y);
                c.scale(3.4f * k, 3.4f * k);
                c.item(icon, -8, -8);
                c.pop();
            }
            float ta = Fx.smooth(rt / 0.3f) * fade;
            Fx.centered(c, "PET DROP!", x, y - 42 * k, 1.3f * k * Fx.backOut(Fx.clamp01(rt / 0.3f)), Fx.alpha(0xFFAA00, ta), true);
            text.draw(c, (rarityLabel.isEmpty() ? "" : rarityLabel + " ") + name, rarity, coins, List.of(countLine),
                    x, y + 38 * k, k, rt - 0.2f, fade, () -> hooks.sound(Cue.TICK, 1.7f, 0.25f), () -> hooks.sound(Cue.POP, 1.5f, 0.25f));
        }
        fx.update(dt);
        fx.render(c, fade);
    }

    /** One worm segment (a cube); the head shows the Scatha face. */
    private void segment(Canvas c, CubeSkin skin, boolean head, float x, float y, float size, float rot, float yaw, float a) {
        if (a < 0.03f) return;
        iso.cx = x;
        iso.cy = y + size * 0.5f;
        iso.scale = size;
        iso.yaw = yaw;
        iso.pitch = (float) Math.toRadians(14);
        iso.begin();
        Pose pose = Pose.identity().pivot(0, 0.5f, 0).rollZ(rot);
        if (skin != null) {
            iso.box(c, skin, Piece.WHOLE, pose, 0, 0, 0, 1, 0.8f, 0, 0);
        } else {
            iso.solidBox(c, pose, -0.5f, 0, -0.5f, 0.5f, 1, 0.5f, head ? 0x2A2E3D : 0x3B4054, a, 0.8f);
        }
    }

    /**
     * A katana: long slightly curved steel blade with a bright edge and the hamon line, a gold tsuba,
     * a black handle with red diamond wrapping and a gold cap. (x, y) is the middle of the blade, angle = direction.
     */
    private void katana(Canvas c, float x, float y, float k, float angle, float a) {
        if (a < 0.02f) return;
        c.push();
        c.translate(x, y);
        c.rotate(angle);
        c.scale(1.7f * k, 1.7f * k);
        // blade from -6 (guard) to 46 (tip), curving up a little towards the tip
        for (int i = -6; i < 46; i++) {
            float f = (i + 6) / 52f;
            int curve = Math.round(-f * f * 4);
            int thick = i > 40 ? Math.max(1, 3 - (i - 40) / 2) : 3;
            c.fill(i, curve - thick + 1, i + 1, curve + 1, Fx.alpha(0xCDD4DC, a));        // steel
            c.fill(i, curve - thick + 1, i + 1, curve - thick + 2, Fx.alpha(0x7C848E, a)); // spine
            c.fill(i, curve, i + 1, curve + 1, Fx.alpha(0xFFFFFF, a));                      // edge
            if (i % 5 == 0 && i < 40) c.fill(i, curve - 1, i + 2, curve, Fx.alpha(0xEEF4FA, a)); // hamon
        }
        // tsuba (guard)
        c.fill(-9, -4, -6, 3, Fx.alpha(0x2A2216, a));
        c.fill(-9, -4, -6, -3, Fx.alpha(0xE2B23A, a));
        c.fill(-9, 2, -6, 3, Fx.alpha(0xE2B23A, a));
        // tsuka (handle) with red ito wrapping
        c.fill(-25, -2, -9, 2, Fx.alpha(0x16141A, a));
        for (int i = -24; i < -9; i += 3) {
            c.fill(i, -2, i + 1, 0, Fx.alpha(0xB0202A, a));
            c.fill(i + 1, 0, i + 2, 2, Fx.alpha(0xB0202A, a));
        }
        // kashira (cap)
        c.fill(-27, -2, -25, 2, Fx.alpha(0xE2B23A, a));
        c.pop();
    }

    /** A cleaver: wide pale blade, dark spine, wooden handle. */
    private void cleaver(Canvas c, float x, float y, float k, float angle, float a) {
        c.push();
        c.translate(x, y);
        c.rotate(angle - 1.2f);
        c.scale(k, k);
        c.fill(-2, -26, 14, -2, Fx.alpha(0xE8ECEF, a));
        c.fill(-2, -26, 14, -24, Fx.alpha(0x8A9096, a));
        c.fill(12, -26, 14, -2, Fx.alpha(0xFFFFFF, a));
        c.fill(0, -2, 5, 14, Fx.alpha(0x7A4A24, a));
        c.fill(0, 4, 5, 6, Fx.alpha(0x4A2A14, a));
        c.pop();
    }
}

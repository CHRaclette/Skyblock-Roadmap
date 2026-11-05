package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Inferno Demonlord drop: the world goes dark, a blaze hovers between four fire pillars, hit markers
 * flash while the pillars ignite (smoke, blue flame, fire), the blaze glows white and bursts into
 * gold, and the drop appears (the Archfiend Dice rolls in) with a gold fountain, name and coins.
 */
public final class BlazeScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final float HITS_START = 0.6f, HIT_GAP = 0.34f, HITS = 6;
    private static final float WHITE = 2.75f, BOOM = 3.05f, TEXT = 4.0f, END = 9.5f;
    private static final int[] PILLAR = {0xF0A040, 0xD07830, 0xA05A2A, 0x7A4024, 0x553020};

    private final String name;
    private final Object icon;
    private final int rarity;
    private final long coins;
    private final List<String> lines;
    private final Hooks hooks;
    private final Particles fx = new Particles(5);
    private final Random random = new Random(9);
    private final Diorama place = new Diorama(Diorama.Theme.CRIMSON, 6);
    private final Iso iso = new Iso();
    private final RevealText text = new RevealText();
    private float lastT = -1;
    private int hitsPlayed;
    private boolean boomed, whitened;
    private final float[][] markers = new float[(int) HITS][];
    private final boolean[] lit = new boolean[4];

    public BlazeScene(String name, Object icon, int rarity, long coins, List<String> lines, Hooks hooks) {
        this.name = name;
        this.icon = icon;
        this.rarity = rarity;
        this.coins = coins;
        this.lines = lines == null ? List.of() : lines;
        this.hooks = hooks;
        for (int i = 0; i < markers.length; i++) {
            markers[i] = new float[]{(random.nextFloat() - 0.5f) * 2, (random.nextFloat() - 0.5f) * 2, random.nextInt(4)};
        }
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
        float fade = Fx.smooth(t / 0.4f) * (1 - Fx.smooth((t - (END - 0.6f)) / 0.6f));
        float cx = w / 2f, cy = h * 0.45f;

        // darken the world, then the Crimson Isle around it
        c.fill(0, 0, w, h, Fx.alpha(0x050302, 0.5f * fade));
        place.draw(c, t, cx, cy + 62 * k, k, fade, 0.4f);
        Fx.glow(c, cx, cy, Math.max(w, h) * 0.45f, 0x2A1A10, 0.5f * fade, 4);

        // fire pillars (two each side)
        for (int i = 0; i < 4; i++) {
            float px = i < 2 ? cx - (110 + i * 34) * k : cx + (110 + (i - 2) * 34) * k;
            float rise = Fx.easeOut(Fx.clamp01((t - 0.1f - i * 0.08f) / 0.5f));
            pillar(c, i, px, h * 0.95f, 16 * k, (120 - Math.abs(i % 2) * 18) * k * rise, t, k, fade);
        }

        // the blaze until it bursts
        if (t < BOOM) {
            float bob = (float) Math.sin(t * 2.2f) * 3 * k;
            float shake = 0;
            int hit = (int) Math.floor((t - HITS_START) / HIT_GAP);
            float sinceHit = (t - HITS_START) - hit * HIT_GAP;
            if (hit >= 0 && hit < HITS) {
                if (hit >= hitsPlayed) {
                    hitsPlayed = hit + 1;
                    hooks.sound(Cue.CHOP, 1.3f + 0.05f * hit, 0.6f);
                    fx.burst(cx + markers[hit][0] * 22 * k, cy + markers[hit][1] * 22 * k, 10, 20 * k, 60 * k, 40 * k, 0.4f, k, 2 * k, 0xFFE15A, 0xFF8A2A);
                }
                shake = sinceHit < 0.12f ? (random.nextFloat() - 0.5f) * 4 * k : 0;
            }
            float white = Fx.smooth((t - WHITE) / (BOOM - WHITE));
            if (white > 0 && !whitened) {
                whitened = true;
                hooks.sound(Cue.MAGIC, 0.8f, 0.8f);
            }
            blaze(c, cx + shake, cy + bob + 22 * k, 34 * k, t, fade);
            if (white > 0) {
                Fx.glow(c, cx, cy + bob - 6 * k, 50 * k * (0.6f + white), 0xFFF4D0, white * fade, 2);
            }
            // hit marker on top
            if (hit >= 0 && hit < HITS && sinceHit < 0.28f) {
                marker(c, (int) markers[hit][2], cx + markers[hit][0] * 34 * k, cy - 10 * k + markers[hit][1] * 26 * k, 7 * k, fade * (1 - sinceHit / 0.28f));
            }
        }

        // burst
        if (t >= BOOM && !boomed) {
            boomed = true;
            hooks.sound(Cue.THUMP, 0.6f, 1.0f);
            hooks.sound(Cue.FIREWORK, 0.9f, 0.9f);
            for (int i = 0; i < 26; i++) {   // the rods fly away as gold bars
                float ang = random.nextFloat() * 6.283f, sp = (80 + random.nextFloat() * 160) * k;
                P bar = fx.add(cx, cy - 10 * k, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 40 * k, 1.1f, 4 * k, random.nextBoolean() ? 0xFFC83A : 0xFFE88A);
                bar.ay = 140 * k;
                bar.kind = Kind.SHARD;
                bar.vrot = (random.nextFloat() - 0.5f) * 12;
            }
            fx.burst(cx, cy - 10 * k, 60, 30 * k, 200 * k, 90 * k, 1.2f, k, 3 * k, 0xFFE15A, 0xFFFFFF, 0xFFB030);
        }
        float bt = t - BOOM;
        if (bt >= 0) {
            float flash = 1 - Fx.smooth(bt / 0.3f);
            if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(0xFFF4D0, 0.6f * flash * fade));
            // the drop: tumbles in dark, then lights up gold
            float pop = Fx.backOut(Fx.clamp01(bt / 0.5f));
            float lit = Fx.smooth((bt - 0.8f) / 0.5f);
            if (lit > 0) {
                Fx.glow(c, cx, cy, 55 * k * lit, 0xFFC83A, 0.75f * lit * fade, 2);
                Fx.rays(c, cx, cy, 110 * k * lit, 14, t * 0.3f, 0xFFE15A, 0.55f * lit * fade);
            }
            // dashed ring closing in when it lights up
            float rp = Fx.clamp01((bt - 0.8f) / 0.6f);
            if (rp > 0 && rp < 1) {
                float r = (90 - 55 * Fx.easeOut(rp)) * k;
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    float dx = cx + (float) Math.cos(a) * r, dy = cy + (float) Math.sin(a) * r;
                    c.fill(Math.round(dx - k), Math.round(dy - k), Math.round(dx + k), Math.round(dy + k), Fx.alpha(0xFFE15A, (1 - rp) * fade));
                }
                if (rp < 0.05f) hooks.sound(Cue.REVEAL, 1.0f, 0.9f);
            }
            float px = 16 * 3.4f * k * pop;
            if (!Props.draw(c, name, cx, cy, px, bt) && icon != null) {
                c.push();
                c.translate(cx, cy);
                c.rotate((1 - pop) * 3f);
                c.scale(px / 16, px / 16);
                c.item(icon, -8, -8);
                c.pop();
            }
            // gold fountain
            if (lit > 0.5f && t < END - 1) {
                for (int i = 0; i < 2; i++) {
                    P g = fx.add(cx + (random.nextFloat() - 0.5f) * 14 * k, cy - 6 * k, (random.nextFloat() - 0.5f) * 60 * k,
                            -(90 + random.nextFloat() * 90) * k, 1.2f, (1 + random.nextFloat() * 1.5f) * k, random.nextBoolean() ? 0xFFD23A : 0xFFF2A0);
                    g.ay = 150 * k;
                }
            }
        }
        fx.update(dt);
        fx.render(c, fade);
        if (t >= TEXT) {
            text.draw(c, name, rarity, coins, lines, cx, cy + 46 * k, k, t - TEXT, fade,
                    () -> hooks.sound(Cue.TICK, 1.6f, 0.3f), () -> hooks.sound(Cue.POP, 1.4f, 0.3f));
        }
    }

    /** Blaze: head with its texture, 12 rods in three rings turning around it (drawn back to front). */
    private void blaze(Canvas c, float cx, float cy, float scale, float t, float fade) {
        if (fade < 0.05f) return;
        iso.cx = cx;
        iso.cy = cy;
        iso.scale = scale;
        iso.yaw = (float) Math.toRadians(30);
        iso.pitch = (float) Math.toRadians(16);
        iso.begin();
        List<float[]> rods = new ArrayList<>();
        float[][] rings = {{1.15f, 0.35f, 1.0f}, {0.9f, -0.45f, -0.8f}, {0.6f, -1.25f, 1.4f}};
        for (float[] ring : rings) {
            for (int i = 0; i < 4; i++) {
                double a = t * ring[2] + i * Math.PI / 2 + ring[1];
                rods.add(new float[]{(float) Math.cos(a) * ring[0], ring[1] - 0.3f, (float) Math.sin(a) * ring[0]});
            }
        }
        rods.sort((p, q) -> Float.compare(iso.depth(p[0], p[1], p[2]), iso.depth(q[0], q[1], q[2])));
        boolean headDrawn = false;
        for (float[] r : rods) {
            if (!headDrawn && iso.depth(r[0], r[1], r[2]) > 0) {
                head(c, t);
                headDrawn = true;
            }
            iso.solidBox(c, Pose.identity(), r[0] - 0.12f, r[1], r[2] - 0.12f, r[0] + 0.12f, r[1] + 0.85f, r[2] + 0.12f, 0xF2C030, 1f, 0.6f);
        }
        if (!headDrawn) head(c, t);
    }

    private void head(Canvas c, float t) {
        Pose pose = Pose.identity().pivot(0, 0.5f, 0).rotY((float) Math.sin(t * 0.9f) * 0.35f);
        iso.box(c, Skins.BLAZE, Piece.WHOLE, pose, 0, 0, 0, 1, 0.8f, 0, 0);
    }

    /** A segmented pillar; its top smokes, then burns blue, then burns properly. */
    private void pillar(Canvas c, int i, float x, float baseY, float w, float h, float t, float k, float fade) {
        if (h < 2) return;
        float top = baseY - h;
        int seg = PILLAR.length;
        for (int s = 0; s < seg; s++) {
            int y1 = Math.round(top + h * s / seg), y2 = Math.round(top + h * (s + 1) / seg);
            int col = PILLAR[s];
            c.fill(Math.round(x - w / 2), y1, Math.round(x + w / 2), y2, Fx.alpha(col, fade));
            c.fill(Math.round(x - w / 2), y1, Math.round(x - w / 6), y2, Fx.alpha(Fx.brighten(col, 0.18f), fade));
            c.fill(Math.round(x + w / 4), y1, Math.round(x + w / 2), y2, Fx.alpha(Fx.darken(col, 0.25f), fade));
        }
        c.fill(Math.round(x - w / 2), Math.round(top), Math.round(x + w / 2), Math.round(top + 2 * k), Fx.alpha(0xFFE0A0, fade));
        // fire state: 0 smoke, 1 blue, 2 fire
        int state = t < 0.9f + i * 0.35f ? 0 : t < 2.2f + i * 0.12f ? 1 : 2;
        if (state >= 1 && !lit[i]) {
            lit[i] = true;
            hooks.sound(Cue.FEVER, 1.6f, 0.25f);
        }
        for (int n = 0; n < 2 && fade > 0.1f; n++) {
            int from = state == 0 ? 0xD8D8D8 : state == 1 ? 0x8AE6FF : 0xFFE15A;
            int to = state == 0 ? 0x606060 : state == 1 ? 0x2060FF : 0xFF4A1A;
            P p = fx.add(x + (random.nextFloat() - 0.5f) * w * 0.8f, top, (random.nextFloat() - 0.5f) * 10 * k,
                    -(state == 0 ? 20 : 45) * k - random.nextFloat() * 25 * k, state == 0 ? 0.9f : 0.5f,
                    (state == 0 ? 4 : 3.5f) * k, from);
            p.toColor = to;
        }
    }

    /** Hit markers like the Inferno Demonlord attunement icons: X, target, plus, diamond. */
    private void marker(Canvas c, int kind, float x, float y, float r, float a) {
        int col = Fx.alpha(kind == 1 ? 0x6AD8FF : 0xFFFFFF, a);
        int dark = Fx.alpha(0x000000, a * 0.8f);
        switch (kind) {
            case 0 -> {
                Fx.line(c, x - r, y - r, x + r, y + r, 3.2f, dark);
                Fx.line(c, x - r, y + r, x + r, y - r, 3.2f, dark);
                Fx.line(c, x - r, y - r, x + r, y + r, 1.8f, col);
                Fx.line(c, x - r, y + r, x + r, y - r, 1.8f, col);
            }
            case 1 -> {
                Fx.ring(c, x, y, r, 2, col);
                c.fill(Math.round(x - 1), Math.round(y - 1), Math.round(x + 2), Math.round(y + 2), col);
            }
            case 2 -> {
                c.fill(Math.round(x - r), Math.round(y - 1), Math.round(x + r), Math.round(y + 2), col);
                c.fill(Math.round(x - 1), Math.round(y - r), Math.round(x + 2), Math.round(y + r), col);
            }
            default -> {
                c.push();
                c.translate(x, y);
                c.rotate(0.785f);
                c.fill(Math.round(-r * 0.6f), Math.round(-r * 0.6f), Math.round(r * 0.6f), Math.round(r * 0.6f), dark);
                c.fill(Math.round(-r * 0.45f), Math.round(-r * 0.45f), Math.round(r * 0.45f), Math.round(r * 0.45f), col);
                c.pop();
            }
        }
    }
}

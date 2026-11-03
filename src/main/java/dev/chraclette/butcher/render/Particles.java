package dev.chraclette.butcher.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Screen-space 2D particles. Pixel-art style: everything is squares, glyphs or plus signs. */
public final class Particles {

    public enum Kind { SQUARE, GLYPH, SPARKLE, RING, SHARD }

    public static final class P {
        public float x, y, vx, vy, ax, ay, drag = 1f;
        public float age, life, size, rot, vrot;
        public int color;
        public Kind kind = Kind.SQUARE;
        public String glyph = "";
        public boolean fadeIn;
        /** Colour it fades towards over its life (e.g. flame -> smoke). -1 = none. */
        public int toColor = -1;
    }

    /** Standard Galactic Alphabet look-alikes (the enchanting table runes). */
    public static final String[] RUNES = {
            "ᔑ", "ʖ", "ᓵ", "↸", "ᒷ", "⎓", "⊣", "⍑", "╎", "⋮", "ꖌ", "ꖎ", "ᒲ", "リ", "ᑑ", "∷", "ᓭ", "ℸ", "⚍", "⍊", "∴", "⨅"};

    private final List<P> list = new ArrayList<>();
    private final Random random;
    private static final int MAX = 700;

    public Particles(long seed) {
        this.random = new Random(seed);
    }

    public Random random() {
        return random;
    }

    public P add(float x, float y, float vx, float vy, float life, float size, int color) {
        P p = new P();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.life = life;
        p.size = size;
        p.color = color;
        if (list.size() < MAX) {
            list.add(p);
        }
        return p;
    }

    /** Burst in all directions. */
    public void burst(float x, float y, int count, float speedMin, float speedMax, float gravity,
                      float life, float sizeMin, float sizeMax, int... colors) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            float s = speedMin + random.nextFloat() * (speedMax - speedMin);
            P p = add(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s - s * 0.35f,
                    life * (0.6f + random.nextFloat() * 0.6f),
                    sizeMin + random.nextFloat() * (sizeMax - sizeMin),
                    colors[random.nextInt(colors.length)]);
            p.ay = gravity;
            p.drag = 0.985f;
            p.vrot = (random.nextFloat() - 0.5f) * 10;
        }
    }

    public void update(float dt) {
        if (dt <= 0) return;
        Iterator<P> it = list.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.vx += p.ax * dt;
            p.vy += p.ay * dt;
            float drag = (float) Math.pow(p.drag, dt * 60);
            p.vx *= drag;
            p.vy *= drag;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vrot * dt;
        }
    }

    public void render(Canvas c, float alphaMul) {
        for (P p : list) {
            float t = p.age / p.life;
            float a = t > 0.6f ? 1 - (t - 0.6f) / 0.4f : 1;
            if (p.fadeIn && t < 0.15f) a *= t / 0.15f;
            a *= alphaMul;
            if (a <= 0.02f) continue;
            int rgb = p.toColor >= 0 ? Fx.lerpColor(p.color, p.toColor, t) : p.color;
            int col = Fx.alpha(rgb, a);
            switch (p.kind) {
                case SQUARE -> {
                    int s = Math.max(1, Math.round(p.size));
                    int x = Math.round(p.x - s / 2f), y = Math.round(p.y - s / 2f);
                    c.fill(x, y, x + s, y + s, col);
                }
                case SHARD -> {
                    c.push();
                    c.translate(p.x, p.y);
                    c.rotate(p.rot);
                    int s = Math.max(1, Math.round(p.size));
                    c.fill(-s, -s / 2, s, (s + 1) / 2, col);
                    c.pop();
                }
                case SPARKLE -> {
                    float pulse = 0.6f + 0.4f * (float) Math.sin(p.age * 14 + p.x);
                    int s = Math.max(1, Math.round(p.size * pulse));
                    int x = Math.round(p.x), y = Math.round(p.y);
                    c.fill(x - s, y, x + s + 1, y + 1, col);
                    c.fill(x, y - s, x + 1, y + s + 1, col);
                    c.fill(x - 1, y - 1, x + 2, y + 2, Fx.alpha(0xFFFFFF, a));
                }
                case RING -> {
                    float r = p.size * (0.4f + 0.6f * t);
                    Fx.ring(c, p.x, p.y, r, Math.max(1, Math.min(3, Math.round(p.size / 25))), col);
                }
                case GLYPH -> {
                    c.push();
                    c.translate(p.x, p.y);
                    c.scale(p.size, p.size);
                    c.text(p.glyph, -c.textWidth(p.glyph) / 2, -4, col, false);
                    c.pop();
                }
            }
        }
    }

    public void forEach(java.util.function.Consumer<P> action) {
        list.forEach(action);
    }

    public void clear() {
        list.clear();
    }

    public int size() {
        return list.size();
    }
}

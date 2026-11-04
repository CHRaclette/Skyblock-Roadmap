package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.util.Random;

/**
 * Archfiend Dice roll as a slot machine: the lever is pulled, three reels of dice faces spin and stop
 * one after another on the number you rolled. A 6 (or a 7 on the High Class dice) is a jackpot:
 * lights flash, coins and sparks fly. The 3D die tumbles on top of the cabinet.
 */
public final class SlotScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final float SPIN_START = 0.3f, FIRST_STOP = 1.05f, STOP_GAP = 0.35f;

    private final int result;           // 1..7
    private final boolean highClass;
    private final String bonus;         // "+60❤" or ""
    private final String diceName;
    private final Hooks hooks;
    private final boolean jackpot;
    private final String tex;
    private final int faces;
    private final Particles fx = new Particles(42);
    private final Random random = new Random();
    private final float end;
    private final int[] lastSymbol = {-1, -1, -1};
    private final boolean[] stopped = new boolean[3];
    private float lastT = -1;
    private boolean pulled, celebrated;

    public SlotScene(int result, boolean highClass, String bonus, Hooks hooks) {
        this.result = Math.max(1, Math.min(highClass ? 7 : 6, result));
        this.highClass = highClass;
        this.bonus = bonus == null ? "" : bonus;
        this.hooks = hooks;
        this.faces = highClass ? 7 : 6;
        this.jackpot = this.result >= 6;
        this.diceName = highClass ? "High Class Archfiend Dice" : "Archfiend Dice";
        this.tex = highClass ? "skyanimations:textures/props/high_class_dice.png" : "skyanimations:textures/props/archfiend_dice.png";
        this.end = stopTime(2) + (jackpot ? 3.0f : 1.6f);
    }

    private static float stopTime(int reel) {
        return FIRST_STOP + reel * STOP_GAP;
    }

    @Override
    public boolean finished(float t) {
        return t >= end;
    }

    @Override
    public void render(Canvas c, float t) {
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        int w = c.width(), h = c.height();
        float k = 1.3f * Math.min(1.5f, Math.max(0.8f, h / 270f));
        float fade = Fx.smooth(t / 0.2f) * (1 - Fx.smooth((t - (end - 0.4f)) / 0.4f));
        float in = Fx.backOut(Math.min(1, t / 0.3f));
        float cx = w / 2f, cy = h * 0.36f;
        int body = highClass ? 0x1C161E : 0x8E1A18, trim = highClass ? 0xE0B040 : 0xF0C040, dark = highClass ? 0x0A080C : 0x4A0A0A;

        c.push();
        c.translate(cx, cy);
        c.scale(in, in);
        c.translate(-cx, -cy);

        // cabinet
        float cw = 128 * k, ch = 84 * k;
        int x1 = Math.round(cx - cw / 2), x2 = Math.round(cx + cw / 2), y1 = Math.round(cy - ch / 2), y2 = Math.round(cy + ch / 2);
        if (jackpot && t > stopTime(2)) {
            Fx.glow(c, cx, cy, cw * 0.9f, trim, 0.6f * fade, 3);
        }
        c.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, Fx.alpha(0x000000, fade));
        c.fill(x1, y1, x2, y2, Fx.alpha(body, fade));
        c.fill(x1, y1, x2, y1 + Math.round(3 * k), Fx.alpha(Fx.brighten(body, 0.25f), fade));
        c.fill(x1, y2 - Math.round(4 * k), x2, y2, Fx.alpha(dark, fade));
        OldBook.rect(c, x1 + 2, y1 + 2, x2 - x1 - 4, y2 - y1 - 4, Fx.alpha(trim, fade));
        // marquee lights around the edge: chase while spinning, flash on a jackpot
        int bulbs = 18;
        for (int i = 0; i < bulbs; i++) {
            float p = i / (float) bulbs;
            float bx, by;
            if (p < 0.5f) { bx = x1 + 6 * k + (cw - 12 * k) * (p * 2); by = y1 + 6 * k; }
            else { bx = x1 + 6 * k + (cw - 12 * k) * ((p - 0.5f) * 2); by = y2 - 9 * k; }
            boolean on = jackpot && t > stopTime(2) ? ((int) (t * 8)) % 2 == 0 : ((int) (t * 10) + i) % 3 == 0;
            int col = on ? 0xFFF2A0 : Fx.darken(trim, 0.5f);
            c.fill(Math.round(bx - k), Math.round(by - k), Math.round(bx + k + 1), Math.round(by + k + 1), Fx.alpha(col, fade));
        }
        // title plate
        String title = highClass ? "HIGH CLASS ARCHFIEND" : "ARCHFIEND";
        Fx.centered(c, title, cx, y1 + 10 * k, 0.75f * k, Fx.alpha(trim, fade), true);

        // three reels
        float rw = 30 * k, rh = 30 * k, gap = 6 * k;
        float rx0 = cx - (3 * rw + 2 * gap) / 2;
        float ry = cy - rh / 2 + 4 * k;
        for (int r = 0; r < 3; r++) {
            float rx = rx0 + r * (rw + gap);
            reel(c, r, t, rx, ry, rw, rh, k, fade, trim);
        }
        // payline
        c.fill(Math.round(rx0 - 4 * k), Math.round(ry + rh / 2), Math.round(rx0 + 3 * rw + 2 * gap + 4 * k), Math.round(ry + rh / 2 + 1),
                Fx.alpha(jackpot && t > stopTime(2) ? 0xFFF2A0 : 0xFF4040, 0.55f * fade));

        // lever on the right
        float pull = t < 0.35f ? (float) Math.sin(t / 0.35f * Math.PI) : 0;
        if (!pulled && t > 0.05f) {
            pulled = true;
            hooks.sound(Cue.RATTLE, 1.2f, 0.7f);
        }
        float lx = x2 + 6 * k, lyTop = y1 + 6 * k, lyBase = cy + 6 * k;
        float knobY = lyTop + (lyBase - lyTop) * pull * 0.9f;
        c.fill(Math.round(x2), Math.round(lyBase - 3 * k), Math.round(lx + 2 * k), Math.round(lyBase + 3 * k), Fx.alpha(0x404040, fade));
        c.fill(Math.round(lx), Math.round(Math.min(knobY, lyBase)), Math.round(lx + 2 * k), Math.round(lyBase), Fx.alpha(0xC8C8C8, fade));
        Fx.disc(c, lx + k, knobY, 4 * k, 1, Fx.alpha(0xE02020, fade));
        Fx.disc(c, lx, knobY - k, 1.5f * k, 1, Fx.alpha(0xFF9090, fade));

        c.pop();

        // the 3D die tumbling on top of the cabinet
        Props.draw(c, diceName, cx, cy - ch / 2 * in - 16 * k, 26 * k * in, t);

        // result text
        if (t > stopTime(2)) {
            float rt = t - stopTime(2);
            float a = Fx.smooth(rt / 0.25f) * fade;
            float pop = Fx.backOut(Math.min(1, rt / 0.3f));
            String head = jackpot ? (result == 7 ? "SEVEN! JACKPOT!" : "JACKPOT!") : "Rolled a " + result;
            int col = jackpot ? (result == 7 ? 0xFF55FF : 0xFFD23A) : 0xFFFFFF;
            if (jackpot && result == 7) Fx.rainbow(c, head, cx, cy + ch / 2 + 10 * k, 1.5f * k * pop, t, a, true);
            else Fx.centered(c, head, cx, cy + ch / 2 + 10 * k, (jackpot ? 1.5f : 1.1f) * k * pop, Fx.alpha(col, a), true);
            if (!bonus.isEmpty()) {
                Fx.centered(c, "Bonus: " + bonus, cx, cy + ch / 2 + 26 * k, 0.9f * k, Fx.alpha(0xFF5555, a), false);
            }
            if (!celebrated) {
                celebrated = true;
                if (jackpot) {
                    hooks.sound(Cue.CASE_WIN, result == 7 ? 1.2f : 1.0f, 1.0f);
                    hooks.sound(Cue.FIREWORK, 1.0f, 0.8f);
                    fx.burst(cx, cy, 80, 50 * k, 220 * k, 160 * k, 1.6f, 2 * k, 4 * k, 0xFFD23A, 0xFFF2A0, 0xFFFFFF, highClass ? 0xFF55FF : 0xE03A2A);
                    P ring = fx.add(cx, cy, 0, 0, 0.8f, 110 * k, trim);
                    ring.kind = Kind.RING;
                } else {
                    hooks.sound(Cue.NOTE, 0.8f + result * 0.1f, 0.7f);
                }
            }
            if (jackpot && random.nextFloat() < 0.6f) {
                // coins raining down
                P coin = fx.add(cx + (random.nextFloat() - 0.5f) * w * 0.6f, -5, (random.nextFloat() - 0.5f) * 20 * k, 40 * k, 1.6f, 2.5f * k, 0xFFD23A);
                coin.ay = 120 * k;
                coin.kind = Kind.SPARKLE;
            }
        }
        fx.update(dt);
        fx.render(c, fade);
    }

    /** One reel: a vertical strip of dice faces scrolling down, slowing and stopping on the result. */
    private void reel(Canvas c, int r, float t, float x, float y, float w, float h, float k, float fade, int trim) {
        c.fill(Math.round(x - 2), Math.round(y - 2), Math.round(x + w + 2), Math.round(y + h + 2), Fx.alpha(0x000000, fade));
        c.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Fx.alpha(0xF4EEE0, fade));
        float stop = stopTime(r);
        // position in "symbols": spins fast, then eases into the result (index result-1) with a small bounce
        float pos;
        if (t < SPIN_START) {
            pos = 0;
        } else if (t < stop) {
            float p = (t - SPIN_START) / (stop - SPIN_START);
            float total = faces * (3 + r) + (result - 1);      // symbols travelled; lands on the result face
            pos = total * (1 - (float) Math.pow(1 - p, 3));
            int sym = (int) Math.floor(pos);
            if (sym != lastSymbol[r]) {
                lastSymbol[r] = sym;
                hooks.sound(Cue.TICK, 1.3f + r * 0.15f, 0.35f);
            }
        } else {
            float total = faces * (3 + r) + (result - 1);
            float b = (float) Math.exp(-(t - stop) * 9) * (float) Math.sin((t - stop) * 30) * 0.12f;
            pos = total + b;
            if (!stopped[r]) {
                stopped[r] = true;
                hooks.sound(Cue.THUMP, 1.1f, 0.9f);
            }
        }
        // draw the symbols around pos, clipped to the window (16 texels per symbol)
        float s = w / 20f;                    // GUI px per texel; window shows 20 texels
        float winTex = h / s;
        float centre = winTex / 2f;
        int base = (int) Math.floor(pos);
        for (int i = base - 2; i <= base + 2; i++) {
            int face = Math.floorMod(i, faces);           // 0..faces-1
            float top = centre - 8 + (i - pos) * -20f;     // symbols move downwards as pos grows
            int v0 = Math.max(0, Math.round(-top)), v1 = Math.min(16, Math.round(winTex - top));
            if (v1 <= v0) continue;
            c.push();
            c.translate(x + 2 * s, y);
            c.scale(s, s);
            c.blit(tex, 0, Math.round(top) + v0, face * 16, v0, 16, v1 - v0, 112, 16);
            c.pop();
        }
        // glass shading
        c.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h * 0.18f), Fx.alpha(0x000000, 0.25f * fade));
        c.fill(Math.round(x), Math.round(y + h * 0.82f), Math.round(x + w), Math.round(y + h), Fx.alpha(0x000000, 0.25f * fade));
        boolean won = stopped[r] && jackpot && ((int) (t * 8)) % 2 == 0;
        OldBook.rect(c, Math.round(x - 1), Math.round(y - 1), Math.round(w + 2), Math.round(h + 2), Fx.alpha(won ? 0xFFF2A0 : trim, fade));
    }
}

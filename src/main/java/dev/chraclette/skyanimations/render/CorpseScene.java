package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Glacite Mineshaft corpse: the frozen corpse in a block of ice. Every pickaxe hit spreads the cracks
 * and knocks one item into the hotbar (its name above the bar). Before a rare item the ice glows in
 * that item's colour; the last hit shatters it, golden stars pull together into a light and the rare
 * item appears with its name, "1 in N corpses", your count and a coin counter. Dyes get paint instead
 * of gold.
 */
public final class CorpseScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final float HIT0 = 1.0f, GAP = 0.62f, FLY = 0.45f, SWING = 0.22f;
    private static final String HOTBAR = "textures/gui/sprites/hud/hotbar.png";

    private final String type;
    private final List<RunScene.Loot> loot;
    private final int rareIdx;
    private final Object pickaxe;
    private final List<String> rareLines;
    private final int[] dye;
    private final Hooks hooks;
    private final Particles fx = new Particles(13);
    private final Random random = new Random(21);
    private final Iso iso = new Iso();
    private final RevealText text = new RevealText();
    private final Diorama place = new Diorama(Diorama.Theme.GLACITE, 9);
    private final List<float[][]> cracks = new ArrayList<>();
    private final int normal;           // items knocked out one by one
    private final float shatter, reveal, end;
    private final float[][] splats;
    private float lastT = -1;
    private int hitsDone;
    private boolean shattered, revealed, auraSound, landed;

    public CorpseScene(String type, List<RunScene.Loot> loot, int rareIdx, Object pickaxe, List<String> rareLines,
                       int[] dyePalette, Hooks hooks) {
        this.type = type == null ? "VANGUARD" : type;
        this.loot = new ArrayList<>(loot);
        this.rareIdx = rareIdx;
        this.pickaxe = pickaxe;
        this.rareLines = rareLines == null ? List.of() : rareLines;
        this.dye = dyePalette;
        this.hooks = hooks;
        // the rare one goes last
        if (rareIdx >= 0 && rareIdx != this.loot.size() - 1) {
            this.loot.add(this.loot.remove(rareIdx));
        }
        this.normal = this.loot.size() - (rareIdx >= 0 ? 1 : 0);
        float lastHit = HIT0 + Math.max(0, normal - 1) * GAP;
        this.shatter = lastHit + (rareIdx >= 0 ? 1.4f : 0);
        this.reveal = shatter + 1.25f;
        this.end = rareIdx >= 0 ? reveal + 6.2f : lastHit + 1.8f;
        for (int i = 0; i < 9; i++) cracks.add(branch(i));
        splats = new float[12][];
        for (int i = 0; i < splats.length; i++) {
            float ang = random.nextFloat() * 6.283f, dist = 0.2f + random.nextFloat() * 0.33f;
            splats[i] = new float[]{(float) Math.cos(ang) * dist, (float) Math.sin(ang) * dist * 0.7f, 5 + random.nextFloat() * 10, 6 + random.nextFloat() * 18};
        }
    }

    /** A crack: a jagged line from the impact point outwards (relative units, 1 = 50 px at k=1). */
    private float[][] branch(int i) {
        float ang = (float) (i / 9.0 * Math.PI * 2 + random.nextFloat() * 0.5f);
        int n = 5 + random.nextInt(4);
        float[][] pts = new float[n][2];
        float x = 0, y = 0;
        for (int j = 0; j < n; j++) {
            ang += (random.nextFloat() - 0.5f) * 1.1f;
            float len = 0.09f + random.nextFloat() * 0.08f;
            x += (float) Math.cos(ang) * len;
            y += (float) Math.sin(ang) * len;
            pts[j][0] = x;
            pts[j][1] = y;
        }
        return pts;
    }

    private RunScene.Loot rare() {
        return rareIdx >= 0 ? loot.get(loot.size() - 1) : null;
    }

    private float hitTime(int i) {
        return HIT0 + i * GAP;
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
        float k = Math.min(1.6f, Math.max(0.8f, h / 250f));
        float fade = Fx.smooth(t / 0.35f) * (1 - Fx.smooth((t - (end - 0.5f)) / 0.5f));
        float cx = w / 2f, cy = h * 0.40f;
        RunScene.Loot rare = rare();
        int rareCol = rare == null ? 0xFFFFFF : dye != null ? dye[0] : rare.color();

        c.fill(0, 0, w, h, Fx.alpha(0x05070C, 0.45f * fade));
        place.draw(c, t, cx, cy + 43 * k, k, fade, 0.3f + 0.3f * Fx.smooth((t - shatter) / 0.4f));

        // ---- hits
        int hitIndex = (int) Math.floor((t - HIT0) / GAP + 1e-4);
        int hits = Math.min(normal, Math.max(0, hitIndex + 1));
        if (t < HIT0) hits = 0;
        while (hitsDone < hits) {
            int i = hitsDone++;
            hooks.sound(Cue.ICE_HIT, 0.9f + random.nextFloat() * 0.2f, 0.9f);
            fx.burst(cx + 14 * k, cy - 4 * k, 14, 30 * k, 90 * k, 120 * k, 0.6f, k, 2.5f * k, 0xDFF6FF, 0xA8DEFF, 0xFFFFFF);
        }
        int crackLevel = Math.min(cracks.size(), hits + (shattered ? 3 : 0));

        // ---- the block (until it shatters)
        float sh = shatter;
        if (t < sh || rare == null) {
            float appear = Fx.backOut(Fx.clamp01(t / 0.5f));
            float aura = rare == null ? 0 : Fx.smooth((t - (hitTime(normal - 1) + 0.3f)) / 0.9f);
            if (aura > 0) {
                Fx.glow(c, cx, cy, 70 * k * (0.8f + 0.2f * aura), rareCol, 0.7f * aura * fade, 3);
                if (!auraSound) {
                    auraSound = true;
                    hooks.sound(Cue.MAGIC, 0.7f, 0.7f);
                }
            }
            float noRareFade = rare == null ? 1 - Fx.smooth((t - (end - 1.2f)) / 0.7f) : 1;
            corpse(c, cx, cy, 1.5f * k * appear, fade * noRareFade);
            ice(c, cx, cy, 70 * k * appear, t, fade * noRareFade, aura, rareCol);
            // cracks from the impact point
            float ix = cx + 14 * k, iy = cy - 4 * k;
            for (int b = 0; b < crackLevel; b++) {
                float[][] pts = cracks.get(b);
                float px = ix, py = iy;
                for (float[] p : pts) {
                    float nx = ix + p[0] * 50 * k, ny = iy + p[1] * 50 * k;
                    Fx.line(c, px, py, nx, ny, 1.4f, Fx.alpha(0xFFFFFF, 0.9f * fade * noRareFade));
                    px = nx;
                    py = ny;
                }
            }
            // pickaxe
            pickaxe(c, t, cx, cy, k, fade * noRareFade);
        }

        // ---- shatter
        if (rare != null && t >= sh && !shattered) {
            shattered = true;
            hooks.sound(Cue.ICE_BREAK, 0.9f, 1.0f);
            hooks.sound(Cue.THUMP, 0.6f, 0.8f);
            for (int i = 0; i < 70; i++) {
                float ang = random.nextFloat() * 6.283f, sp = (60 + random.nextFloat() * 200) * k;
                P s = fx.add(cx + (random.nextFloat() - 0.5f) * 50 * k, cy + (random.nextFloat() - 0.5f) * 60 * k,
                        (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp - 50 * k, 1.0f, (2 + random.nextFloat() * 4) * k,
                        random.nextBoolean() ? 0xCFEFFF : 0x9ED8FF);
                s.ay = 260 * k;
                s.kind = Kind.SHARD;
                s.vrot = (random.nextFloat() - 0.5f) * 10;
            }
        }
        if (rare != null && t >= sh) {
            float st = t - sh;
            float flash = 1 - Fx.smooth(st / 0.25f);
            if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(0xFFFFFF, 0.7f * flash * fade));
            // the world drains of colour for a moment
            float grey = Fx.smooth(st / 0.2f) * (1 - Fx.smooth((st - 0.9f) / 0.4f));
            if (grey > 0) c.fill(0, 0, w, h, Fx.alpha(dye != null ? Fx.lerpColor(0x808080, rareCol, 0.4f) : 0x8C8670, 0.5f * grey * fade));
            // stars (or paint drops) pull together into a light
            if (st < reveal - sh) {
                float pull = Fx.easeIn(Fx.clamp01((st - 0.15f) / 0.8f));
                for (int i = 0; i < 6; i++) {
                    double a = i / 6.0 * Math.PI * 2 + st * 2.5;
                    float r = (55 - 55 * pull) * k;
                    float sx = cx + (float) Math.cos(a) * r, sy = cy + (float) Math.sin(a) * r * 0.8f;
                    float sz = 5 * k;
                    int col = dye != null ? dye[i % dye.length] : 0xFFD23A;
                    Fx.glow(c, sx, sy, 10 * k, col, 0.6f * fade, 1);
                    c.push();
                    c.translate(sx, sy);
                    c.rotate(st * 4 + i);
                    c.fill(Math.round(-sz / 2), Math.round(-sz / 2), Math.round(sz / 2), Math.round(sz / 2), Fx.alpha(col, fade));
                    c.fill(Math.round(-sz / 4), Math.round(-sz), Math.round(sz / 4), Math.round(sz), Fx.alpha(Fx.brighten(col, 0.4f), fade));
                    c.pop();
                }
                float orb = Fx.smooth((st - 0.8f) / 0.4f);
                if (orb > 0) Fx.glow(c, cx, cy, 30 * k * orb, dye != null ? rareCol : 0xFFE15A, orb * fade, 1);
            }
        }

        // ---- reveal
        if (rare != null && t >= reveal) {
            float rt = t - reveal;
            if (!revealed) {
                revealed = true;
                hooks.sound(dye != null ? Cue.SPLASH : Cue.CASE_WIN, 1.0f, 1.0f);
                hooks.sound(Cue.FIREWORK, 1.0f, 0.7f);
                if (dye != null) {
                    fx.burst(cx, cy, 80, 60 * k, 240 * k, 120 * k, 1.3f, 2 * k, 5 * k, dye);
                } else {
                    fx.burst(cx, cy, 60, 40 * k, 200 * k, 140 * k, 1.4f, 2 * k, 4 * k, 0xFFD23A, 0xFFF2A0, 0xFFFFFF);
                }
            }
            float ia = fade * (1 - Fx.smooth((t - (end - 1.4f)) / 0.4f));
            if (dye != null) {
                float wash = Fx.smooth(rt / 0.2f) * (1 - 0.6f * Fx.smooth((rt - 0.3f) / 0.6f));
                c.fill(0, 0, w, h, Fx.alpha(dye[0], 0.45f * wash * ia));
                splats(c, cx, cy, w, h, k, rt, ia);
            }
            Fx.glow(c, cx, cy, 70 * k, rareCol, 0.85f * ia, 3);
            Fx.rays(c, cx, cy, 120 * k, 14, t * 0.3f, Fx.brighten(rareCol, 0.3f), 0.5f * ia);
            if (dye == null && random.nextFloat() < 0.5f && rt < 4) {
                P coin = fx.add(cx + (random.nextFloat() - 0.5f) * w * 0.7f, -5, 0, 30 * k, 2f, 2.5f * k, 0xFFD23A);
                coin.ay = 90 * k;
            }
            // the item, then it flies into the last slot
            float fly = Fx.easeIn(Fx.clamp01((t - (end - 1.4f)) / 0.5f));
            float ix = cx + (slotX(c, loot.size() - 1, k) - cx) * fly;
            float iy = cy + (slotY(c, k) - cy) * fly;
            float pop = Fx.backOut(Fx.clamp01(rt / 0.4f));
            float px = 16 * (3.6f - 2.6f * fly) * k * pop;
            if (fly < 1) {
                if (!Props.draw(c, rare.name(), ix, iy, px, rt) && rare.icon() != null) {
                    c.push();
                    c.translate(ix, iy);
                    c.scale(px / 16, px / 16);
                    c.item(rare.icon(), -8, -8);
                    c.pop();
                }
            } else if (!landed) {
                landed = true;
                hooks.sound(Cue.POP, 1.0f, 0.8f);
            }
            text.draw(c, rare.name(), rare.color(), rare.coins(), rareLines, cx, cy + 40 * k, k, rt - 0.3f, ia * (1 - fly),
                    () -> hooks.sound(Cue.TICK, 1.7f, 0.25f), () -> hooks.sound(Cue.POP, 1.5f, 0.25f));
        }

        // ---- hotbar + flying items + name
        hotbar(c, t, k, fade);
        fx.update(dt);
        fx.render(c, fade);
    }

    private void splats(Canvas c, float cx, float cy, int w, int h, float k, float rt, float a) {
        for (int i = 0; i < splats.length; i++) {
            float[] s = splats[i];
            float grow = Fx.backOut(Fx.clamp01((rt - i * 0.02f) / 0.25f));
            float sx = cx + s[0] * w, sy = cy + s[1] * h, r = s[2] * k * grow;
            int col = dye[i % dye.length];
            Fx.disc(c, sx, sy, r, 1, Fx.alpha(col, 0.9f * a));
            float drip = Fx.clamp01((rt - 0.4f) / 3f) * s[3] * k;
            if (drip > 0) {
                c.fill(Math.round(sx - r * 0.2f), Math.round(sy), Math.round(sx + r * 0.2f), Math.round(sy + r * 0.5f + drip), Fx.alpha(col, 0.9f * a));
                Fx.disc(c, sx, sy + r * 0.5f + drip, r * 0.25f, 1, Fx.alpha(col, 0.9f * a));
            }
        }
    }

    /** The frozen corpse: a dark armoured figure with a coloured visor (by corpse type). */
    private void corpse(Canvas c, float cx, float cy, float k, float a) {
        if (a < 0.03f || k < 0.05f) return;
        int armor = switch (type) {
            case "LAPIS" -> 0x23306A;
            case "UMBER" -> 0x5A3A22;
            case "TUNGSTEN" -> 0x4A4A50;
            default -> 0x22262E;
        };
        int visor = switch (type) {
            case "LAPIS" -> 0x3A6AFF;
            case "UMBER" -> 0xE09040;
            case "TUNGSTEN" -> 0xC8C8D0;
            default -> 0x4AA0FF;
        };
        c.push();
        c.translate(cx - 4 * k, cy + 2 * k);
        c.rotate(-0.12f);
        c.scale(k, k);
        int A = Fx.alpha(armor, a), D = Fx.alpha(Fx.darken(armor, 0.3f), a), L = Fx.alpha(Fx.brighten(armor, 0.15f), a);
        c.fill(-7, -34, 7, -20, L);                 // head
        c.fill(-5, -30, 5, -28, Fx.alpha(visor, a)); // visor bar
        c.fill(-1, -28, 1, -23, Fx.alpha(visor, a));
        c.fill(-9, -20, 9, 2, A);                    // body
        c.fill(-14, -19, -9, 0, D);                  // arms
        c.fill(9, -19, 14, 0, D);
        c.fill(-8, 2, -1, 24, D);                    // legs
        c.fill(1, 2, 8, 24, A);
        c.pop();
    }

    /** The ice: a pale translucent box (3D), turning a little, tinted towards the rare colour. */
    private void ice(Canvas c, float cx, float cy, float scale, float t, float a, float aura, int auraCol) {
        if (a < 0.03f || scale < 1) return;
        iso.cx = cx;
        iso.cy = cy + scale * 0.62f;
        iso.scale = scale;
        iso.yaw = (float) Math.toRadians(24 + 4 * Math.sin(t * 0.7));
        iso.pitch = (float) Math.toRadians(12);
        iso.begin();
        int col = Fx.lerpColor(0xD8F2FF, auraCol, 0.25f * aura);
        // no outline pass (it would darken the ice); light edges instead
        iso.solidBox(c, Pose.identity(), -0.55f, 0, -0.42f, 0.55f, 1.35f, 0.42f, col, 0.42f * a, 0);
        float[][] corners = {{-0.55f, 1.35f, 0.42f}, {0.55f, 1.35f, 0.42f}, {0.55f, 0, 0.42f}, {-0.55f, 0, 0.42f},
                {-0.55f, 1.35f, -0.42f}, {0.55f, 1.35f, -0.42f}, {0.55f, 0, -0.42f}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {4, 5}, {5, 1}, {5, 6}, {6, 2}};
        for (int[] e : edges) {
            float[] p = corners[e[0]], q = corners[e[1]];
            Fx.line(c, iso.sx(p[0], p[1], p[2]), iso.sy(p[0], p[1], p[2]), iso.sx(q[0], q[1], q[2]), iso.sy(q[0], q[1], q[2]),
                    1.2f, Fx.alpha(0xF4FCFF, 0.85f * a));
        }
        // bright edges / glints
        float g = (t * 0.6f) % 1f;
        Fx.glow(c, cx - scale * 0.3f + g * scale * 0.6f, cy - scale * 0.3f, 10, 0xFFFFFF, 0.35f * a, 1);
    }

    private void pickaxe(Canvas c, float t, float cx, float cy, float k, float a) {
        if (pickaxe == null || a < 0.05f) return;
        // swing into each hit: raised (+0.55 rad), down to -0.35 rad at the hit time
        float angle = 0.55f;
        int last = rareIdx >= 0 ? normal : normal - 1;
        for (int i = 0; i <= last; i++) {
            float ht = i < normal ? hitTime(i) : shatter;
            float d = t - ht;
            if (d > -SWING && d < 0.25f) {
                angle = d < 0 ? 0.55f - 0.9f * Fx.easeIn((d + SWING) / SWING) : -0.35f + 0.9f * Fx.smooth(d / 0.25f);
            }
        }
        float px = cx + 78 * k, py = cy + 44 * k;
        float appear = Fx.smooth((t - 0.5f) / 0.3f);
        c.push();
        c.translate(px + (1 - appear) * 40 * k, py);
        c.rotate(angle);
        c.scale(-3.6f * k, 3.6f * k);   // mirrored: the head points up-left at the block
        c.item(pickaxe, -1, -15);        // handle end (texel 1,15) at the pivot
        c.pop();
    }

    private float slotY(Canvas c, float k) {
        return c.height() - 30 * k;
    }

    private float slotX(Canvas c, int i, float k) {
        int n = loot.size();
        float s = 20 * k;
        return c.width() / 2f - n * s / 2 + i * s + s / 2;
    }

    private void hotbar(Canvas c, float t, float k, float fade) {
        float appear = Fx.easeOut((t - 0.4f) / 0.4f);
        if (appear <= 0 || fade < 0.05f) return;
        int n = loot.size();
        float y = slotY(c, k) + (1 - appear) * 30 * k;
        float x0 = slotX(c, 0, k) - 10 * k - k;
        c.push();
        c.translate(x0, y - 11 * k);
        c.scale(k, k);
        c.blit(HOTBAR, 0, 0, 0, 0, 1 + 20 * n, 22, 182, 22);
        c.blit(HOTBAR, 1 + 20 * n, 0, 181, 0, 1, 22, 182, 22);
        c.pop();
        for (int i = 0; i < normal; i++) {
            float ht = hitTime(i);
            if (t < ht) continue;
            RunScene.Loot l = loot.get(i);
            float p = Fx.clamp01((t - ht) / FLY);
            float sx = slotX(c, i, k), sy = y;
            float fx0 = c.width() / 2f + 14 * k, fy0 = c.height() * 0.40f - 4 * k;
            float x = fx0 + (sx - fx0) * Fx.easeIn(p);
            float yy = fy0 + (sy - fy0) * Fx.easeIn(p) - (float) Math.sin(p * Math.PI) * 30 * k;
            float sc = k * (p < 1 ? 1.4f - 0.4f * p : 1);
            if (l.icon() != null) {
                c.push();
                c.translate(x, yy);
                c.scale(sc, sc);
                c.item(l.icon(), -8, -8);
                c.pop();
            }
            if (p >= 1 && l.count() > 1) {
                String cnt = String.valueOf(l.count());
                c.push();
                c.translate(sx + 9 * k, y + 9 * k);
                c.scale(k, k);
                c.text(cnt, -c.textWidth(cnt), -8, Fx.alpha(0xFFFFFF, fade), true);
                c.pop();
            }
            // the name of the newest item above the bar
            float na = Fx.smooth((t - ht) / 0.15f) * (1 - Fx.smooth((t - ht - 0.9f) / 0.3f));
            boolean newest = i == normal - 1 || t < hitTime(i + 1);
            if (newest && na > 0.02f) {
                String nm = l.name() + (l.count() > 1 ? " x" + l.count() : "");
                Fx.centered(c, nm, c.width() / 2f, y - 22 * k, 0.8f * k, Fx.alpha(l.color(), na * fade), true);
            }
        }
        if (landed && rare() != null && rare().icon() != null) {
            float sx = slotX(c, loot.size() - 1, k);
            c.push();
            c.translate(sx, y);
            c.scale(k, k);
            c.item(rare().icon(), -8, -8);
            c.pop();
        }
    }
}

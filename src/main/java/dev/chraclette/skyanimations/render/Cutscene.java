package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropTier;
import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The rare-drop cutscene: the source's head lands on a chopping board, a cleaver chops it in
 * half, the drop's effect bursts out, then the item is revealed with rays, title, magic find and
 * price. Pure Java: give it a Canvas and the time since it started.
 */
public final class Cutscene {

    /** Things the cutscene needs from the game. */
    public interface Hooks {
        /** Item icon to show at the reveal (looked up lazily, so the item has time to arrive). */
        Object icon();

        /** RGB colour for the item name (rarity colour), or -1 to use the tier colour. */
        int nameColor();

        /** Price, or null while unknown. */
        Price price();

        void sound(Cue cue, float pitch, float volume);
    }

    public record Price(long coins, String label) {
    }

    public enum Cue { WHOOSH, THUMP, CHOP, SQUISH, SPLIT, MAGIC, REVEAL, LEVELUP, FIREWORK, TWINKLE, NOTE, BELL, TOTEM, POP,
        BUZZ, SUCK, SLURP, RATTLE, SPIT, FEVER, FEVER_END, TOAST, PEST_ALERT, PAGE, SLAP }

    private record SoundAt(float time, Cue cue, float pitch, float volume) {
    }

    // ---------------------------------------------------------------- timeline (seconds)
    private static final float LAND = 0.55f;
    private static final float STRIKE1 = 1.05f;
    private static final float STRIKE2 = 1.75f;
    private static final float SPLIT = 1.80f;
    private static final float FLASH = 3.15f;
    private static final float REVEAL_FULL = 3.25f;
    private static final float REVEAL_TIME = 4.6f;
    // pest (vacuum) scene
    private static final float VAC_IN = 0.75f;
    private static final float SUCK_START = 1.1f;
    private static final float SUCK_END = 2.05f;
    private static final float SPIT = 2.6f;

    private final Drop drop;
    private final Hooks hooks;
    private final boolean compact;
    private final boolean test;
    private final boolean music;
    private final boolean pestScene;
    private final CubeSkin skin;
    private final long seed;
    private final Particles fx;
    private final Random random;
    private final Iso iso = new Iso();
    private final List<SoundAt> sounds = new ArrayList<>();
    private final float reveal;
    private final float end;

    private float lastT = -1;
    private int soundIndex;
    private Object icon;
    private int nameColor = -1;
    private Price price;
    private float priceSince = -1;
    private float nextFirework;

    public Cutscene(Drop drop, Hooks hooks, boolean compact, boolean test, boolean music) {
        this(drop, hooks, compact, test, music, true);
    }

    public Cutscene(Drop drop, Hooks hooks, boolean compact, boolean test, boolean music, boolean vacuum) {
        this.drop = drop;
        this.hooks = hooks;
        this.compact = compact;
        this.test = test;
        this.music = music;
        this.skin = Skins.forDrop(drop);
        this.pestScene = vacuum && drop.category() == dev.chraclette.skyanimations.core.Category.PEST && !drop.fever();
        this.seed = drop.name().hashCode() * 31L + drop.tier().ordinal();
        this.fx = new Particles(seed);
        this.random = new Random(seed ^ 0x5DEECE66DL);
        this.reveal = compact ? 0.1f : REVEAL_FULL;
        this.end = reveal + (compact ? 3.4f : REVEAL_TIME);
        scheduleSounds();
    }

    public Drop drop() {
        return drop;
    }

    public float duration() {
        return end;
    }

    public boolean finished(float t) {
        return t >= end;
    }

    // ---------------------------------------------------------------- sounds

    private void at(float time, Cue cue, float pitch, float volume) {
        sounds.add(new SoundAt(time, cue, pitch, volume));
    }

    private static float note(int semitone) {
        return (float) Math.pow(2, (semitone - 12) / 12.0);
    }

    private void scheduleSounds() {
        int rank = drop.tier().rank;
        if (!compact && pestScene) {
            at(0.02f, Cue.WHOOSH, 0.8f, 0.6f);
            at(LAND - 0.05f, Cue.THUMP, 1.4f, 0.7f);
            at(0.3f, Cue.BUZZ, 1.6f, 0.8f);
            at(VAC_IN, Cue.WHOOSH, 0.7f, 0.8f);
            at(SUCK_START, Cue.SUCK, 1.0f, 0.9f);
            at(SUCK_START + 0.5f, Cue.SUCK, 1.3f, 0.7f);
            at(SUCK_END - 0.05f, Cue.SLURP, 1.0f, 1.0f);
            at(SUCK_END + 0.1f, Cue.RATTLE, 1.0f, 0.8f);
            at(SUCK_END + 0.3f, Cue.RATTLE, 1.3f, 0.8f);
            at(SPIT, Cue.SPIT, 1.0f, 1.0f);
            at(SPIT + 0.05f, Cue.MAGIC, 1.0f, 0.9f);
            at(FLASH - 0.05f, Cue.POP, 1.2f, 0.8f);
        } else if (!compact) {
            at(0.02f, Cue.WHOOSH, 0.8f, 0.6f);
            at(LAND - 0.05f, Cue.THUMP, 0.8f, 0.9f);
            at(STRIKE1 - 0.12f, Cue.WHOOSH, 1.3f, 0.7f);
            at(STRIKE1, Cue.CHOP, 1.0f, 1.0f);
            at(STRIKE2 - 0.12f, Cue.WHOOSH, 1.1f, 0.8f);
            at(STRIKE2, Cue.CHOP, 0.8f, 1.0f);
            at(STRIKE2 + 0.02f, Cue.SQUISH, 0.9f, 0.9f);
            at(SPLIT + 0.05f, Cue.SPLIT, 1.0f, 0.8f);
            at(SPLIT + 0.15f, Cue.MAGIC, 1.0f, 0.9f);
            at(FLASH - 0.05f, Cue.POP, 1.2f, 0.8f);
        }
        at(reveal, Cue.REVEAL, compact ? 1.25f : 1.0f, 0.9f);
        at(reveal + 0.35f, Cue.LEVELUP, compact ? 1.4f : 1.1f, 0.7f);
        if (music && (rank >= 3 || drop.tier() == DropTier.RARE)) {
            int[] melody = rank >= 4 ? new int[]{0, 4, 7, 12, 16, 19, 24} : new int[]{7, 12, 16, 19};
            for (int i = 0; i < melody.length; i++) {
                at(reveal + 0.1f + i * 0.085f, Cue.NOTE, note(melody[i]), 0.8f);
            }
            float chord = reveal + 0.15f + melody.length * 0.085f;
            at(chord, Cue.BELL, note(12), 0.7f);
            at(chord, Cue.BELL, note(16), 0.6f);
            at(chord, Cue.BELL, note(19), 0.6f);
        }
        if (rank >= 5) {
            at(reveal + 0.05f, Cue.TOTEM, 1.0f, 0.6f);
            for (int i = 0; i < 4; i++) {
                at(reveal + 0.6f + i * 0.55f, Cue.FIREWORK, 0.9f + i * 0.05f, 0.7f);
                at(reveal + 0.85f + i * 0.55f, Cue.TWINKLE, 1.0f, 0.6f);
            }
        }
        sounds.sort((a, b) -> Float.compare(a.time, b.time));
    }

    private void playSounds(float t) {
        while (soundIndex < sounds.size() && sounds.get(soundIndex).time <= t) {
            SoundAt s = sounds.get(soundIndex++);
            if (t - s.time < 0.5f) { // don't replay a backlog after a lag spike
                hooks.sound(s.cue, s.pitch, s.volume);
            }
        }
    }

    // ---------------------------------------------------------------- main render

    public void render(Canvas c, float t) {
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        playSounds(t);

        int w = c.width(), h = c.height();
        float fadeOut = 1 - Fx.smooth((t - (end - 0.5f)) / 0.5f);
        float dim = Fx.smooth(t / 0.3f) * fadeOut;

        // Dark backdrop with a tint of the effect colour.
        c.fill(0, 0, w, h, Fx.alpha(0x07060C, 0.62f * dim));

        if (!compact && t < FLASH + 0.05f) {
            if (pestScene) {
                renderPestScene(c, t, dt, w, h);
            } else {
                renderScene(c, t, dt, w, h);
            }
        }

        if (t >= reveal) {
            renderReveal(c, t - reveal, dt, w, h, fadeOut);
        } else {
            fx.update(dt);
            fx.render(c, 1);
        }

        // Flash between the chop and the reveal.
        if (!compact) {
            float f = t < FLASH ? Fx.smooth((t - (FLASH - 0.12f)) / 0.12f) : 1 - Fx.smooth((t - FLASH) / 0.3f);
            if (f > 0.01f) {
                c.fill(0, 0, w, h, Fx.alpha(Fx.brighten(drop.color(), 0.75f), 0.85f * f));
            }
        } else if (t < 0.35f) {
            float f = 1 - Fx.smooth(t / 0.35f);
            c.fill(0, 0, w, h, Fx.alpha(0xFFFFFF, 0.35f * f));
        }
    }

    // ---------------------------------------------------------------- the chopping scene

    private float shakeUntil, shakeStrength;

    private void renderScene(Canvas c, float t, float dt, int w, int h) {
        float base = Math.min(h * 0.25f, w * 0.17f);
        float zoom = 1 + 0.18f * Fx.smooth((t - SPLIT) / 1.2f);
        iso.scale = base * zoom;
        iso.yaw = (float) Math.toRadians(30 + 5 * Math.sin(t * 0.9));
        iso.pitch = (float) Math.toRadians(27);
        iso.cx = w / 2f;
        iso.cy = h * 0.70f;

        if (t >= shakeUntil) shakeStrength = 0;
        if (shakeStrength > 0) {
            float k = (shakeUntil - t) / 0.25f;
            iso.cx += (float) Math.sin(t * 90) * shakeStrength * k;
            iso.cy += (float) Math.cos(t * 77) * shakeStrength * k * 0.6f;
        }
        iso.begin();

        float sceneIn = Fx.backOut(t / 0.35f);
        float outline = Math.max(1f, iso.scale / 28f);

        // Soft coloured glow behind everything once the head opens.
        float open = Fx.smooth((t - SPLIT) / 0.5f);
        if (open > 0) {
            Fx.glow(c, iso.sx(0, 0.6f, 0), iso.sy(0, 0.6f, 0), iso.scale * (1.6f + open), drop.color(), open * 0.5f, 3);
        }

        // Board: a flat slab with its top at y = 0.
        float boardSize = 2.1f * sceneIn;
        if (boardSize > 0.05f) {
            Piece slab = new Piece(0, 0, 0, 1, 0.11f, 1, true);
            iso.box(c, Skins.BOARD, slab, Pose.identity(), 0, -0.11f * boardSize, 0, boardSize, outline, 0, seed);
            // groove around the edge of the board
            float g = boardSize * 0.42f;
            int groove = 0x40000000;
            iso.solidQuad(c, Pose.identity(), new float[]{-g, 0.002f, -g}, new float[]{2 * g, 0, 0},
                    new float[]{0, 0, 2 * g}, new float[]{0, 1, 0}, groove, 0, false);
            iso.solidQuad(c, Pose.identity(), new float[]{-g + 0.06f, 0.003f, -g + 0.06f}, new float[]{2 * g - 0.12f, 0, 0},
                    new float[]{0, 0, 2 * g - 0.12f}, new float[]{0, 1, 0}, 0x18FFFFFF, 0, false);
        }

        // Head falls onto the board.
        float fall = Fx.bounceOut(t / LAND);
        float headY = 3.2f * (1 - fall);
        float headH = skin.sy();
        float shadow = Fx.clamp01(1 - headY / 3.2f);
        if (shadow > 0 && t < SPLIT) {
            float s = 0.5f + 0.15f * shadow;
            iso.solidQuad(c, Pose.identity(), new float[]{-s, 0.004f, -s}, new float[]{2 * s, 0, 0},
                    new float[]{0, 0, 2 * s}, new float[]{0, 1, 0}, Fx.alpha(0, 0.35f * shadow), 0, false);
        }

        float[] blade = cleaver(t);
        int cracks = t >= STRIKE2 ? 2 : t >= STRIKE1 ? 1 : 0;

        if (t < SPLIT) {
            iso.box(c, skin, Piece.WHOLE, Pose.identity(), 0, headY, 0, 1, outline, cracks, seed);
            if (blade != null) drawCleaver(c, blade, headH, outline);
        } else {
            float s = Fx.easeOut((t - SPLIT) / 0.6f);
            float tilt = s * 1.2f;
            float sep = s * 0.42f;
            float halfW = skin.sx() / 2;
            Pose left = Pose.identity().pivot(-halfW, 0, 0).rollZ(tilt).move(-sep, 0, 0);
            Pose right = Pose.identity().pivot(halfW, 0, 0).rollZ(-tilt).move(sep, 0, 0);
            iso.box(c, skin, new Piece(0, 0, 0, 0.5f, 1, 1, false), left, 0, 0, 0, 1, outline, 0, seed);
            if (blade != null) drawCleaver(c, blade, 0, outline);
            iso.box(c, skin, new Piece(0.5f, 0, 0, 1, 1, 1, false), right, 0, 0, 0, 1, outline, 0, seed + 99);
        }

        // Impacts.
        float topX = iso.sx(0, headH, 0), topY = iso.sy(0, headH, 0);
        if (crossed(t, dt, STRIKE1)) {
            shakeUntil = t + 0.25f;
            shakeStrength = iso.scale * 0.06f;
            fx.burst(topX, topY, 18, iso.scale * 0.8f, iso.scale * 2.4f, iso.scale * 6, 0.7f, 2, Math.max(3, iso.scale / 12),
                    skin.flesh(), skin.fleshDark(), 0x2A2A2A);
        }
        if (crossed(t, dt, STRIKE2)) {
            shakeUntil = t + 0.3f;
            shakeStrength = iso.scale * 0.1f;
            fx.burst(topX, topY, 34, iso.scale * 1.2f, iso.scale * 3.4f, iso.scale * 7, 0.9f, 2, Math.max(3, iso.scale / 9),
                    skin.flesh(), skin.fleshDark(), 0x1E1E1E, Fx.brighten(skin.flesh(), 0.3f));
        }

        // The effect bursting out of the open head.
        if (t >= SPLIT) {
            float cx = iso.sx(0, headH * 0.45f, 0), cy = iso.sy(0, headH * 0.45f, 0);
            effect(c, t - SPLIT, dt, cx, cy, iso.scale, crossed(t, dt, SPLIT + 0.02f));
        }
    }


    // ---------------------------------------------------------------- the vacuum scene (pests)

    private void renderPestScene(Canvas c, float t, float dt, int w, int h) {
        float base = Math.min(h * 0.25f, w * 0.17f);
        iso.scale = base;
        iso.yaw = (float) Math.toRadians(30 + 4 * Math.sin(t * 0.9));
        iso.pitch = (float) Math.toRadians(27);
        iso.cx = w / 2f - base * 0.5f;
        iso.cy = h * 0.70f;
        if (t >= shakeUntil) shakeStrength = 0;
        if (shakeStrength > 0) {
            float k = (shakeUntil - t) / 0.25f;
            iso.cx += (float) Math.sin(t * 90) * shakeStrength * k;
        }
        iso.begin();
        float outline = Math.max(1f, iso.scale / 28f);
        float sceneIn = Fx.backOut(t / 0.35f);

        // glow once the vacuum spits
        float open = Fx.smooth((t - SPIT) / 0.4f);
        if (open > 0) {
            float[] tip = iso.screen(Pose.identity(), 0.55f, 0.55f, 0);
            Fx.glow(c, tip[0], tip[1], iso.scale * (1.4f + open), drop.color(), open * 0.5f, 3);
        }

        // board
        float boardSize = 2.1f * sceneIn;
        if (boardSize > 0.05f) {
            Piece slab = new Piece(0, 0, 0, 1, 0.11f, 1, true);
            iso.box(c, Skins.BOARD, slab, Pose.identity(), 0, -0.11f * boardSize, 0, boardSize, outline, 0, seed);
        }

        // vacuum: slides in from the right, nozzle pointing at the pest
        float vin = Fx.easeOut((t - VAC_IN) / 0.35f);
        float vx = 1.75f + 2.6f * (1 - vin);
        float rattle = 0;
        if (t > SUCK_END && t < SPIT + 0.15f) {
            rattle = (float) Math.sin(t * 70) * 0.05f * (1 - Fx.smooth((t - SUCK_END) / (SPIT + 0.15f - SUCK_END)));
        }
        float bulge = t > SUCK_END && t < SPIT ? 0.08f * (float) Math.sin((t - SUCK_END) / (SPIT - SUCK_END) * Math.PI) : 0;
        float recoil = t >= SPIT ? 0.15f * (1 - Fx.easeOut((t - SPIT) / 0.4f)) : 0;
        float vs = 1.4f;
        Pose vac = Pose.identity().pivot(vx, 0, 0).scale(vs, vs, vs).rollZ(rattle).move(recoil, 0, 0);

        // pest position: hovers and zig-zags, then gets pulled into the nozzle
        float fall = Fx.bounceOut(t / LAND);
        float hover = Skins.flies(drop.pest()) ? 0.25f : 0f;
        float px = (float) Math.sin(t * 5.3f) * 0.25f * Fx.smooth((t - LAND) / 0.3f);
        float pz = (float) Math.cos(t * 3.7f) * 0.18f * Fx.smooth((t - LAND) / 0.3f);
        float py = 3.2f * (1 - fall) + hover + (float) Math.abs(Math.sin(t * 9)) * 0.08f;
        float pull = Fx.easeIn((t - SUCK_START) / (SUCK_END - SUCK_START));
        float nozzleX = vx - 0.95f, nozzleY = 0.45f;
        float worldNozzleX = vx - 0.95f * vs + recoil, worldNozzleY = nozzleY * vs;
        px = px + (worldNozzleX - px) * pull;
        py = py + (worldNozzleY - 0.2f - py) * pull;
        pz = pz * (1 - pull);
        float struggle = t > SUCK_START && t < SUCK_END ? (float) Math.sin(t * 60) * 0.06f * (1 - pull) : 0;
        float size = (1 - pull * 0.9f) * 0.75f;
        float stretch = 1 + pull * 1.4f;

        if (vin > 0.01f) {
            drawVacuum(c, vac, vx, outline, bulge);
        }
        if (t < SUCK_END) {
            Pose pp = Pose.identity().pivot(px, py + size / 2, pz)
                    .scale(stretch, 1 / (1 + pull * 0.6f), 1 / (1 + pull * 0.6f))
                    .rollZ(struggle + pull * 0.3f);
            iso.box(c, skin, Piece.WHOLE, pp, px, py, pz, size, outline, 0, seed);
            if (Skins.flies(drop.pest())) {
                drawWings(c, pp, px, py + size, pz, size, t);
            }
            if (t < SUCK_START) {
                // buzzing lines
                float[] sp = iso.screen(Pose.identity(), px, py + size, pz);
                if (((int) (t * 12)) % 2 == 0) {
                    Fx.line(c, sp[0] - iso.scale * 0.5f, sp[1] - iso.scale * 0.2f, sp[0] - iso.scale * 0.35f,
                            sp[1] - iso.scale * 0.25f, 1, 0xC0FFFFFF);
                    Fx.line(c, sp[0] + iso.scale * 0.35f, sp[1] - iso.scale * 0.3f, sp[0] + iso.scale * 0.5f,
                            sp[1] - iso.scale * 0.22f, 1, 0xC0FFFFFF);
                }
            }
        }

        // suction streaks flowing into the nozzle
        if (t > SUCK_START - 0.1f && t < SUCK_END + 0.2f) {
            float[] tip = iso.screen(vac, nozzleX, nozzleY, 0);
            Random r = fx.random();
            spawn(dt, 55, () -> {
                double ang = Math.PI * (0.6 + r.nextDouble() * 0.8);
                float dist = iso.scale * (1.2f + r.nextFloat() * 0.8f);
                P p = fx.add(tip[0] + (float) Math.cos(ang) * dist, tip[1] + (float) Math.sin(ang) * dist * 0.6f,
                        -(float) Math.cos(ang) * dist * 2.4f, -(float) Math.sin(ang) * dist * 1.45f, 0.4f,
                        Math.max(2, iso.scale / 12), r.nextBoolean() ? 0xFFFFFF : 0xBFE8FF);
                p.kind = Kind.SHARD;
                p.rot = (float) ang;
            });
        }
        if (crossed(t, dt, SUCK_END - 0.02f)) {
            float[] tip = iso.screen(vac, nozzleX, nozzleY, 0);
            fx.burst(tip[0], tip[1], 14, iso.scale * 0.5f, iso.scale * 1.4f, iso.scale * 3, 0.5f, 2,
                    Math.max(2, iso.scale / 12), skin.flesh(), skin.fleshDark());
            shakeUntil = t + 0.2f;
            shakeStrength = iso.scale * 0.04f;
        }

        // spit: the drop's effect bursts out of the nozzle
        if (t >= SPIT) {
            float[] tip = iso.screen(vac, nozzleX - 0.15f, nozzleY + 0.05f, 0);
            if (crossed(t, dt, SPIT)) {
                shakeUntil = t + 0.3f;
                shakeStrength = iso.scale * 0.08f;
            }
            effect(c, t - SPIT, dt, tip[0], tip[1], iso.scale, crossed(t, dt, SPIT));
        }
    }

    /** InfiniVacuum-ish: green tank, grey hose, wide nozzle pointing left. */
    private void drawVacuum(Canvas c, Pose vac, float vx, float outline, float bulge) {
        float b = bulge;
        // wheels
        iso.solidBox(c, vac, vx - 0.05f, 0, -0.42f, vx + 0.75f, 0.12f, 0.42f, 0x2A2A2A, 1, outline);
        // tank
        iso.solidBox(c, vac, vx - b, 0.1f, -0.33f - b, vx + 0.7f + b, 0.95f + b, 0.33f + b, 0x4FA83A, 1, outline);
        // stripe + gauge
        iso.solidBox(c, vac, vx - b - 0.01f, 0.55f, -0.34f - b, vx + 0.71f + b, 0.65f, 0.34f + b, 0xE8C440, 1, 0);
        iso.solidBox(c, vac, vx + 0.2f, 0.95f + b, -0.12f, vx + 0.5f, 1.08f + b, 0.12f, 0x9AA3AD, 1, outline);
        // hose to nozzle
        iso.solidBox(c, vac, vx - 0.6f, 0.4f, -0.09f, vx, 0.55f, 0.09f, 0x6E747C, 1, outline);
        // nozzle (wide mouth)
        iso.solidBox(c, vac, vx - 0.95f, 0.3f, -0.2f, vx - 0.6f, 0.62f, 0.2f, 0x3C4148, 1, outline);
        iso.solidBox(c, vac, vx - 0.97f, 0.34f, -0.16f, vx - 0.93f, 0.58f, 0.16f, 0x111111, 1, 0);
    }

    private void drawWings(Canvas c, Pose pp, float x, float y, float z, float size, float t) {
        float flap = (float) Math.sin(t * 60) * 0.7f;
        int col = 0xA0E8F4FF;
        for (int side = -1; side <= 1; side += 2) {
            Pose wing = Pose.identity().pivot(x, y, z).rollZ(side * (0.4f + flap * 0.5f));
            iso.solidQuad(c, wing, new float[]{x, y, z - size * 0.3f}, new float[]{side * size * 0.8f, 0, 0},
                    new float[]{0, 0, size * 0.6f}, new float[]{0, 1, 0}, col, 0, false);
        }
    }

    private static boolean crossed(float t, float dt, float mark) {
        return t >= mark && t - dt < mark;
    }

    /** Cleaver keyframes: time, blade bottom height, swing angle, alpha. */
    private static final float[][] KEYS = {
            {0.55f, 3.0f, -0.55f, 0},
            {0.85f, 2.05f, -0.55f, 1},
            {0.93f, 2.2f, -0.75f, 1},
            {STRIKE1, 0.74f, 0f, 1},
            {1.22f, 0.74f, 0f, 1},
            {1.50f, 2.1f, -0.7f, 1},
            {1.62f, 2.2f, -0.75f, 1},
            {STRIKE2, 0.52f, 0f, 1},
            {1.98f, 0.0f, 0.05f, 1},
            {2.45f, 0.0f, 0.05f, 1},
            {2.80f, 0.0f, 0.05f, 0},
    };

    /** Returns {bladeBottom, angle, alpha} or null when hidden. */
    private static float[] cleaver(float t) {
        if (t < KEYS[0][0] || t > KEYS[KEYS.length - 1][0]) return null;
        for (int i = 0; i < KEYS.length - 1; i++) {
            float[] a = KEYS[i], b = KEYS[i + 1];
            if (t >= a[0] && t <= b[0]) {
                float k = (t - a[0]) / Math.max(0.0001f, b[0] - a[0]);
                // swings down accelerate, lifts ease
                k = b[1] < a[1] - 0.5f ? Fx.easeIn(k) : Fx.smooth(k);
                return new float[]{
                        a[1] + (b[1] - a[1]) * k,
                        a[2] + (b[2] - a[2]) * k,
                        a[3] + (b[3] - a[3]) * k};
            }
        }
        return null;
    }

    /** The cleaver is a flat blade in the split plane (x = 0), with a wooden handle. */
    private void drawCleaver(Canvas c, float[] k, float minY, float outline) {
        float yb = k[0], angle = k[1], a = k[2];
        if (a <= 0.01f) return;
        float bladeH = 0.62f, zFront = -0.72f, zBack = 0.42f;
        float visibleBottom = Math.max(yb, minY);
        Pose pose = Pose.identity().pivot(0, yb + 0.45f, 1.05f).pitchX(angle);
        float[] n = {1, 0, 0};
        int steel = Fx.alpha(0xC9CED6, a), edge = Fx.alpha(0xF2F5F8, a), spine = Fx.alpha(0x7D838C, a);
        int wood = Fx.alpha(0x6B4423, a), woodDark = Fx.alpha(0x4A2E17, a), rivet = Fx.alpha(0xB8BCC2, a);
        float top = yb + bladeH;
        if (top > visibleBottom) {
            // blade body (only the part above the head surface is drawn while it is embedded)
            iso.solidQuad(c, pose, new float[]{0, top, zFront}, new float[]{0, 0, zBack - zFront},
                    new float[]{0, visibleBottom - top, 0}, n, steel, outline, false);
            iso.solidQuad(c, pose, new float[]{0, top, zFront}, new float[]{0, 0, zBack - zFront},
                    new float[]{0, -0.09f, 0}, n, spine, 0, false);
            if (visibleBottom <= yb + 0.001f) {
                iso.solidQuad(c, pose, new float[]{0, yb + 0.07f, zFront}, new float[]{0, 0, zBack - zFront},
                        new float[]{0, -0.07f, 0}, n, edge, 0, false);
            }
            // a little shine
            iso.solidQuad(c, pose, new float[]{0, top - 0.16f, zFront + 0.12f}, new float[]{0, 0, 0.32f},
                    new float[]{0, -0.06f, 0}, n, Fx.alpha(0xFFFFFF, 0.55f * a), 0, false);
        }
        // handle
        float hy = yb + 0.38f;
        iso.solidQuad(c, pose, new float[]{0, hy + 0.16f, zBack}, new float[]{0, 0, 0.66f},
                new float[]{0, -0.16f, 0}, n, wood, outline, false);
        iso.solidQuad(c, pose, new float[]{0, hy + 0.05f, zBack}, new float[]{0, 0, 0.66f},
                new float[]{0, -0.05f, 0}, n, woodDark, 0, false);
        for (int i = 0; i < 2; i++) {
            float z = zBack + 0.16f + i * 0.3f;
            iso.solidQuad(c, pose, new float[]{0, hy + 0.11f, z}, new float[]{0, 0, 0.06f},
                    new float[]{0, -0.06f, 0}, n, rivet, 0, false);
        }
    }

    // ---------------------------------------------------------------- effects

    /** tau = seconds since the head split. (cx,cy) = where it bursts from. s = scene scale. */
    private void effect(Canvas c, float tau, float dt, float cx, float cy, float s, boolean first) {
        Effect e = drop.effect();
        int col = drop.color();
        int light = Fx.brighten(col, 0.55f);
        Random r = fx.random();
        float grow = Fx.easeOut(tau / 0.6f);
        float fade = 1 - Fx.smooth((tau - 1.1f) / 0.3f);
        boolean big = drop.tier().rank >= 4;

        if (first) {
            fx.burst(cx, cy, big ? 40 : 24, s * 0.8f, s * 2.6f, s * 2.5f, 1.0f, 2, Math.max(2, s / 14), col, light, 0xFFFFFF);
        }

        switch (e) {
            case ARCANE -> {
                beam(c, cx, cy, s, grow * fade, col);
                spawn(dt, 26, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 1.4f, cy - r.nextFloat() * s * 0.4f,
                            (r.nextFloat() - 0.5f) * s * 0.2f, -s * (0.7f + r.nextFloat() * 0.9f), 1.3f,
                            Math.max(1f, s / 30f) * (0.8f + r.nextFloat() * 0.6f), r.nextBoolean() ? light : col);
                    p.kind = Kind.GLYPH;
                    p.glyph = Particles.RUNES[r.nextInt(Particles.RUNES.length)];
                    p.fadeIn = true;
                });
            }
            case GOLDEN -> {
                threads(c, cx, cy, s, tau, grow * fade, col, light);
                constellation(c, cx, cy - s * 1.55f, s, tau, fade);
                spawn(dt, 14, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 2, cy - r.nextFloat() * s * 2, 0, -s * 0.15f, 0.9f,
                            Math.max(2, s / 14), 0xFFF6C8);
                    p.kind = Kind.SPARKLE;
                });
            }
            case DYE -> helix(c, cx, cy, s, tau, grow * fade, col);
            case BLOOD -> {
                if (first) {
                    P ring = fx.add(cx, cy, 0, 0, 0.7f, s * 2.6f, Fx.brighten(col, 0.2f));
                    ring.kind = Kind.RING;
                }
                spawn(dt, 30, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 0.6f, cy, (r.nextFloat() - 0.5f) * s * 2.2f,
                            -s * (1.2f + r.nextFloat() * 1.8f), 1.0f, Math.max(2, s / 12), r.nextInt(3) == 0 ? 0x5A0A12 : col);
                    p.ay = s * 4.5f;
                });
            }
            case VOID -> {
                Fx.disc(c, cx, cy, s * 0.45f * grow * fade, 2, Fx.alpha(0x0A0414, 0.85f * fade));
                Fx.ring(c, cx, cy, s * 0.45f * grow, Math.max(1, Math.round(s / 20)), Fx.alpha(col, 0.9f * fade));
                spawn(dt, 50, () -> {
                    double ang = r.nextDouble() * Math.PI * 2;
                    float dist = s * (1.6f + r.nextFloat());
                    P p = fx.add(cx + (float) Math.cos(ang) * dist, cy + (float) Math.sin(ang) * dist * 0.7f,
                            -(float) Math.cos(ang) * dist * 1.5f, -(float) Math.sin(ang) * dist * 1.05f, 0.65f,
                            Math.max(2, s / 14), r.nextBoolean() ? col : 0x1A0B2E);
                    p.fadeIn = true;
                });
            }
            case VENOM -> {
                if (first) {
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.PI + i * Math.PI / 9;
                        float len = s * (1.8f + r.nextFloat());
                        webs.add(new float[]{(float) Math.cos(ang) * len, (float) Math.sin(ang) * len});
                    }
                }
                for (float[] wv : webs) {
                    Fx.line(c, cx, cy, cx + wv[0] * grow, cy + wv[1] * grow, Math.max(1, s / 35), Fx.alpha(0xEAF5E0, 0.6f * fade));
                }
                spawn(dt, 28, () -> {
                    P p = fx.add(cx, cy, (r.nextFloat() - 0.5f) * s * 2.4f, -s * (1.0f + r.nextFloat() * 1.6f), 1.1f,
                            Math.max(2, s / 12), r.nextBoolean() ? col : 0x3F7A1C);
                    p.ay = s * 4f;
                });
            }
            case INFERNO -> spawn(dt, 70, () -> {
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 1.1f, cy, (r.nextFloat() - 0.5f) * s * 0.3f,
                        -s * (1.2f + r.nextFloat() * 1.4f), 0.9f, Math.max(2, s / 10) * (0.6f + r.nextFloat()),
                        r.nextBoolean() ? 0xFFE15A : 0xFF7A1A);
                p.toColor = 0x3A3030;
            });
            case BONE -> {
                spawn(dt, 30, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s, cy, (r.nextFloat() - 0.5f) * s * 0.5f,
                            -s * (0.8f + r.nextFloat()), 1.2f, Math.max(2, s / 11), 0x5CE1FF);
                    p.toColor = 0x1B4B7A;
                });
                if (first) {
                    for (int i = 0; i < 16; i++) {
                        P p = fx.add(cx, cy, (r.nextFloat() - 0.5f) * s * 3, -s * (1 + r.nextFloat() * 2), 1.3f,
                                Math.max(2, s / 9), 0xE9E4D4);
                        p.kind = Kind.SHARD;
                        p.ay = s * 4f;
                        p.vrot = (r.nextFloat() - 0.5f) * 14;
                    }
                }
            }
            case HARVEST -> {
                spawn(dt, 34, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 0.8f, cy, (r.nextFloat() - 0.5f) * s * 2.6f,
                            -s * (1.4f + r.nextFloat() * 1.6f), 1.4f, Math.max(2, s / 11),
                            r.nextInt(3) == 0 ? 0xFFD24A : r.nextBoolean() ? 0x6FCF3B : 0x3E9A2A);
                    p.ay = s * 2.2f;
                    p.ax = (r.nextFloat() - 0.5f) * s;
                    p.kind = r.nextInt(3) == 0 ? Kind.SQUARE : Kind.SHARD;
                    p.vrot = (r.nextFloat() - 0.5f) * 8;
                });
            }
            case OCEAN -> {
                spawn(dt, 26, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 1.2f, cy, (r.nextFloat() - 0.5f) * s * 0.4f,
                            -s * (0.8f + r.nextFloat()), 1.3f, Math.max(3, s / 6), 0xBDF6FF);
                    p.kind = Kind.RING;
                    p.ax = (r.nextFloat() - 0.5f) * s;
                });
                if (first) {
                    fx.burst(cx, cy, 30, s, s * 3, s * 5, 1.0f, 2, Math.max(2, s / 12), col, 0xFFFFFF, 0x1C7FA6);
                }
            }
            case STARS -> {
                spawn(dt, 22, () -> {
                    P p = fx.add(cx + (r.nextFloat() - 0.5f) * s * 2.4f, cy - r.nextFloat() * s * 2.2f,
                            (r.nextFloat() - 0.5f) * s * 0.3f, -s * 0.2f, 0.8f, Math.max(2, s / 12),
                            r.nextBoolean() ? 0xFFFFFF : col);
                    p.kind = Kind.SPARKLE;
                });
                if (crossedTau(tau, dt, 0.35f) || crossedTau(tau, dt, 0.75f)) {
                    float fxX = cx + (r.nextFloat() - 0.5f) * s * 2.5f, fyY = cy - s * (1.2f + r.nextFloat());
                    fx.burst(fxX, fyY, 26, s * 0.8f, s * 1.6f, s * 1.2f, 0.9f, 2, Math.max(2, s / 14),
                            col, light, Themes2.random(r));
                }
            }
        }
    }

    private final List<float[]> webs = new ArrayList<>();
    private float spawnCarry;

    private void spawn(float dt, float perSecond, Runnable spawner) {
        spawnCarry += perSecond * dt;
        while (spawnCarry >= 1) {
            spawnCarry -= 1;
            spawner.run();
        }
    }

    private static boolean crossedTau(float tau, float dt, float mark) {
        return tau >= mark && tau - dt < mark;
    }

    /** Vertical light column. */
    private static void beam(Canvas c, float cx, float cy, float s, float a, int col) {
        if (a <= 0.01f) return;
        float height = s * 2.6f * a;
        for (int i = 0; i < 4; i++) {
            float wdt = s * (0.55f - i * 0.12f);
            int alpha = (int) (255 * a * (0.18f + i * 0.12f));
            int rgb = i == 3 ? 0xFFFFFF : Fx.brighten(col, i * 0.2f);
            c.fill(Math.round(cx - wdt / 2), Math.round(cy - height), Math.round(cx + wdt / 2), Math.round(cy),
                    (alpha << 24) | rgb);
        }
        Fx.glow(c, cx, cy, s * 0.9f * a, col, a, 2);
    }

    /** Golden threads curling upwards. */
    private static void threads(Canvas c, float cx, float cy, float s, float tau, float a, int col, int light) {
        if (a <= 0.01f) return;
        int px = Math.max(1, Math.round(s / 22));
        for (int j = 0; j < 9; j++) {
            float phase = j * 0.9f;
            float spread = (j - 4) * s * 0.12f;
            float len = s * (1.7f + (j % 3) * 0.3f) * a;
            for (int i = 0; i < 40; i++) {
                float k = i / 40f;
                float y = cy - k * len;
                float x = cx + spread * k * 1.6f + (float) Math.sin(k * 7 + phase + tau * 5) * s * 0.18f * k;
                int color = Fx.alpha(i % 4 == 0 ? light : col, a * (1 - k * 0.6f));
                c.fill(Math.round(x), Math.round(y), Math.round(x) + px, Math.round(y) + px, color);
            }
        }
    }

    /** A little star sign that draws itself above the board (a sheep, if you squint). */
    private static final float[][] STARS = {
            {-1.0f, 0.1f}, {-0.45f, -0.25f}, {0.2f, -0.3f}, {0.75f, -0.15f}, {1.05f, -0.45f}, {1.3f, -0.2f},
            {0.75f, 0.35f}, {-0.6f, 0.4f}};
    private static final int[][] LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {3, 6}, {1, 7}};

    private static void constellation(Canvas c, float cx, float cy, float s, float tau, float fade) {
        float a = Fx.smooth((tau - 0.3f) / 0.4f) * fade;
        if (a <= 0.01f) return;
        float scale = s * 0.95f;
        for (int i = 0; i < LINKS.length; i++) {
            float k = Fx.clamp01((tau - 0.35f - i * 0.07f) / 0.18f);
            if (k <= 0) continue;
            float[] p = STARS[LINKS[i][0]], q = STARS[LINKS[i][1]];
            float x1 = cx + p[0] * scale, y1 = cy + p[1] * scale;
            float x2 = x1 + (q[0] - p[0]) * scale * k, y2 = y1 + (q[1] - p[1]) * scale * k;
            Fx.line(c, x1, y1, x2, y2, Math.max(1, s / 30), Fx.alpha(0xFFE89A, a * 0.85f));
        }
        for (float[] p : STARS) {
            float x = cx + p[0] * scale, y = cy + p[1] * scale;
            int sz = Math.max(2, Math.round(s / 12));
            c.fill(Math.round(x) - sz, Math.round(y), Math.round(x) + sz + 1, Math.round(y) + 1, Fx.alpha(0xFFF6C8, a));
            c.fill(Math.round(x), Math.round(y) - sz, Math.round(x) + 1, Math.round(y) + sz + 1, Fx.alpha(0xFFF6C8, a));
            c.fill(Math.round(x) - 1, Math.round(y) - 1, Math.round(x) + 2, Math.round(y) + 2, Fx.alpha(0xFFFFFF, a));
        }
    }

    /** Dye: a spiral column of coloured pixels. */
    private static void helix(Canvas c, float cx, float cy, float s, float tau, float a, int col) {
        if (a <= 0.01f) return;
        int n = 90;
        float height = s * 2.3f * a;
        for (int i = 0; i < n; i++) {
            float k = ((i / (float) n) + tau * 0.35f) % 1f;
            float ang = k * 14 + tau * 6 + i * 0.07f;
            float radius = s * (0.36f + 0.16f * (float) Math.sin(k * 6 + tau * 2));
            float depth = (float) Math.sin(ang);
            float x = cx + (float) Math.cos(ang) * radius;
            float y = cy - k * height;
            int size = Math.max(2, Math.round(s / 14 * (1.1f + 0.5f * depth)));
            int rgb = depth > 0 ? Fx.brighten(col, 0.35f) : Fx.darken(col, 0.25f);
            c.fill(Math.round(x) - size / 2, Math.round(y) - size / 2, Math.round(x) + (size + 1) / 2,
                    Math.round(y) + (size + 1) / 2, Fx.alpha(rgb, a * (1 - k * 0.5f)));
        }
    }

    // ---------------------------------------------------------------- reveal

    private void renderReveal(Canvas c, float rt, float dt, int w, int h, float fadeOut) {
        if (icon == null) {
            icon = hooks.icon();
            nameColor = hooks.nameColor();
        }
        Price p = hooks.price();
        if (p != null && price == null) {
            price = p;
            priceSince = rt;
        }

        float k = Math.min(1.8f, Math.max(0.85f, h / 250f));
        float cx = w / 2f, cy = h * 0.42f;
        float in = Fx.smooth(rt / 0.35f) * fadeOut;
        int col = drop.color();
        DropTier tier = drop.tier();
        boolean big = tier.rank >= 4;

        // rays + glow
        float rayLen = Math.min(w, h) * 0.62f;
        int rayColor = drop.effect() == Effect.DYE ? col : Fx.lerpColor(col, tier.color, 0.35f);
        Fx.glow(c, cx, cy, rayLen * 0.42f * Fx.easeOut(rt / 0.5f), rayColor, in * 0.9f, 3);
        Fx.rays(c, cx, cy, rayLen, big ? 16 : 12, rt * 0.35f, rayColor, in * (big ? 1.25f : 1f));
        Fx.disc(c, cx, cy, 30 * k * Fx.easeOut(rt / 0.4f), 2, Fx.alpha(Fx.brighten(rayColor, 0.6f), 0.18f * in));

        // ambient particles around the item
        ambient(c, rt, dt, cx, cy, k, in, rayColor);

        // fireworks for the biggest drops
        if (big && tier.rank >= 5 && rt > 0.5f && rt < REVEAL_TIME - 0.8f && rt >= nextFirework) {
            nextFirework = rt + 0.45f + random.nextFloat() * 0.3f;
            float fxX = cx + (random.nextFloat() - 0.5f) * w * 0.7f;
            float fxY = cy + (random.nextFloat() - 0.6f) * h * 0.5f;
            int c1 = Themes2.random(random);
            fx.burst(fxX, fxY, 40, 30 * k, 70 * k, 25 * k, 1.1f, 2, 3 * k, c1, Fx.brighten(c1, 0.5f), 0xFFFFFF);
        }
        fx.update(dt);
        fx.render(c, fadeOut);

        // the item
        float pop = Fx.backOut(rt / 0.4f);
        float bob = (float) Math.sin(rt * 2.4f) * 2.5f * k;
        float itemScale = 4.2f * k * pop;
        if (itemScale > 0.05f && icon != null) {
            c.push();
            c.translate(cx, cy + bob);
            c.scale(itemScale, itemScale);
            c.item(icon, -8, -8);
            c.pop();
        }

        // text
        float textY = cy + 40 * k;
        float titleA = Fx.smooth((rt - 0.12f) / 0.25f) * fadeOut;
        if (titleA > 0.02f) {
            if (tier.rainbow()) {
                Fx.rainbow(c, tier.label, cx, textY, 1.25f * k, rt, titleA, true);
            } else {
                Fx.centered(c, tier.label, cx, textY, 1.25f * k, Fx.alpha(tier.color, titleA), true);
            }
        }
        // name types itself out
        String name = drop.displayName();
        int chars = Math.min(name.length(), (int) Math.max(0, (rt - 0.25f) * 28));
        if (chars > 0) {
            int nc = nameColor >= 0 ? nameColor : tier.color;
            Fx.centered(c, name.substring(0, chars), cx, textY + 13 * k, 1.1f * k, Fx.alpha(nc, fadeOut), false);
        }
        float infoA = Fx.smooth((rt - 0.6f) / 0.3f) * fadeOut;
        if (infoA > 0.02f) {
            StringBuilder info = new StringBuilder(drop.source());
            if (!drop.bonus().isEmpty()) info.append(" · ").append(drop.bonus());
            if (test) info.append(" · test run");
            Fx.centered(c, info.toString(), cx, textY + 26 * k, 0.75f * k, Fx.alpha(0xB8B8C8, infoA), false);
        }
        if (price != null && price.coins() > 0) {
            float pt = rt - Math.max(0.8f, priceSince);
            float pa = Fx.smooth(pt / 0.25f) * fadeOut;
            if (pa > 0.02f) {
                long shown = Math.round(price.coins() * Fx.easeOut(pt / 1.1f));
                String coins = NumberFormat.getIntegerInstance(Locale.US).format(shown) + " coins";
                Fx.centered(c, coins, cx, textY + 36 * k, 1.0f * k, Fx.alpha(0xFFD24A, pa), true);
                Fx.centered(c, price.label(), cx, textY + 47 * k, 0.65f * k, Fx.alpha(0x9A9AA8, pa), false);
            }
        }
    }

    private void ambient(Canvas c, float rt, float dt, float cx, float cy, float k, float a, int col) {
        Random r = fx.random();
        float reach = 90 * k;
        switch (drop.effect()) {
            case ARCANE -> spawn(dt, 8, () -> {
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * reach * 2, cy + reach * 0.6f, 0, -25 * k, 2.2f, 1.1f * k,
                        r.nextBoolean() ? col : Fx.brighten(col, 0.5f));
                p.kind = Kind.GLYPH;
                p.glyph = Particles.RUNES[r.nextInt(Particles.RUNES.length)];
                p.fadeIn = true;
            });
            case DYE -> spawn(dt, 6, () -> {
                // drips falling from the top of the screen
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * c.width() * 0.8f, 0, 0, 40 * k, 3f, 3 * k, col);
                p.ay = 30 * k;
                p.fadeIn = true;
            });
            case GOLDEN, STARS -> spawn(dt, 10, () -> {
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * reach * 2.4f, cy + (r.nextFloat() - 0.5f) * reach * 1.6f,
                        0, -6 * k, 1.1f, 2.5f * k, r.nextBoolean() ? 0xFFF6C8 : col);
                p.kind = Kind.SPARKLE;
            });
            case INFERNO -> spawn(dt, 14, () -> {
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * reach * 2, cy + reach * 0.9f, 0, -45 * k, 1.6f, 3 * k,
                        r.nextBoolean() ? 0xFFE15A : 0xFF7A1A);
                p.toColor = 0x3A3030;
            });
            case OCEAN -> spawn(dt, 6, () -> {
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * reach * 2, cy + reach, 0, -30 * k, 2.2f, 7 * k, 0xBDF6FF);
                p.kind = Kind.RING;
            });
            default -> spawn(dt, 9, () -> {
                // pixel motes drifting around, like the pink dust in the reference video
                P p = fx.add(cx + (r.nextFloat() - 0.5f) * c.width() * 0.9f, cy + (r.nextFloat() - 0.5f) * c.height() * 0.8f,
                        (r.nextFloat() - 0.5f) * 8 * k, (r.nextFloat() - 0.5f) * 8 * k, 2.5f, 3 * k,
                        Fx.lerpColor(col, 0xFF7FB0, 0.4f));
                p.fadeIn = true;
            });
        }
    }

    /** Firework colour picker kept separate so the class above stays readable. */
    private static final class Themes2 {
        private static final int[] COLORS = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};

        static int random(Random r) {
            return COLORS[r.nextInt(COLORS.length)];
        }
    }
}

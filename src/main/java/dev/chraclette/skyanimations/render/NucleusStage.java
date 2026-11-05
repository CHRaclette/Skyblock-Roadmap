package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;
import dev.chraclette.skyanimations.render.Skins.Face;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Crystal Nucleus in 3D, before the loot comes: a small stone island in the Crystal Hollows with five
 * pedestals. Jade, Amber, Amethyst, Sapphire and Topaz are placed one by one (like you do in game), each
 * one beams its colour into the middle, the beams mix into one bright orb and it bursts - the loot bundle.
 */
final class NucleusStage {

    interface Sound {
        void play(Cue cue, float pitch, float volume);
    }

    /** How long the stage plays before the loot starts. */
    static final float LENGTH = 3.35f;
    static final float BURST = 3.0f;
    private static final float GEM0 = 0.6f, GEM_STEP = 0.32f, BEAMS = GEM0 + 5 * GEM_STEP + 0.05f;

    private static final String[] GEM_NAMES = {"Jade", "Amber", "Amethyst", "Sapphire", "Topaz"};
    private static final String[] GLASS = {"lime", "orange", "purple", "light_blue", "yellow"};
    private static final int[] GEM_COLORS = {0x7EE03A, 0xFF9A1F, 0xB050FF, 0x3AA8FF, 0xFFE03A};

    private static Face b(String block) {
        return new Face("textures/block/" + block + ".png", 16, 16, 0, 0, 16, 16);
    }

    private static CubeSkin cube(String top, String side, int flesh) {
        Face t = b(top), s = b(side);
        return new CubeSkin(t, t, s, s, s, s, null, 1, 1, 1, flesh, Fx.darken(flesh, 0.4f));
    }

    private static CubeSkin slab(String block, int flesh) {
        Face t = b(block);
        Face s = new Face("textures/block/" + block + ".png", 16, 16, 0, 0, 16, 4);
        return new CubeSkin(t, t, s, s, s, s, null, 1, 0.25f, 1, flesh, Fx.darken(flesh, 0.4f));
    }

    private static final CubeSkin STONE = slab("stone", 0x7F7F7F), ANDESITE = slab("polished_andesite", 0x8A8A86),
            BRICKS = cube("stone_bricks", "stone_bricks", 0x7A7A7A), CHISELED = cube("chiseled_stone_bricks", "chiseled_stone_bricks", 0x777777);
    private static final CubeSkin[] GEMS = new CubeSkin[5];

    static {
        for (int i = 0; i < 5; i++) GEMS[i] = cube(GLASS[i] + "_stained_glass", GLASS[i] + "_stained_glass", GEM_COLORS[i]);
    }

    private interface Draw {
        void run();
    }

    private final Iso iso = new Iso();
    private final Random random = new Random(5);
    private int gemsPlaced;
    private boolean beamsPlayed, burst;

    /** Where the orb (and then the loot) is on screen, after the last draw. */
    float orbX, orbY;

    void draw(Canvas c, float t, float cx, float cy, float k, float fade, Particles fx, Sound sound) {
        float out = 1 - Fx.smooth((t - BURST) / (LENGTH - BURST + 0.1f));
        float a = fade * out;
        float in = Fx.easeOut(Fx.clamp01(t / 0.55f));
        float shake = t > BEAMS && t < BURST ? (float) Math.sin(t * 75) * 1.3f * k * Fx.clamp01((t - BEAMS) / 0.5f) : 0;
        iso.cx = cx + shake;
        iso.cy = cy + 24 * k + (1 - in) * 40 * k;
        iso.scale = 19 * k * (0.85f + 0.15f * in);
        iso.yaw = 0.4f + t * 0.24f;
        iso.pitch = (float) Math.toRadians(30);
        iso.begin();
        Pose id = Pose.identity();
        float[] orb = iso.screen(id, 0, 2.2f, 0);
        orbX = orb[0];
        orbY = orb[1];
        if (a < 0.02f) {
            burst(t, fx, sound, k);
            return;
        }

        // island: andesite ring around a stone floor
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            int r = Math.max(Math.abs(x), Math.abs(z));
            if (r == 3 && Math.abs(x) + Math.abs(z) > 4) continue;
            iso.box(c, r >= 2 ? STONE : ANDESITE, Piece.WHOLE, id, x, -0.25f, z, 1, 0, 0, 0);
        }

        List<Object[]> list = new ArrayList<>();
        // the nucleus in the middle
        list.add(new Object[]{0f, (Draw) () -> iso.box(c, CHISELED, Piece.WHOLE, id, 0, 0, 0, 1, 0, 0, 0)});
        for (int i = 0; i < 5; i++) {
            double ang = -Math.PI / 2 + i * Math.PI * 2 / 5;
            float px = (float) Math.cos(ang) * 2.3f, pz = (float) Math.sin(ang) * 2.3f;
            float placeAt = GEM0 + i * GEM_STEP;
            final int gi = i;
            list.add(new Object[]{depth(px, pz), (Draw) () -> {
                iso.box(c, BRICKS, Piece.WHOLE, id, px, 0, pz, 1, 0, 0, 0);
                if (t < placeAt) return;
                float drop = 1 - Fx.easeOut(Fx.clamp01((t - placeAt) / 0.2f));
                float bob = (float) Math.sin(t * 2.5f + gi) * 0.08f;
                float y = 1.25f + drop * 2.2f + bob;
                Pose spin = Pose.identity().pivot(px, y + 0.4f, pz).rotY(t * 1.8f + gi).rollZ(0.62f);
                // solid core + stained glass shell, turned on its corner like a gem
                iso.solidBox(c, spin, px - 0.22f, y + 0.18f, pz - 0.22f, px + 0.22f, y + 0.62f, pz + 0.22f, GEM_COLORS[gi], 1f, 0);
                iso.box(c, GEMS[gi], Piece.WHOLE, spin, px, y, pz, 0.8f, 0, 0, 0);
            }});
        }
        list.sort((p, q) -> Float.compare((Float) p[0], (Float) q[0]));
        for (Object[] o : list) ((Draw) o[1]).run();

        // placing sounds + sparkles + the gem's name
        while (gemsPlaced < 5 && t >= GEM0 + gemsPlaced * GEM_STEP + 0.18f) {
            double ang = -Math.PI / 2 + gemsPlaced * Math.PI * 2 / 5;
            float[] sp = iso.screen(id, (float) Math.cos(ang) * 2.3f, 1.4f, (float) Math.sin(ang) * 2.3f);
            int col = GEM_COLORS[gemsPlaced];
            fx.burst(sp[0], sp[1], 14, 20 * k, 70 * k, 50 * k, 0.6f, k, 2.5f * k, col, 0xFFFFFF);
            Particles.P ring = fx.add(sp[0], sp[1], 0, 0, 0.35f, 16 * k, col);
            ring.kind = Particles.Kind.RING;
            sound.play(Cue.TWINKLE, 0.9f + gemsPlaced * 0.12f, 0.7f);
            sound.play(Cue.TICK, 1.3f + gemsPlaced * 0.1f, 0.4f);
            gemsPlaced++;
        }
        for (int i = 0; i < gemsPlaced; i++) {
            float since = t - (GEM0 + i * GEM_STEP + 0.18f);
            float la = (1 - Fx.smooth((since - 0.5f) / 0.3f)) * Fx.smooth(since / 0.1f) * a;
            if (la < 0.02f) continue;
            double ang = -Math.PI / 2 + i * Math.PI * 2 / 5;
            float[] sp = iso.screen(id, (float) Math.cos(ang) * 2.3f, 2.1f + since * 0.4f, (float) Math.sin(ang) * 2.3f);
            Fx.centered(c, GEM_NAMES[i], sp[0], sp[1], 0.75f * k, Fx.alpha(GEM_COLORS[i], la), true);
        }

        // the five beams meet over the nucleus
        if (t > BEAMS) {
            if (!beamsPlayed) {
                beamsPlayed = true;
                sound.play(Cue.MAGIC, 0.8f, 0.9f);
                sound.play(Cue.RATTLE, 0.8f, 0.5f);
            }
            float bA = Fx.smooth((t - BEAMS) / 0.3f) * a;
            for (int i = 0; i < 5; i++) {
                double ang = -Math.PI / 2 + i * Math.PI * 2 / 5;
                float bob = (float) Math.sin(t * 2.5f + i) * 0.08f;
                float[] from = iso.screen(id, (float) Math.cos(ang) * 2.3f, 1.45f + bob, (float) Math.sin(ang) * 2.3f);
                beam(c, from[0], from[1], orbX, orbY, k, bA, t, GEM_COLORS[i]);
            }
            float grow = Fx.smooth((t - BEAMS) / (BURST - BEAMS));
            int mix = GEM_COLORS[(int) (t * 10) % 5];
            Fx.glow(c, orbX, orbY, 42 * k * grow, mix, 0.65f * bA, 2);
            Fx.glow(c, orbX, orbY, 18 * k * grow, 0xFFFFFF, 0.7f * bA, 2);
            Fx.rays(c, orbX, orbY, 60 * k * grow, 10, t * 0.8f, 0xFFFFFF, 0.3f * bA);
            if (random.nextFloat() < 0.8f) {
                float ang = random.nextFloat() * 6.283f, dist = 46 * k;
                Particles.P p = fx.add(orbX + (float) Math.cos(ang) * dist, orbY + (float) Math.sin(ang) * dist,
                        -(float) Math.cos(ang) * dist * 2.2f, -(float) Math.sin(ang) * dist * 2.2f, 0.42f, 2 * k, GEM_COLORS[random.nextInt(5)]);
                p.kind = Particles.Kind.SPARKLE;
            }
        }
        burst(t, fx, sound, k);
    }

    private void burst(float t, Particles fx, Sound sound, float k) {
        if (t < BURST || burst) return;
        burst = true;
        sound.play(Cue.CASE_WIN, 1.1f, 0.9f);
        sound.play(Cue.TOTEM, 1.4f, 0.4f);
        fx.burst(orbX, orbY, 70, 50 * k, 200 * k, 70 * k, 1.0f, 2 * k, 4.5f * k, GEM_COLORS[0], GEM_COLORS[1], GEM_COLORS[2], GEM_COLORS[3], GEM_COLORS[4], 0xFFFFFF);
        Particles.P ring = fx.add(orbX, orbY, 0, 0, 0.6f, 90 * k, 0xFFFFFF);
        ring.kind = Particles.Kind.RING;
    }

    private float depth(float x, float z) {
        return x * (float) Math.sin(iso.yaw) + z * (float) Math.cos(iso.yaw);
    }

    static void beam(Canvas c, float x0, float y0, float x1, float y1, float k, float a, float t, int color) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(2, Math.round(len / Math.max(1, k)));
        float flick = 0.75f + 0.25f * (float) Math.sin(t * 40 + x0);
        int light = Fx.lerpColor(color, 0xFFFFFF, 0.6f);
        int g = Math.max(1, Math.round(2 * k)), core = Math.max(1, Math.round(k));
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int px = Math.round(x0 + (x1 - x0) * f), py = Math.round(y0 + (y1 - y0) * f);
            c.fill(px - g, py - g, px + g + 1, py + g + 1, Fx.alpha(color, 0.3f * a * flick));
            c.fill(px, py, px + core, py + core, Fx.alpha(light, a * flick));
        }
    }
}

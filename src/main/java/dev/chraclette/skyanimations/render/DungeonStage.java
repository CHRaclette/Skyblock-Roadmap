package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;
import dev.chraclette.skyanimations.render.Skins.Face;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 3D intros for dungeon rewards, in the same style as the Dragon's Nest:
 * <ul>
 * <li>{@link #chest}: the Catacombs reward room - stone-brick floor, the reward chests in a row with their names
 * floating above, the camera moves onto yours, it rattles, the lid swings open and a light pillar shoots out.</li>
 * <li>{@link #meter}: the RNG Meter as a 3D glass tube that fills block by block with pink, glows, cracks and
 * bursts when it is full.</li>
 * </ul>
 */
final class DungeonStage {

    interface Sound {
        void play(Cue cue, float pitch, float volume);
    }

    static final float CHEST_LENGTH = 2.75f, CHEST_BURST = 2.15f;
    static final float METER_LENGTH = 3.1f, METER_BURST = 2.55f;

    private static Face b(String block) {
        return new Face("textures/block/" + block + ".png", 16, 16, 0, 0, 16, 16);
    }

    private static CubeSkin cube(String block, int flesh) {
        Face f = b(block);
        return new CubeSkin(f, f, f, f, f, f, null, 1, 1, 1, flesh, Fx.darken(flesh, 0.4f));
    }

    private static CubeSkin slab(String block, int flesh) {
        Face t = b(block);
        Face s = new Face("textures/block/" + block + ".png", 16, 16, 0, 0, 16, 4);
        return new CubeSkin(t, t, s, s, s, s, null, 1, 0.25f, 1, flesh, Fx.darken(flesh, 0.4f));
    }

    private static final CubeSkin BRICKS = slab("stone_bricks", 0x7A7A7A), CRACKED = slab("cracked_stone_bricks", 0x737373),
            MOSSY = slab("mossy_stone_bricks", 0x6E7A5E), WALL = cube("stone_bricks", 0x7A7A7A), WALL_MOSS = cube("mossy_stone_bricks", 0x6E7A5E);

    private static final String[] TIERS = {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};

    private record Chest(CubeSkin base, CubeSkin lid, CubeSkin lock) {
        static Chest of(String tex) {
            return new Chest(Skins.model(tex, 64, 64, 0, 19, 14, 10, 14, 0x9C6B30, 0x5A3A18),
                    Skins.model(tex, 64, 64, 0, 0, 14, 5, 14, 0x9C6B30, 0x5A3A18),
                    Skins.model(tex, 64, 64, 0, 0, 2, 4, 1, 0xB0B0B0, 0x606060));
        }
    }

    private static final Chest NORMAL = Chest.of("textures/entity/chest/normal.png");
    private static final Chest ENDER = Chest.of("textures/entity/chest/ender.png");
    private static final Chest TRAPPED = Chest.of("textures/entity/chest/trapped.png");

    private static Chest model(String tier) {
        String t = tier.toLowerCase(Locale.ROOT);
        if (t.startsWith("obsidian") || t.startsWith("bedrock")) return ENDER;
        if (t.startsWith("emerald") || t.startsWith("diamond")) return TRAPPED;
        return NORMAL;
    }

    private interface Draw {
        void run();
    }

    private final Iso iso = new Iso();
    private final Random random = new Random(3);
    private boolean opened, burst, full;
    private int rattles, filled;

    /** Where the light / loot comes from on screen, after the last draw. */
    float srcX, srcY;

    // ------------------------------------------------------------------ the reward room

    void chest(Canvas c, float t, String chestName, int accent, float cx, float cy, float k, float fade, Particles fx, Sound sound) {
        float out = 1 - Fx.smooth((t - CHEST_BURST) / (CHEST_LENGTH - CHEST_BURST + 0.1f));
        float a = fade * out;
        float in = Fx.easeOut(Fx.clamp01(t / 0.55f));
        int tierIdx = 0;
        for (int i = 0; i < TIERS.length; i++) if (chestName.toLowerCase(Locale.ROOT).startsWith(TIERS[i].toLowerCase(Locale.ROOT))) tierIdx = i;
        // camera starts wide on the row and glides onto your chest
        float focus = Fx.easeInOut(Fx.clamp01((t - 0.2f) / 0.9f));
        float zoom = 15 + 15 * focus;
        float rattle = t > 0.95f && t < 1.55f ? (float) Math.sin(t * 60) * 0.035f * Fx.clamp01((t - 0.95f) / 0.4f) : 0;
        iso.cx = cx;
        iso.cy = cy + 26 * k + (1 - in) * 40 * k;
        iso.scale = zoom * k * (0.85f + 0.15f * in);
        iso.yaw = -0.35f + 0.25f * focus + (float) Math.sin(t * 0.7f) * 0.03f;
        iso.pitch = (float) Math.toRadians(28);
        iso.begin();
        Pose id = Pose.identity();
        float mineX = (tierIdx - 2.5f) * 1.6f;
        // glide so that the whole row is centred at first and your chest at the end
        float[] row = iso.screen(id, 0, 0.5f, 0), mineS = iso.screen(id, mineX, 0.5f, 0);
        iso.cx += (cx - (row[0] + (mineS[0] - row[0]) * focus));
        float[] src = iso.screen(id, mineX, 1.2f, 0);
        srcX = src[0];
        srcY = src[1];
        if (a < 0.02f) {
            chestBurst(t, fx, sound, k, accent);
            return;
        }

        // floor + back wall
        Random r = new Random(11);
        for (int x = -6; x <= 6; x++) for (int z = -1; z <= 2; z++) {
            float n = r.nextFloat();
            iso.box(c, n < 0.18f ? CRACKED : n < 0.32f ? MOSSY : BRICKS, Piece.WHOLE, id, x, -0.25f, z, 1, 0, 0, 0);
        }
        for (int x = -6; x <= 6; x++) for (int y = 0; y < 2; y++) {
            iso.box(c, r.nextFloat() < 0.25f ? WALL_MOSS : WALL, Piece.WHOLE, id, x, y, -2, 1, 0, 0, 0);
        }

        // the chests
        float open = Fx.easeOut(Fx.clamp01((t - 1.6f) / 0.35f));
        for (int i = 0; i < TIERS.length; i++) {
            float x = (i - 2.5f) * 1.6f;
            boolean mine = i == tierIdx;
            float lid = mine ? open : 0;
            float shake = mine ? rattle : 0;
            if (mine && open > 0) {
                Fx.glow(c, srcX, srcY, 50 * k * open, accent, 0.55f * a, 2);
            }
            drawChest(c, model(TIERS[i]), x, 0, shake, lid);
            float[] lab = iso.screen(id, x, 1.55f, 0);
            int col = RunScene.chestColor(TIERS[i]);
            float la = a * (mine ? 1f : 0.55f * (1 - focus * 0.6f));
            Fx.centered(c, TIERS[i], lab[0], lab[1], (mine ? 0.9f : 0.6f) * k, Fx.alpha(col, la), mine);
        }
        while (rattles < 3 && t >= 1.0f + rattles * 0.18f) {
            sound.play(Cue.RATTLE, 1.1f + rattles * 0.1f, 0.6f);
            rattles++;
        }
        if (!opened && t >= 1.6f) {
            opened = true;
            sound.play(Cue.CASE_OPEN, 0.9f, 1.0f);
        }
        // light pillar out of the open chest
        if (open > 0) {
            float pa = a * open;
            int top = Math.round(srcY - 140 * k * open);
            float pw = 9 * k * zoom / 24f;
            for (int i = 0; i < 6; i++) {
                float f = i / 6f;
                c.fill(Math.round(srcX - pw * (1 - f)), top, Math.round(srcX + pw * (1 - f)), Math.round(srcY),
                        Fx.alpha(Fx.lerpColor(accent, 0xFFFFFF, f), 0.12f * pa));
            }
            Fx.rays(c, srcX, srcY, 70 * k * open, 12, t * 0.5f, Fx.lerpColor(accent, 0xFFE8A0, 0.5f), 0.5f * pa);
            if (random.nextFloat() < 0.8f) {
                Particles.P p = fx.add(srcX + (random.nextFloat() - 0.5f) * pw * 2, srcY, 0, -60 * k - random.nextFloat() * 60 * k, 0.6f, 2 * k,
                        Fx.lerpColor(accent, 0xFFFFFF, random.nextFloat()));
                p.kind = Particles.Kind.SPARKLE;
            }
        }
        chestBurst(t, fx, sound, k, accent);
    }

    private void drawChest(Canvas c, Chest ch, float x, float z, float shake, float open) {
        Pose base = Pose.identity().pivot(x, 0, z).rollZ(shake);
        float s = 14 / 16f;
        iso.box(c, ch.base, Piece.WHOLE, base, x, 0, z, s, 0, 0, 0);
        // lid hinges at the back top edge
        Pose lid = Pose.identity().pivot(x, 9 / 16f, z - 7 / 16f).pitchX(-open * 1.6f);
        Pose both = lid.rollZ(shake);
        iso.box(c, ch.lid, Piece.WHOLE, both, x, 9 / 16f, z, s, 0, 0, 0);
        iso.box(c, ch.lock, Piece.WHOLE, both, x, 7 / 16f, z + 7.5f / 16f, 2 / 16f, 0, 0, 0);
    }

    private void chestBurst(float t, Particles fx, Sound sound, float k, int accent) {
        if (t < CHEST_BURST || burst) return;
        burst = true;
        sound.play(Cue.CASE_WIN, 1.0f, 0.8f);
        fx.burst(srcX, srcY, 50, 40 * k, 170 * k, 60 * k, 0.9f, 2 * k, 4 * k, accent, 0xFFE8A0, 0xFFFFFF);
        Particles.P ring = fx.add(srcX, srcY, 0, 0, 0.5f, 70 * k, accent);
        ring.kind = Particles.Kind.RING;
    }

    // ------------------------------------------------------------------ the RNG meter tube

    private static final CubeSkin GLASS = cube("magenta_stained_glass", 0xC040C0);
    private static final int SEGMENTS = 7;

    void meter(Canvas c, float t, float cx, float cy, float k, float fade, Particles fx, Sound sound) {
        float out = 1 - Fx.smooth((t - METER_BURST) / (METER_LENGTH - METER_BURST + 0.1f));
        float a = fade * out;
        float in = Fx.easeOut(Fx.clamp01(t / 0.5f));
        iso.cx = cx;
        iso.cy = cy + 10 * k + (1 - in) * 30 * k;
        iso.scale = 20 * k * (0.85f + 0.15f * in);
        iso.yaw = 0.35f + t * 0.12f;
        iso.pitch = (float) Math.toRadians(24);
        iso.begin();
        Pose id = Pose.identity();
        float[] mid = iso.screen(id, 0, 0.5f, 0);
        srcX = mid[0];
        srcY = mid[1];
        if (a < 0.02f) {
            meterBurst(t, fx, sound, k);
            return;
        }
        float fill = 0.55f + 0.45f * Fx.easeInOut(Fx.clamp01((t - 0.4f) / 1.5f)); // starts more than half full
        int nowFilled = (int) Math.floor(fill * SEGMENTS + 0.001f);
        while (filled < nowFilled) {
            filled++;
            if (t > 0.45f) sound.play(Cue.TICK, 0.9f + filled * 0.1f, 0.6f);
        }
        if (!full && fill >= 0.999f) {
            full = true;
            sound.play(Cue.LEVELUP, 1.4f, 0.8f);
        }
        float shake = full ? (float) Math.sin(t * 70) * 0.02f * Fx.clamp01((t - 1.9f) / 0.5f) : 0;
        Pose tube = Pose.identity().pivot(0, 0.5f, 0).rollZ(shake);
        float x0 = -SEGMENTS / 2f + 0.5f;
        // pink liquid inside, then the glass around it (back to front along x)
        boolean leftFirst = Math.sin(iso.yaw) > 0;
        for (int n = 0; n < SEGMENTS; n++) {
            int i = leftFirst ? n : SEGMENTS - 1 - n;
            float x = x0 + i;
            float level = Fx.clamp01(fill * SEGMENTS - i);
            if (level > 0) {
                int col = full ? Fx.lerpColor(0xFF55FF, 0xFFFFFF, 0.3f + 0.3f * (float) Math.sin(t * 12 + i)) : 0xFF55FF;
                iso.solidBox(c, tube, x - 0.46f, 0.1f, -0.36f, x - 0.46f + 0.92f * level, 0.9f, 0.36f, col, a, 0);
            }
            iso.box(c, GLASS, Piece.WHOLE, tube, x, 0, 0, 1, 0, 0, 0);
        }
        float[] lab = iso.screen(id, 0, 1.6f, 0);
        Fx.centered(c, Math.round(fill * 100) + "%", lab[0], lab[1], 1.0f * k, Fx.alpha(0xFFFFFF, a), true);
        if (full) {
            Fx.glow(c, srcX, srcY, 90 * k, 0xFF55FF, 0.5f * a, 2);
            // cracks spreading over the glass
            float cr = Fx.clamp01((t - 1.95f) / 0.5f);
            Random r = new Random(9);
            for (int i = 0; i < 14 * cr; i++) {
                float[] p = iso.screen(tube, x0 - 0.5f + r.nextFloat() * SEGMENTS, r.nextFloat(), 0.5f);
                float len = (4 + r.nextFloat() * 8) * k;
                float ang = r.nextFloat() * 6.283f;
                for (int s = 0; s < len; s++) {
                    int qx = Math.round(p[0] + (float) Math.cos(ang) * s), qy = Math.round(p[1] + (float) Math.sin(ang) * s);
                    c.fill(qx, qy, qx + 1, qy + 1, Fx.alpha(0xFFFFFF, 0.8f * a));
                }
            }
        }
        meterBurst(t, fx, sound, k);
    }

    private void meterBurst(float t, Particles fx, Sound sound, float k) {
        if (t < METER_BURST || burst) return;
        burst = true;
        sound.play(Cue.ICE_BREAK, 1.2f, 0.9f);
        sound.play(Cue.CASE_WIN, 1.1f, 0.8f);
        fx.burst(srcX, srcY, 70, 50 * k, 200 * k, 90 * k, 1.0f, 2 * k, 4.5f * k, 0xFF55FF, 0xFFB8FF, 0xFFFFFF, 0xC040C0);
        for (int i = 0; i < 18; i++) {
            Particles.P p = fx.add(srcX + (random.nextFloat() - 0.5f) * 120 * k, srcY + (random.nextFloat() - 0.5f) * 20 * k,
                    (random.nextFloat() - 0.5f) * 160 * k, -random.nextFloat() * 120 * k, 1.0f, 3 * k, 0xE0A0E0);
            p.kind = Particles.Kind.SHARD;
        }
        Particles.P ring = fx.add(srcX, srcY, 0, 0, 0.6f, 100 * k, 0xFF55FF);
        ring.kind = Particles.Kind.RING;
    }

    static List<String> tiers() {
        return new ArrayList<>(List.of(TIERS));
    }
}

package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;

import java.util.List;
import java.util.Random;

/**
 * PET DROP: a 3D pet egg in the pet's rarity colour wobbles, cracks and bursts open; the pet hops out as a
 * real 3D head with its own skin (that is what SkyBlock pets are: floating heads), spins in, then floats and
 * bobs and looks around like a pet next to you, with PET DROP!, its name, value and count - and finally flies off
 * to your side.
 */
public final class PetScene implements Scene {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final float[] KNOCKS = {0.7f, 1.15f, 1.5f, 1.8f};
    private static final float POP = 0.55f;
    /** Ender Dragon: a mini Dragon's Nest instead of the egg - eyes go into the portal frames, the crystals beam, it spawns. */
    private final boolean dragon;
    private final float HATCH, FLY, END;
    private static final float EYE0 = 0.75f, EYE_STEP = 0.2f, BEAMS = 2.45f;
    private int eyesPlaced;

    private final String name;
    private final String rarityLabel;
    private final Object icon;
    private final int rarity;
    private final List<String> lines;
    private final long coins;
    private final Hooks hooks;
    private final Particles fx = new Particles(41);
    private final Random random = new Random(17);
    private final Iso iso = new Iso();
    private final RevealText text = new RevealText();
    private final int[][] eggSide, eggTop;
    private float lastT = -1;
    private int knocks;
    private boolean hatched;

    public PetScene(String name, String rarityLabel, Object icon, int rarity, List<String> lines, long coins, Hooks hooks) {
        this.name = name;
        this.rarityLabel = rarityLabel == null ? "" : rarityLabel;
        this.icon = icon;
        this.rarity = rarity;
        this.lines = lines == null ? List.of() : lines;
        this.coins = coins;
        this.hooks = hooks;
        this.eggSide = eggPattern(10, 10, 1);
        this.eggTop = eggPattern(10, 10, 2);
        this.dragon = name.toLowerCase(java.util.Locale.ROOT).contains("ender dragon");
        this.HATCH = dragon ? 3.45f : 2.05f;
        this.END = HATCH + 5.55f;
        this.FLY = END - 1.0f;
    }

    /** Cream shell with spots in the rarity colour, like a SkyBlock pet egg. */
    private int[][] eggPattern(int cols, int rows, long seed) {
        Random r = new Random(seed * 31 + name.hashCode());
        int shell = 0xF4EFDD, shade = 0xE2DCC4;
        int spot = rarity, spotDark = Fx.darken(rarity, 0.25f);
        int[][] g = new int[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) g[y][x] = (x + y) % 5 == 0 ? shade : shell;
        }
        for (int i = 0; i < 6; i++) {
            int sx = r.nextInt(cols - 1), sy = r.nextInt(rows - 1);
            g[sy][sx] = spot;
            g[sy][sx + 1] = spot;
            g[sy + 1][sx] = spotDark;
            if (r.nextBoolean()) g[sy + 1][sx + 1] = spot;
        }
        return g;
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
        float fade = Fx.smooth(t / 0.35f) * (1 - Fx.smooth((t - (END - 0.5f)) / 0.5f));
        float cx = w / 2f, cy = h * 0.42f;
        c.fill(0, 0, w, h, Fx.alpha(0x07070C, 0.8f * fade));
        Fx.glow(c, cx, cy, 110 * k, rarity, 0.35f * fade, 3);

        // ---------------------------------------------------------------- the summon (Ender Dragon)
        if (dragon && t < HATCH + 0.7f) {
            summon(c, t, cx, cy, k, fade);
        }
        // ---------------------------------------------------------------- the egg
        if (!dragon && t < HATCH + 0.05f) {
            while (knocks < KNOCKS.length && t >= KNOCKS[knocks]) {
                hooks.sound(Cue.TICK, 0.7f + knocks * 0.12f, 0.9f);
                hooks.sound(Cue.CASE_OPEN, 1.6f + knocks * 0.1f, 0.35f);
                fx.burst(cx, cy + 18 * k, 6 + knocks * 3, 20 * k, 60 * k, 90 * k, 0.5f, k, 2 * k, 0xF4EFDD, rarity);
                knocks++;
            }
            float in = Fx.backOut(Fx.clamp01(t / 0.45f));
            float sinceKnock = knocks == 0 ? 9 : t - KNOCKS[knocks - 1];
            float wobble = (float) Math.sin(sinceKnock * 34) * (float) Math.exp(-sinceKnock * 7) * (0.12f + knocks * 0.05f);
            float swell = 1 + (t > HATCH - 0.25f ? 0.12f * Fx.smooth((t - (HATCH - 0.25f)) / 0.25f) : 0);
            float size = 4.6f * k * in * swell;
            // shadow
            c.fill(Math.round(cx - 18 * size / 3.2f), Math.round(cy + 21 * size / 3.2f), Math.round(cx + 18 * size / 3.2f),
                    Math.round(cy + 23 * size / 3.2f), Fx.alpha(0x000000, 0.4f * fade));
            egg(c, cx, cy, size, (float) Math.sin(t * 0.8f) * 0.5f + 0.6f, wobble, fade);
            cracks(c, cx, cy, size, knocks, fade);
            if (t > HATCH - 0.1f) {
                float f = Fx.smooth((t - (HATCH - 0.1f)) / 0.15f);
                Fx.glow(c, cx, cy, 60 * k * f, 0xFFFFFF, 0.6f * f * fade, 2);
            }
        }

        // ---------------------------------------------------------------- the hatch
        if (t >= HATCH && !hatched) {
            hatched = true;
            hooks.sound(Cue.SPLASH, dragon ? 0.7f : 1.3f, 0.7f);
            if (dragon) hooks.sound(Cue.THUMP, 0.6f, 1.0f);
            hooks.sound(Cue.TOTEM, 1.25f, 0.5f);
            hooks.sound(Cue.CASE_WIN, 1.0f, 1.0f);
            if (dragon) fx.burst(cx, cy, 70, 50 * k, 210 * k, 60 * k, 1.1f, 2 * k, 4.5f * k, 0xC040FF, 0x1A0A2A, 0xFF66FF, 0xFFFFFF);
            else fx.burst(cx, cy, 60, 50 * k, 190 * k, 120 * k, 1.0f, 2 * k, 4.5f * k, 0xF4EFDD, 0xE2DCC4, rarity, 0xFFFFFF);
            Particles.P ring = fx.add(cx, cy, 0, 0, 0.6f, 90 * k, rarity);
            ring.kind = Particles.Kind.RING;
        }

        if (t >= HATCH) {
            float rt = t - HATCH;
            float flash = 1 - Fx.smooth(rt / 0.25f);
            if (flash > 0) c.fill(0, 0, w, h, Fx.alpha(0xFFFFFF, 0.5f * flash * fade));
            // fly off to the side at the end, like it starts following you
            float fly = Fx.easeInOut(Fx.clamp01((t - FLY) / (END - 0.3f - FLY)));
            float pop = Fx.backOut(Fx.clamp01(rt / POP));
            float bob = (float) Math.sin(rt * 2.6f) * 5 * k;
            float x = cx + fly * (w * 0.33f) + (float) Math.sin(rt * 1.1f) * 4 * k;
            float y = cy + bob - pop * 6 * k + fly * (h * 0.18f) + (dragon ? (1 - pop) * 20 * k : 0);
            float size = 44 * k * pop * (1 - 0.55f * fly);
            // spins in fast, then calmly looks around
            float spin = rt < POP + 0.3f ? (1 - Fx.easeOut(Fx.clamp01(rt / (POP + 0.3f)))) * (float) Math.PI * 4 : 0;
            float look = (float) Math.sin(rt * 0.9f) * 0.55f + (float) Math.sin(rt * 2.3f) * 0.08f;
            float yaw = (float) Math.toRadians(-20) + look + spin + fly * 0.9f;
            float tilt = (float) Math.sin(rt * 1.7f) * 0.06f;

            Fx.rays(c, x, y, 130 * k * pop * (1 - fly), 16, t * 0.3f, rarity, 0.45f * fade * (1 - fly));
            Fx.glow(c, x, y, 70 * k * pop, rarity, 0.55f * fade, 2);
            // its shadow on the "floor"
            float sh = 1 - 0.3f * (bob / (5 * k) * 0.5f + 0.5f);
            c.fill(Math.round(x - size * 0.45f * sh), Math.round(cy + 34 * k + fly * (h * 0.18f)), Math.round(x + size * 0.45f * sh),
                    Math.round(cy + 36 * k + fly * (h * 0.18f)), Fx.alpha(0x000000, 0.35f * fade * pop));
            pet(c, x, y, size, yaw, tilt, fade);
            if (random.nextFloat() < 0.5f && fade > 0.2f) {
                Particles.P p = fx.add(x + (random.nextFloat() - 0.5f) * size, y + (random.nextFloat() - 0.5f) * size,
                        (random.nextFloat() - 0.5f) * 20 * k, -25 * k * random.nextFloat(), 0.8f, 2 * k, Fx.lerpColor(rarity, 0xFFFFFF, 0.4f));
                p.kind = Particles.Kind.SPARKLE;
            }

            float ta = Fx.smooth((rt - 0.25f) / 0.3f) * fade * (1 - fly);
            if (ta > 0.01f) {
                Fx.centered(c, "PET DROP!", cx, cy - 64 * k, 1.3f * k * Fx.backOut(Fx.clamp01((rt - 0.25f) / 0.3f)),
                        Fx.alpha(0xFFAA00, ta), true);
                text.draw(c, (rarityLabel.isEmpty() ? "" : rarityLabel + " ") + name, rarity, coins, lines,
                        cx, cy + 42 * k, k, rt - 0.45f, ta, () -> hooks.sound(Cue.TICK, 1.7f, 0.25f), () -> hooks.sound(Cue.POP, 1.5f, 0.25f));
            }
        }
        fx.update(dt);
        fx.render(c, fade);
    }

    // ---------------------------------------------------------------- mini Dragon's Nest

    private static Skins.Face f(String tex, int u, int v, int w, int h) {
        return new Skins.Face(tex, 16, 16, u, v, w, h);
    }

    private static final String FRAME_TOP = "textures/block/end_portal_frame_top.png", FRAME_SIDE = "textures/block/end_portal_frame_side.png",
            EYE = "textures/block/end_portal_frame_eye.png", END_STONE = "textures/block/end_stone.png",
            OBSIDIAN = "textures/block/obsidian.png", CRYSTAL = "textures/entity/end_crystal/end_crystal.png";
    private static final CubeSkin FRAME = new CubeSkin(f(FRAME_TOP, 0, 0, 16, 16), f(END_STONE, 0, 0, 16, 16),
            f(FRAME_SIDE, 0, 3, 16, 13), f(FRAME_SIDE, 0, 3, 16, 13), f(FRAME_SIDE, 0, 3, 16, 13), f(FRAME_SIDE, 0, 3, 16, 13),
            null, 1, 13 / 16f, 1, 0x3E6B5E, 0x1E3A32);
    private static final CubeSkin EYE_SKIN = new CubeSkin(f(EYE, 4, 4, 8, 8), f(EYE, 4, 4, 8, 8),
            f(EYE, 4, 0, 8, 3), f(EYE, 4, 0, 8, 3), f(EYE, 4, 0, 8, 3), f(EYE, 4, 0, 8, 3), null, 1, 3 / 8f, 1, 0x2E8C6E, 0x0E3A2E);
    private static final CubeSkin STONE = new CubeSkin(f(END_STONE, 0, 0, 16, 16), f(END_STONE, 0, 0, 16, 16),
            f(END_STONE, 0, 0, 16, 4), f(END_STONE, 0, 0, 16, 4), f(END_STONE, 0, 0, 16, 4), f(END_STONE, 0, 0, 16, 4),
            null, 1, 0.25f, 1, 0xDDE0A5, 0xB8BA80);
    private static final CubeSkin OBS = new CubeSkin(f(OBSIDIAN, 0, 0, 16, 16), f(OBSIDIAN, 0, 0, 16, 16), f(OBSIDIAN, 0, 0, 16, 16),
            f(OBSIDIAN, 0, 0, 16, 16), f(OBSIDIAN, 0, 0, 16, 16), f(OBSIDIAN, 0, 0, 16, 16), null, 1, 1, 1, 0x1A1028, 0x0A0612);
    private static CubeSkin crystal(int u) {
        return new CubeSkin(new Skins.Face(CRYSTAL, 64, 32, u + 8, 0, 8, 8), new Skins.Face(CRYSTAL, 64, 32, u + 16, 0, 8, 8),
                new Skins.Face(CRYSTAL, 64, 32, u + 8, 8, 8, 8), new Skins.Face(CRYSTAL, 64, 32, u + 24, 8, 8, 8),
                new Skins.Face(CRYSTAL, 64, 32, u + 16, 8, 8, 8), new Skins.Face(CRYSTAL, 64, 32, u, 8, 8, 8), null, 1, 1, 1, 0xE070E0, 0x802080);
    }
    private static final CubeSkin GLASS = crystal(0), CORE = crystal(32);

    private interface Draw {
        void run();
    }

    private void summon(Canvas c, float t, float cx, float cy, float k, float fade) {
        float out = 1 - Fx.smooth((t - HATCH) / 0.6f);           // the nest fades once the dragon is out
        float a = fade * out;
        if (a < 0.02f) return;
        float in = Fx.easeOut(Fx.clamp01(t / 0.6f));
        float shake = t > BEAMS && t < HATCH ? (float) Math.sin(t * 80) * 1.5f * k * Fx.clamp01((t - BEAMS) / 0.6f) : 0;
        iso.cx = cx + shake;
        iso.cy = cy + 26 * k + (1 - in) * 40 * k;
        iso.scale = 19 * k * (0.85f + 0.15f * in);
        iso.yaw = 0.55f + t * 0.22f;
        iso.pitch = (float) Math.toRadians(30);
        iso.begin();
        Pose id = Pose.identity();

        // end stone island (5x5)
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) == 2 && Math.abs(z) == 2) continue;
            iso.box(c, STONE, Piece.WHOLE, id, x, -0.25f, z, 1, 0, 0, 0);
        }
        if (a < 0.98f) c.fill(0, 0, c.width(), c.height(), Fx.alpha(0x07070C, (1 - out) * 0.6f)); // dim while fading

        // the portal in the middle comes alive once all eyes are in
        float portal = Fx.smooth((t - (EYE0 + 8 * EYE_STEP)) / 0.4f);
        if (portal > 0.01f) {
            int[][] g = new int[8][8];
            Random r = new Random((long) (t * 12));
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                float n = r.nextFloat();
                g[y][x] = n > 0.93f ? 0x9AF0E0 : n > 0.86f ? 0x2E6A72 : n > 0.75f ? 0x1A2E3A : 0x060A12;
            }
            iso.pixelQuad(c, id, new float[]{-1, 0.02f, -1}, new float[]{2, 0, 0}, new float[]{0, 0, 2}, new float[]{0, 1, 0}, g, portal * a);
        }

        // objects, drawn back to front
        java.util.List<Object[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            float fx0 = (float) Math.cos(ang) * 1.9f, fz = (float) Math.sin(ang) * 1.9f;
            final int idx = i;
            list.add(new Object[]{depth(fx0, fz), (Draw) () -> {
                iso.box(c, FRAME, Piece.WHOLE, id, fx0, 0, fz, 1, 0, 0, 0);
                float placeAt = EYE0 + idx * EYE_STEP;
                if (t >= placeAt) {
                    float drop = 1 - Fx.easeOut(Fx.clamp01((t - placeAt) / 0.18f));
                    iso.box(c, EYE_SKIN, Piece.WHOLE, id, fx0, 13 / 16f + drop * 1.5f, fz, 0.5f, 0, 0, 0);
                }
            }});
        }
        float[][] pillars = {{3.3f, 0}, {0, 3.3f}, {-3.3f, 0}, {0, -3.3f}};
        for (float[] pl : pillars) {
            list.add(new Object[]{depth(pl[0], pl[1]), (Draw) () -> {
                for (int y = 0; y < 3; y++) iso.box(c, OBS, Piece.WHOLE, id, pl[0], y - 0.25f, pl[1], 1, 0, 0, 0);
                float bob = (float) Math.sin(t * 3 + pl[0]) * 0.15f;
                Pose spin = Pose.identity().pivot(pl[0], 3.55f + bob, pl[1]).rotY(t * 2.4f).rollZ(0.62f);
                iso.box(c, CORE, Piece.WHOLE, spin, pl[0], 3.3f + bob, pl[1], 0.5f, 0, 0, 0);
                iso.box(c, GLASS, Piece.WHOLE, spin, pl[0], 3.15f + bob, pl[1], 0.8f, 0, 0, 0);
            }});
        }
        list.sort((p, q) -> Float.compare((Float) p[0], (Float) q[0]));
        for (Object[] o : list) ((Draw) o[1]).run();

        // eye sounds + sparkles
        while (eyesPlaced < 8 && t >= EYE0 + eyesPlaced * EYE_STEP) {
            double ang = eyesPlaced * Math.PI / 4;
            float[] sp = iso.screen(id, (float) Math.cos(ang) * 1.9f, 1, (float) Math.sin(ang) * 1.9f);
            fx.burst(sp[0], sp[1], 8, 15 * k, 50 * k, 40 * k, 0.5f, k, 2 * k, 0x5AF0C8, 0xFFFFFF);
            hooks.sound(Cue.POP, 0.9f + eyesPlaced * 0.08f, 0.6f);
            hooks.sound(Cue.TICK, 1.2f + eyesPlaced * 0.06f, 0.4f);
            eyesPlaced++;
            if (eyesPlaced == 8) {
                hooks.sound(Cue.MAGIC, 0.6f, 0.9f);
                hooks.sound(Cue.RATTLE, 0.7f, 0.6f);
            }
        }

        // crystals beam into the portal, the dragon forms above it
        if (t > BEAMS) {
            float b = Fx.smooth((t - BEAMS) / 0.4f) * a;
            float[] target = iso.screen(id, 0, 1.6f, 0);
            for (float[] pl : pillars) {
                float bob = (float) Math.sin(t * 3 + pl[0]) * 0.15f;
                float[] from = iso.screen(id, pl[0], 3.55f + bob, pl[1]);
                beam(c, from[0], from[1], target[0], target[1], k, b, t);
            }
            float orb = Fx.smooth((t - BEAMS) / (HATCH - BEAMS));
            Fx.glow(c, target[0], target[1], 40 * k * orb, 0xC040FF, 0.7f * b, 2);
            Fx.glow(c, target[0], target[1], 18 * k * orb, 0xFFFFFF, 0.6f * b, 2);
            if (random.nextFloat() < 0.7f) {
                float ang = random.nextFloat() * 6.283f, dist = 50 * k;
                Particles.P p = fx.add(target[0] + (float) Math.cos(ang) * dist, target[1] + (float) Math.sin(ang) * dist,
                        -(float) Math.cos(ang) * dist * 2, -(float) Math.sin(ang) * dist * 2, 0.45f, 2 * k, 0xD070FF);
                p.kind = Particles.Kind.SPARKLE;
            }
        }
    }

    private float depth(float x, float z) {
        return x * (float) Math.sin(iso.yaw) + z * (float) Math.cos(iso.yaw);
    }

    /** A flickering end-crystal beam (pink-white core, purple glow). */
    private static void beam(Canvas c, float x0, float y0, float x1, float y1, float k, float a, float t) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(2, Math.round(len / Math.max(1, k)));
        float flick = 0.75f + 0.25f * (float) Math.sin(t * 40 + x0);
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            int px = Math.round(x0 + (x1 - x0) * f), py = Math.round(y0 + (y1 - y0) * f);
            int g = Math.max(1, Math.round(2 * k));
            c.fill(px - g, py - g, px + g + 1, py + g + 1, Fx.alpha(0xA030E0, 0.35f * a * flick));
            c.fill(px, py, px + Math.max(1, Math.round(k)), py + Math.max(1, Math.round(k)), Fx.alpha(0xFFD0FF, a * flick));
        }
    }

    /** The pet: its head skin as a 3D cube, or a placeholder until the skin has loaded. */
    private void pet(Canvas c, float x, float y, float size, float yaw, float tilt, float a) {
        if (size < 0.5f || a < 0.03f) return;
        CubeSkin skin = Props.headSkins.apply(name);
        if (skin == null && icon != null) {
            c.push();
            c.translate(x, y);
            c.scale(size / 16f * 1.2f, size / 16f * 1.2f);
            c.item(icon, -8, -8);
            c.pop();
            return;
        }
        iso.cx = x;
        iso.cy = y + size * 0.5f;
        iso.scale = size;
        iso.yaw = yaw;
        iso.pitch = (float) Math.toRadians(16);
        iso.begin();
        Pose pose = Pose.identity().pivot(0, 0.5f, 0).rollZ(tilt);
        if (skin != null) {
            iso.box(c, skin, Piece.WHOLE, pose, 0, 0, 0, 1, 0.8f, 0, 0);
        } else {
            iso.solidBox(c, pose, -0.5f, 0, -0.5f, 0.5f, 1, 0.5f, rarity, a, 0.8f);
        }
    }

    /** Egg built from stacked 3D boxes (round-ish like a voxel egg). size = screen px per texel. */
    private void egg(Canvas c, float x, float y, float size, float yaw, float roll, float a) {
        iso.cx = x;
        iso.cy = y;
        iso.scale = size;
        iso.yaw = yaw;
        iso.pitch = (float) Math.toRadians(20);
        iso.begin();
        Pose pose = Pose.identity().pivot(0, -6, 0).rollZ(roll);
        //            half width, y0, y1
        float[][] layers = {{3.5f, -6.5f, -5.5f}, {4.5f, -5.5f, -3.5f}, {5f, -3.5f, 1.5f}, {4.5f, 1.5f, 3.5f}, {3.5f, 3.5f, 5f}, {2.2f, 5f, 6f}};
        for (float[] l : layers) box(c, pose, l[0], l[1], l[2], a);
    }

    private void box(Canvas c, Pose pose, float r, float y0, float y1, float a) {
        int rows = Math.max(1, Math.round(y1 - y0)), cols = Math.max(2, Math.round(r * 2));
        int[][] side = crop(eggSide, rows, cols), top = crop(eggTop, cols, cols);
        float x0 = -r, x1 = r, z0 = -r, z1 = r;
        iso.pixelQuad(c, pose, new float[]{x0, y0, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z0 - z1}, new float[]{0, -1, 0}, top, a);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, 1}, side, a);
        iso.pixelQuad(c, pose, new float[]{x1, y1, z1}, new float[]{0, 0, z0 - z1}, new float[]{0, y0 - y1, 0}, new float[]{1, 0, 0}, side, a);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z0}, new float[]{0, 0, z1 - z0}, new float[]{0, y0 - y1, 0}, new float[]{-1, 0, 0}, side, a);
        iso.pixelQuad(c, pose, new float[]{x1, y1, z0}, new float[]{x0 - x1, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, -1}, side, a);
        iso.pixelQuad(c, pose, new float[]{x0, y1, z0}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z1 - z0}, new float[]{0, 1, 0}, top, a);
    }

    private static int[][] crop(int[][] src, int rows, int cols) {
        int[][] g = new int[rows][cols];
        for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) g[y][x] = src[y % src.length][x % src[0].length];
        return g;
    }

    /** Dark zig-zag cracks on the shell, one more per knock. */
    private void cracks(Canvas c, float x, float y, float size, int n, float a) {
        int dark = Fx.alpha(0x3A3020, a);
        float[][] paths = {
                {-1, -2, 0, -1, -1, 0, 0, 1, 1, 2},
                {1, -3, 2, -2, 1, -1, 3, 0},
                {-2, 0, -3, 1, -2, 2, -3, 3},
                {0, -4, 1, -3, 0, -2, 1, -1, 0, 0, 1, 1, 0, 2, 1, 3},
        };
        for (int i = 0; i < Math.min(n, paths.length); i++) {
            float[] p = paths[i];
            for (int j = 0; j + 3 < p.length; j += 2) {
                float ax = x + p[j] * size, ay = y + p[j + 1] * size, bx = x + p[j + 2] * size, by = y + p[j + 3] * size;
                int steps = Math.max(2, Math.round(Math.max(Math.abs(bx - ax), Math.abs(by - ay))));
                for (int s = 0; s <= steps; s++) {
                    float f = s / (float) steps;
                    int px = Math.round(ax + (bx - ax) * f), py = Math.round(ay + (by - ay) * f);
                    int th = Math.max(1, Math.round(size * 0.3f));
                    c.fill(px, py, px + th, py + th, dark);
                }
            }
        }
    }
}

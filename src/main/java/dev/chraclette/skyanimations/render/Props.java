package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Iso.Piece;
import dev.chraclette.skyanimations.render.Iso.Pose;
import dev.chraclette.skyanimations.render.Skins.CubeSkin;

import java.util.Locale;
import java.util.function.Function;

/**
 * Custom 3D models for drops that are flat heads in game: the Archfiend Dice actually rolls and lands,
 * the Judgement Core is a spinning, glowing core with orbiting shards, and any other head item
 * (Warden Heart, Manti-core, Scatha ...) becomes a turning 3D head with its real skin.
 */
public final class Props {

    private Props() {
    }

    /** Client hook: item name -> head skin (texture downloaded from the item's skin), or null if not ready. */
    public static Function<String, CubeSkin> headSkins = name -> null;

    private static final CubeSkin ARCHFIEND = Skins.strip("skyanimations:textures/props/archfiend_dice.png", 7, 5, 0, 1, 4, 2, 3);
    private static final CubeSkin HIGH_CLASS = Skins.strip("skyanimations:textures/props/high_class_dice.png", 7, 6, 0, 1, 4, 2, 3);
    private static final String CORE_TEX = "skyanimations:textures/props/judgement_core.png";
    private static final CubeSkin CORE = new CubeSkin(
            new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16), new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16),
            new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16), new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16),
            new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16), new Skins.Face(CORE_TEX, 16, 16, 0, 0, 16, 16),
            null, 1, 1, 1, 0xC040E0, 0x401060);

    private static final Iso ISO = new Iso();

    public enum Kind { DICE, HIGH_CLASS_DICE, CORE, HEAD }

    /** Which model a drop gets, or null for the normal item icon. */
    public static Kind kind(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("high class archfiend dice")) return Kind.HIGH_CLASS_DICE;
        if (n.contains("archfiend dice") || n.endsWith(" dice")) return Kind.DICE;
        if (n.contains("judgement core")) return Kind.CORE;
        if (headSkins.apply(name) != null) return Kind.HEAD;
        return null;
    }

    /**
     * Draws the model centred at (cx,cy). px = how big an item icon would be (16 * scale).
     * t = seconds since it appeared (the dice rolls in the first 1.3 s).
     * Returns false if this item has no custom model (draw the normal icon then).
     */
    public static boolean draw(Canvas c, String name, float cx, float cy, float px, float t) {
        Kind kind = kind(name);
        if (kind == null || px < 2) return false;
        switch (kind) {
            case DICE, HIGH_CLASS_DICE -> dice(c, kind == Kind.HIGH_CLASS_DICE ? HIGH_CLASS : ARCHFIEND,
                    kind == Kind.HIGH_CLASS_DICE ? 0xF0C040 : 0xE03A2A, cx, cy, px, t);
            case CORE -> core(c, cx, cy, px, t);
            case HEAD -> head(c, headSkins.apply(name), cx, cy, px, t);
        }
        return true;
    }

    private static void setup(float cx, float cy, float scale, float yawDeg, float pitchDeg) {
        ISO.cx = cx;
        ISO.cy = cy;
        ISO.scale = scale;
        ISO.yaw = (float) Math.toRadians(yawDeg);
        ISO.pitch = (float) Math.toRadians(pitchDeg);
        ISO.begin();
    }

    /** A die thrown in from above: tumbles, bounces twice and lands with the best face (6, or 7) on top. */
    private static void dice(Canvas c, CubeSkin skin, int glow, float cx, float cy, float px, float t) {
        float roll = Fx.clamp01(t / 1.3f);
        float e = Fx.easeOut(roll);
        float drop = (1 - Fx.bounceOut(roll)) * px * 1.6f;          // falls in from above, bounces
        float spin = (1 - e);
        Pose pose = Pose.identity().pivot(0, 0, 0)
                .pitchX(spin * 9.4f)
                .rollZ(spin * 6.3f)
                .rotY(spin * 4.1f + (float) Math.sin(t * 0.8f) * 0.15f);
        float s = px / 1.7f;
        if (roll >= 1) Fx.glow(c, cx, cy, px * 0.9f, glow, 0.45f + 0.15f * (float) Math.sin(t * 3), 2);
        // shadow on the "table"
        float shadowW = px * 0.55f * (0.6f + 0.4f * (1 - drop / (px * 1.6f)));
        c.fill(Math.round(cx - shadowW), Math.round(cy + px * 0.42f), Math.round(cx + shadowW), Math.round(cy + px * 0.42f + Math.max(1, px * 0.05f)), 0x50000000);
        setup(cx, cy - drop, s, 35, 28);
        ISO.box(c, skin, Piece.WHOLE, pose, 0, -0.5f, 0, 1, Math.max(0.6f, px / 40f), 0, 0);
    }

    /** Judgement Core: a cube turned on its corner, spinning, pulsing, with three shards orbiting it. */
    private static void core(Canvas c, float cx, float cy, float px, float t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
        Fx.glow(c, cx, cy, px * (1.0f + 0.15f * pulse), 0xC040E0, 0.75f, 2);
        Fx.rays(c, cx, cy, px * 1.6f, 8, t * 0.5f, 0xE070FF, 0.5f);
        float s = px / 1.9f;
        // orbiting shards: those behind are drawn first
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 3; i++) {
                float a = t * 1.8f + i * 2.094f;
                float depth = (float) Math.sin(a);
                if ((pass == 0) != (depth < 0)) continue;
                float ox = cx + (float) Math.cos(a) * px * 0.95f, oy = cy + depth * px * 0.28f - px * 0.05f;
                float sz = px * (0.07f + 0.03f * (depth + 1));
                c.push();
                c.translate(ox, oy);
                c.rotate(t * 3 + i);
                c.fill(Math.round(-sz), Math.round(-sz), Math.round(sz), Math.round(sz), 0xFF000000 | (depth > 0 ? 0xF0A0FF : 0x8030A0));
                c.pop();
            }
            if (pass == 0) {
                Pose pose = Pose.identity().pivot(0, 0, 0).pitchX(0.6155f).rollZ(0.785f).rotY(t * 1.2f);
                setup(cx, cy + (float) Math.sin(t * 2) * px * 0.05f, s, 30, 25);
                ISO.box(c, CORE, Piece.WHOLE, pose, 0, -0.5f, 0, 1, Math.max(0.6f, px / 40f), 0, 0);
                // light leaking from the eye
                Fx.glow(c, cx, cy, px * 0.35f * (0.7f + 0.3f * pulse), 0xFFD0FF, 0.6f, 1);
            }
        }
    }

    /** Any head item: a real 3D head with its skin, slowly turning, bobbing. */
    private static void head(Canvas c, CubeSkin skin, float cx, float cy, float px, float t) {
        float s = px / 1.75f;
        Pose pose = Pose.identity().pivot(0, 0, 0).rotY(t * 0.9f);
        setup(cx, cy + (float) Math.sin(t * 2.2f) * px * 0.04f, s, 30, 22);
        ISO.box(c, skin, Piece.WHOLE, pose, 0, -0.5f, 0, 1, Math.max(0.6f, px / 40f), 0, 0);
    }
}

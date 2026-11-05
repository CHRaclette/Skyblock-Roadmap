package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Skins.CubeSkin;
import dev.chraclette.skyanimations.render.Skins.Face;

/**
 * A tiny 3D-on-the-HUD renderer. The camera is orthographic, so every box face becomes a
 * parallelogram, which is just an affine transform of a flat texture. That is all the HUD can do,
 * and all we need.
 */
public final class Iso {

    /** Big grid used for solid fills on faces, so outlines and cracks can be sub-pixel precise. */
    private static final int K = 1000;

    public float yaw = (float) Math.toRadians(32);
    public float pitch = (float) Math.toRadians(28);
    public float scale = 50;
    public float cx, cy;

    private float cosY, sinY, cosP, sinP;

    public void begin() {
        cosY = (float) Math.cos(yaw);
        sinY = (float) Math.sin(yaw);
        cosP = (float) Math.cos(pitch);
        sinP = (float) Math.sin(pitch);
    }

    public float sx(float x, float y, float z) {
        return cx + (x * cosY - z * sinY) * scale;
    }

    public float sy(float x, float y, float z) {
        float zr = x * sinY + z * cosY;
        return cy + (zr * sinP - y * cosP) * scale;
    }

    /** How much a direction points at the viewer (>0 means visible). */
    public float facing(float nx, float ny, float nz) {
        float zr = nx * sinY + nz * cosY;
        return ny * sinP + zr * cosP;
    }

    /** Larger = closer to the viewer. */
    public float depth(float x, float y, float z) {
        float zr = x * sinY + z * cosY;
        return y * sinP + zr * cosP;
    }

    // ------------------------------------------------------------------ rigid transforms

    /** Rotation (3x3, row major) around a pivot, then a translation. Applied to world points. */
    public static final class Pose {
        public final float[] r = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        public float px, py, pz;   // pivot
        public float tx, ty, tz;   // translation

        public static Pose identity() {
            return new Pose();
        }

        /** Rotate around the world z axis (roll) by angle, about the pivot. */
        public Pose rollZ(float angle) {
            return mulLeft(new float[]{
                    (float) Math.cos(angle), -(float) Math.sin(angle), 0,
                    (float) Math.sin(angle), (float) Math.cos(angle), 0,
                    0, 0, 1});
        }

        /** Rotate around the world x axis by angle, about the pivot. */
        public Pose pitchX(float angle) {
            return mulLeft(new float[]{
                    1, 0, 0,
                    0, (float) Math.cos(angle), -(float) Math.sin(angle),
                    0, (float) Math.sin(angle), (float) Math.cos(angle)});
        }

        public Pose rotY(float angle) {
            return mulLeft(new float[]{
                    (float) Math.cos(angle), 0, (float) Math.sin(angle),
                    0, 1, 0,
                    -(float) Math.sin(angle), 0, (float) Math.cos(angle)});
        }

        private Pose mulLeft(float[] m) {
            float[] o = new float[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    o[i * 3 + j] = m[i * 3] * r[j] + m[i * 3 + 1] * r[3 + j] + m[i * 3 + 2] * r[6 + j];
                }
            }
            System.arraycopy(o, 0, r, 0, 9);
            return this;
        }

        /** Scale about the pivot (applied before any rotation added later). */
        public Pose scale(float sx, float sy, float sz) {
            return mulLeft(new float[]{sx, 0, 0, 0, sy, 0, 0, 0, sz});
        }

        public Pose pivot(float x, float y, float z) {
            px = x;
            py = y;
            pz = z;
            return this;
        }

        public Pose move(float x, float y, float z) {
            tx += x;
            ty += y;
            tz += z;
            return this;
        }

        float[] point(float x, float y, float z) {
            float dx = x - px, dy = y - py, dz = z - pz;
            return new float[]{
                    r[0] * dx + r[1] * dy + r[2] * dz + px + tx,
                    r[3] * dx + r[4] * dy + r[5] * dz + py + ty,
                    r[6] * dx + r[7] * dy + r[8] * dz + pz + tz};
        }

        float[] dir(float x, float y, float z) {
            return new float[]{
                    r[0] * x + r[1] * y + r[2] * z,
                    r[3] * x + r[4] * y + r[5] * z,
                    r[6] * x + r[7] * y + r[8] * z};
        }
    }

    // ------------------------------------------------------------------ quads

    /** Screen-space affine of the quad O, O+U, O+V (world space, after pose). Null if facing away. */
    private float[] quad(Pose pose, float[] o, float[] u, float[] v, float[] n, boolean cull) {
        float[] wo = pose.point(o[0], o[1], o[2]);
        float[] wu = pose.point(o[0] + u[0], o[1] + u[1], o[2] + u[2]);
        float[] wv = pose.point(o[0] + v[0], o[1] + v[1], o[2] + v[2]);
        if (cull) {
            float[] wn = pose.dir(n[0], n[1], n[2]);
            if (facing(wn[0], wn[1], wn[2]) <= 0.001f) {
                return null;
            }
        }
        float ox = sx(wo[0], wo[1], wo[2]), oy = sy(wo[0], wo[1], wo[2]);
        return new float[]{
                sx(wu[0], wu[1], wu[2]) - ox, sy(wu[0], wu[1], wu[2]) - oy,
                sx(wv[0], wv[1], wv[2]) - ox, sy(wv[0], wv[1], wv[2]) - oy,
                ox, oy};
    }

    /** Light: brighter on top, darker on the right. Returns black overlay alpha 0..255. */
    private int shade(Pose pose, float[] n) {
        float[] wn = pose.dir(n[0], n[1], n[2]);
        float light = 0.55f * wn[1] + 0.35f * (wn[2] * cosY + wn[0] * sinY) - 0.25f * (wn[0] * cosY - wn[2] * sinY);
        float dark = 0.42f - 0.42f * Math.max(-0.2f, Math.min(1f, light));
        return Math.max(0, Math.min(170, (int) (dark * 255)));
    }

    public void solidQuad(Canvas c, Pose pose, float[] o, float[] u, float[] v, float[] n,
                          int argb, float outlinePx, boolean cull) {
        float[] q = quad(pose, o, u, v, n, cull);
        if (q == null) return;
        c.push();
        c.transform(q[0] / K, q[1] / K, q[2] / K, q[3] / K, q[4], q[5]);
        if (outlinePx > 0) {
            int eu = (int) (outlinePx / Math.max(0.01f, len(q[0], q[1])) * K);
            int ev = (int) (outlinePx / Math.max(0.01f, len(q[2], q[3])) * K);
            c.fill(-eu, -ev, K + eu, K + ev, 0xFF000000);
        }
        c.fill(0, 0, K, K, argb);
        c.pop();
    }

    /**
     * A quad covered with a grid of pixel colours (rows top to bottom along V, columns along U), lit like
     * the other faces. colors[row][col] = 0xRRGGBB, or -1 for a hole. Returns false if it faces away.
     */
    public boolean pixelQuad(Canvas c, Pose pose, float[] o, float[] u, float[] v, float[] n, int[][] colors, float alpha) {
        float[] q = quad(pose, o, u, v, n, true);
        if (q == null) return false;
        int rows = colors.length, cols = colors[0].length;
        float dark = shade(pose, n) / 255f;
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24;
        c.push();
        c.transform(q[0] / K, q[1] / K, q[2] / K, q[3] / K, q[4], q[5]);
        for (int r = 0; r < rows; r++) {
            int y0 = r * K / rows, y1 = (r + 1) * K / rows;
            int start = 0;
            for (int col = 1; col <= cols; col++) {
                if (col < cols && colors[r][col] == colors[r][start]) continue;
                int rgb = colors[r][start];
                if (rgb >= 0) {
                    // overlap by a hair so no seams show between pixels
                    c.fill(start * K / cols - 2, y0 - 2, col * K / cols + 2, y1 + 2, a | Fx.lerpColor(rgb, 0, dark));
                }
                start = col;
            }
        }
        c.pop();
        return true;
    }

    // ------------------------------------------------------------------ boxes

    /**
     * A piece of a box: the sub-box [x0,x1]*[y0,y1]*[z0,z1] of the unit cube (0..1 per axis).
     * Faces on the original surface are textured; faces inside the box (cuts) show flesh.
     */
    public record Piece(float x0, float y0, float z0, float x1, float y1, float z1, boolean textured) {
        public static final Piece WHOLE = new Piece(0, 0, 0, 1, 1, 1, false);
    }

    /** Draws a box. (bx,by,bz) is the world position of the box's bottom centre, size its scale. */
    public void box(Canvas c, CubeSkin skin, Piece p, Pose pose, float bx, float by, float bz, float size,
                    float outlinePx, int cracks, long seed) {
        float w = skin.sx() * size, h = skin.sy() * size, d = skin.sz() * size;
        float x0 = bx - w / 2 + p.x0() * w, x1 = bx - w / 2 + p.x1() * w;
        float y0 = by + p.y0() * h, y1 = by + p.y1() * h;
        float z0 = bz - d / 2 + p.z0() * d, z1 = bz - d / 2 + p.z1() * d;

        // Faces: origin, U, V, normal, texture face, uv crop (u0,u1,v0,v1 as fractions), is original surface
        Object[][] faces = {
                {new float[]{x0, y1, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, 1},
                        skin.front(), new float[]{p.x0(), p.x1(), 1 - p.y1(), 1 - p.y0()}, p.z1() >= 1},
                {new float[]{x0, y1, z0}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z1 - z0}, new float[]{0, 1, 0},
                        skin.top(), new float[]{p.x0(), p.x1(), p.z0(), p.z1()}, p.y1() >= 1},
                {new float[]{x1, y1, z1}, new float[]{0, 0, z0 - z1}, new float[]{0, y0 - y1, 0}, new float[]{1, 0, 0},
                        skin.right(), new float[]{1 - p.z1(), 1 - p.z0(), 1 - p.y1(), 1 - p.y0()}, p.x1() >= 1},
                {new float[]{x0, y1, z0}, new float[]{0, 0, z1 - z0}, new float[]{0, y0 - y1, 0}, new float[]{-1, 0, 0},
                        skin.left(), new float[]{p.z0(), p.z1(), 1 - p.y1(), 1 - p.y0()}, p.x0() <= 0},
                {new float[]{x1, y1, z0}, new float[]{x0 - x1, 0, 0}, new float[]{0, y0 - y1, 0}, new float[]{0, 0, -1},
                        skin.back(), new float[]{1 - p.x1(), 1 - p.x0(), 1 - p.y1(), 1 - p.y0()}, p.z0() <= 0},
                {new float[]{x0, y0, z1}, new float[]{x1 - x0, 0, 0}, new float[]{0, 0, z0 - z1}, new float[]{0, -1, 0},
                        skin.bottom(), new float[]{p.x0(), p.x1(), 1 - p.z1(), 1 - p.z0()}, p.y0() <= 0},
        };

        // Silhouette outline first, so every face sits on a black border.
        if (outlinePx > 0) {
            for (Object[] f : faces) {
                solidQuad(c, pose, (float[]) f[0], (float[]) f[1], (float[]) f[2], (float[]) f[3], 0xFF000000, outlinePx, true);
            }
        }
        int faceIndex = 0;
        for (Object[] f : faces) {
            float[] o = (float[]) f[0], u = (float[]) f[1], v = (float[]) f[2], n = (float[]) f[3];
            float[] q = quad(pose, o, u, v, n, true);
            faceIndex++;
            if (q == null) continue;
            boolean surface = (Boolean) f[6] || p.textured();
            int shadeAlpha = shade(pose, n);
            if (surface) {
                drawTextured(c, q, (Face) f[4], (float[]) f[5]);
                if (skin.overlay() != null) {
                    Face of = overlayFace(skin.overlay(), faceIndex);
                    drawTextured(c, q, of, (float[]) f[5]);
                }
                if (cracks > 0 && (faceIndex == 1 || faceIndex == 2)) {
                    drawCrack(c, q, cracks, seed + faceIndex, faceIndex == 2);
                }
            } else {
                drawFlesh(c, q, skin.flesh(), skin.fleshDark(), seed + faceIndex);
            }
            if (shadeAlpha > 0) {
                c.push();
                c.transform(q[0] / K, q[1] / K, q[2] / K, q[3] / K, q[4], q[5]);
                c.fill(0, 0, K, K, shadeAlpha << 24);
                c.pop();
            }
        }
    }

    /**
     * A box in plain colours (for machines like the vacuum). Corners in world units, around the pose.
     * Faces get light/dark versions of the colour automatically.
     */
    public void solidBox(Canvas c, Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                         int rgb, float alpha, float outlinePx) {
        float[][][] faces = {
                {{x0, y1, z1}, {x1 - x0, 0, 0}, {0, y0 - y1, 0}, {0, 0, 1}},
                {{x0, y1, z0}, {x1 - x0, 0, 0}, {0, 0, z1 - z0}, {0, 1, 0}},
                {{x1, y1, z1}, {0, 0, z0 - z1}, {0, y0 - y1, 0}, {1, 0, 0}},
                {{x0, y1, z0}, {0, 0, z1 - z0}, {0, y0 - y1, 0}, {-1, 0, 0}},
                {{x1, y1, z0}, {x0 - x1, 0, 0}, {0, y0 - y1, 0}, {0, 0, -1}},
                {{x0, y0, z1}, {x1 - x0, 0, 0}, {0, 0, z0 - z1}, {0, -1, 0}},
        };
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24;
        if (outlinePx > 0) {
            for (float[][] f : faces) {
                solidQuad(c, pose, f[0], f[1], f[2], f[3], a, outlinePx, true);
            }
        }
        for (float[][] f : faces) {
            int dark = shade(pose, f[3]);
            int col = Fx.lerpColor(rgb, 0, dark / 255f);
            solidQuad(c, pose, f[0], f[1], f[2], f[3], a | col, 0, true);
        }
    }

    /** Screen position of a world point after a pose. */
    public float[] screen(Pose pose, float x, float y, float z) {
        float[] w = pose.point(x, y, z);
        return new float[]{sx(w[0], w[1], w[2]), sy(w[0], w[1], w[2])};
    }

    private static Face overlayFace(CubeSkin o, int index) {
        return switch (index) {
            case 1 -> o.front();
            case 2 -> o.top();
            case 3 -> o.right();
            case 4 -> o.left();
            case 5 -> o.back();
            default -> o.bottom();
        };
    }

    private void drawTextured(Canvas c, float[] q, Face face, float[] crop) {
        int u0 = Math.round(crop[0] * face.w()), u1 = Math.round(crop[1] * face.w());
        int v0 = Math.round(crop[2] * face.h()), v1 = Math.round(crop[3] * face.h());
        int tw = Math.max(1, u1 - u0), th = Math.max(1, v1 - v0);
        c.push();
        c.transform(q[0] / tw, q[1] / tw, q[2] / th, q[3] / th, q[4], q[5]);
        c.blit(face.texture(), 0, 0, face.u() + u0, face.v() + v0, tw, th, face.texW(), face.texH());
        c.pop();
    }

    /** Cut faces: raw meat with darker speckles, in a fixed pixel grid. */
    private void drawFlesh(Canvas c, float[] q, int flesh, int dark, long seed) {
        c.push();
        c.transform(q[0] / 8, q[1] / 8, q[2] / 8, q[3] / 8, q[4], q[5]);
        c.fill(0, 0, 8, 8, 0xFF000000 | flesh);
        java.util.Random r = new java.util.Random(seed * 31 + 7);
        for (int i = 0; i < 9; i++) {
            int x = r.nextInt(8), y = r.nextInt(8);
            c.fill(x, y, x + 1, y + 1, 0xFF000000 | dark);
        }
        // lighter rim so the cut reads as a cut
        c.fill(0, 0, 8, 1, 0x40FFFFFF);
        c.pop();
    }

    /** A jagged crack down the middle of a face (the line the cleaver will split along). */
    private void drawCrack(Canvas c, float[] q, int strength, long seed, boolean along) {
        c.push();
        c.transform(q[0] / 16, q[1] / 16, q[2] / 16, q[3] / 16, q[4], q[5]);
        java.util.Random r = new java.util.Random(seed);
        int x = 8;
        int len = strength >= 2 ? 16 : 9;
        for (int y = 0; y < len; y++) {
            x += r.nextInt(3) - 1;
            x = Math.max(6, Math.min(9, x));
            if (along) {
                c.fill(x, y, x + 1, y + 1, 0xE0100808);
            } else {
                c.fill(y < 16 ? x : 8, y, x + 1, y + 1, 0xE0100808);
            }
            if (r.nextInt(4) == 0) {
                int side = r.nextBoolean() ? 1 : -1;
                c.fill(x + side, y, x + side + 1, y + 1, 0x90100808);
            }
        }
        c.pop();
    }

    private static float len(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }
}

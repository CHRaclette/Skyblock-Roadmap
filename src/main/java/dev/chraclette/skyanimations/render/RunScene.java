package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A "run reward" scene with a CS2 case at the end. Used for Crystal Nucleus runs, dungeon reward
 * chests and RNG Meter drops; the {@link Style} decides what hangs at the top.
 *
 * Crystal Nucleus run: the Nucleus crystal hangs at the top, the loot falls out of it one by one
 * into a hotbar while a list says what you got. If there is a rare item, it comes last: a case
 * pops out of the crystal and opens like a CS2 case, the reel of every RNG-meter item spins and
 * lands on your drop, which then drops into the last slot.
 */
public final class RunScene implements Scene {

    /** One loot entry. color = rarity colour (RGB). */
    public record Loot(String name, int count, Object icon, int color, long coins, boolean powder) {
    }

    /** One possible item on the reel. */
    public record ReelItem(String name, Object icon, int color) {
    }

    /** Where the loot comes from. */
    public enum Source { CRYSTAL, CHEST, METER }

    /**
     * Look of the scene.
     * @param title      header text ("CRYSTAL NUCLEUS", "OBSIDIAN CHEST", "RNG METER")
     * @param subtitle   small line under the header (e.g. "Catacombs - Floor 7"), may be empty
     * @param accent     chest / meter colour (RGB)
     * @param rareTitle  big text after the case ("RARE NUCLEUS DROP!")
     * @param summary    start of the summary line ("Nucleus run")
     */
    public record Style(Source source, String title, String subtitle, int accent, String rareTitle, String summary) {
        public static Style nucleus() {
            return new Style(Source.CRYSTAL, "CRYSTAL NUCLEUS", "", 0xB05CFF, "RARE NUCLEUS DROP!", "Nucleus run");
        }

        public static Style chest(String chest, String floor, int accent) {
            return new Style(Source.CHEST, chest.toUpperCase(Locale.ROOT), floor, accent,
                    chest.toUpperCase(Locale.ROOT) + " JACKPOT!", chest);
        }

        public static Style meter(String source) {
            return new Style(Source.METER, "RNG METER", source, 0xFF55FF, "RNG METER DROP!", "RNG Meter");
        }
    }

    /** Colour of a dungeon reward chest by name (Wood, Gold, Diamond, Emerald, Obsidian, Bedrock). */
    public static int chestColor(String chest) {
        String c = chest.toLowerCase(Locale.ROOT);
        if (c.startsWith("wood")) return 0x9C6B30;
        if (c.startsWith("gold")) return 0xF2C230;
        if (c.startsWith("diamond")) return 0x5CE1E6;
        if (c.startsWith("emerald")) return 0x2EBD59;
        if (c.startsWith("obsidian")) return 0x4A2A78;
        if (c.startsWith("bedrock")) return 0x6A6A6A;
        return 0x9C6B30;
    }

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);
    }

    private static final int[] GEMS = {0xFFB12E, 0xB05CFF, 0x3DDC84, 0x3C8DFF, 0xFFE14A}; // amber amethyst jade sapphire topaz
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private final float INTRO;
    private static final float STEP = 0.48f;
    private static final float FLIGHT = 0.42f;
    private static final float CASE_SHAKE = 0.9f;
    private static final float REEL_IN = 0.35f;
    private static final float REEL_SPIN = 5.2f;
    private static final float REEL_HOLD = 1.0f;
    private static final float WIN_DROP = 0.55f;
    private static final float OUTRO = 3.0f;

    private final List<Loot> slots;      // what goes into the hotbar, last = rarest
    private final List<Loot> powders;    // shown as text only
    private final boolean rare;
    private final List<ReelItem> reel = new ArrayList<>();
    private final int winIndex;
    private final float winOffset;       // where inside the winning card the marker stops (-0.4..0.4)
    private final Hooks hooks;
    private final Particles fx = new Particles(1234);
    private final Random random;
    private final boolean test;
    private final Style style;

    private final float dropsEnd, caseStart, reelStart, reelStop, winLand, end;
    private float lastT = -1;
    private int lastTick = -1;
    private final boolean[] landed;
    private boolean caseOpened, spinStarted, reelDone, winLanded, introPlayed;

    /**
     * @param loot    all items from the bundle (any order)
     * @param rareIdx index in loot of the rare item, or -1 if none is rare
     * @param pool    every item on the Nucleus RNG meter, for the reel
     */
    public RunScene(List<Loot> loot, int rareIdx, List<ReelItem> pool, Hooks hooks, boolean test) {
        this(Style.nucleus(), loot, rareIdx, pool, hooks, test);
    }

    public RunScene(Style style, List<Loot> loot, int rareIdx, List<ReelItem> pool, Hooks hooks, boolean test) {
        this.style = style;
        this.INTRO = style.source == Source.METER ? 1.9f : style.source == Source.CHEST ? 1.2f : 0.9f;
        this.hooks = hooks;
        this.test = test;
        this.random = new Random(loot.hashCode());
        List<Loot> items = new ArrayList<>();
        List<Loot> pw = new ArrayList<>();
        Loot rareItem = rareIdx >= 0 ? loot.get(rareIdx) : null;
        for (Loot l : loot) {
            if (l == rareItem) continue;
            if (l.powder) pw.add(l);
            else items.add(l);
        }
        // cheapest first, so it builds up; at most 8 normal slots (+1 for the rare one)
        items.sort((a, b) -> Long.compare(a.coins * Math.max(1, a.count) , b.coins * Math.max(1, b.count)));
        while (items.size() > (rareItem != null ? 8 : 9)) items.remove(0);
        if (rareItem != null) items.add(rareItem);
        this.slots = items;
        this.powders = pw;
        this.rare = rareItem != null;
        this.landed = new boolean[slots.size()];

        int normal = rare ? slots.size() - 1 : slots.size();
        dropsEnd = INTRO + normal * STEP + FLIGHT;
        if (rare) {
            caseStart = dropsEnd + 0.3f;
            reelStart = caseStart + CASE_SHAKE + REEL_IN;
            reelStop = reelStart + REEL_SPIN;
            winLand = reelStop + REEL_HOLD + WIN_DROP;
            end = winLand + OUTRO;
            // build the reel: random RNG-meter items, the drop placed far enough in to spin a while
            List<ReelItem> p = pool.isEmpty() ? List.of(new ReelItem(rareItem.name, rareItem.icon, rareItem.color)) : pool;
            winIndex = 34 + random.nextInt(5);
            for (int i = 0; i < winIndex + 6; i++) {
                reel.add(i == winIndex ? new ReelItem(rareItem.name, rareItem.icon, rareItem.color) : p.get(random.nextInt(p.size())));
            }
            winOffset = (random.nextFloat() - 0.5f) * 0.8f;
        } else {
            caseStart = reelStart = reelStop = winLand = dropsEnd;
            end = dropsEnd + OUTRO;
            winIndex = 0;
            winOffset = 0;
        }
    }

    public float duration() {
        return end;
    }

    @Override
    public boolean finished(float t) {
        return t >= end;
    }

    // ---------------------------------------------------------------- layout helpers

    private float k(Canvas c) {
        return Math.min(1.6f, Math.max(0.8f, c.height() / 250f));
    }

    private float crystalX(Canvas c) {
        return c.width() / 2f;
    }

    private float crystalY(Canvas c) {
        return c.height() * (style.source == Source.CRYSTAL ? 0.2f : 0.17f);
    }

    private float slotSize(Canvas c) {
        return 20 * k(c);
    }

    private float slotX(Canvas c, int i) {
        float s = slotSize(c);
        float total = slots.size() * s;
        return c.width() / 2f - total / 2f + i * s + s / 2f;
    }

    private float slotY(Canvas c) {
        return c.height() - 42 * k(c);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(Canvas c, float t) {
        float dt = lastT < 0 ? 0 : Math.max(0, Math.min(0.1f, t - lastT));
        lastT = t;
        int w = c.width(), h = c.height();
        float k = k(c);
        float fade = Fx.smooth(t / 0.4f) * (1 - Fx.smooth((t - (end - 0.5f)) / 0.5f));
        c.fill(0, 0, w, h, Fx.alpha(0x05040A, 0.66f * fade));
        if (!introPlayed) {
            introPlayed = true;
            hooks.sound(Cue.MAGIC, 1.4f, 0.8f);
        }

        boolean spinning = rare && t >= caseStart && t < winLand;
        switch (style.source) {
            case CRYSTAL -> crystal(c, t, k, fade, spinning ? 0.35f : 1f);
            case CHEST -> chest(c, t, k, fade, spinning ? 0.35f : 1f);
            case METER -> meter(c, t, k, fade, spinning ? 0.35f : 1f);
        }
        hotbar(c, t, k, fade);
        drops(c, t, dt, k);
        feed(c, t, k, fade);
        if (rare && t >= caseStart && t < winLand + 0.2f) {
            caseOpening(c, t, dt, k);
        }
        fx.update(dt);
        fx.render(c, fade);
        if (t >= (rare ? winLand : dropsEnd)) {
            summary(c, t - (rare ? winLand : dropsEnd), k, fade);
        }
    }

    // ---------------------------------------------------------------- crystal

    /** The Nucleus "crystal": a pixel-art Matterhorn with a waving Swiss flag on the summit. */
    private static final String MOUNTAIN = "skyanimations:textures/nucleus/matterhorn.png";
    private static final int MW = 58, MH = 41, PEAK_X = 27, PEAK_Y = 2;
    private static final int[] ALPENGLOW = {0xFFB36B, 0xFFD9B0, 0xFF9E7A, 0xFFE6C8};

    private void crystal(Canvas c, float t, float k, float fade, float alpha) {
        float cx = crystalX(c), cy = crystalY(c) + (float) Math.sin(t * 1.6f) * 1.5f * k;
        float appear = Fx.backOut(t / 0.6f);
        if (appear < 0.02f) return;
        float a = fade * alpha;
        float s = 1.25f * k * appear;           // texel size in GUI pixels
        int glowCol = ALPENGLOW[(int) (t * 1.2f) % ALPENGLOW.length];
        Fx.glow(c, cx, cy - 4 * k, 46 * k * appear, glowCol, a * 0.7f, 2);
        Fx.rays(c, cx, cy - 8 * k, 80 * k * appear, 10, t * 0.25f, 0xFFE2C0, a * 0.5f);

        c.push();
        c.translate(cx, cy);
        c.scale(s, s);
        c.translate(-MW / 2f, -MH / 2f);
        if (a > 0.05f) c.blit(MOUNTAIN, 0, 0, 0, 0, MW, MH, MW, MH);
        flag(c, t, a);
        c.pop();

        // a glint running along the sunlit ridge now and then
        float g = (t * 0.45f) % 1f;
        if (g < 0.35f) {
            float p = g / 0.35f;
            float gx = cx + (PEAK_X - MW / 2f - p * 22) * s, gy = cy + (PEAK_Y - MH / 2f + p * 26) * s;
            Fx.glow(c, gx, gy, 6 * k, 0xFFFFFF, a * (1 - Math.abs(p - 0.5f) * 2) * 0.9f, 1);
        }
        // caption under the cloud bank
        float ta = a * Fx.smooth((t - 0.2f) / 0.4f);
        Fx.centered(c, style.title, cx, cy + MH / 2f * s + 3 * k, 1.0f * k, Fx.alpha(0xFFF1E6, ta), true);
    }

    /** Swiss flag on the summit (in sprite texels): pole + a red flag with a white cross that waves. */
    private void flag(Canvas c, float t, float a) {
        int px = PEAK_X, top = PEAK_Y - 13;
        c.fill(px, top, px + 1, PEAK_Y + 1, Fx.alpha(0xD8D8D8, a));
        c.fill(px, top - 1, px + 1, top, Fx.alpha(0xFFE14A, a)); // gold knob
        int fw = 11, fh = 9;
        for (int x = 0; x < fw; x++) {
            // wave: columns further from the pole move more
            int off = Math.round((float) Math.sin(t * 6 - x * 0.7f) * (x / (float) fw) * 1.6f);
            int x0 = px + 1 + x, y0 = top + off;
            int shade = (float) Math.sin(t * 6 - x * 0.7f) > 0.3f ? 0xC8102E : 0xE2192E;
            c.fill(x0, y0, x0 + 1, y0 + fh, Fx.alpha(shade, a));
            // white cross: 3 wide arms, centred
            boolean vertical = x >= 4 && x <= 6;
            if (vertical) c.fill(x0, y0 + 1, x0 + 1, y0 + 8, Fx.alpha(0xFFFFFF, a));
            else if (x >= 2 && x <= 8) c.fill(x0, y0 + 3, x0 + 1, y0 + 6, Fx.alpha(0xFFFFFF, a));
        }
    }

    /** A dungeon reward chest at the top: its lid swings open and the loot flies out. */
    private void chest(Canvas c, float t, float k, float fade, float alpha) {
        float cx = crystalX(c), cy = crystalY(c) + 12 * k;
        float appear = Fx.backOut(t / 0.5f);
        float a = fade * alpha;
        if (appear <= 0.01f) return;
        float w = 40 * k * appear, h = 24 * k * appear, lidH = 9 * k * appear;
        int col = style.accent;
        float open = Fx.easeOut((t - 0.55f) / 0.35f);
        if (open > 0) {
            Fx.glow(c, cx, cy - h / 2, 50 * k * open, Fx.lerpColor(col, 0xFFE8A0, 0.6f), a * 0.9f * open, 2);
            Fx.rays(c, cx, cy - h / 2, 80 * k * open, 12, t * 0.4f, 0xFFE8A0, a * 0.6f * open);
        }
        int x1 = Math.round(cx - w / 2), x2 = Math.round(cx + w / 2);
        int top = Math.round(cy - h / 2), bottom = Math.round(cy + h / 2);
        c.fill(x1 - 1, top - 1, x2 + 1, bottom + 1, Fx.alpha(0x0A0A0A, a));
        c.fill(x1, top, x2, bottom, Fx.alpha(col, a));
        c.fill(x1, bottom - Math.round(3 * k), x2, bottom, Fx.alpha(Fx.darken(col, 0.35f), a));
        // planks / bands
        for (int i = 1; i < 3; i++) {
            int yy = top + Math.round(i * h / 3);
            c.fill(x1, yy, x2, yy + 1, Fx.alpha(Fx.darken(col, 0.25f), a));
        }
        c.fill(x1, top, x2, top + Math.round(2 * k), Fx.alpha(Fx.brighten(col, 0.3f), a));
        // latch
        c.fill(Math.round(cx - 2 * k), top - Math.round(2 * k), Math.round(cx + 2 * k), top + Math.round(4 * k), Fx.alpha(0xD8D8D8, a));
        // lid (hinged at the back: it rises and flips over)
        c.push();
        c.translate(cx, top);
        c.translate(0, -open * 14 * k);
        c.rotate(-open * 0.35f);
        int lw = Math.round(w / 2);
        int lh = Math.round(lidH * (1 - 0.5f * open));
        c.fill(-lw - 1, -lh - 1, lw + 1, 1, Fx.alpha(0x0A0A0A, a));
        c.fill(-lw, -lh, lw, 0, Fx.alpha(Fx.brighten(col, 0.12f), a));
        c.fill(-lw, -lh, lw, -lh + Math.round(2 * k), Fx.alpha(Fx.brighten(col, 0.35f), a));
        c.pop();
        if (open > 0 && open < 1 && t - 0.55f < 0.06f) hooks.sound(Cue.CASE_OPEN, 0.8f, 0.6f);
        header(c, t, k, a, cx, top - lidH - 27 * k);
    }

    /** RNG meter: a pink bar fills to 100 %, flashes, and the case is on its way. */
    private void meter(Canvas c, float t, float k, float fade, float alpha) {
        float cx = crystalX(c), cy = crystalY(c) + 8 * k;
        float a = fade * alpha * Fx.smooth(t / 0.3f);
        float w = 150 * k, h = 9 * k;
        float fill = 0.62f + 0.38f * Fx.easeInOut(Fx.clamp01((t - 0.3f) / 1.1f));
        boolean full = fill >= 0.999f;
        int x1 = Math.round(cx - w / 2), x2 = Math.round(cx + w / 2), y1 = Math.round(cy - h / 2), y2 = Math.round(cy + h / 2);
        c.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, Fx.alpha(0x000000, a * 0.9f));
        c.fill(x1, y1, x2, y2, Fx.alpha(0x2A1A2E, a));
        int fx2 = Math.round(x1 + w * fill);
        int col = full ? Fx.lerpColor(0xFF55FF, 0xFFFFFF, 0.5f + 0.5f * (float) Math.sin(t * 14)) : 0xFF55FF;
        c.fill(x1, y1, fx2, y2, Fx.alpha(col, a));
        c.fill(x1, y1, fx2, y1 + Math.max(1, Math.round(2 * k)), Fx.alpha(0xFFB8FF, a));
        // stripes moving along the bar
        for (float sx = x1 + ((t * 30 * k) % (10 * k)); sx < fx2 - 3 * k; sx += 10 * k) {
            c.fill(Math.round(sx), y1, Math.round(sx + 3 * k), y2, Fx.alpha(0xFFFFFF, a * 0.18f));
        }
        String pct = Math.round(fill * 100) + "%";
        Fx.centered(c, pct, cx, cy - 4 * k, 0.8f * k, Fx.alpha(0xFFFFFF, a), true);
        if (full) {
            float ft = t - 1.4f;
            if (ft >= 0 && ft < 0.05f) {
                hooks.sound(Cue.LEVELUP, 1.4f, 0.8f);
                fx.burst(cx, cy, 40, 40 * k, 140 * k, 30 * k, 0.8f, 1, 2.5f * k, 0xFF55FF, 0xFFFFFF, 0xFFB8FF);
            }
            Fx.glow(c, cx, cy, w * 0.6f, 0xFF55FF, a * 0.5f * (1 - Fx.smooth(ft / 1.5f)), 2);
        } else if (((int) (fill * 40)) != ((int) ((fill - 0.01f) * 40)) && t > 0.3f) {
            // nothing: the TICK sound is reserved for the reel
        }
        header(c, t, k, a, cx, cy - h - 18 * k);
    }

    private void header(Canvas c, float t, float k, float a, float cx, float y) {
        float ta = a * Fx.smooth((t - 0.2f) / 0.4f);
        Fx.centered(c, style.title, cx, y, 1.1f * k, Fx.alpha(Fx.lerpColor(style.accent, 0xFFFFFF, 0.55f), ta), true);
        if (!style.subtitle.isEmpty()) {
            Fx.centered(c, style.subtitle, cx, y + 11 * k, 0.75f * k, Fx.alpha(0xBBBBBB, ta), false);
        }
    }

    // ---------------------------------------------------------------- hotbar + drops

    private void hotbar(Canvas c, float t, float k, float fade) {
        float appear = Fx.easeOut((t - 0.3f) / 0.4f);
        if (appear <= 0) return;
        float s = slotSize(c);
        float y = slotY(c) - (1 - appear) * 40 * k;
        float x0 = slotX(c, 0) - s / 2f;
        c.fill(Math.round(x0 - 2), Math.round(y - s / 2 - 2), Math.round(x0 + slots.size() * s + 2), Math.round(y + s / 2 + 2),
                Fx.alpha(0x000000, 0.85f * fade));
        for (int i = 0; i < slots.size(); i++) {
            float sx = slotX(c, i);
            boolean last = rare && i == slots.size() - 1;
            int x1 = Math.round(sx - s / 2 + 1), y1 = Math.round(y - s / 2 + 1), x2 = Math.round(sx + s / 2 - 1), y2 = Math.round(y + s / 2 - 1);
            c.fill(x1, y1, x2, y2, Fx.alpha(0x373737, fade));
            c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, Fx.alpha(0x8B8B8B, fade));
            c.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, Fx.alpha(0x5A5A5A, fade));
            if (last && !winLanded) {
                // the mystery slot pulses gold
                float p = 0.4f + 0.4f * (float) Math.sin(t * 6);
                c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, Fx.alpha(0xFFD23A, p * 0.6f * fade));
                Fx.centered(c, "?", sx, y - 4 * k, k, Fx.alpha(0xFFFFFF, fade), true);
            }
            if (landed[i]) {
                Loot l = slots.get(i);
                float pop = Fx.backOut(Math.min(1, (t - landTime(i)) / 0.25f));
                float isz = s * 0.8f / 16f * pop;
                c.push();
                c.translate(sx, y);
                c.scale(isz, isz);
                if (l.icon != null) c.item(l.icon, -8, -8);
                c.pop();
                if (l.count > 1) {
                    String n = l.count > 999 ? (l.count / 1000) + "k" : String.valueOf(l.count);
                    c.push();
                    c.translate(sx + s / 2 - 2, y + s / 2 - 2);
                    c.scale(k * 0.75f, k * 0.75f);
                    c.text(n, -c.textWidth(n), -8, Fx.alpha(0xFFFFFF, fade), true);
                    c.pop();
                }
                // rarity flash when it lands
                float flash = 1 - Fx.smooth((t - landTime(i)) / 0.5f);
                if (flash > 0) c.fill(x1, y1, x2, y2, Fx.alpha(l.color, flash * 0.6f * fade));
            }
        }
    }

    private float dropTime(int i) {
        return INTRO + i * STEP;
    }

    private float landTime(int i) {
        if (rare && i == slots.size() - 1) return winLand;
        return dropTime(i) + FLIGHT;
    }

    private void drops(Canvas c, float t, float dt, float k) {
        float cx = crystalX(c), cy = crystalY(c);
        int normal = rare ? slots.size() - 1 : slots.size();
        for (int i = 0; i < normal; i++) {
            float st = dropTime(i);
            if (t < st) continue;
            float p = (t - st) / FLIGHT;
            Loot l = slots.get(i);
            if (p >= 1) {
                if (!landed[i]) land(c, i, k);
                continue;
            }
            if (p < 0.05f && t - dt < st) hooks.sound(Cue.SWOOSH, 1.3f, 0.5f);
            float tx = slotX(c, i), ty = slotY(c);
            float e = Fx.easeIn(p);
            float x = cx + (tx - cx) * Fx.smooth(p);
            float y = cy + (ty - cy) * e - (float) Math.sin(p * Math.PI) * 18 * k;
            float sc = (1.6f - 0.6f * p) * k;
            c.push();
            c.translate(x, y);
            c.rotate((1 - p) * 6.28f);
            c.scale(sc, sc);
            if (l.icon != null) c.item(l.icon, -8, -8);
            c.pop();
            if (random.nextFloat() < 0.6f) {
                P tr = fx.add(x, y, 0, 0, 0.4f, 2 * k, l.color);
                tr.fadeIn = false;
            }
        }
    }

    private void land(Canvas c, int i, float k) {
        landed[i] = true;
        Loot l = slots.get(i);
        hooks.sound(Cue.TOAST, 0.9f + i * 0.08f, 0.7f);
        fx.burst(slotX(c, i), slotY(c), 10, 20 * k, 50 * k, 60 * k, 0.5f, 1, 2 * k, l.color, 0xFFFFFF);
    }

    /** "What you got" list between the crystal and the hotbar. */
    private void feed(Canvas c, float t, float k, float fade) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Float> times = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (!landed[i]) continue;
            Loot l = slots.get(i);
            lines.add("+" + (l.count > 1 ? NUM.format(l.count) + "x " : "") + l.name);
            colors.add(l.color);
            times.add(landTime(i));
        }
        float powderAt = INTRO + 0.2f;
        for (int i = 0; i < powders.size(); i++) {
            if (t < powderAt + i * 0.25f) continue;
            Loot l = powders.get(i);
            lines.add("+" + NUM.format(l.count) + " " + l.name);
            colors.add(l.color);
            times.add(powderAt + i * 0.25f);
        }
        int max = rare && t >= winLand ? 6 : 9;
        int from = Math.max(0, lines.size() - max);
        float lineH = 10 * k;
        float y0 = slotY(c) - slotSize(c) - 12 * k - (lines.size() - from) * lineH;
        if (rare && t >= caseStart && t < winLand) y0 = c.height() * 2; // hide behind the case
        // after a rare win the feed makes room for the big title: it fades and slides down a bit
        float outroA = 1;
        if (rare && t >= winLand) {
            float o = Fx.smooth((t - winLand) / 0.35f);
            outroA = 1 - 0.75f * o;
            y0 += o * 14 * k;
        }
        fade *= outroA;
        for (int i = from; i < lines.size(); i++) {
            float age = t - times.get(i);
            float a = Fx.smooth(age / 0.2f) * fade;
            float slide = (1 - Fx.easeOut(age / 0.25f)) * 12 * k;
            Fx.centered(c, lines.get(i), c.width() / 2f + slide, y0 + (i - from) * lineH, 0.9f * k, Fx.alpha(colors.get(i), a), false);
        }
    }

    // ---------------------------------------------------------------- CS2 case

    private void caseOpening(Canvas c, float t, float dt, float k) {
        int w = c.width(), h = c.height();
        float cx = w / 2f, cy = h * 0.45f;
        float ct = t - caseStart;
        Loot win = slots.get(slots.size() - 1);

        // 1) the case pops out of the crystal and shakes
        if (ct < CASE_SHAKE + 0.25f) {
            float fly = Fx.easeOut(ct / 0.3f);
            float y = crystalY(c) + (cy - crystalY(c)) * fly;
            float shake = ct > 0.3f ? (float) Math.sin(ct * 60) * 2.5f * k * Math.min(1, (ct - 0.3f) / 0.5f) : 0;
            float lid = ct > CASE_SHAKE ? Fx.easeOut((ct - CASE_SHAKE) / 0.25f) : 0;
            drawCase(c, cx + shake, y, k, lid);
            if (ct - dt < 0.3f && ct >= 0.3f) hooks.sound(Cue.RATTLE, 1.4f, 0.8f);
            if (ct - dt < CASE_SHAKE && ct >= CASE_SHAKE) {
                hooks.sound(Cue.CASE_OPEN, 1.0f, 1.0f);
                caseOpened = true;
                fx.burst(cx, y - 10 * k, 30, 40 * k, 120 * k, 40 * k, 0.8f, 1, 3 * k, 0xFFD23A, 0xFFFFFF, win.color);
            }
            return;
        }

        // 2) the reel
        float rt = t - reelStart;
        float panelIn = Fx.easeOut((t - (reelStart - REEL_IN)) / REEL_IN);
        float panelOut = 1 - Fx.smooth((t - (reelStop + REEL_HOLD)) / 0.3f);
        float pa = panelIn * panelOut;
        if (pa <= 0.01f) {
            winDrop(c, t, k, win);
            return;
        }
        float cardW = 46 * k, cardH = 56 * k, gap = 3 * k;
        float panelW = Math.min(w * 0.86f, cardW * 9), panelH = cardH + 16 * k;
        float px = cx - panelW / 2, py = cy - panelH / 2;
        c.fill(Math.round(px - 2), Math.round(py - 2), Math.round(px + panelW + 2), Math.round(py + panelH + 2), Fx.alpha(0x000000, 0.9f * pa));
        c.fill(Math.round(px), Math.round(py), Math.round(px + panelW), Math.round(py + panelH), Fx.alpha(0x1B1E24, pa));

        // where the reel is: decelerates like CS (ease-out quart), stops inside the winning card
        float target = winIndex * (cardW + gap) + cardW / 2 + winOffset * cardW;
        float p = Fx.clamp01(rt / REEL_SPIN);
        // exponential slow-down like the CS2 reel: fast start, long crawl past the last few cards
        float eased = (float) ((1 - Math.pow(2, -9 * p)) / (1 - Math.pow(2, -9)));
        float pos = target * eased;
        // tick sound each time a card edge passes the marker
        int tick = (int) Math.floor((pos + gap) / (cardW + gap));
        if (tick != lastTick && rt > 0 && p < 1) {
            lastTick = tick;
            hooks.sound(Cue.TICK, 1.6f + 0.4f * (1 - p), 0.5f);
        }
        if (p >= 1 && !reelDone) {
            reelDone = true;
            hooks.sound(Cue.CASE_WIN, 1.0f, 1.0f);
            hooks.sound(Cue.TOTEM, 1.2f, 0.35f);
        }
        float markerX = cx;
        for (int i = 0; i < reel.size(); i++) {
            float x = markerX - pos + i * (cardW + gap);
            if (x + cardW < px || x > px + panelW) continue;
            ReelItem it = reel.get(i);
            boolean winner = reelDone && i == winIndex;
            float grow = winner ? 1 + 0.08f * Fx.backOut(Math.min(1, (t - reelStop) / 0.3f)) : 1;
            if (winner) {
                // enchanted rainbow glow + sparks around the winning card
                float ht = t - reelStop;
                int rb = Fx.hsv((ht * 0.6f) % 1f, 0.7f, 1f);
                Fx.glow(c, x + cardW / 2, py + 8 * k + cardH / 2, cardW * 1.1f, rb, pa * 0.7f, 2);
                if (random.nextFloat() < 0.5f) {
                    P sp = fx.add(x + random.nextFloat() * cardW, py + 8 * k + random.nextFloat() * cardH,
                            (random.nextFloat() - 0.5f) * 40 * k, -30 * k * random.nextFloat(), 0.6f, 2 * k, rb);
                    sp.kind = Kind.SPARKLE;
                }
            }
            drawCard(c, x, py + 8 * k, cardW, cardH, it, k, pa * (reelDone && !winner ? 0.45f : 1f), grow, t);
        }
        // fade the edges like the CS reel
        for (int i = 0; i < 12; i++) {
            float a = (12 - i) / 12f * 0.9f * pa;
            int ew = Math.round(panelW * 0.012f);
            c.fill(Math.round(px + i * ew), Math.round(py), Math.round(px + (i + 1) * ew), Math.round(py + panelH), Fx.alpha(0x1B1E24, a));
            c.fill(Math.round(px + panelW - (i + 1) * ew), Math.round(py), Math.round(px + panelW - i * ew), Math.round(py + panelH), Fx.alpha(0x1B1E24, a));
        }
        // the yellow marker
        int mx = Math.round(markerX);
        c.fill(mx - 1, Math.round(py - 4), mx + 1, Math.round(py + panelH + 4), Fx.alpha(0xFFD23A, pa));
        for (int i = 0; i < 4; i++) {
            c.fill(mx - 4 + i, Math.round(py - 6 + i), mx + 4 - i, Math.round(py - 5 + i), Fx.alpha(0xFFD23A, pa));
            c.fill(mx - 4 + i, Math.round(py + panelH + 5 - i), mx + 4 - i, Math.round(py + panelH + 6 - i), Fx.alpha(0xFFD23A, pa));
        }
        Fx.centered(c, reelDone ? win.name : "Opening...", cx, py - 14 * k, 1.0f * k,
                Fx.alpha(reelDone ? win.color : 0xCCCCCC, pa), true);
    }

    private void drawCard(Canvas c, float x, float y, float w, float h, ReelItem it, float k, float a, float grow, float t) {
        c.push();
        c.translate(x + w / 2, y + h / 2);
        c.scale(grow, grow);
        c.translate(-w / 2, -h / 2);
        int iw = Math.round(w), ih = Math.round(h);
        // dark gradient with the rarity colour rising from the bottom
        for (int i = 0; i < ih; i += 2) {
            float f = i / (float) ih;
            int col = Fx.lerpColor(0x2A2E36, Fx.darken(it.color, 0.35f), Fx.smooth((f - 0.3f) / 0.7f) * 0.8f);
            c.fill(0, i, iw, Math.min(ih, i + 2), Fx.alpha(col, a));
        }
        c.fill(0, ih - Math.max(2, Math.round(3 * k)), iw, ih, Fx.alpha(it.color, a));
        float s = w * 0.55f / 16f;
        c.push();
        c.translate(w / 2, h * 0.42f);
        c.scale(s, s);
        if (it.icon != null && a > 0.3f) c.item(it.icon, -8, -8);
        c.pop();
        String name = it.name;
        float ns = 0.5f * k;
        int maxW = Math.round((w - 4) / ns);
        while (name.length() > 3 && c.textWidth(name) > maxW) name = name.substring(0, name.length() - 2) + "…";
        c.push();
        c.translate(w / 2, h - 10 * k);
        c.scale(ns, ns);
        c.text(name, -c.textWidth(name) / 2, 0, Fx.alpha(0xFFFFFF, a), false);
        c.pop();
        c.pop();
    }

    /** CS-style weapon case: dark crate with gold trim and the Nucleus gem on the front. */
    private void drawCase(Canvas c, float cx, float cy, float k, float lid) {
        float w = 64 * k, h = 40 * k, lidH = 10 * k;
        int x1 = Math.round(cx - w / 2), x2 = Math.round(cx + w / 2);
        int top = Math.round(cy - h / 2), bottom = Math.round(cy + h / 2);
        c.fill(x1 - 1, top + Math.round(lidH) - 1, x2 + 1, bottom + 1, 0xFF0A0C10);
        c.fill(x1, top + Math.round(lidH), x2, bottom, 0xFF2B3A33);
        c.fill(x1, bottom - Math.round(4 * k), x2, bottom, 0xFF1E2924);
        // gold bands
        c.fill(x1, top + Math.round(lidH), x2, top + Math.round(lidH + 2 * k), 0xFFC9A24A);
        c.fill(x1 + Math.round(8 * k), top + Math.round(lidH), x1 + Math.round(10 * k), bottom, 0xFFC9A24A);
        c.fill(x2 - Math.round(10 * k), top + Math.round(lidH), x2 - Math.round(8 * k), bottom, 0xFFC9A24A);
        // gem emblem
        float gx = cx, gy = cy + 4 * k;
        for (int i = 0; i < 5; i++) {
            int s = Math.round((6 - i) * k);
            int col = style.source == Source.CRYSTAL ? GEMS[i] : Fx.lerpColor(style.accent, 0xFFFFFF, i * 0.12f);
            c.fill(Math.round(gx - s), Math.round(gy - 6 * k + i * 2.4f * k), Math.round(gx + s), Math.round(gy - 6 * k + (i + 1) * 2.4f * k), 0xFF000000 | col);
        }
        // lid: lifts and tilts away
        c.push();
        c.translate(cx, top + lidH);
        c.translate(0, -lid * 26 * k);
        c.rotate(-lid * 0.6f);
        int lw = Math.round(w / 2);
        c.fill(-lw - 1, -Math.round(lidH) - 1, lw + 1, 1, Fx.alpha(0x0A0C10, 1 - lid * 0.8f));
        c.fill(-lw, -Math.round(lidH), lw, 0, Fx.alpha(0x34463D, 1 - lid * 0.8f));
        c.fill(-lw, -Math.round(lidH), lw, -Math.round(lidH) + Math.round(2 * k), Fx.alpha(0xC9A24A, 1 - lid * 0.8f));
        c.pop();
        if (lid > 0) {
            Fx.rays(c, cx, top + lidH, 90 * k * lid, 12, lid, 0xFFE8A0, lid);
        }
    }

    /** After the reel: the won item drops into the last slot. */
    private void winDrop(Canvas c, float t, float k, Loot win) {
        float start = reelStop + REEL_HOLD;
        float p = Fx.clamp01((t - start) / WIN_DROP);
        int i = slots.size() - 1;
        if (p >= 1) {
            if (!winLanded) {
                winLanded = true;
                landed[i] = true;
                hooks.sound(Cue.SLAP, 1.0f, 0.9f);
                fx.burst(slotX(c, i), slotY(c), 40, 30 * k, 110 * k, 70 * k, 0.9f, 1, 3 * k, win.color, 0xFFFFFF, 0xFFD23A);
                P ring = fx.add(slotX(c, i), slotY(c), 0, 0, 0.6f, 40 * k, win.color);
                ring.kind = Kind.RING;
            }
            return;
        }
        float x = c.width() / 2f + (slotX(c, i) - c.width() / 2f) * Fx.smooth(p);
        float y = c.height() * 0.45f + (slotY(c) - c.height() * 0.45f) * Fx.easeIn(p);
        float sc = (3.2f - 2.2f * p) * k;
        Fx.glow(c, x, y, 24 * k * (1.5f - p), win.color, 0.8f, 2);
        c.push();
        c.translate(x, y);
        c.scale(sc, sc);
        if (win.icon != null) c.item(win.icon, -8, -8);
        c.pop();
    }

    // ---------------------------------------------------------------- summary

    private void summary(Canvas c, float st, float k, float fade) {
        long coins = 0;
        int count = 0;
        for (Loot l : slots) {
            coins += l.coins;
            count++;
        }
        float a = Fx.smooth(st / 0.3f) * fade;
        if (rare) {
            Loot win = slots.get(slots.size() - 1);
            float pop = Fx.backOut(Math.min(1, st / 0.35f));
            Fx.centered(c, style.rareTitle, c.width() / 2f, c.height() * 0.33f, 1.6f * k * pop, Fx.alpha(0xFF55FF, a), true);
            Fx.centered(c, win.name + (win.coins > 0 ? "  ·  " + NUM.format(win.coins) + " coins" : ""), c.width() / 2f,
                    c.height() * 0.33f + 18 * k, 1.0f * k, Fx.alpha(win.color, a), false);
        }
        String line = style.summary + ": " + count + (count == 1 ? " item" : " items")
                + (coins > 0 ? "  ·  " + NUM.format(coins) + " coins" : "") + (test ? "  ·  test run" : "");
        Fx.centered(c, line, c.width() / 2f, slotY(c) + slotSize(c) / 2 + 6 * k, 0.8f * k, Fx.alpha(0xD8D8E0, a), false);
    }
}

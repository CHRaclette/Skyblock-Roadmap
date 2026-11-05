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
    private static final float CASE_SHAKE = 1.5f;   // case flies in, turns, unlock button is pressed
    private static final float REEL_IN = 0.35f;
    private static final float REEL_SPIN = 5.2f;
    private static final float REEL_HOLD = 2.0f;    // winner glows, then the CS2 reveal card
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
    private float markerFlash;
    private boolean revealPlayed, pressPlayed;
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
        this.INTRO = style.source == Source.METER ? 1.9f : style.source == Source.CHEST ? 1.2f : 0.9f + NucleusStage.LENGTH;
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

    private final NucleusStage nucleus = new NucleusStage();

    private float crystalY(Canvas c) {
        return c.height() * (style.source == Source.CRYSTAL ? 0.2f : 0.17f);
    }

    /** Vertical centre of the CS2 reel (lower under the big mountain). */
    private float reelY() {
        return style.source == Source.CRYSTAL ? 0.5f : 0.45f;
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
            case CRYSTAL -> {
                if (t < NucleusStage.LENGTH + 0.1f) {
                    nucleus.draw(c, t, w / 2f, h * 0.47f, k, fade, fx, hooks::sound);
                }
                crystal(c, t - NucleusStage.LENGTH, k, fade, spinning ? 0.35f : 1f);
            }
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
        float appear = t <= 0 ? 0 : Fx.backOut(Fx.clamp01(t / 0.6f));
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
        if (alpha > 0.5f) Fx.centered(c, style.title, cx, cy + MH / 2f * s + 3 * k, 1.0f * k, Fx.alpha(0xFFF1E6, ta), true);
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

    private static final String CHEST_TEX = "textures/entity/chest/normal.png";
    private static final String ENDER_TEX = "textures/entity/chest/ender.png";

    /**
     * A Minecraft chest seen from the front (texture regions of the chest model: lid front,
     * body front, lock). open = 0..1 tilts the lid back and lets light out.
     * tint = colour washed over it (0 = none), so Obsidian/Bedrock/... chests get their look.
     */
    static void mcChest(Canvas c, String tex, float cx, float cy, float scale, float open, int tint, float tintA, float a) {
        if (a < 0.03f) return;
        c.push();
        c.translate(cx, cy);
        c.scale(scale, scale);
        // body (14 x 10), centred, bottom at y = +5
        c.blit(tex, -7, -5, 42, 33, 14, 10, 64, 64);
        if (tint != 0) c.fill(-7, -5, 7, 5, Fx.alpha(tint, tintA));
        // inside glow when open
        if (open > 0) {
            c.fill(-7, -6, 7, -5, Fx.alpha(0x1A1008, 1));
            c.fill(-6, -6, 6, -5, Fx.alpha(0xFFE8A0, open));
        }
        // lid: front face shrinks as it tilts back, its top face (14 x 14) appears above
        float lidFront = 5 * (1 - open);
        float lidTop = 9 * open;
        c.push();
        c.translate(0, -5 - lidFront - lidTop);
        if (lidTop > 0.5f) {
            c.push();
            c.scale(1, lidTop / 14f);
            c.blit(tex, -7, 0, 14, 0, 14, 14, 64, 64);
            if (tint != 0) c.fill(-7, 0, 7, 14, Fx.alpha(tint, tintA));
            c.pop();
        }
        c.translate(0, lidTop);
        if (lidFront > 0.5f) {
            c.push();
            c.scale(1, lidFront / 5f);
            c.blit(tex, -7, 0, 42, 14, 14, 5, 64, 64);
            if (tint != 0) c.fill(-7, 0, 7, 5, Fx.alpha(tint, tintA));
            c.pop();
        }
        c.pop();
        // lock (2 x 4) sits on the seam while closed, moves up with the lid
        float lockY = -5 - 2 - open * (lidTop + 2);
        c.blit(tex, -1, Math.round(lockY), 1, 1, 2, 4, 64, 64);
        c.pop();
    }

    /** A dungeon reward chest at the top: the real chest texture, tinted by type; the lid opens. */
    private void chest(Canvas c, float t, float k, float fade, float alpha) {
        float cx = crystalX(c), cy = crystalY(c) + 24 * k;
        float appear = Fx.backOut(t / 0.5f);
        float a = fade * alpha;
        if (appear <= 0.01f) return;
        int col = style.accent;
        float open = Fx.easeOut((t - 0.55f) / 0.35f);
        if (open > 0) {
            Fx.glow(c, cx, cy - 8 * k, 50 * k * open, Fx.lerpColor(col, 0xFFE8A0, 0.6f), a * 0.9f * open, 2);
            Fx.rays(c, cx, cy - 8 * k, 80 * k * open, 12, t * 0.4f, 0xFFE8A0, a * 0.6f * open);
        }
        boolean wood = style.title.startsWith("WOOD");
        mcChest(c, CHEST_TEX, cx, cy, 2.6f * k * appear, open, wood ? 0 : col, 0.5f, a);
        if (open > 0 && open < 1 && t - 0.55f < 0.06f) hooks.sound(Cue.CASE_OPEN, 0.8f, 0.6f);
        header(c, t, k, a, cx, cy - 60 * k);
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

    private static final String HOTBAR = "textures/gui/sprites/hud/hotbar.png";
    private static final String HOTBAR_SEL = "textures/gui/sprites/hud/hotbar_selection.png";

    /** The real Minecraft hotbar sprite, cut to as many slots as we need. */
    private void hotbar(Canvas c, float t, float k, float fade) {
        float appear = Fx.easeOut((t - 0.3f) / 0.4f);
        if (appear <= 0 || fade < 0.05f) return;
        float s = slotSize(c);
        float y = slotY(c) - (1 - appear) * 40 * k;
        int n = slots.size();
        float x0 = slotX(c, 0) - s / 2f - k;
        c.push();
        c.translate(x0, y - 11 * k);
        c.scale(k, k);
        c.blit(HOTBAR, 0, 0, 0, 0, 1 + 20 * n, 22, 182, 22);
        c.blit(HOTBAR, 1 + 20 * n, 0, 181, 0, 1, 22, 182, 22);
        if (rare && !winLanded) {
            // the mystery slot pulses with the selection frame
            int last = n - 1;
            if (((int) (t * 3)) % 2 == 0) c.blit(HOTBAR_SEL, 20 * last - 1, -1, 0, 0, 24, 23, 24, 23);
        }
        c.pop();
        for (int i = 0; i < n; i++) {
            float sx = slotX(c, i);
            boolean last = rare && i == n - 1;
            int x1 = Math.round(sx - s / 2 + 2 * k), y1 = Math.round(y - s / 2 + 2 * k), x2 = Math.round(sx + s / 2 - 2 * k), y2 = Math.round(y + s / 2 - 2 * k);
            if (last && !winLanded) {
                float p = 0.25f + 0.25f * (float) Math.sin(t * 6);
                c.fill(x1, y1, x2, y2, Fx.alpha(0xFFD23A, p * fade));
                Fx.centered(c, "?", sx, y - 4 * k, k, Fx.alpha(0xFFFFFF, fade), true);
            }
            if (landed[i]) {
                Loot l = slots.get(i);
                float pop = Fx.backOut(Math.min(1, (t - landTime(i)) / 0.25f));
                float isz = k * pop;
                c.push();
                c.translate(sx, y);
                c.scale(isz, isz);
                if (l.icon != null) c.item(l.icon, -8, -8);
                c.pop();
                if (l.count > 1) {
                    String cnt = l.count > 999 ? (l.count / 1000) + "k" : String.valueOf(l.count);
                    c.push();
                    c.translate(sx + 9 * k, y + 9 * k);
                    c.scale(k, k);
                    c.text(cnt, -c.textWidth(cnt), -8, Fx.alpha(0xFFFFFF, fade), true);
                    c.pop();
                }
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

    private static final float CARD_W = 50, CARD_H = 66, CARD_GAP = 4;

    private void caseOpening(Canvas c, float t, float dt, float k) {
        int w = c.width(), h = c.height();
        float cx = w / 2f, cy = h * reelY();
        float ct = t - caseStart;
        Loot win = slots.get(slots.size() - 1);

        // 1) the case flies in, turns on a turntable, the "Unlock Container" button gets pressed, lid opens
        if (ct < CASE_SHAKE + 0.3f) {
            float fly = Fx.easeOut(ct / 0.35f);
            float y = crystalY(c) + (cy - 10 * k - crystalY(c)) * fly;
            float turn = (float) Math.cos(Math.min(1, ct / 1.0f) * Math.PI * 2) ; // one full turn, ends facing us
            float sx = 0.25f + 0.75f * Math.abs(turn);
            float press = 1.05f;
            float lid = ct > CASE_SHAKE ? Fx.easeOut((ct - CASE_SHAKE) / 0.28f) : 0;
            float shake = ct > press && ct < CASE_SHAKE ? (float) Math.sin(ct * 70) * 2.2f * k : 0;
            // spotlight under the case
            Fx.glow(c, cx, y + 26 * k, 70 * k, style.source == Source.CRYSTAL ? 0xFFD9B0 : style.accent, 0.35f * fly, 3);
            c.fill(Math.round(cx - 34 * k * sx), Math.round(y + 24 * k), Math.round(cx + 34 * k * sx), Math.round(y + 26 * k), Fx.alpha(0x000000, 0.45f * fly));
            c.push();
            c.translate(cx + shake, 0);
            c.scale(sx, 1);
            c.translate(-cx, 0);
            drawCase(c, cx, y, k, lid);
            c.pop();
            if (ct - dt < 0.35f && ct >= 0.35f) hooks.sound(Cue.SWOOSH, 1.2f, 0.7f);
            // CS2 style unlock button
            if (ct > 0.45f && ct < CASE_SHAKE + 0.15f) {
                float ba = Fx.smooth((ct - 0.45f) / 0.2f) * (1 - Fx.smooth((ct - CASE_SHAKE) / 0.15f));
                float pressed = ct > press ? 1 - 0.08f * (1 - Fx.clamp01((ct - press) / 0.18f)) : 1;
                float bw = 110 * k * pressed, bh = 16 * k * pressed, by = y + 40 * k;
                int col = ct > press ? Fx.lerpColor(0x7EE07A, 0x4CAF50, Fx.clamp01((ct - press) / 0.3f)) : 0x4CAF50;
                c.fill(Math.round(cx - bw / 2 - 1), Math.round(by - 1), Math.round(cx + bw / 2 + 1), Math.round(by + bh + 1), Fx.alpha(0x0B2A0C, ba));
                c.fill(Math.round(cx - bw / 2), Math.round(by), Math.round(cx + bw / 2), Math.round(by + bh), Fx.alpha(col, ba));
                c.fill(Math.round(cx - bw / 2), Math.round(by), Math.round(cx + bw / 2), Math.round(by + bh * 0.45f), Fx.alpha(0xFFFFFF, 0.12f * ba));
                Fx.centered(c, "UNLOCK CONTAINER", cx, by + bh / 2 - 3.5f * k * pressed, 0.8f * k * pressed, Fx.alpha(0xFFFFFF, ba), true);
                if (ct - dt < press && ct >= press && !pressPlayed) {
                    pressPlayed = true;
                    hooks.sound(Cue.TICK, 0.6f, 1.0f);
                    hooks.sound(Cue.RATTLE, 1.3f, 0.8f);
                }
            }
            if (ct - dt < CASE_SHAKE && ct >= CASE_SHAKE) {
                hooks.sound(Cue.CASE_OPEN, 1.0f, 1.0f);
                caseOpened = true;
                fx.burst(cx, y - 10 * k, 36, 40 * k, 130 * k, 40 * k, 0.8f, 1, 3 * k, 0xFFD23A, 0xFFFFFF, win.color);
            }
            return;
        }

        // 2) the reel
        float rt = t - reelStart;
        float panelIn = Fx.easeOut((t - (reelStart - REEL_IN)) / REEL_IN);
        float hold = t - reelStop;                       // time since the reel stopped
        float revealP = Fx.clamp01((hold - 0.45f) / 0.45f); // winner card leaves the reel
        float panelOut = 1 - Fx.smooth((hold - 0.45f) / 0.35f);
        float pa = panelIn * Math.max(0, panelOut);
        float cardW = CARD_W * k, cardH = CARD_H * k, gap = CARD_GAP * k;
        float panelW = Math.min(w * 0.9f, cardW * 9.4f), panelH = cardH + 18 * k;
        float px = cx - panelW / 2, py = cy - panelH / 2;
        float cardY = py + 9 * k;

        // where the reel is: exponential slow-down like the CS2 reel (fast start, long crawl past the last cards)
        float target = winIndex * (cardW + gap) + cardW / 2 + winOffset * cardW;
        float p = Fx.clamp01(rt / REEL_SPIN);
        double norm = 1 - Math.pow(2, -9);
        float eased = (float) ((1 - Math.pow(2, -9 * p)) / norm);
        float pos = target * eased;
        float speed = p >= 1 ? 0 : (float) (target * 9 * Math.log(2) * Math.pow(2, -9 * p) / norm / REEL_SPIN); // px per second
        float blur = Fx.clamp01(speed / (cardW * 30)); // 0..1 motion blur

        // tick sound + marker flash each time a card edge passes the marker
        int tick = (int) Math.floor((pos + gap) / (cardW + gap));
        if (tick != lastTick && rt > 0 && p < 1) {
            lastTick = tick;
            markerFlash = 1;
            hooks.sound(Cue.TICK, 1.5f + 0.5f * (1 - p), 0.55f);
        }
        markerFlash = Math.max(0, markerFlash - dt * 6);
        if (p >= 1 && !reelDone) {
            reelDone = true;
            hooks.sound(Cue.CASE_WIN, 1.0f, 1.0f);
        }

        if (pa > 0.01f) {
            // darker stage behind the reel, rarity light leaking from the strip
            c.fill(0, Math.round(py - 30 * k), w, Math.round(py + panelH + 30 * k), Fx.alpha(0x000000, 0.35f * pa));
            c.fill(Math.round(px - 2), Math.round(py - 2), Math.round(px + panelW + 2), Math.round(py + panelH + 2), Fx.alpha(0x000000, 0.9f * pa));
            for (int i = 0; i < panelH; i += 2) { // brushed-metal gradient
                float f = i / panelH;
                c.fill(Math.round(px), Math.round(py + i), Math.round(px + panelW), Math.round(Math.min(py + panelH, py + i + 2)),
                        Fx.alpha(Fx.lerpColor(0x262A33, 0x15171C, f), pa));
            }
            float markerX = cx;
            c.clip(Math.round(px), Math.round(py - 1), Math.round(px + panelW), Math.round(py + panelH + 16 * k));
            for (int i = 0; i < reel.size(); i++) {
                float x = markerX - pos + i * (cardW + gap);
                if (x + cardW < px || x > px + panelW) continue;
                ReelItem it = reel.get(i);
                boolean winner = reelDone && i == winIndex;
                if (winner && revealP > 0) continue; // drawn by the reveal
                float grow = winner ? 1 + 0.07f * Fx.backOut(Math.min(1, hold / 0.3f)) : 1;
                if (winner) {
                    int rb = Fx.hsv((hold * 0.6f) % 1f, 0.6f, 1f);
                    Fx.glow(c, x + cardW / 2, cardY + cardH / 2, cardW * 1.1f, it.color, pa * 0.6f, 2);
                    Fx.glow(c, x + cardW / 2, cardY + cardH / 2, cardW * 0.7f, rb, pa * 0.35f, 2);
                }
                drawCard(c, x, cardY, cardW, cardH, it, k, pa * (reelDone && !winner ? 0.4f : 1f), grow, t, blur);
                // floor reflection: the rarity colour mirrored under the strip
                for (int r = 0; r < 6; r++) {
                    float ra = (6 - r) / 6f * 0.18f * pa * (reelDone && !winner ? 0.4f : 1f);
                    c.fill(Math.round(x), Math.round(py + panelH + 2 + r * 2 * k), Math.round(x + cardW),
                            Math.round(py + panelH + 2 + (r + 1) * 2 * k), Fx.alpha(it.color, ra));
                }
            }
            c.unclip();
            // speed streaks while it flies
            if (blur > 0.15f) {
                Random sr = new Random(tick * 31L);
                for (int i = 0; i < 7; i++) {
                    float sy = cardY + sr.nextFloat() * cardH;
                    float sl = (0.2f + sr.nextFloat() * 0.5f) * panelW * blur;
                    float sxx = px + sr.nextFloat() * (panelW - sl);
                    c.fill(Math.round(sxx), Math.round(sy), Math.round(sxx + sl), Math.round(sy + 1), Fx.alpha(0xFFFFFF, 0.10f * blur * pa));
                }
            }
            // fade the edges like the CS reel
            for (int i = 0; i < 14; i++) {
                float a = (14 - i) / 14f * 0.95f * pa;
                int ew = Math.max(1, Math.round(panelW * 0.011f));
                c.fill(Math.round(px + i * ew), Math.round(py), Math.round(px + (i + 1) * ew), Math.round(py + panelH), Fx.alpha(0x15171C, a));
                c.fill(Math.round(px + panelW - (i + 1) * ew), Math.round(py), Math.round(px + panelW - i * ew), Math.round(py + panelH), Fx.alpha(0x15171C, a));
            }
            // the marker: thin glowing line with arrow heads, flashes on every tick
            int mx = Math.round(markerX);
            int mc = Fx.lerpColor(0xFFC21A, 0xFFFFFF, markerFlash * 0.6f);
            c.fill(mx - 2, Math.round(py - 3), mx + 2, Math.round(py + panelH + 3), Fx.alpha(mc, 0.25f * pa));
            c.fill(mx - 1, Math.round(py - 3), mx + 1, Math.round(py + panelH + 3), Fx.alpha(mc, pa));
            for (int i = 0; i < 5; i++) {
                c.fill(mx - 5 + i, Math.round(py - 8 + i), mx + 5 - i, Math.round(py - 7 + i), Fx.alpha(mc, pa));
                c.fill(mx - 5 + i, Math.round(py + panelH + 7 - i), mx + 5 - i, Math.round(py + panelH + 8 - i), Fx.alpha(mc, pa));
            }
            String head = reelDone ? "" : style.source == Source.METER ? "RNG Meter" : style.source == Source.CHEST ? "Opening chest..." : "Unlocking container...";
            if (!head.isEmpty()) Fx.centered(c, head, cx, py - 18 * k, 0.9f * k, Fx.alpha(0xCCCCCC, pa), false);
        }

        // 3) CS2 reveal: the winning card lifts out of the reel, grows, rarity light floods the screen
        if (revealP > 0) {
            float out = 1 - Fx.smooth((t - (reelStop + REEL_HOLD - 0.25f)) / 0.25f); // hands over to winDrop
            if (out <= 0.01f) {
                winDrop(c, t, k, win);
                return;
            }
            float rp = Fx.backOut(revealP);
            float g = 1.07f + 0.6f * rp;
            float startX = cx - pos + winIndex * (cardW + gap) + cardW / 2;
            float x = startX + (cx - startX) * Fx.smooth(revealP);
            float y = cardY + cardH / 2 + (cy - 4 * k - cardY - cardH / 2) * Fx.smooth(revealP);
            if (!revealPlayed) {
                revealPlayed = true;
                hooks.sound(Cue.TOTEM, 1.2f, 0.4f);
                hooks.sound(Cue.LEVELUP, 1.4f, 0.6f);
                fx.burst(cx, cy, 50, 40 * k, 150 * k, 40 * k, 1.0f, 1, 3 * k, win.color, 0xFFFFFF, 0xFFD23A);
                P ring = fx.add(cx, cy, 0, 0, 0.7f, 80 * k, win.color);
                ring.kind = Kind.RING;
            }
            float ht = t - reelStop;
            Fx.glow(c, x, y, 120 * k * rp, win.color, 0.55f * out, 3);
            Fx.rays(c, x, y, 110 * k * rp, 14, ht * 0.4f, win.color, 0.45f * out);
            Fx.rays(c, x, y, 80 * k * rp, 9, -ht * 0.6f, 0xFFFFFF, 0.25f * out);
            ReelItem wi = new ReelItem(win.name, win.icon, win.color);
            drawCard(c, x - cardW / 2, y - cardH / 2, cardW, cardH, wi, k, out, g, t, 0);
            String rn = rarityName(win.color);
            float la = Fx.smooth((revealP - 0.4f) / 0.6f) * out;
            if (rn != null && la > 0.01f) {
                String label = rn.toUpperCase(Locale.ROOT).replace("", " ").trim();
                Fx.centered(c, label, cx, y - cardH * g / 2 - 11 * k, 0.9f * k, Fx.alpha(win.color, la), true);
            }
            if (random.nextFloat() < 0.6f) {
                P sp = fx.add(x + (random.nextFloat() - 0.5f) * cardW * g, y + (random.nextFloat() - 0.5f) * cardH * g,
                        (random.nextFloat() - 0.5f) * 40 * k, -40 * k * random.nextFloat(), 0.7f, 2 * k, Fx.hsv(random.nextFloat(), 0.4f, 1f));
                sp.kind = Kind.SPARKLE;
            }
        } else if (pa <= 0.01f) {
            winDrop(c, t, k, win);
        }
    }

    private void drawCard(Canvas c, float x, float y, float w, float h, ReelItem it, float k, float a, float grow, float t) {
        drawCard(c, x, y, w, h, it, k, a, grow, t, 0);
    }

    /**
     * A CS2-style item card: image area (SkyBlock pack tooltip background if present, otherwise a dark gradient)
     * with the rarity colour glowing up from the bottom, a rarity bar, and an info strip with name + rarity.
     * blur = 0..1 stretches the icon sideways while the reel flies past.
     */
    private void drawCard(Canvas c, float x, float y, float w, float h, ReelItem it, float k, float a, float grow, float t, float blur) {
        c.push();
        c.translate(x + w / 2, y + h / 2);
        c.scale(grow, grow);
        c.translate(-w / 2, -h / 2);
        int iw = Math.round(w), ih = Math.round(h);
        int imgH = Math.round(h * 0.72f);
        String rarity = rarityName(it.color);
        String bg = "hypixel_skyblock:textures/gui/sprites/tooltip/" + rarity + "_background.png";
        String frame = "hypixel_skyblock:textures/gui/sprites/tooltip/" + rarity + "_frame.png";
        c.fill(-1, -1, iw + 1, ih + 1, Fx.alpha(0x000000, a)); // outline
        if (rarity != null && c.hasTexture(frame) && c.hasTexture(bg)) {
            c.push();
            c.scale(iw / 100f, imgH / 100f);
            c.blit(bg, 0, 0, 0, 0, 100, 100, 100, 100);
            c.pop();
        } else {
            for (int i = 0; i < imgH; i += 2) {
                float f = i / (float) imgH;
                c.fill(0, i, iw, Math.min(imgH, i + 2), Fx.alpha(Fx.lerpColor(0x4A505C, 0x2A2E36, f), a));
            }
        }
        // rarity light rising from the bottom of the image, like a CS2 card
        for (int i = imgH / 4; i < imgH; i += 2) {
            float f = (i - imgH / 4f) / (imgH * 3 / 4f);
            c.fill(0, i, iw, Math.min(imgH, i + 2), Fx.alpha(it.color, a * 0.42f * f * f));
        }
        Fx.glow(c, w / 2, imgH * 0.55f, w * 0.38f, it.color, 0.22f * a, 2);
        if (rarity != null && c.hasTexture(frame)) nineSlice(c, frame, 0, 0, iw, imgH, 0.45f);
        int bar = Math.max(2, Math.round(3 * k));
        c.fill(0, imgH - bar, iw, imgH, Fx.alpha(it.color, a));
        // info strip
        c.fill(0, imgH, iw, ih, Fx.alpha(0x1C1F25, a));
        c.fill(0, imgH, iw, imgH + 1, Fx.alpha(0x000000, 0.5f * a));
        // the icon
        float s = w * 0.56f / 16f;
        c.push();
        c.translate(w / 2, imgH * 0.5f);
        c.scale(s * (1 + blur * 0.8f), s * (1 - blur * 0.15f));
        boolean model = grow > 1.0001f && Props.kind(it.name) != null;
        if (it.icon != null && a > 0.3f && !model) c.item(it.icon, -8, -8);
        c.pop();
        if (model) Props.draw(c, it.name, w / 2, imgH * 0.5f, w * 0.56f, t - reelStop);
        // name + rarity
        float ns = 0.48f * k;
        int maxW = Math.round((w - 4) / ns);
        String name = it.name;
        while (name.length() > 3 && c.textWidth(name) > maxW) name = name.substring(0, name.length() - 2) + "…";
        c.push();
        c.translate(w / 2, imgH + 3 * k);
        c.scale(ns, ns);
        c.text(name, -c.textWidth(name) / 2, 0, Fx.alpha(0xFFFFFF, a), false);
        c.pop();
        if (rarity != null) {
            String rl = rarity.toUpperCase(Locale.ROOT);
            float rs = 0.4f * k;
            c.push();
            c.translate(w / 2, imgH + 9.5f * k);
            c.scale(rs, rs);
            c.text(rl, -c.textWidth(rl) / 2, 0, Fx.alpha(it.color, a), false);
            c.pop();
        }
        if (a < 0.95f) c.fill(0, 0, iw, ih, Fx.alpha(0x101218, 1 - a)); // dim (textures ignore alpha)
        c.pop();
    }

    /** SkyBlock rarity name for a rarity colour (to pick the pack's tooltip frame). */
    private static String rarityName(int color) {
        return switch (color & 0xFFFFFF) {
            case 0xFFFFFF -> "common";
            case 0x55FF55 -> "uncommon";
            case 0x5555FF -> "rare";
            case 0xAA00AA -> "epic";
            case 0xFFAA00 -> "legendary";
            case 0xFF55FF -> "mythic";
            case 0xFF5555 -> "special";
            case 0x55FFFF -> "supreme";
            default -> null;
        };
    }

    /** Draws a 100x100 nine-slice sprite (border 24) stretched to w x h; s = size of the border in GUI px per texel. */
    static void nineSlice(Canvas c, String tex, int x, int y, int w, int h, float s) {
        float b = 24 * s;
        float iw = Math.max(0, w - 2 * b), ih = Math.max(0, h - 2 * b);
        // corners
        part(c, tex, x, y, s, s, 0, 0, 24, 24);
        part(c, tex, x + w - b, y, s, s, 76, 0, 24, 24);
        part(c, tex, x, y + h - b, s, s, 0, 76, 24, 24);
        part(c, tex, x + w - b, y + h - b, s, s, 76, 76, 24, 24);
        // edges (stretched)
        if (iw > 0) {
            part(c, tex, x + b, y, iw / 52f, s, 24, 0, 52, 24);
            part(c, tex, x + b, y + h - b, iw / 52f, s, 24, 76, 52, 24);
        }
        if (ih > 0) {
            part(c, tex, x, y + b, s, ih / 52f, 0, 24, 24, 52);
            part(c, tex, x + w - b, y + b, s, ih / 52f, 76, 24, 24, 52);
        }
    }

    private static void part(Canvas c, String tex, float x, float y, float sx, float sy, int u, int v, int w, int h) {
        c.push();
        c.translate(x, y);
        c.scale(sx, sy);
        c.blit(tex, 0, 0, u, v, w, h, 100, 100);
        c.pop();
    }

    /** The "weapon case": an ender chest, shaking, then the lid flips open with light pouring out. */
    private void drawCase(Canvas c, float cx, float cy, float k, float lid) {
        int tint = style.source == Source.CRYSTAL ? 0 : style.accent;
        mcChest(c, ENDER_TEX, cx, cy + 6 * k, 3.4f * k, lid, tint, 0.25f, 1f);
        if (lid > 0) {
            Fx.rays(c, cx, cy - 14 * k, 90 * k * lid, 12, lid, 0xFFE8A0, lid);
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
        float y = c.height() * reelY() + (slotY(c) - c.height() * reelY()) * Fx.easeIn(p);
        float sc = (3.2f - 2.2f * p) * k;
        Fx.glow(c, x, y, 24 * k * (1.5f - p), win.color, 0.8f, 2);
        if (!Props.draw(c, win.name, x, y, 16 * sc, t - reelStop)) {
            c.push();
            c.translate(x, y);
            c.scale(sc, sc);
            if (win.icon != null) c.item(win.icon, -8, -8);
            c.pop();
        }
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
            float titleY = c.height() * (style.source == Source.CRYSTAL ? 0.38f : 0.33f);
            Fx.centered(c, style.rareTitle, c.width() / 2f, titleY, 1.6f * k * pop, Fx.alpha(0xFF55FF, a), true);
            Fx.centered(c, win.name + (win.coins > 0 ? "  ·  " + NUM.format(win.coins) + " coins" : ""), c.width() / 2f,
                    titleY + 18 * k, 1.0f * k, Fx.alpha(win.color, a), false);
        }
        String line = style.summary + ": " + count + (count == 1 ? " item" : " items")
                + (coins > 0 ? "  ·  " + NUM.format(coins) + " coins" : "") + (test ? "  ·  test run" : "");
        Fx.centered(c, line, c.width() / 2f, slotY(c) + slotSize(c) / 2 + 6 * k, 0.8f * k, Fx.alpha(0xD8D8E0, a), false);
    }
}

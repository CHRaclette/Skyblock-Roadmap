package dev.chraclette.skyanimations.render;

import dev.chraclette.skyanimations.render.Cutscene.Cue;
import dev.chraclette.skyanimations.render.Particles.Kind;
import dev.chraclette.skyanimations.render.Particles.P;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Crop Fever: a short "you caught it" intro, a countdown bar while it lasts, and a summary card
 * of everything it dropped once it is cured. Pure Java; time is passed in as seconds.
 */
public final class FeverHud {

    public interface Hooks {
        void sound(Cue cue, float pitch, float volume);

        /** Crop icons to rain down during the intro. */
        List<Object> cropIcons();
    }

    /** One fever roll, for the summary. */
    public record Roll(String name, int count, Object icon, long coins, int tierColor, int rank) {
    }

    private static final float INTRO = 2.8f;
    private static final float SUMMARY = 6.5f;
    private static final int HOT = 0xFF6A1A, HOTTER = 0xFF2A1A, WARM = 0xFFC93C;

    private final Hooks hooks;
    private final Particles fx = new Particles(42);
    private final Random random = new Random(7);

    private boolean active;
    private boolean showIntro, showTimer, showSummary;
    private float startedAt = -100, endsAt, endedAt = -100;
    private float lastNow = -1;
    private int soundStep;
    private final List<Roll> rolls = new ArrayList<>();
    private List<Roll> summary = List.of();
    private long summaryCoins;
    private int summaryCount;
    private float spawnCarry;

    public FeverHud(Hooks hooks) {
        this.hooks = hooks;
    }

    public boolean active() {
        return active;
    }

    public void start(float now, int seconds, boolean intro, boolean timer, boolean summary) {
        active = true;
        startedAt = now;
        endsAt = now + seconds;
        endedAt = -100;
        showIntro = intro;
        showTimer = timer;
        showSummary = summary;
        soundStep = 0;
        rolls.clear();
        fx.clear();
    }

    /** Called when Hypixel says the fever is cured (or the timer ran out). */
    public void end(float now) {
        if (!active) return;
        active = false;
        endedAt = now;
        // merge same items
        Map<String, Roll> merged = new LinkedHashMap<>();
        long coins = 0;
        int count = 0;
        for (Roll r : rolls) {
            Roll m = merged.get(r.name);
            merged.put(r.name, m == null ? r : new Roll(r.name, m.count + r.count, m.icon, m.coins + r.coins,
                    r.rank > m.rank ? r.tierColor : m.tierColor, Math.max(r.rank, m.rank)));
            coins += r.coins;
            count++;
        }
        List<Roll> list = new ArrayList<>(merged.values());
        list.sort((a, b) -> b.rank != a.rank ? b.rank - a.rank : Long.compare(b.coins, a.coins));
        summary = list;
        summaryCoins = coins;
        summaryCount = count;
        if (showSummary) {
            hooks.sound(Cue.FEVER_END, 1.0f, 0.8f);
        }
    }

    public void add(Roll roll) {
        if (active) rolls.add(roll);
    }

    /** Prices can arrive late: update the coins of rolls already recorded. */
    public void reprice(java.util.function.Function<Roll, Long> pricer) {
        for (int i = 0; i < rolls.size(); i++) {
            Roll r = rolls.get(i);
            if (r.coins <= 0) {
                long p = pricer.apply(r);
                if (p > 0) rolls.set(i, new Roll(r.name, r.count, r.icon, p, r.tierColor, r.rank));
            }
        }
    }

    public boolean idle(float now) {
        return !active && now - endedAt > SUMMARY && fx.size() == 0;
    }

    public void render(Canvas c, float now, boolean cutscenePlaying) {
        float dt = lastNow < 0 ? 0 : Math.max(0, Math.min(0.1f, now - lastNow));
        lastNow = now;
        if (active && now > endsAt + 3) {
            end(now); // missed the "cured" message (e.g. changed server)
        }
        float since = now - startedAt;
        if (active && showIntro && since < INTRO && !cutscenePlaying) {
            intro(c, since, dt);
        } else {
            fx.update(dt);
        }
        if (active && showTimer && since > (showIntro ? INTRO - 0.4f : 0)) {
            timer(c, now, dt);
        }
        if (!active && showSummary && now - endedAt < SUMMARY && !cutscenePlaying) {
            summary(c, now - endedAt);
        }
    }

    // ------------------------------------------------------------------ intro

    private void introSounds(float t) {
        float[] times = {0.0f, 0.05f, 0.35f, 0.7f, 1.05f, 1.4f};
        while (soundStep < times.length && t >= times[soundStep]) {
            switch (soundStep) {
                case 0 -> hooks.sound(Cue.FEVER, 1.0f, 1.0f);
                case 1 -> hooks.sound(Cue.WHOOSH, 0.7f, 0.8f);
                default -> hooks.sound(Cue.NOTE, (float) Math.pow(2, (new int[]{0, 0, 0, 4, 7, 12}[soundStep] - 12) / 12.0) * 1.5f, 0.6f);
            }
            soundStep++;
        }
    }

    private void intro(Canvas c, float t, float dt) {
        introSounds(t);
        int w = c.width(), h = c.height();
        float in = Fx.smooth(t / 0.25f), out = 1 - Fx.smooth((t - (INTRO - 0.5f)) / 0.5f);
        float a = in * out;

        // heat vignette: hot edges that pulse
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 9);
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            float k = 1 - i / (float) bands;
            int band = Math.round(Math.min(w, h) * 0.035f);
            int col = Fx.alpha(Fx.lerpColor(HOTTER, WARM, i / (float) bands), a * pulse * 0.11f * k);
            c.fill(i * band, i * band, w - i * band, (i + 1) * band, col);
            c.fill(i * band, h - (i + 1) * band, w - i * band, h - i * band, col);
            c.fill(i * band, (i + 1) * band, (i + 1) * band, h - (i + 1) * band, col);
            c.fill(w - (i + 1) * band, (i + 1) * band, w - i * band, h - (i + 1) * band, col);
        }
        c.fill(0, 0, w, h, Fx.alpha(0x2A0A00, 0.25f * a));

        // crops raining down
        List<Object> icons = hooks.cropIcons();
        if (!icons.isEmpty() && t < INTRO - 0.6f) {
            spawnCarry += dt * 22;
            while (spawnCarry >= 1) {
                spawnCarry -= 1;
                P p = fx.add(random.nextFloat() * w, -20, (random.nextFloat() - 0.5f) * 30, 60 + random.nextFloat() * 80,
                        2.4f, 1 + random.nextFloat() * 0.8f, 0xFFFFFF);
                p.ay = 260;
                p.vrot = (random.nextFloat() - 0.5f) * 6;
                p.glyph = "";
                p.kind = Kind.SQUARE;
                p.toColor = -2 - random.nextInt(icons.size()); // icon index, see drawIcons
            }
        }
        // flames from the bottom edge
        spawnCarry2 += dt * 60;
        while (spawnCarry2 >= 1 && t < INTRO - 0.5f) {
            spawnCarry2 -= 1;
            P p = fx.add(random.nextFloat() * w, h + 4, (random.nextFloat() - 0.5f) * 10, -(60 + random.nextFloat() * 120),
                    0.9f + random.nextFloat() * 0.5f, 3 + random.nextFloat() * 4, random.nextBoolean() ? WARM : HOT);
            p.toColor = 0x401010;
        }
        fx.update(dt);
        drawIcons(c, icons, a);
        renderFlames(c, a);

        // thermometer filling up
        float fill = Fx.easeOut((t - 0.15f) / 0.9f);
        float cx = w / 2f, cy = h * 0.42f;
        float k = Math.min(2f, Math.max(1f, h / 240f));
        drawThermometer(c, cx + 8 * k - c.textWidth("CROP FEVER!") * 1.6f * k - 18 * k, cy - 2 * k, k, fill, a);

        // wobbly title, letter by letter
        String title = "CROP FEVER!";
        float pop = Fx.backOut((t - 0.05f) / 0.35f);
        float scale = 3.2f * k * pop;
        if (scale > 0.05f) {
            c.push();
            c.translate(cx + 8 * k, cy);
            c.scale(scale, scale);
            int x = -c.textWidth(title) / 2;
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                float wob = (float) Math.sin(t * 12 + i * 0.7f) * 1.2f;
                int col = Fx.lerpColor(WARM, HOTTER, 0.5f + 0.5f * (float) Math.sin(t * 6 + i * 0.5f));
                c.push();
                c.translate(x, wob - 4);
                Fx.bold(c, ch, 0, 0, Fx.alpha(col, a));
                c.pop();
                x += c.textWidth(ch) + 1;
            }
            c.pop();
        }
        float subA = Fx.smooth((t - 0.45f) / 0.3f) * out;
        if (subA > 0.02f) {
            Fx.centered(c, "You caught a case of the Crop Fever!", cx + 8 * k, cy + 20 * k, 1.0f * k, Fx.alpha(0xFFE7C2, subA), false);
            Fx.centered(c, "rare crop rolls for " + Math.round(endsAt - startedAt) + " seconds", cx + 8 * k, cy + 31 * k, 0.8f * k,
                    Fx.alpha(0xD9A27A, subA), false);
        }
    }

    private float spawnCarry2;

    /** Particles with toColor <= -2 are crop icons (index = -toColor - 2). */
    private void drawIcons(Canvas c, List<Object> icons, float a) {
        if (a < 0.05f) return;
        fx.forEach(p -> {
            if (p.toColor <= -2 && !icons.isEmpty()) {
                Object icon = icons.get((-p.toColor - 2) % icons.size());
                c.push();
                c.translate(p.x, p.y);
                c.rotate(p.rot);
                c.scale(p.size, p.size);
                c.item(icon, -8, -8);
                c.pop();
            }
        });
    }

    private void renderFlames(Canvas c, float a) {
        fx.forEach(p -> {
            if (p.toColor > -2) {
                float tt = p.age / p.life;
                int s = Math.max(1, Math.round(p.size * (1 - tt * 0.5f)));
                int col = Fx.alpha(Fx.lerpColor(p.color, 0x401010, tt), a * (1 - tt));
                c.fill(Math.round(p.x) - s / 2, Math.round(p.y) - s / 2, Math.round(p.x) + (s + 1) / 2, Math.round(p.y) + (s + 1) / 2, col);
            }
        });
    }

    private static void drawThermometer(Canvas c, float x, float y, float k, float fill, float a) {
        c.push();
        c.translate(x, y);
        c.scale(k, k);
        int glass = Fx.alpha(0xEDEDED, a), dark = Fx.alpha(0x000000, a), red = Fx.alpha(0xE8302A, a);
        // outline
        c.fill(-4, -26, 4, 6, dark);
        c.fill(-7, 3, 7, 15, dark);
        c.fill(-3, -25, 3, 5, glass);
        c.fill(-6, 4, 6, 14, glass);
        // bulb + mercury
        c.fill(-5, 5, 5, 13, red);
        int top = Math.round(4 - 28 * fill);
        c.fill(-1, top, 1, 6, red);
        // ticks
        for (int i = 0; i < 5; i++) {
            c.fill(3, -22 + i * 5, 5, -21 + i * 5, dark);
        }
        c.pop();
    }

    // ------------------------------------------------------------------ timer

    private void timer(Canvas c, float now, float dt) {
        int w = c.width();
        float left = Math.max(0, endsAt - now);
        float total = Math.max(1, endsAt - startedAt);
        float frac = left / total;
        float appear = Fx.easeOut((now - startedAt - (showIntro ? INTRO - 0.4f : 0)) / 0.4f);
        int bw = 140, bh = 5;
        float x = w / 2f - bw / 2f;
        float y = 30 - 20 * (1 - appear);
        boolean urgent = left < 10;
        float blink = urgent ? 0.6f + 0.4f * (float) Math.sin(now * 10) : 1;

        // panel
        c.fill(Math.round(x - 4), Math.round(y - 12), Math.round(x + bw + 4), Math.round(y + bh + 14), Fx.alpha(0x140A06, 0.75f * appear));
        int secs = (int) Math.ceil(left);
        String label = "CROP FEVER " + secs / 60 + ":" + String.format("%02d", secs % 60);
        Fx.centered(c, label, w / 2f, y - 10, 1f, Fx.alpha(Fx.lerpColor(HOTTER, WARM, frac), appear * blink), true);
        // bar
        c.fill(Math.round(x), Math.round(y), Math.round(x + bw), Math.round(y + bh), Fx.alpha(0x3A1A10, appear));
        int fillW = Math.round(bw * frac);
        for (int i = 0; i < fillW; i++) {
            float k = i / (float) bw;
            c.fill(Math.round(x) + i, Math.round(y), Math.round(x) + i + 1, Math.round(y + bh),
                    Fx.alpha(Fx.lerpColor(HOTTER, WARM, k), appear));
        }
        // flame licking at the end of the bar
        if (fillW > 0 && dt > 0) {
            P p = fx.add(x + fillW, y + bh / 2f, (random.nextFloat() - 0.5f) * 10, -20 - random.nextFloat() * 20, 0.5f,
                    2 + random.nextFloat() * 2, random.nextBoolean() ? WARM : HOT);
            p.toColor = 0x401010;
        }
        fx.update(dt);
        renderFlames(c, appear);
        // stats
        long coins = 0;
        for (Roll r : rolls) coins += r.coins;
        String stats = rolls.size() + (rolls.size() == 1 ? " drop" : " drops")
                + (coins > 0 ? " · " + NumberFormat.getIntegerInstance(Locale.US).format(coins) + " coins" : "");
        Fx.centered(c, stats, w / 2f, y + bh + 3, 0.75f, Fx.alpha(0xE8D2B8, appear), false);
    }

    // ------------------------------------------------------------------ summary

    private void summary(Canvas c, float t) {
        int w = c.width();
        float in = Fx.backOut(t / 0.35f), out = 1 - Fx.smooth((t - (SUMMARY - 0.5f)) / 0.5f);
        float a = Fx.clamp01(in) * out;
        int rows = Math.min(5, summary.size());
        int bw = 210, bh = 34 + Math.max(1, rows) * 13 + (summary.size() > rows ? 9 : 0);
        float x = w / 2f - bw / 2f, y = 22;
        c.push();
        c.translate(w / 2f, y);
        c.scale(in, in);
        c.translate(-w / 2f, -y);
        c.fill(Math.round(x), Math.round(y), Math.round(x + bw), Math.round(y + bh), Fx.alpha(0x140A06, 0.88f * out));
        c.fill(Math.round(x), Math.round(y), Math.round(x + bw), Math.round(y + 1), Fx.alpha(HOT, out));
        Fx.centered(c, "CROP FEVER CURED", w / 2f, y + 4, 1f, Fx.alpha(WARM, a), true);
        String head = summaryCount == 0 ? "No rolls this time..." :
                summaryCount + (summaryCount == 1 ? " roll" : " rolls")
                        + (summaryCoins > 0 ? " · " + NumberFormat.getIntegerInstance(Locale.US).format(summaryCoins) + " coins" : "");
        Fx.centered(c, head, w / 2f, y + 15, 0.8f, Fx.alpha(0xE8D2B8, a), false);
        for (int i = 0; i < rows; i++) {
            Roll r = summary.get(i);
            float ry = y + 28 + i * 13;
            float rowA = Fx.smooth((t - 0.3f - i * 0.12f) / 0.2f) * out;
            if (rowA <= 0.02f) continue;
            if (r.icon != null) {
                c.push();
                c.translate(x + 8, ry - 1);
                c.scale(0.7f, 0.7f);
                c.item(r.icon, 0, 0);
                c.pop();
            }
            String coins = r.coins > 0 ? NumberFormat.getIntegerInstance(Locale.US).format(r.coins) : "";
            String label = r.count + "x " + r.name;
            int maxW = bw - 30 - (coins.isEmpty() ? 0 : Math.round(c.textWidth(coins) * 0.75f) + 6);
            while (label.length() > 3 && c.textWidth(label) > maxW) label = label.substring(0, label.length() - 2) + "…";
            c.text(label, Math.round(x + 22), Math.round(ry + 1), Fx.alpha(r.tierColor, rowA), true);
            if (!coins.isEmpty()) {
                c.push();
                c.translate(x + bw - 6 - c.textWidth(coins) * 0.75f, ry + 2);
                c.scale(0.75f, 0.75f);
                c.text(coins, 0, 0, Fx.alpha(0xFFD24A, rowA), true);
                c.pop();
            }
        }
        if (summary.size() > rows) {
            Fx.centered(c, "+" + (summary.size() - rows) + " more", w / 2f, y + bh - 9, 0.7f, Fx.alpha(0x9A8A7A, a), false);
        }
        c.pop();
    }
}

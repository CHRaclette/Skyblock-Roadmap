package dev.chraclette.skyanimations.render;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * The text block under a big reveal, like in the clips: the name types itself in, a coin counter
 * counts up to the price, and small info lines ("1 in 52 corpses", "Your 3rd Shattered Locket").
 */
public final class RevealText {

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);
    private int lastTyped = -1, lastStep = -1;

    /**
     * t = seconds since the reveal. tick = played for each typed letter / counter step.
     * Returns the y below the block.
     */
    public float draw(Canvas c, String name, int nameColor, long coins, List<String> lines,
                      float cx, float y, float k, float t, float a, Runnable typeTick, Runnable countTick) {
        if (t < 0 || a <= 0.02f) return y;
        int typed = Math.min(name.length(), (int) (t * 30));
        if (typed != lastTyped && typed > 0 && typed < name.length() && typeTick != null) {
            lastTyped = typed;
            if (typed % 2 == 0) typeTick.run();
        }
        Fx.centered(c, name.substring(0, typed), cx, y, 1.25f * k, Fx.alpha(nameColor, a), true);
        float ly = y + 15 * k;
        float lt = t - name.length() / 30f;
        float la = a * Fx.smooth(lt / 0.3f);
        if (lines != null) {
            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                Fx.centered(c, line, cx, ly, 0.65f * k, Fx.alpha(0xC8C8C8, la), false);
                ly += 8 * k;
            }
        }
        if (coins > 0) {
            float cp = Fx.easeOut(Fx.clamp01((lt - 0.2f) / 1.8f));
            int step = (int) (cp * 30);
            if (step != lastStep && cp > 0 && cp < 1 && countTick != null) {
                lastStep = step;
                countTick.run();
            }
            String s = NUM.format((long) (coins * cp)) + " coins";
            Fx.centered(c, s, cx, ly + 2 * k, 0.95f * k, Fx.alpha(0xFFD23A, la), true);
            ly += 13 * k;
        }
        return ly;
    }
}

package dev.chraclette.butcher.render;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Small pop-ups that slide in on the right: Crop Fever rolls, pest spawns, pest kills.
 * Pure Java; time is passed in as seconds.
 */
public final class Toasts {

    public static final class Toast {
        public final String title;
        public final int titleColor;
        public String text;
        public final int accent;
        public final Object icon;
        public long coins;
        public int stack = 1;
        final float duration;
        float born;
        float bump;

        public Toast(String title, int titleColor, String text, int accent, Object icon, long coins, float duration) {
            this.title = title;
            this.titleColor = titleColor;
            this.text = text;
            this.accent = accent;
            this.icon = icon;
            this.coins = coins;
            this.duration = duration;
        }
    }

    private final List<Toast> list = new ArrayList<>();
    private static final int MAX = 6;

    /** Adds a toast; an identical toast still on screen is stacked ("x2") instead. */
    public void add(Toast toast, float now) {
        for (Toast t : list) {
            if (t.title.equals(toast.title) && t.text.equals(toast.text) && now - t.born < t.duration - 0.5f) {
                t.stack++;
                t.coins += toast.coins;
                t.born = now - Math.min(0.3f, now - t.born); // keep it on screen
                t.bump = now;
                return;
            }
        }
        toast.born = now;
        list.add(0, toast);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void clear() {
        list.clear();
    }

    public void render(Canvas c, float now) {
        Iterator<Toast> it = list.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            if (now - t.born > t.duration) it.remove();
        }
        int w = c.width();
        int boxW = 176, boxH = 26, gap = 3;
        float y = c.height() * 0.30f;
        for (Toast t : list) {
            float age = now - t.born;
            float in = Fx.easeOut(age / 0.25f);
            float out = 1 - Fx.easeIn((age - (t.duration - 0.35f)) / 0.35f);
            float slide = Math.min(in, out);
            float bump = 1 + 0.08f * (1 - Fx.smooth((now - t.bump) / 0.25f));
            int x = Math.round(w - 4 - boxW * slide);
            c.push();
            c.translate(x + boxW / 2f, y + boxH / 2f);
            c.scale(bump, bump);
            c.translate(-boxW / 2f, -boxH / 2f);
            // panel
            c.fill(0, 0, boxW, boxH, Fx.alpha(0x101018, 0.88f * slide));
            c.fill(0, 0, 2, boxH, Fx.alpha(t.accent, slide));
            c.fill(2, 0, boxW, 1, Fx.alpha(t.accent, 0.35f * slide));
            // icon
            if (t.icon != null) {
                c.item(t.icon, 6, 5);
            }
            // text
            String title = t.stack > 1 ? t.title + " x" + t.stack : t.title;
            c.text(title, 26, 4, Fx.alpha(t.titleColor, slide), true);
            String coins = t.coins > 0 ? NumberFormat.getIntegerInstance(Locale.US).format(t.coins) : "";
            int max = (int) ((boxW - 30) / 0.8f);
            int coinsW = coins.isEmpty() ? 0 : c.textWidth(coins) + 4;
            String line = clip(c, t.text, max - coinsW);
            c.push();
            c.translate(26, 15);
            c.scale(0.8f, 0.8f);
            c.text(line, 0, 0, Fx.alpha(0xD8D8E0, slide), true);
            if (!coins.isEmpty()) {
                c.text(coins, c.textWidth(line) + 4, 0, Fx.alpha(0xFFD24A, slide), true);
            }
            c.pop();
            c.pop();
            y += (boxH + gap) * slide;
        }
    }

    private static String clip(Canvas c, String s, int max) {
        if (c.textWidth(s) <= max) return s;
        while (s.length() > 1 && c.textWidth(s + "…") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}

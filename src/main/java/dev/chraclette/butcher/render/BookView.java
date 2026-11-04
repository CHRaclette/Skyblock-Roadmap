package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Ledger;
import dev.chraclette.butcher.core.Ledger.Chapter;
import dev.chraclette.butcher.core.Ledger.Entry;
import dev.chraclette.butcher.core.Ledger.Level;
import dev.chraclette.butcher.core.Ledger.Slot;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** The sticker book screen, drawn on any Canvas. Keeps its own tab/page state and hit areas. */
public final class BookView {

    private static final int LEATHER = 0x5A3420, LEATHER_DARK = 0x3A2014, PAPER = 0xF3E9D2, INK = 0x3A2A1A;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private final Ledger ledger;
    private final Function<String, Object> icons;
    private Chapter chapter = Chapter.DIANA;
    private int page;
    private float openedAt = -1;

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();

    /** @param icons item name -> icon object for the Canvas (ItemStack in game) */
    public BookView(Ledger ledger, Function<String, Object> icons) {
        this.ledger = ledger;
        this.icons = icons;
    }

    public void select(Chapter c) {
        chapter = c;
        page = 0;
    }

    /** Handles a click. Returns true if something was hit. */
    public boolean click(double mx, double my) {
        for (Hit h : hits) {
            if (mx >= h.x1 && mx < h.x2 && my >= h.y1 && my < h.y2) {
                h.action.run();
                return true;
            }
        }
        return false;
    }

    public void scroll(double amount) {
        if (amount < 0) page++;
        else if (amount > 0 && page > 0) page--;
    }

    public void render(Canvas c, int mouseX, int mouseY, float time) {
        if (openedAt < 0) openedAt = time;
        hits.clear();
        int w = c.width(), h = c.height();
        float open = Fx.backOut((time - openedAt) / 0.35f);
        int bw = Math.min(w - 20, 440), bh = Math.min(h - 46, 260);
        int bx = (w - bw) / 2, by = (h - bh) / 2 + 12;

        c.fill(0, 0, w, h, 0x99000000);
        c.push();
        c.translate(w / 2f, h / 2f);
        c.scale(open, open);
        c.translate(-w / 2f, -h / 2f);

        // tabs (ribbons) sticking out above the book
        Chapter[] chapters = Chapter.values();
        int tabW = Math.min(58, (bw - 20) / chapters.length - 3);
        for (int i = 0; i < chapters.length; i++) {
            Chapter ch = chapters[i];
            boolean sel = ch == chapter;
            int tx = bx + 12 + i * (tabW + 3), th = sel ? 18 : 14;
            int ty = by - 4 - th;
            boolean hov = mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < by;
            int col = sel ? ch.color : Fx.darken(ch.color, hov ? 0.15f : 0.4f);
            c.fill(tx, ty, tx + tabW, by + 4, 0xFF000000 | Fx.darken(col, 0.35f));
            c.fill(tx + 1, ty + 1, tx + tabW - 1, by + 4, 0xFF000000 | col);
            String label = ch.title;
            float ls = 0.75f;
            c.push();
            c.translate(tx + tabW / 2f, ty + 4);
            c.scale(ls, ls);
            c.text(label, -c.textWidth(label) / 2, 0, sel ? 0xFF1A1008 : 0xFF2A1A10, false);
            c.pop();
            final Chapter target = ch;
            hits.add(new Hit(tx, ty, tx + tabW, by + 4, () -> select(target)));
        }

        // cover + pages
        c.fill(bx - 4, by - 4, bx + bw + 4, by + bh + 4, 0xFF000000 | LEATHER_DARK);
        c.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF000000 | LEATHER);
        int px = bx + 6, py = by + 6, pw = (bw - 12) / 2, ph = bh - 12;
        page(c, px, py, pw, ph, true);
        page(c, px + pw, py, pw, ph, false);
        // stitches on the cover
        for (int x = bx + 4; x < bx + bw - 4; x += 8) {
            c.fill(x, by - 1, x + 4, by, 0x60F3E9D2);
            c.fill(x, by + bh, x + 4, by + bh + 1, 0x60F3E9D2);
        }

        // left page: chapter header
        int lx = px + 12, ly = py + 10, contentW = pw - 24;
        Fx.bold(c, chapter.title, lx, ly, 0xFF000000 | Fx.darken(chapter.color, 0.45f));
        small(c, chapter.subtitle, lx, ly + 11, 0xFF7A6248, 0.75f);
        int earned = ledger.earned(chapter), total = Math.max(1, ledger.total(chapter));
        // progress bar
        int barY = ly + 21;
        c.fill(lx, barY, lx + contentW, barY + 5, 0xFF000000 | Fx.darken(PAPER, 0.2f));
        c.fill(lx, barY, lx + Math.round(contentW * earned / (float) total), barY + 5, 0xFF000000 | chapter.color);
        small(c, earned + " / " + total + " stickers", lx, barY + 7, 0xFF000000 | INK, 0.75f);
        String extra = switch (chapter) {
            case PESTS -> "Pests vacuumed: " + NUM.format(ledger.pestsVacuumed);
            case GARDEN -> "Crop Fevers caught: " + NUM.format(ledger.fevers);
            default -> "";
        };
        if (!extra.isEmpty()) {
            small(c, extra, lx + contentW - Math.round(c.textWidth(extra) * 0.75f), barY + 7, 0xFF000000 | INK, 0.75f);
        }
        // level counts
        int levelY = barY + 17;
        int[] counts = new int[Level.values().length];
        long coins = 0;
        for (Slot s : ledger.slots(chapter)) {
            if (!s.locked()) {
                counts[s.entry().level().ordinal()]++;
                coins += s.entry().coins;
            }
        }
        int lxx = lx;
        for (Level lv : Level.values()) {
            c.fill(lxx, levelY + 1, lxx + 5, levelY + 6, 0xFF000000 | lv.color);
            c.fill(lxx, levelY + 1, lxx + 5, levelY + 2, 0x40000000);
            small(c, String.valueOf(counts[lv.ordinal()]), lxx + 7, levelY + 1, 0xFF000000 | INK, 0.75f);
            lxx += 20;
        }
        if (coins > 0) {
            String cs = NUM.format(coins) + " coins";
            small(c, cs, lx + contentW - Math.round(c.textWidth(cs) * 0.75f), levelY + 1, 0xFFB07A10, 0.75f);
        }

        // sticker grid: 3 cols on the left page (under the header), 3 cols on the right page
        List<Slot> slots = ledger.slots(chapter);
        int cell = Math.min(contentW / 3, (ph - 20) / 3);
        int leftTop = levelY + 14;
        int leftRows = Math.max(1, (py + ph - 14 - leftTop) / cell);
        int rightRows = Math.max(1, (ph - 24) / cell);
        int perPage = 3 * leftRows + 3 * rightRows;
        int pages = Math.max(1, (slots.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        int start = page * perPage;
        Slot hovered = null;
        float hx = 0, hy = 0;
        for (int i = 0; i < perPage && start + i < slots.size(); i++) {
            Slot s = slots.get(start + i);
            int col, row, ox, oy;
            if (i < 3 * leftRows) {
                col = i % 3;
                row = i / 3;
                ox = lx;
                oy = leftTop;
            } else {
                int j = i - 3 * leftRows;
                col = j % 3;
                row = j / 3;
                ox = px + pw + 12;
                oy = py + 12;
            }
            float cx = ox + col * cell + cell / 2f;
            float cy = oy + row * cell + cell / 2f - 4;
            float size = cell * 0.62f;
            boolean hov = Math.abs(mouseX - cx) < size / 2 + 2 && Math.abs(mouseY - cy) < size / 2 + 2;
            Entry e = s.entry();
            long seed = s.name().hashCode();
            Sticker.draw(c, cx, cy, size, s.locked() ? null : e.level(), s.locked() ? null : icons.apply(s.name()),
                    s.locked() ? 0 : e.times, time, hov, seed);
            String name = s.locked() ? "???" : s.name();
            float ns = 0.6f;
            int maxW = Math.round((cell - 4) / ns);
            while (name.length() > 3 && c.textWidth(name) > maxW) name = name.substring(0, name.length() - 2) + "…";
            c.push();
            c.translate(cx, cy + size / 2 + 4);
            c.scale(ns, ns);
            c.text(name, -c.textWidth(name) / 2, 0, s.locked() ? 0xFF9A8A70 : 0xFF000000 | INK, false);
            c.pop();
            if (hov) {
                hovered = s;
                hx = mouseX;
                hy = mouseY;
            }
        }
        if (slots.isEmpty()) {
            small(c, "Your rare drops will be stuck in here.", px + pw + 14, py + 20, 0xFF7A6248, 0.8f);
        }

        // page arrows + page number
        if (pages > 1) {
            String pn = (page + 1) + " / " + pages;
            small(c, pn, px + pw - Math.round(c.textWidth(pn) * 0.375f), py + ph - 9, 0xFF7A6248, 0.75f);
            if (page > 0) arrow(c, px + 6, py + ph - 14, false, mouseX, mouseY, () -> page--);
            if (page < pages - 1) arrow(c, px + 2 * pw - 18, py + ph - 14, true, mouseX, mouseY, () -> page++);
        }
        c.pop();

        // title above everything
        Fx.centered(c, "Butcher's Sticker Book", w / 2f, by - 34, 1.2f, 0xFFFFD24A, true);

        if (hovered != null) tooltip(c, hovered, hx, hy, w, h);
    }

    private void page(Canvas c, int x, int y, int w, int h, boolean left) {
        c.fill(x, y, x + w, y + h, 0xFF000000 | PAPER);
        // shading towards the spine
        for (int i = 0; i < 10; i++) {
            int a = (10 - i) * 5;
            int sx = left ? x + w - 1 - i : x + i;
            c.fill(sx, y, sx + 1, y + h, a << 24);
        }
        // faint lines like a notebook
        for (int ly = y + 50; ly < y + h - 6; ly += 12) {
            c.fill(x + 8, ly, x + w - 8, ly + 1, 0x10000000);
        }
    }

    private void arrow(Canvas c, int x, int y, boolean right, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx < x + 12 && my >= y && my < y + 10;
        int col = hov ? 0xFF000000 | INK : 0xFF9A8A70;
        for (int i = 0; i < 5; i++) {
            int xx = right ? x + 3 + i : x + 8 - i;
            c.fill(xx, y + i, xx + 1, y + 10 - i, col);
        }
        hits.add(new Hit(x - 2, y - 2, x + 14, y + 12, action));
    }

    private void tooltip(Canvas c, Slot s, float mx, float my, int w, int h) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (s.locked()) {
            lines.add("???");
            colors.add(0xFFAAAAAA);
            lines.add("Not found yet. Keep grinding!");
            colors.add(0xFF888888);
        } else {
            Entry e = s.entry();
            Level lv = e.level();
            lines.add(e.name);
            colors.add(0xFFFFFFFF);
            lines.add(lv.title + " sticker");
            colors.add(0xFF000000 | lv.color);
            lines.add("Dropped " + e.times + (e.times == 1 ? " time" : " times") + (e.items > e.times ? " (" + NUM.format(e.items) + " items)" : ""));
            colors.add(0xFFDDDDDD);
            Level next = lv.next();
            if (next != null) {
                lines.add(String.format(Locale.US, "%.0f / %d points to %s", e.points, next.points, next.title));
                colors.add(0xFFAAAAAA);
            } else {
                lines.add("Maxed out!");
                colors.add(0xFFFF6AD5);
            }
            if (!e.bestTier.isEmpty()) {
                lines.add("Best: " + e.bestTier);
                colors.add(0xFFFFAA00);
            }
            if (e.bestMagicFind > 0) {
                lines.add("Best Magic Find: +" + e.bestMagicFind + "%");
                colors.add(0xFF55FFFF);
            }
            if (e.coins > 0) {
                lines.add("Worth so far: " + NUM.format(e.coins) + " coins");
                colors.add(0xFFFFD24A);
            }
            if (e.firstAt > 0) {
                lines.add("First: " + new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date(e.firstAt)));
                colors.add(0xFF888888);
            }
        }
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, c.textWidth(l));
        int th = lines.size() * 10 + 6;
        int x = Math.round(mx + 10), y = Math.round(my - 6);
        if (x + tw + 8 > w) x = Math.round(mx - tw - 18);
        if (y + th > h) y = h - th - 2;
        c.fill(x, y, x + tw + 8, y + th, 0xF0100818);
        c.fill(x, y, x + tw + 8, y + 1, 0xFF5000A0);
        c.fill(x, y + th - 1, x + tw + 8, y + th, 0xFF28005A);
        for (int i = 0; i < lines.size(); i++) {
            c.text(lines.get(i), x + 4, y + 4 + i * 10, colors.get(i), true);
        }
    }

    private static void small(Canvas c, String s, int x, int y, int argb, float scale) {
        c.push();
        c.translate(x, y);
        c.scale(scale, scale);
        c.text(s, 0, 0, argb, false);
        c.pop();
    }
}

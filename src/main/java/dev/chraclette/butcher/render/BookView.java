package dev.chraclette.butcher.render;

import dev.chraclette.butcher.core.Badges;
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

    private static final int INK = OldBook.SEPIA;
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private final Ledger ledger;
    private final Function<String, Object> icons;
    private Chapter chapter = Chapter.DIANA;
    private boolean badges;
    private int page;
    private Slot hovered;
    private Badges.Badge hoveredBadge;
    private float hx, hy;
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
        badges = false;
        page = 0;
    }

    public void selectBadges() {
        badges = true;
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
        int bw = Math.min(w - 20, 440), bh = Math.min(h - 64, 260);
        int bx = (w - bw) / 2, by = (h - bh) / 2 + 22;

        c.fill(0, 0, w, h, 0xA0100804);
        for (int i = 0; i < 12; i++) {
            int band = Math.round(Math.min(w, h) * 0.02f), a = (12 - i) * 6;
            c.fill(0, i * band, w, (i + 1) * band, a << 24);
            c.fill(0, h - (i + 1) * band, w, h - i * band, a << 24);
        }
        c.push();
        c.translate(w / 2f, h / 2f);
        c.scale(open, open);
        c.translate(-w / 2f, -h / 2f);

        // tabs (ribbons) sticking out above the book
        Chapter[] chapters = Chapter.values();
        int tabW = Math.min(52, (bw - 20) / (chapters.length + 1) - 3);
        for (int i = 0; i <= chapters.length; i++) {
            boolean isBadges = i == chapters.length;
            Chapter ch = isBadges ? null : chapters[i];
            boolean sel = isBadges ? badges : !badges && ch == chapter;
            int tabColor = isBadges ? 0xD08A4E : ch.color;
            int tx = bx + 12 + i * (tabW + 3), th = sel ? 18 : 14;
            int ty = by - 4 - th;
            boolean hov = mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < by;
            OldBook.tab(c, tx, ty, tabW, by + 4 - ty, hov && !sel ? Fx.brighten(tabColor, 0.15f) : tabColor, sel);
            String label = isBadges ? "Badges" : ch.title;
            float ls = 0.75f;
            c.push();
            c.translate(tx + tabW / 2f, ty + 4);
            c.scale(ls, ls);
            c.text(label, -c.textWidth(label) / 2, 0, sel ? 0xFFFFF0D0 : 0xFFE0CCA8, true);
            c.pop();
            final Chapter target = ch;
            hits.add(new Hit(tx, ty, tx + tabW, by + 4, isBadges ? this::selectBadges : () -> select(target)));
        }

        // cover + pages
        OldBook.cover(c, bx, by, bw, bh);
        OldBook.ribbon(c, bx + bw * 3 / 4, by + bh - 6, 16, time);
        int px = bx + 8, py = by + 7, pw = (bw - 16) / 2, ph = bh - 14;
        OldBook.page(c, px, py, pw, ph, true, 11);
        OldBook.page(c, px + pw, py, pw, ph, false, 23);

        hovered = null;
        hoveredBadge = null;
        if (badges) {
            badgesPage(c, px, py, pw, ph, mouseX, mouseY, time);
        } else {
            chapterPage(c, px, py, pw, ph, mouseX, mouseY, time);
        }
        c.pop();

        header(c, bx, by, bw, time);

        if (hovered != null) tooltip(c, hovered, hx, hy, w, h);
        if (hoveredBadge != null) badgeTooltip(c, hoveredBadge, hx, hy, w, h);
    }

    private void chapterPage(Canvas c, int px, int py, int pw, int ph, int mouseX, int mouseY, float time) {
        // left page: chapter header
        int lx = px + 14, ly = py + 12, contentW = pw - 28;
        title(c, chapter.title, lx, ly);
        small(c, chapter.subtitle, lx, ly + 12, 0xFF000000 | OldBook.SEPIA_LIGHT, 0.75f);
        OldBook.divider(c, lx, ly + 21, contentW);
        int earned = ledger.earned(chapter), total = Math.max(1, ledger.total(chapter));
        // progress bar
        int barY = ly + 27;
        OldBook.bar(c, lx, barY, contentW, 4, earned / (float) total, chapter.color);
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
            float cy = oy + row * cell + cell / 2f - 7;
            float size = cell * 0.56f;
            boolean hov = Math.abs(mouseX - cx) < size / 2 + 2 && Math.abs(mouseY - cy) < size / 2 + 2;
            Entry e = s.entry();
            long seed = s.name().hashCode();
            Sticker.draw(c, cx, cy, size, s.locked() ? null : e.level(), s.locked() ? null : icons.apply(s.name()),
                    s.locked() ? 0 : e.times, time, hov, seed);
            if (!s.locked()) {
                Medal.stars(c, cx, cy + size / 2 + 5, Ledger.stars(e.level()), Ledger.crowned(e.level()),
                        Math.max(1, size / 40f), time);
            }
            String name = s.locked() ? "???" : s.name();
            float ns = 0.6f;
            int maxW = Math.round((cell - 4) / ns);
            while (name.length() > 3 && c.textWidth(name) > maxW) name = name.substring(0, name.length() - 2) + "…";
            c.push();
            c.translate(cx, cy + size / 2 + (s.locked() ? 4 : 10));
            c.scale(ns, ns);
            c.text(name, -c.textWidth(name) / 2, 0, s.locked() ? 0xFF8A6A4A : 0xFF000000 | INK, false);
            c.pop();
            if (hov) {
                hovered = s;
                hx = mouseX;
                hy = mouseY;
            }
        }
        if (slots.isEmpty()) {
            small(c, "Your rare drops will be stuck in here.", px + pw + 14, py + 20, 0xFF6A4A2A, 0.8f);
        }

        // page arrows + page number
        if (pages > 1) {
            String pn = (page + 1) + " / " + pages;
            small(c, pn, px + pw - Math.round(c.textWidth(pn) * 0.375f), py + ph - 9, 0xFF6A4A2A, 0.75f);
            if (page > 0) arrow(c, px + 6, py + ph - 14, false, mouseX, mouseY, () -> page--);
            if (page < pages - 1) arrow(c, px + 2 * pw - 18, py + ph - 14, true, mouseX, mouseY, () -> page++);
        }
    }


    /** Above the book: Ledger Level plaque with XP bar, and the badge counts. */
    private void header(Canvas c, int bx, int by, int bw, float time) {
        long[] lv = ledger.level();
        int y = by - 44;
        int x = bx + 4;
        // plaque
        OldBook.label(c, x, y - 2, 130, 22);
        Fx.bold(c, "Ledger Lv " + lv[0], x + 6, y + 2, 0xFF000000 | OldBook.GOLD_LIGHT);
        int barX = x + 6, barY = y + 13, barW = 118;
        c.fill(barX, barY, barX + barW, barY + 3, 0xFF2A1208);
        c.fill(barX, barY, barX + Math.round(barW * lv[1] / (float) Math.max(1, lv[2])), barY + 3, 0xFF000000 | OldBook.GOLD);
        String xp = lv[1] + " / " + lv[2] + " xp";
        small(c, xp, x + 126 - Math.round(c.textWidth(xp) * 0.6f), y + 3, 0xFFE8D2B8, 0.6f);
        // badge counts on the right
        OldBook.label(c, bx + bw - 140, y - 2, 136, 22);
        int rx = bx + bw - 8;
        Badges.Tier[] tiers = {Badges.Tier.GOLD, Badges.Tier.SILVER, Badges.Tier.BRONZE};
        for (Badges.Tier t : tiers) {
            String n = String.valueOf(Badges.count(ledger, t));
            int tw = c.textWidth(n);
            rx -= tw + 18;
            Medal.draw(c, rx + 5, y + 8, 6, t, null, time, false, t.ordinal());
            c.text(n, rx + 13, y + 4, 0xFF000000 | OldBook.GOLD_LIGHT, true);
        }
        small(c, "Badges", rx - 34, y + 5, 0xFF000000 | OldBook.GOLD_LIGHT, 0.8f);
    }

    private void badgesPage(Canvas c, int px, int py, int pw, int ph, int mouseX, int mouseY, float time) {
        int lx = px + 14, ly = py + 12, contentW = pw - 28;
        title(c, "Badges", lx, ly);
        small(c, "Bronze, Silver, Gold. Earn them all!", lx, ly + 12, 0xFF000000 | OldBook.SEPIA_LIGHT, 0.75f);
        OldBook.divider(c, lx, ly + 21, contentW);
        int got = 0, max = Badges.ALL.size() * 3;
        for (Badges.Badge b : Badges.ALL) got += b.tier(ledger).ordinal();
        int barY = ly + 27;
        OldBook.bar(c, lx, barY, contentW, 4, got / (float) max, 0xD08A4E);
        small(c, got + " / " + max + " tiers", lx, barY + 7, 0xFF000000 | INK, 0.75f);

        List<Badges.Badge> list = Badges.ALL;
        int cols = 4;
        int cell = contentW / cols;
        int rowH = Math.round(cell * 1.25f);
        int leftTop = barY + 20;
        int leftRows = Math.max(1, (py + ph - 12 - leftTop) / rowH);
        int rightRows = Math.max(1, (ph - 16) / rowH);
        int perPage = cols * (leftRows + rightRows);
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            Badges.Badge b = list.get(start + i);
            int col, row, ox, oy;
            if (i < cols * leftRows) {
                col = i % cols;
                row = i / cols;
                ox = lx;
                oy = leftTop;
            } else {
                int j = i - cols * leftRows;
                col = j % cols;
                row = j / cols;
                ox = px + pw + 12;
                oy = py + 10;
            }
            float cx = ox + col * cell + cell / 2f;
            float cy = oy + row * rowH + cell * 0.36f;
            float r = cell * 0.3f;
            boolean hov = Math.abs(mouseX - cx) < r + 2 && Math.abs(mouseY - cy) < r + 2;
            Badges.Tier tier = b.tier(ledger);
            Medal.draw(c, cx, cy, r, tier, icons.apply("badge:" + b.icon()), time, hov, b.id().hashCode());
            // three pips: bronze / silver / gold reached
            for (int k = 0; k < 3; k++) {
                int pxl = Math.round(cx - 7 + k * 6), pyl = Math.round(cy + r * 1.5f);
                int pc = tier.ordinal() > k ? Badges.Tier.values()[k + 1].color : 0xC9BBA0;
                c.fill(pxl, pyl, pxl + 4, pyl + 4, 0xFF000000 | Fx.darken(pc, 0.35f));
                c.fill(pxl + 1, pyl + 1, pxl + 3, pyl + 3, 0xFF000000 | pc);
            }
            String name = b.title();
            float ns = 0.55f;
            int maxW = Math.round((cell - 2) / ns);
            while (name.length() > 3 && c.textWidth(name) > maxW) name = name.substring(0, name.length() - 2) + "…";
            c.push();
            c.translate(cx, cy + r * 1.5f + 6);
            c.scale(ns, ns);
            c.text(name, -c.textWidth(name) / 2, 0, tier == Badges.Tier.NONE ? 0xFF8A6A4A : 0xFF000000 | INK, false);
            c.pop();
            if (hov) {
                hoveredBadge = b;
                hx = mouseX;
                hy = mouseY;
            }
        }
        if (pages > 1) {
            String pn = (page + 1) + " / " + pages;
            small(c, pn, px + pw - Math.round(c.textWidth(pn) * 0.375f), py + ph - 9, 0xFF6A4A2A, 0.75f);
            if (page > 0) arrow(c, px + 6, py + ph - 14, false, mouseX, mouseY, () -> page--);
            if (page < pages - 1) arrow(c, px + 2 * pw - 18, py + ph - 14, true, mouseX, mouseY, () -> page++);
        }
    }

    private void badgeTooltip(Canvas c, Badges.Badge b, float mx, float my, int w, int h) {
        Badges.Tier tier = b.tier(ledger);
        long v = b.value(ledger), next = b.nextGoal(ledger);
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(b.title());
        colors.add(0xFFFFFFFF);
        lines.add(b.what());
        colors.add(0xFFAAAAAA);
        lines.add(tier == Badges.Tier.NONE ? "Not earned yet" : tier.title + " badge");
        colors.add(0xFF000000 | (tier == Badges.Tier.NONE ? 0x888888 : tier.color));
        lines.add(next < 0 ? NUM.format(v) + " (maxed!)" : NUM.format(v) + " / " + NUM.format(next));
        colors.add(0xFFDDDDDD);
        lines.add("Bronze " + NUM.format(b.goals()[0]) + " · Silver " + NUM.format(b.goals()[1]) + " · Gold " + NUM.format(b.goals()[2]));
        colors.add(0xFF888888);
        box(c, lines, colors, mx, my, w, h);
    }

    private void arrow(Canvas c, int x, int y, boolean right, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx < x + 12 && my >= y && my < y + 10;
        int col = hov ? 0xFF000000 | INK : 0xFF8A6A4A;
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
            lines.add("Mastery " + "★".repeat(Ledger.stars(lv)) + "☆".repeat(5 - Ledger.stars(lv))
                    + (Ledger.crowned(lv) ? " ♛" : "") + "  (" + lv.title + ")");
            colors.add(0xFF000000 | lv.color);
            lines.add("Dropped " + e.times + (e.times == 1 ? " time" : " times") + (e.items > e.times ? " (" + NUM.format(e.items) + " items)" : ""));
            colors.add(0xFFDDDDDD);
            Level next = lv.next();
            if (next != null) {
                lines.add(String.format(Locale.US, "%.0f / %d points to %s (%d%%)", e.points, next.points,
                        Ledger.stars(next) > Ledger.stars(lv) ? Ledger.stars(next) + "★" : "the crown",
                        Math.round(Ledger.progress(e) * 100)));
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
        box(c, lines, colors, mx, my, w, h);
    }

    private static void box(Canvas c, List<String> lines, List<Integer> colors, float mx, float my, int w, int h) {
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, c.textWidth(l));
        int th = lines.size() * 10 + 6;
        int x = Math.round(mx + 10), y = Math.round(my - 6);
        if (x + tw + 10 > w) x = Math.round(mx - tw - 20);
        if (y + th > h) y = h - th - 2;
        OldBook.tooltip(c, x, y, tw + 10, th + 1);
        for (int i = 0; i < lines.size(); i++) {
            c.text(lines.get(i), x + 5, y + 4 + i * 10, inkify(colors.get(i), i == 0), false);
        }
    }

    /** Tooltip colours were made for a dark background; turn them into ink on parchment. */
    private static int inkify(int argb, boolean first) {
        int rgb = argb & 0xFFFFFF;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        boolean grey = Math.abs(r - g) < 20 && Math.abs(g - b) < 20;
        if (first) return 0xFF000000 | OldBook.SEPIA;
        if (grey) return 0xFF000000 | (r > 180 ? OldBook.SEPIA : OldBook.SEPIA_LIGHT);
        return 0xFF000000 | Fx.darken(rgb, 0.45f);
    }

    /** Heading with a red drop-cap first letter. */
    private static void title(Canvas c, String s, int x, int y) {
        String first = s.substring(0, 1), rest = s.substring(1);
        c.push();
        c.translate(x, y - 3);
        c.scale(1.5f, 1.5f);
        Fx.bold(c, first, 0, 0, 0xFF000000 | OldBook.INK_RED);
        c.pop();
        int fw = Math.round(c.textWidth(first) * 1.5f) + 2;
        Fx.bold(c, rest, x + fw, y + 1, 0xFF000000 | OldBook.SEPIA);
    }

    private static void small(Canvas c, String s, int x, int y, int argb, float scale) {
        c.push();
        c.translate(x, y);
        c.scale(scale, scale);
        c.text(s, 0, 0, argb, false);
        c.pop();
    }
}

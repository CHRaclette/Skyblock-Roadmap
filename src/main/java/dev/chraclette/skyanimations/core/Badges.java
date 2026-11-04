package dev.chraclette.skyanimations.core;

import dev.chraclette.skyanimations.core.Ledger.Chapter;
import dev.chraclette.skyanimations.core.Ledger.Entry;
import dev.chraclette.skyanimations.core.Ledger.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/** Badges with bronze / silver / gold tiers, computed from the sticker book. */
public final class Badges {

    private Badges() {
    }

    public enum Tier {
        NONE("Locked", 0x5A4632), BRONZE("Bronze", 0xD08A4E), SILVER("Silver", 0xD7DEE6), GOLD("Gold", 0xFFD23A);

        public final String title;
        public final int color;

        Tier(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    /**
     * @param id      stable key for saving
     * @param title   shown under the medallion
     * @param what    one-line description ("Vacuum pests")
     * @param icon    item name used to pick an icon
     * @param color   ink colour of the medallion
     * @param goals   values needed for bronze, silver, gold
     */
    public record Badge(String id, String title, String what, String icon, int color, long[] goals,
                        ToLongFunction<Ledger> metric) {

        public long value(Ledger l) {
            return metric.applyAsLong(l);
        }

        public Tier tier(Ledger l) {
            long v = value(l);
            Tier t = Tier.NONE;
            for (int i = 0; i < goals.length; i++) {
                if (v >= goals[i]) t = Tier.values()[i + 1];
            }
            return t;
        }

        /** Goal of the next tier, or -1 when gold. */
        public long nextGoal(Ledger l) {
            long v = value(l);
            for (long g : goals) {
                if (v < g) return g;
            }
            return -1;
        }
    }

    public static final List<Badge> ALL = new ArrayList<>();

    private static long stickers(Ledger l, Chapter c) {
        return l.earned(c);
    }

    private static long drops(Ledger l, Chapter c) {
        long n = 0;
        for (Entry e : l.stickers.values()) {
            if (e.chapter == c) n += e.times;
        }
        return n;
    }

    private static long atLeast(Ledger l, Level level) {
        long n = 0;
        for (Entry e : l.stickers.values()) {
            if (e.level().ordinal() >= level.ordinal()) n++;
        }
        return n;
    }

    static {
        String[][] chapterIcons = {
                {"DIANA", "Griffin Feather"}, {"SLAYER", "Scythe Blade"}, {"DUNGEONS", "Necron's Handle"}, {"MINING", "Divan's Alloy"},
                {"GARDEN", "Cropie"}, {"PESTS", "Beady Eyes"}, {"FISHING", "Lucky Clover Core"}};
        for (String[] ci : chapterIcons) {
            Chapter c = Chapter.valueOf(ci[0]);
            int total = Ledger.KNOWN.get(c).size();
            long gold = Math.max(3, total);
            ALL.add(new Badge("collect_" + c.name().toLowerCase(), c.title + " Collector",
                    "Collect different " + c.title + " stickers", ci[1], c.color,
                    new long[]{1, Math.max(2, gold / 2), gold}, l -> stickers(l, c)));
            ALL.add(new Badge("grind_" + c.name().toLowerCase(), c.title + " Grinder",
                    "Get " + c.title + " drops", ci[1], c.color,
                    new long[]{10, 100, 1000}, l -> drops(l, c)));
        }
        ALL.add(new Badge("nucleus_runs", "Nucleus Runner", "Finish Crystal Nucleus runs", "Divan Fragment", 0x8FD8FF,
                new long[]{10, 100, 500}, l -> l.runs("nucleus")));
        ALL.add(new Badge("dungeon_runs", "Catacombs Veteran", "Finish Catacombs runs", "Wither Catalyst", 0x5CE1FF,
                new long[]{50, 500, 2500}, Ledger::dungeonRuns));
        ALL.add(new Badge("master", "Mastery", "Stickers at 4 stars or more", "Nether Star", 0xFFD23A,
                new long[]{1, 5, 15}, l -> atLeast(l, Level.GOLD)));
        ALL.add(new Badge("legend", "Legend", "Stickers with a crown", "Totem", 0xFF4FD8,
                new long[]{1, 3, 10}, l -> atLeast(l, Level.HOLO)));
        ALL.add(new Badge("rng", "Blessed", "INSANE or PRAY TO RNGESUS drops", "Ender Eye", 0xB04CFF,
                new long[]{1, 3, 10}, l -> l.jackpots));
        ALL.add(new Badge("pests", "Exterminator", "Vacuum pests", "Pest", 0x9BD13A,
                new long[]{100, 1000, 10000}, l -> l.pestsVacuumed));
        ALL.add(new Badge("fever", "Feverish", "Catch Crop Fever", "Golden Hoe", 0xFF6A1A,
                new long[]{1, 10, 50}, l -> l.fevers));
        ALL.add(new Badge("coins", "Loaded", "Coins worth of rare drops", "Gold Ingot", 0xFFC93C,
                new long[]{10_000_000, 100_000_000, 1_000_000_000}, l -> {
                    long n = 0;
                    for (Entry e : l.stickers.values()) n += e.coins;
                    return n;
                }));
    }

    /** A badge that just reached a new tier. */
    public record Earned(Badge badge, Tier tier) {
    }

    /** Compares current tiers with what was saved; updates the saved tiers and returns new ones. */
    public static List<Earned> check(Ledger l) {
        List<Earned> out = new ArrayList<>();
        for (Badge b : ALL) {
            Tier now = b.tier(l);
            int saved = l.badgeTiers.getOrDefault(b.id(), 0);
            if (now.ordinal() > saved) {
                l.badgeTiers.put(b.id(), now.ordinal());
                out.add(new Earned(b, now));
            }
        }
        return out;
    }

    public static int count(Ledger l, Tier tier) {
        int n = 0;
        for (Badge b : ALL) {
            if (b.tier(l) == tier) n++;
        }
        return n;
    }
}

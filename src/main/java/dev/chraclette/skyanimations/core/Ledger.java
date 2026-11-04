package dev.chraclette.skyanimations.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sticker book: every rare drop you get earns a sticker, and the more (and rarer) drops of an
 * item you collect, the fancier its sticker gets. Pure Java; the client saves it as JSON.
 */
public final class Ledger {

    /** Book chapters, in page order. */
    public enum Chapter {
        DIANA("Diana", "Mythological Ritual", 0xFFC93C),
        SLAYER("Slayer", "Slayer bosses", 0xD6283A),
        DUNGEONS("Dungeons", "The Catacombs", 0x5CE1FF),
        GARDEN("Garden", "Crops & Crop Fever", 0x6FCF3B),
        PESTS("Pests", "Vacuumed vermin", 0x9BD13A),
        FISHING("Fishing", "Sea creatures", 0x2EC4D6),
        OTHER("Other", "Everything else", 0xB04CFF);

        public final String title;
        public final String subtitle;
        public final int color;

        Chapter(String title, String subtitle, int color) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
        }

        public static Chapter of(Drop drop) {
            if (drop.fever()) return GARDEN;
            return switch (drop.category()) {
                case DIANA -> DIANA;
                case REVENANT, TARANTULA, SVEN, VOIDGLOOM, INFERNO, RIFTSTALKER -> SLAYER;
                case DUNGEON -> DUNGEONS;
                case FARMING -> GARDEN;
                case PEST -> PESTS;
                case FISHING -> FISHING;
                case GENERIC -> OTHER;
            };
        }
    }

    /** Sticker levels. Points needed: 1, 3, 10, 25, 60, 150. */
    public enum Level {
        PAPER("Paper", 1, 0xF2EEE4),
        BRONZE("Bronze", 3, 0xC8834A),
        SILVER("Silver", 10, 0xC9D1DA),
        GOLD("Gold", 25, 0xFFC93C),
        DIAMOND("Diamond", 60, 0x6FF0FF),
        HOLO("Holo", 150, 0xFF6AD5);

        public final String title;
        public final int points;
        public final int color;

        Level(String title, int points, int color) {
            this.title = title;
            this.points = points;
            this.color = color;
        }

        public static Level of(double points) {
            Level best = null;
            for (Level l : values()) {
                if (points >= l.points) best = l;
            }
            return best;
        }

        public Level next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }
    }

    /** Rarer drops fill the sticker faster. */
    public static double weight(DropTier tier) {
        return switch (tier) {
            case UNCOMMON -> 0;
            case RARE, RARE_CROP -> 1;
            case VERY_RARE, VERY_RARE_CROP, REWARD -> 2;
            case PET -> 3;
            case CRAZY_RARE -> 4;
            case INSANE -> 6;
            case RNGESUS -> 8;
        };
    }

    /** One sticker. Public fields so Gson can save it as is. */
    public static final class Entry {
        public String name;
        public Chapter chapter;
        public int times;          // drops
        public long items;         // items in total (stacks counted)
        public double points;
        public int bestRank;
        public String bestTier = "";
        public long firstAt;
        public long lastAt;
        public int bestMagicFind;
        public long coins;

        public Level level() {
            return Level.of(points);
        }
    }

    /** What changed when a drop was recorded (for the "sticker upgraded" pop-up). */
    public record Result(Entry entry, boolean isNew, Level before, Level after) {
        public boolean upgraded() {
            return after != null && after != before;
        }
    }

    // Saved fields
    public Map<String, Entry> stickers = new LinkedHashMap<>();
    public long pestsVacuumed;
    public long fevers;
    /** INSANE and PRAY TO RNGESUS drops. */
    public long jackpots;
    /** Badge id -> highest tier announced (0 none, 1 bronze, 2 silver, 3 gold). */
    public Map<String, Integer> badgeTiers = new LinkedHashMap<>();

    // ------------------------------------------------------------------ mastery

    /** Stars for a level: Paper 1 ... Diamond 5. Holo is 5 stars plus a crown. */
    public static int stars(Level level) {
        return level == null ? 0 : Math.min(5, level.ordinal() + 1);
    }

    public static boolean crowned(Level level) {
        return level == Level.HOLO;
    }

    /** Progress (0..1) from the current level to the next one. 1 when maxed. */
    public static float progress(Entry e) {
        Level lv = e.level();
        Level next = lv == null ? Level.PAPER : lv.next();
        if (next == null) return 1f;
        int from = lv == null ? 0 : lv.points;
        return (float) Math.max(0, Math.min(1, (e.points - from) / (double) (next.points - from)));
    }

    /** Overall Ledger Level: every sticker point counts as XP. Level n needs 5 + 5n more XP. */
    public double xp() {
        double n = 0;
        for (Entry e : stickers.values()) n += e.points;
        return n + pestsVacuumed * 0.05 + fevers * 2;
    }

    /** {level, xp into level, xp needed for next}. */
    public long[] level() {
        double xp = xp();
        long level = 1;
        double need = 10;
        while (xp >= need) {
            xp -= need;
            level++;
            need = 5 + 5 * level;
        }
        return new long[]{level, (long) xp, (long) need};
    }

    private static final Pattern MF = Pattern.compile("\\+([\\d,.]+)%?\\s*\\S*\\s*Magic Find");

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT).trim();
    }

    /** Records a drop. Returns null if this kind of drop does not earn stickers. */
    public Result record(Drop drop, long coins, long nowMillis) {
        double w = weight(drop.tier());
        if (w <= 0) return null;
        String k = key(drop.name());
        Entry e = stickers.get(k);
        boolean isNew = e == null;
        if (isNew) {
            e = new Entry();
            e.name = drop.name();
            e.chapter = Chapter.of(drop);
            e.firstAt = nowMillis;
            stickers.put(k, e);
        }
        Level before = e.level();
        e.times++;
        e.items += Math.max(1, drop.count());
        e.points += w;
        e.lastAt = nowMillis;
        e.coins += Math.max(0, coins);
        if (drop.tier().rank >= DropTier.INSANE.rank) {
            jackpots++;
        }
        if (drop.tier().rank > e.bestRank) {
            e.bestRank = drop.tier().rank;
            e.bestTier = drop.tier().label;
        }
        Matcher m = MF.matcher(drop.bonus());
        if (m.find()) {
            try {
                e.bestMagicFind = Math.max(e.bestMagicFind, (int) Double.parseDouble(m.group(1).replace(",", "")));
            } catch (NumberFormatException ignored) {
                // keep the old best
            }
        }
        return new Result(e, isNew, before, e.level());
    }

    // ------------------------------------------------------------------ the catalogue

    /** Well-known drops per chapter, shown as locked "???" stickers until you get them. */
    public static final Map<Chapter, List<String>> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put(Chapter.DIANA, List.of("Griffin Feather", "Daedalus Stick", "Ancient Claw", "Dwarf Turtle Shelmet",
                "Crochet Tiger Plushie", "Antique Remedies", "Minos Relic", "Chimera I", "Crown of Greed",
                "Washed-up Souvenir", "Shimmering Wool", "Mythological Dye"));
        KNOWN.put(Chapter.SLAYER, List.of("Scythe Blade", "Shard of the Shredded", "Warden Heart", "Beheaded Horror",
                "Digested Mosquito", "Tarantula Talisman", "Fly Swatter", "Overflux Capacitor", "Grizzly Bait",
                "Red Claw Egg", "Judgement Core", "Pocket Espresso Machine", "Handy Blood Chalice",
                "High Class Archfiend Dice", "Kelvin Inverter", "Subzero Inverter", "Wilson's Engineering Plans",
                "McGrubber's Burger", "Unfanged Vampire Part"));
        KNOWN.put(Chapter.DUNGEONS, List.of("Necron's Handle", "Implosion", "Shadow Warp", "Wither Shield",
                "Giant's Sword", "Dark Claymore", "Spirit Wing", "Bonzo's Staff", "Scarf's Studies",
                "Recombobulator 3000", "Fifth Master Star", "Master Skull - Tier 5"));
        KNOWN.put(Chapter.GARDEN, List.of("Cropie", "Squash", "Fermento", "Helianthus", "Overclocker 3000",
                "Rarefinder Chip", "Enchanted Hay Bale", "Mutant Nether Wart", "Polished Pumpkin",
                "Enchanted Golden Carrot", "Enchanted Baked Potato", "Enchanted Sugar Cane"));
        KNOWN.put(Chapter.PESTS, List.of("Beady Eyes", "Clipped Wings", "Chirping Stereo", "Atmospheric Filter",
                "Wriggling Larva", "Bookworm's Favorite Book", "Mantid Claw", "Fire in a Bottle",
                "Vermin Vaporizer Chip", "Squeaky Toy", "Squeaky Mousemat", "Pesterminator I", "Sunder VI", "Dung Dye"));
        KNOWN.put(Chapter.FISHING, List.of("Lucky Clover Core", "Deep Sea Orb", "Radioactive Vial", "Tiki Mask",
                "Titanoboa Shed", "Megalodon", "Flying Fish", "Carmine Dye"));
        KNOWN.put(Chapter.OTHER, List.of());
    }

    /** A slot in the book: either an earned sticker or a locked one (entry == null). */
    public record Slot(String name, Entry entry) {
        public boolean locked() {
            return entry == null;
        }
    }

    /** Stickers of a chapter: earned ones (best first), then the known ones still missing. */
    public List<Slot> slots(Chapter chapter) {
        List<Slot> earned = new ArrayList<>();
        for (Entry e : stickers.values()) {
            if (e.chapter == chapter) earned.add(new Slot(e.name, e));
        }
        earned.sort((a, b) -> {
            int lv = Integer.compare(b.entry.level().ordinal(), a.entry.level().ordinal());
            return lv != 0 ? lv : Double.compare(b.entry.points, a.entry.points);
        });
        List<Slot> all = new ArrayList<>(earned);
        for (String name : KNOWN.getOrDefault(chapter, List.of())) {
            if (!stickers.containsKey(key(name))) all.add(new Slot(name, null));
        }
        return all;
    }

    public int earned(Chapter chapter) {
        int n = 0;
        for (Entry e : stickers.values()) {
            if (e.chapter == chapter) n++;
        }
        return n;
    }

    public int total(Chapter chapter) {
        int known = 0;
        for (String name : KNOWN.getOrDefault(chapter, List.of())) {
            if (!stickers.containsKey(key(name))) known++;
        }
        return earned(chapter) + known;
    }
}

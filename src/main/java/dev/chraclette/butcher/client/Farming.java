package dev.chraclette.butcher.client;

import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.core.DropParser;
import dev.chraclette.butcher.core.FarmEvent;
import dev.chraclette.butcher.render.Canvas;
import dev.chraclette.butcher.render.Cutscene;
import dev.chraclette.butcher.render.FeverHud;
import dev.chraclette.butcher.render.Toasts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Garden extras: Crop Fever intro/timer/summary, fever roll pop-ups, pest spawn and kill pop-ups. */
public final class Farming {

    private static final long START = System.nanoTime();

    private static final FeverHud FEVER = new FeverHud(new FeverHud.Hooks() {
        @Override
        public void sound(Cutscene.Cue cue, float pitch, float volume) {
            Sounds.play(cue, dev.chraclette.butcher.core.Effect.HARVEST, pitch, volume);
        }

        @Override
        public List<Object> cropIcons() {
            return CROPS;
        }
    });
    private static final Toasts TOASTS = new Toasts();
    private static final List<Object> CROPS = new ArrayList<>();

    private static String lastPest = "";
    private static float lastPestAt = -100;
    private static int pestsThisSession;

    /** Scheduled fake chat lines for /butcher test fever / pests. */
    private record Scheduled(float at, String line) {
    }

    private static final List<Scheduled> SCHEDULE = new ArrayList<>();

    private record LaterToast(float at, Toasts.Toast toast, Cutscene.Cue cue) {
    }

    private static final List<LaterToast> LATER = new ArrayList<>();

    private record LaterPop(float at, dev.chraclette.butcher.core.Ledger.Level before,
                            dev.chraclette.butcher.core.Ledger.Level after, String name, Object icon, int count,
                            dev.chraclette.butcher.core.Badges.Earned badge) {
    }

    /** Shows a badge medal after a delay. */
    static void badgeLater(dev.chraclette.butcher.core.Badges.Earned earned, Object icon, float delay) {
        POP_QUEUE.add(new LaterPop(now() + delay, null, null, earned.badge().title(), icon, 0, earned));
    }

    private static final List<LaterPop> POP_QUEUE = new ArrayList<>();
    private static dev.chraclette.butcher.render.StickerPop pop;

    /** Slaps a sticker onto the screen after a delay. before == null means brand new. */
    static void stickerLater(dev.chraclette.butcher.core.Ledger.Level before, dev.chraclette.butcher.core.Ledger.Level after,
                             String name, Object icon, int count, float delay) {
        POP_QUEUE.add(new LaterPop(now() + delay, before, after, name, icon, count, null));
    }

    /** Shows a toast after a delay (e.g. once the cutscene is over). */
    static void toastLater(Toasts.Toast toast, float delay, Cutscene.Cue cue) {
        LATER.add(new LaterToast(now() + delay, toast, cue));
    }

    private Farming() {
    }

    static float now() {
        return (System.nanoTime() - START) / 1_000_000_000f;
    }

    private static List<Object> crops() {
        if (CROPS.isEmpty()) {
            for (var item : new net.minecraft.world.item.Item[]{Items.WHEAT, Items.CARROT, Items.POTATO, Items.MELON_SLICE,
                    Items.SUGAR_CANE, Items.NETHER_WART, Items.COCOA_BEANS, Items.PUMPKIN, Items.CACTUS, Items.RED_MUSHROOM}) {
                CROPS.add(new ItemStack(item));
            }
        }
        return CROPS;
    }

    /** Handles Garden events. Returns true if the line was one. */
    /** True while a test line is being handled (tests don't count towards the book). */
    static boolean testing;

    static boolean onEvent(FarmEvent e) {
        Config.Data cfg = Config.get();
        switch (e.kind()) {
            case FEVER_START -> {
                if (!testing) LedgerStore.feverCaught();
                crops();
                Prices.refreshIfNeeded();
                FEVER.start(now(), e.amount(), cfg.feverIntro, cfg.feverTimer, cfg.feverSummary);
            }
            case FEVER_END -> FEVER.end(now());
            case PEST_SPAWN -> {
                if (!cfg.pestSpawn) return true;
                String text = e.amount() == 0 ? "While offline: " + e.where()
                        : e.amount() == 1 ? "A Pest appeared in " + e.where()
                        : e.amount() + " Pests spawned in " + e.where();
                TOASTS.add(new Toasts.Toast("PESTS!", 0xFF5555, text, 0xFF5555, pestIcon(), 0, 5f), now());
                Sounds.play(Cutscene.Cue.PEST_ALERT, dev.chraclette.butcher.core.Effect.HARVEST, 1f, 0.8f);
            }
            case PEST_KILL -> {
                pestsThisSession++;
                if (!testing) LedgerStore.pestVacuumed();
                lastPest = e.pest();
                lastPestAt = now();
                CutsceneManager.attachPest(e.pest());
                if (!cfg.pestKills) return true;
                Drop fake = DropParser.parse("RARE DROP! " + e.amount() + "x " + e.item(), null, null);
                long coins = 0;
                Object icon = pestIcon();
                if (fake != null) {
                    Cutscene.Price p = Prices.lookup(fake, "");
                    coins = p == null ? 0 : p.coins();
                    icon = ItemLookup.icon(fake, ItemStack.EMPTY);
                }
                TOASTS.add(new Toasts.Toast(e.pest() + " vacuumed", 0x55FF55,
                        e.amount() + "x " + e.item() + " · #" + pestsThisSession + " today", 0x55FF55, icon, coins, 4f), now());
                Sounds.play(Cutscene.Cue.TOAST, dev.chraclette.butcher.core.Effect.HARVEST, 1.2f, 0.5f);
            }
        }
        return true;
    }

    /**
     * Handles drops that belong to farming. Returns the drop that should still get a cutscene,
     * or null if it was fully handled here (shown as a pop-up).
     */
    static Drop onDrop(Drop drop) {
        Config.Data cfg = Config.get();
        if (drop.fever()) {
            Cutscene.Price p = Prices.lookup(drop, "");
            ItemStack icon = ItemLookup.icon(drop, ItemStack.EMPTY);
            FEVER.add(new FeverHud.Roll(drop.name(), drop.count(), icon, p == null ? 0 : p.coins(),
                    drop.tier().color, drop.tier().rank));
            boolean big = drop.tier().rank >= cfg.feverCutsceneFromRank;
            if (cfg.feverToasts && !big) {
                TOASTS.add(new Toasts.Toast(drop.tier().label, drop.tier().color, "+" + drop.count() + "x " + drop.name(),
                        0xFF8A2A, icon, p == null ? 0 : p.coins(), 4f), now());
                Sounds.play(Cutscene.Cue.TOAST, drop.effect(), 1.0f + drop.tier().rank * 0.1f, 0.6f);
            }
            return big ? drop : null;
        }
        // A rare drop right after a pest kill came from that pest.
        if (drop.pest().isEmpty() && now() - lastPestAt < 3f && isPestish(drop)) {
            return drop.withPest(lastPest);
        }
        return drop;
    }

    static boolean isPestish(Drop drop) {
        return switch (drop.category()) {
            case PEST, FARMING, GENERIC -> true;
            default -> false;
        };
    }

    private static Object pestIcon() {
        return new ItemStack(ItemLookup.byIdPublic("silverfish_spawn_egg", Items.STRING));
    }

    /** Draws fever HUD and pop-ups; runs scheduled test lines. */
    static void render(Canvas c, boolean cutscenePlaying) {
        float t = now();
        if (!SCHEDULE.isEmpty()) {
            List<Scheduled> due = new ArrayList<>();
            SCHEDULE.removeIf(s -> {
                if (s.at <= t) {
                    due.add(s);
                    return true;
                }
                return false;
            });
            for (Scheduled s : due) {
                ButcherClient.handle(s.line, true);
            }
        }
        if (!LATER.isEmpty()) {
            List<LaterToast> due = new ArrayList<>();
            LATER.removeIf(l -> {
                if (l.at <= t) {
                    due.add(l);
                    return true;
                }
                return false;
            });
            for (LaterToast l : due) {
                TOASTS.add(l.toast, t);
                if (l.cue != null) Sounds.play(l.cue, dev.chraclette.butcher.core.Effect.STARS, 1.2f, 0.7f);
            }
        }
        if (pop == null && !POP_QUEUE.isEmpty() && POP_QUEUE.get(0).at <= t && !cutscenePlaying) {
            LaterPop p = POP_QUEUE.remove(0);
            pop = p.badge != null
                    ? dev.chraclette.butcher.render.StickerPop.badge(p.badge.badge().title(), p.badge.badge().what(), p.badge.tier(), p.icon, t)
                    : new dev.chraclette.butcher.render.StickerPop(p.name, p.before, p.after, p.icon, p.count, t);
            Sounds.play(Cutscene.Cue.SLAP, dev.chraclette.butcher.core.Effect.STARS, 1f, 0.9f);
            if (p.badge != null ? p.badge.tier() == dev.chraclette.butcher.core.Badges.Tier.GOLD
                    : p.after.ordinal() >= dev.chraclette.butcher.core.Ledger.Level.GOLD.ordinal()) {
                Sounds.play(Cutscene.Cue.TOTEM, dev.chraclette.butcher.core.Effect.STARS, 1.2f, 0.5f);
            }
        }
        if (pop != null) {
            pop.render(c, t);
            if (pop.finished(t)) pop = null;
        }
        FEVER.reprice(r -> {
            Drop d = DropParser.parse("RARE DROP! " + r.name(), null, null);
            Cutscene.Price p = d == null ? null : Prices.lookup(d.withCount(r.count()), "");
            return p == null ? 0L : p.coins();
        });
        if (!FEVER.idle(t)) {
            FEVER.render(c, t, cutscenePlaying);
        }
        if (!TOASTS.isEmpty()) {
            TOASTS.render(c, t);
        }
    }

    static void clear() {
        TOASTS.clear();
        POP_QUEUE.clear();
        pop = null;
        if (FEVER.active()) FEVER.end(now());
        SCHEDULE.clear();
    }

    // ------------------------------------------------------------------ tests

    static void testFever() {
        float t = now();
        SCHEDULE.clear();
        SCHEDULE.add(new Scheduled(t, "WOAH! You caught a case of the CROP FEVER for 20 seconds!"));
        SCHEDULE.add(new Scheduled(t + 4.0f, "UNCOMMON DROP! You dropped 6x Enchanted Wheat!"));
        SCHEDULE.add(new Scheduled(t + 5.5f, "UNCOMMON DROP! You dropped 6x Enchanted Wheat!"));
        SCHEDULE.add(new Scheduled(t + 7.0f, "RARE DROP! You dropped 10x Enchanted Wheat!"));
        SCHEDULE.add(new Scheduled(t + 9.0f, "CRAZY RARE DROP! You dropped 120x Enchanted Wheat!"));
        SCHEDULE.add(new Scheduled(t + 16.5f, "UNCOMMON DROP! You dropped 6x Enchanted Wheat!"));
        SCHEDULE.add(new Scheduled(t + 20.0f, "GONE! Your CROP FEVER has been cured!"));
    }

    static void testPests() {
        float t = now();
        SCHEDULE.clear();
        SCHEDULE.add(new Scheduled(t, "YUCK! 3  Pests have spawned in Plot - 14!"));
        SCHEDULE.add(new Scheduled(t + 2.0f, "You received 7x Enchanted Potato for killing a Locust!"));
        SCHEDULE.add(new Scheduled(t + 3.0f, "You received 3x Enchanted Nether Wart for killing a Beetle!"));
        SCHEDULE.add(new Scheduled(t + 4.0f, "You received 2x Enchanted Sugar for killing a Mosquito!"));
        SCHEDULE.add(new Scheduled(t + 4.3f, "RARE DROP! Clipped Wings (+12% ✯ Magic Find)"));
    }

    /** Shows every sticker level once, one after another. */
    static void testStickers(Object icon) {
        dev.chraclette.butcher.core.Ledger.Level prev = null;
        float d = 0;
        for (var lv : dev.chraclette.butcher.core.Ledger.Level.values()) {
            stickerLater(prev, lv, "Chimera I", icon, lv.points, d);
            prev = lv;
            d += 0.1f;
        }
        var badge = dev.chraclette.butcher.core.Badges.ALL.get(0);
        for (var tier : new dev.chraclette.butcher.core.Badges.Tier[]{dev.chraclette.butcher.core.Badges.Tier.BRONZE,
                dev.chraclette.butcher.core.Badges.Tier.SILVER, dev.chraclette.butcher.core.Badges.Tier.GOLD}) {
            badgeLater(new dev.chraclette.butcher.core.Badges.Earned(badge, tier), BookScreen.badgeIcon(badge.icon()), d);
            d += 0.1f;
        }
    }
}

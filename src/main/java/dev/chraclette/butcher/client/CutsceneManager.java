package dev.chraclette.butcher.client;

import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.render.Cutscene;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Plays one cutscene at a time; drops that arrive meanwhile wait in a queue. */
public final class CutsceneManager {

    private record Pending(Drop drop, boolean test) {
    }

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static final int MAX_QUEUE = 12;

    private static Cutscene active;
    private static long startNanos;

    private CutsceneManager() {
    }

    public static void enqueue(Drop drop, boolean test) {
        Config.Mode mode = Config.mode(drop.tier());
        if (mode == Config.Mode.OFF && !test) return;
        Prices.refreshIfNeeded();

        // Same item again right behind it (Crop Fever spam, pest drops): merge into one.
        Pending last = QUEUE.peekLast();
        if (last != null && last.drop.name().equals(drop.name()) && last.drop.tier() == drop.tier()) {
            QUEUE.pollLast();
            QUEUE.addLast(new Pending(last.drop.withCount(last.drop.count() + drop.count()), test));
            return;
        }
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Pending(drop, test));
    }

    public static void skip() {
        active = null;
    }

    public static void clear() {
        active = null;
        QUEUE.clear();
    }

    public static boolean busy() {
        return active != null || !QUEUE.isEmpty();
    }

    private static void start(Pending p) {
        Config.Data cfg = Config.get();
        // A long queue would take forever: later ones play compact.
        boolean compact = Config.mode(p.drop.tier()) == Config.Mode.COMPACT || QUEUE.size() >= 3;
        Drop drop = p.drop;
        active = new Cutscene(drop, new Cutscene.Hooks() {
            private ItemStack found = ItemStack.EMPTY;
            private boolean looked;

            private ItemStack found() {
                if (!looked || found.isEmpty()) {
                    found = ItemLookup.findInInventory(drop);
                    looked = true;
                }
                return found;
            }

            @Override
            public Object icon() {
                return ItemLookup.icon(drop, found());
            }

            @Override
            public int nameColor() {
                return ItemLookup.nameColor(found());
            }

            @Override
            public Cutscene.Price price() {
                return Prices.lookup(drop, ItemLookup.skyblockId(found));
            }

            @Override
            public void sound(Cutscene.Cue cue, float pitch, float volume) {
                Sounds.play(cue, drop.effect(), pitch, volume);
            }
        }, compact, p.test, cfg.music);
        startNanos = System.nanoTime();
    }

    /** HUD element: called every frame. */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            clear();
            return;
        }
        if (active == null) {
            Pending next = QUEUE.pollFirst();
            if (next == null) return;
            start(next);
        }
        float speed = Math.max(0.25f, Math.min(3f, Config.get().speed));
        float t = (System.nanoTime() - startNanos) / 1_000_000_000f * speed;
        try {
            active.render(new McCanvas(graphics), t);
        } catch (RuntimeException e) {
            ButcherClient.LOG.error("Cutscene crashed, skipping it", e);
            active = null;
            return;
        }
        if (active.finished(t)) {
            active = null;
        }
    }
}

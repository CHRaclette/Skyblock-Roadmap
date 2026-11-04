package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.render.Cutscene;
import dev.chraclette.skyanimations.render.Scene;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Plays one cutscene at a time; drops that arrive meanwhile wait in a queue. */
public final class CutsceneManager {

    /** Either a drop (built into a cutscene when it starts) or a ready-made scene. */
    private record Pending(Drop drop, boolean test, long queuedAt, Scene scene) {
        Pending(Drop drop, boolean test) {
            this(drop, test, Clock.nanos(), null);
        }

        Pending(Drop drop, boolean test, long queuedAt) {
            this(drop, test, queuedAt, null);
        }
    }

    /** Queues a ready-made scene (e.g. the Crystal Nucleus opening). */
    /**
     * Plays a scene instead of the normal cutscene for a drop that just happened (e.g. the RNG Meter
     * scene replaces the RARE DROP cutscene of the same item). Removes it from the queue, or stops it
     * if it only just started.
     */
    static void replace(String dropName, Scene scene) {
        String n = dropName.toLowerCase(java.util.Locale.ROOT);
        QUEUE.removeIf(p -> p.drop != null && p.drop.name().toLowerCase(java.util.Locale.ROOT).equals(n));
        if (activeDrop != null && activeDrop.name().toLowerCase(java.util.Locale.ROOT).equals(n)
                && Clock.nanos() - startNanos < 2_500_000_000L) {
            active = null;
            activeDrop = null;
        }
        QUEUE.addFirst(new Pending(null, true, Clock.nanos() - HOLD_NANOS, scene));
    }

    /** Plays a scene now, replacing a running/queued scene of the same type (dice rolled again quickly). */
    static void playReplacing(Scene scene) {
        QUEUE.removeIf(p -> p.scene != null && p.scene.getClass() == scene.getClass());
        if (active != null && active.getClass() == scene.getClass()) {
            active = scene;
            startNanos = Clock.nanos();
            return;
        }
        play(scene);
    }

    static void play(Scene scene) {
        QUEUE.addLast(new Pending(null, true, Clock.nanos() - HOLD_NANOS, scene));
    }

    /** Drops wait this long before playing, so a "killed a Beetle" line right after can still label them. */
    private static final long HOLD_NANOS = 600_000_000L;

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static final int MAX_QUEUE = 12;

    private static Scene active;
    private static Drop activeDrop;
    private static long startNanos;

    private CutsceneManager() {
    }

    public static void enqueue(Drop drop, boolean test) {
        Config.Mode mode = Config.mode(drop.tier());
        if (mode == Config.Mode.OFF && !test) return;
        Prices.refreshIfNeeded();

        // Same item again right behind it (Crop Fever spam, pest drops): merge into one.
        Pending last = QUEUE.peekLast();
        if (last != null && last.drop != null && last.drop.name().equals(drop.name()) && last.drop.tier() == drop.tier()) {
            QUEUE.pollLast();
            QUEUE.addLast(new Pending(last.drop.withCount(last.drop.count() + drop.count()), test, last.queuedAt));
            return;
        }
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Pending(drop, test));
    }

    /** A pest was just killed: label waiting drops that look like they came from it. */
    static void attachPest(String pest) {
        Pending[] all = QUEUE.toArray(new Pending[0]);
        QUEUE.clear();
        for (Pending p : all) {
            boolean recent = Clock.nanos() - p.queuedAt < 2_000_000_000L;
            if (recent && p.drop != null && p.drop.pest().isEmpty() && !p.drop.fever() && Farming.isPestish(p.drop)) {
                QUEUE.addLast(new Pending(p.drop.withPest(pest), p.test, p.queuedAt));
            } else {
                QUEUE.addLast(p);
            }
        }
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
        activeDrop = p.drop;
        if (p.scene != null) {
            active = p.scene;
            startNanos = Clock.nanos();
            return;
        }
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
        }, compact, p.test, cfg.music, cfg.vacuum);
        startNanos = Clock.nanos();
    }

    /** HUD element: called every frame. */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            clear();
            return;
        }
        McCanvas canvas = new McCanvas(graphics);
        if (active == null) {
            Pending next = QUEUE.peekFirst();
            if (next != null && Clock.nanos() - next.queuedAt >= HOLD_NANOS) {
                QUEUE.pollFirst();
                start(next);
            }
        }
        if (active == null) {
            Farming.render(canvas, false);
            return;
        }
        float speed = Math.max(0.25f, Math.min(3f, Config.get().speed));
        float t = (Clock.nanos() - startNanos) / 1_000_000_000f * speed;
        try {
            active.render(canvas, t);
        } catch (RuntimeException e) {
            SkyAnimationsClient.LOG.error("Cutscene crashed, skipping it", e);
            active = null;
            return;
        }
        if (active.finished(t)) {
            active = null;
        }
        Farming.render(canvas, true);
    }
}

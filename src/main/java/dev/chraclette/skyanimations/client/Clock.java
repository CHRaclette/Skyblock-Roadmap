package dev.chraclette.skyanimations.client;

/**
 * Animation clock. Normally real time; in demo recording mode it only moves when {@link #step}
 * is called, so every screenshot is exactly one frame apart.
 */
public final class Clock {

    private static boolean manual;
    private static long manualNanos;

    private Clock() {
    }

    public static long nanos() {
        return manual ? manualNanos : System.nanoTime();
    }

    public static void manual(boolean on) {
        if (on && !manual) manualNanos = System.nanoTime();
        manual = on;
    }

    public static void step(float seconds) {
        manualNanos += (long) (seconds * 1_000_000_000L);
    }
}

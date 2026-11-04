package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.SlotScene;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Archfiend Dice rolls -> slot machine. Hypixel's line (pattern as in SkyHanni's ItemAddManager):
 * "Your Archfiend Dice rolled a 4! Bonus: +40❤" / "Your High Class Archfiend Dice rolled a 7! ..."
 */
final class Dice {

    static final Pattern LINE = Pattern.compile("^Your (High Class )?Archfiend Dice rolled a (\\d)! Bonus: (.+)$");

    private Dice() {
    }

    static boolean matches(String text) {
        return LINE.matcher(text.replaceAll("§.", "").trim()).matches();
    }

    static void onChat(String raw, boolean test) {
        if (!Config.get().diceSlot) return;
        Matcher m = LINE.matcher(raw.replaceAll("§.", "").trim());
        if (!m.matches()) {
            onAnyLine(raw);
            return;
        }
        play(Integer.parseInt(m.group(2)), m.group(1) != null, m.group(3).trim());
    }

    private static SlotScene last;
    private static long lastAt;
    private static final Pattern COINS = Pattern.compile("([\\d,]{7,}) [Cc]oins");

    static void play(int roll, boolean highClass, String bonus) {
        last = new SlotScene(roll, highClass, bonus, (cue, pitch, volume) -> Sounds.play(cue, Effect.GOLDEN, pitch, volume));
        lastAt = System.currentTimeMillis();
        CutsceneManager.playReplacing(last);
    }

    /** Right after a 6, Hypixel tells you how many coins you got: put that number on the payout counter. */
    static void onAnyLine(String raw) {
        if (last == null || !last.isSix() || System.currentTimeMillis() - lastAt > 4000) return;
        Matcher m = COINS.matcher(raw.replaceAll("§.", ""));
        if (m.find()) {
            try {
                last.setPayout(Long.parseLong(m.group(1).replace(",", "")));
            } catch (NumberFormatException ignored) {
                // keep what we have
            }
        }
    }

    /** /sa test dice [n] and /sa test hcdice [n]. */
    static void test(boolean highClass, int roll) {
        onChat("Your " + (highClass ? "High Class " : "") + "Archfiend Dice rolled a " + roll + "! Bonus: +" + (roll * 10) + "❤", true);
    }
}

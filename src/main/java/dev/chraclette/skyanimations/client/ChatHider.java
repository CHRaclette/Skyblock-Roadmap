package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Category;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hides the Hypixel chat lines that a scene already shows (/sa hidechat):
 * the Crystal Nucleus loot bundle, "RNG METER! Reselected ...", your own "WOW! ... Dye" line,
 * "PET DROP! Scatha" and RARE DROP lines that get a cutscene. If no scene ends up playing
 * (e.g. a Nucleus run without a rare item while only-rare is on), the lines are put back.
 */
final class ChatHider {

    private static final String BAR = "▬";

    private static Component pendingBar;     // a ▬ line that might start a Nucleus bundle
    private static int pendingTicks;
    private static boolean inNucleus;
    private static long nucleusAt;
    private static final List<Component> nucleusLines = new ArrayList<>();

    private ChatHider() {
    }

    /** ALLOW_GAME: return true to hide the message. */
    static boolean hide(Component message) {
        Config.Data cfg = Config.get();
        if (!cfg.enabled || !cfg.hideChat) {
            flushPending();
            return false;
        }
        String text = message.getString().replaceAll("§.", "");
        String t = text.trim();

        // ---- Crystal Nucleus bundle (starts with a ▬ line we only recognise afterwards)
        if (cfg.nucleus) {
            if (inNucleus) {
                nucleusLines.add(message);
                if (isBar(t)) inNucleus = false; // the closing line; Nucleus decides what to do
                return true;
            }
            if (t.startsWith("CRYSTAL NUCLEUS LOOT BUNDLE")) {
                nucleusLines.clear();
                if (pendingBar != null) nucleusLines.add(pendingBar);
                pendingBar = null;
                nucleusLines.add(message);
                inNucleus = true;
                nucleusAt = System.currentTimeMillis();
                return true;
            }
            if (isBar(t) && !t.contains(":")) {
                flushPending();
                pendingBar = message;
                pendingTicks = 0;
                return true;
            }
        }
        flushPending();

        if (cfg.rngMeter && RngMeter.LINE.matcher(t).matches()) return true;
        String own = SkyAnimationsClient.ownName();
        if (cfg.dyeScene) {
            var m = Specials.WOW.matcher(t);
            if (m.matches() && own != null && m.group("player").equalsIgnoreCase(own)) return true;
        }
        if (t.startsWith("PET DROP!") && cfg.scathaScene && t.toLowerCase(Locale.ROOT).contains("scatha")) return true;
        if (t.startsWith("RARE REWARD!") && !"off".equals(cfg.dungeonChests) && own != null && t.contains(own)) return true;
        // a normal rare drop that gets a full cutscene
        Drop d = DropParser.parse(text, own, null);
        if (d != null && d.category() != Category.FARMING && d.category() != Category.PEST
                && Config.mode(d.tier()) != Config.Mode.OFF) {
            return true;
        }
        return false;
    }

    private static boolean isBar(String t) {
        return t.length() >= 20 && t.chars().allMatch(ch -> ch == BAR.charAt(0) || ch == ' ');
    }

    /** Called by Nucleus when the bundle ended: put the lines back if no scene played. */
    static void nucleusDone(boolean played) {
        if (!played) {
            for (Component c : nucleusLines) add(c);
        }
        nucleusLines.clear();
        inNucleus = false;
    }

    /** Every client tick: a held ▬ line that wasn't a bundle goes back to chat. */
    static void tick() {
        if (pendingBar != null && ++pendingTicks > 2) flushPending();
        if (inNucleus && System.currentTimeMillis() - nucleusAt > 4000) {
            // never saw the closing line: show what we hid
            nucleusDone(false);
        }
    }

    private static void flushPending() {
        if (pendingBar != null) {
            Component c = pendingBar;
            pendingBar = null;
            add(c);
        }
    }

    private static java.lang.reflect.Method chatGetter, addMessage;

    /** Puts a line back into chat. Found by reflection (the chat accessor was renamed in 26.x). */
    static void add(Component c) {
        try {
            Object gui = Minecraft.getInstance().gui;
            if (chatGetter == null) {
                for (var m : gui.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("ChatComponent")) {
                        chatGetter = m;
                        break;
                    }
                }
            }
            if (chatGetter == null) return;
            Object chat = chatGetter.invoke(gui);
            if (addMessage == null) {
                for (var m : chat.getClass().getMethods()) {
                    if (m.getName().equals("addMessage") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(Component.class)) {
                        addMessage = m;
                        break;
                    }
                }
            }
            if (addMessage != null) addMessage.invoke(chat, c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAnimationsClient.LOG.debug("Could not restore chat line", e);
        }
    }
}

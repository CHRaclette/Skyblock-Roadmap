package dev.chraclette.butcher.client;

import dev.chraclette.butcher.core.Category;
import dev.chraclette.butcher.core.DropParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where you are and which profile you play, read the same way SkyHanni / Skyblocker do it without
 * an API key: the tab list ("Profile: Banana", "Area: Garden"), the sidebar title ("SKYBLOCK") and
 * the "You are playing on profile:" chat line.
 */
public final class HypixelInfo {

    private static final Pattern PROFILE_TAB = Pattern.compile("^\\s*Profile: ([\\w ]+?)(?:\\s*[^\\w ].*)?$");
    private static final Pattern AREA_TAB = Pattern.compile("^\\s*(?:Area|Dungeon): (.+?)\\s*$");
    private static final Pattern PROFILE_CHAT = Pattern.compile("^You are playing on profile: ([\\w ]+?)(?: \\(.*\\))?$");
    private static final Pattern PROFILE_SWITCH = Pattern.compile("^Your profile was changed to: ([\\w ]+?)(?: \\(.*\\))?!?$");

    private static String profile = "";
    private static String area = "";
    private static boolean skyblock;
    private static int ticks;

    private HypixelInfo() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks % 20 != 0 || client.player == null || client.getConnection() == null) return;
            try {
                scan(client);
            } catch (RuntimeException e) {
                ButcherClient.LOG.debug("Tab list scan failed", e);
            }
        });
    }

    private static void scan(Minecraft client) {
        Objective sidebar = client.getConnection().scoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        skyblock = sidebar != null && DropParser.strip(sidebar.getDisplayName().getString())
                .toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        String newArea = "";
        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            var name = info.getTabListDisplayName();
            if (name == null) continue;
            String line = DropParser.strip(name.getString());
            Matcher m = PROFILE_TAB.matcher(line);
            if (m.matches()) setProfile(m.group(1));
            m = AREA_TAB.matcher(line);
            if (m.matches()) newArea = m.group(1);
        }
        area = newArea;
    }

    /** Chat lines that tell us the profile. */
    static void onChat(String text) {
        String t = DropParser.strip(text);
        Matcher m = PROFILE_CHAT.matcher(t);
        if (m.matches()) setProfile(m.group(1));
        m = PROFILE_SWITCH.matcher(t);
        if (m.matches()) setProfile(m.group(1));
    }

    private static void setProfile(String name) {
        String p = name.trim();
        if (p.isEmpty() || p.equalsIgnoreCase(profile)) return;
        profile = p;
        LedgerStore.switchProfile(p);
    }

    public static String profile() {
        return profile;
    }

    public static String area() {
        return area;
    }

    /** True when the sidebar says SKYBLOCK (or we can't tell yet). */
    public static boolean inSkyBlock() {
        return skyblock || area.isEmpty() && profile.isEmpty();
    }

    /** A fallback category from where you are, for drops whose name gives no hint. */
    public static Category areaContext() {
        String a = area.toLowerCase(Locale.ROOT);
        if (a.contains("garden")) return Category.FARMING;
        if (a.contains("catacombs") || a.contains("dungeon")) return Category.DUNGEON;
        if (a.contains("spider")) return Category.TARANTULA;
        if (a.contains("end")) return Category.VOIDGLOOM;
        if (a.contains("crimson")) return Category.INFERNO;
        if (a.contains("rift")) return Category.RIFTSTALKER;
        if (a.contains("backwater") || a.contains("jerry") || a.contains("park") && a.contains("fish")) return Category.FISHING;
        return null;
    }
}

package dev.chraclette.butcher.client;

import dev.chraclette.butcher.core.Category;
import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.core.DropParser;
import dev.chraclette.butcher.core.FarmEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class ButcherClient implements ClientModInitializer {

    public static final String MOD_ID = "butcherdrops";
    public static final Logger LOG = LoggerFactory.getLogger("Butcher Drops");

    /** Last context hint from chat (e.g. a Diana dig) and when we saw it. */
    private static Category context;
    private static long contextAt;

    @Override
    public void onInitializeClient() {
        Config.load();
        LedgerStore.load();
        BookKey.register();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "cutscene"),
                CutsceneManager::render);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CutsceneManager.clear();
            Farming.clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Prices.refreshIfNeeded());

        Commands.register();
        LOG.info("Butcher Drops ready. Try /butcher test");
    }

    private static void onChat(Component message) {
        Config.Data cfg = Config.get();
        if (!cfg.enabled) return;
        if (cfg.onlyOnHypixel && !onHypixel()) return;
        handle(message.getString(), false);
    }

    /** Runs one chat line through everything. Also used by the test commands. */
    static void handle(String text, boolean test) {
        FarmEvent farm = FarmEvent.parse(text);
        if (farm != null) {
            Farming.testing = test;
            Farming.onEvent(farm);
            Farming.testing = false;
            return;
        }

        Category hint = DropParser.contextFrom(text);
        if (hint != null) {
            context = hint == Category.GENERIC ? null : hint;
            contextAt = System.currentTimeMillis();
        }
        Category recent = System.currentTimeMillis() - contextAt < 20_000 ? context : null;

        Drop drop = DropParser.parse(text, test ? "You" : ownName(), recent);
        if (drop != null) {
            Drop routed = Farming.onDrop(drop);
            if (!test) LedgerStore.record(routed != null ? routed : drop);
            drop = routed;
            if (drop != null) {
                CutsceneManager.enqueue(drop, test);
            }
        }
    }

    static String ownName() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean onHypixel() {
        var server = Minecraft.getInstance().getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel");
    }
}

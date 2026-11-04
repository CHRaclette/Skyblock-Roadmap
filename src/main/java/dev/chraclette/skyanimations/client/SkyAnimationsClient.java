package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Category;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.FarmEvent;
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

public final class SkyAnimationsClient implements ClientModInitializer {

    public static final String MOD_ID = "skyanimations";
    public static final Logger LOG = LoggerFactory.getLogger("SkyAnimations");

    /** Last context hint from chat (e.g. a Diana dig) and when we saw it. */
    private static Category context;
    private static long contextAt;

    @Override
    public void onInitializeClient() {
        Migration.run();
        Config.load();
        LedgerStore.load();
        ItemCatalog.init();
        HypixelInfo.register();
        BookKey.register();
        DungeonChest.register();
        NeuRepo.init();
        HeadTextures.register();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "cutscene"),
                CutsceneManager::render);

        // lines a scene already shows are hidden (ALLOW_GAME); hidden ones still reach onChat
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !ChatHider.hide(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CutsceneManager.clear();
            Farming.clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Prices.refreshIfNeeded());

        Commands.register();
        LOG.info("SkyAnimations ready. Try /sa test (or /skyanimations test)");
    }

    private static void onChat(Component message) {
        Config.Data cfg = Config.get();
        if (!cfg.enabled) return;
        if (cfg.onlyOnHypixel && !onHypixel()) return;
        HypixelInfo.onChat(message.getString());
        if (cfg.onlyOnHypixel && !HypixelInfo.inSkyBlock()) return;
        Nucleus.onChat(message.getString(), false);
        RngMeter.onChat(message.getString(), false);
        Dice.onChat(message.getString(), false);
        DungeonRun.onChat(message.getString());
        if (Specials.onWow(message.getString(), ownName(), false)) return;
        Specials.current = message;
        try {
            handle(message.getString(), false);
        } finally {
            Specials.current = null;
        }
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
        if (recent == null) recent = HypixelInfo.areaContext();

        Drop drop = DropParser.parse(text, test ? "You" : ownName(), recent);
        if (drop != null) {
            Drop routed = Farming.onDrop(drop);
            if (!test && !Specials.alreadyRecorded(drop.name())) LedgerStore.record(routed != null ? routed : drop);
            drop = routed;
            // own "RARE REWARD! ... in their Obsidian Chest" is shown by the dungeon chest scene
            if (drop != null && !test && text.startsWith("RARE REWARD!") && !"off".equals(Config.get().dungeonChests)) return;
            if (drop != null && !Specials.play(drop, test)) {
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

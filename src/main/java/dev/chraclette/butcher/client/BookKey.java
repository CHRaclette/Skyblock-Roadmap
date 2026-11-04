package dev.chraclette.butcher.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** "J" opens the sticker book (changeable in Options > Controls). */
final class BookKey {

    private static KeyMapping key;
    private static boolean openNextTick;

    private BookKey() {
    }

    static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "main"));
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.butcherdrops.book", InputConstants.Type.KEYSYM, InputConstants.KEY_J, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.gui.screen() == null) openNextTick = true;
            }
            if (openNextTick) {
                openNextTick = false;
                client.gui.setScreen(new BookScreen());
            }
        });
    }

    /** Commands run while the chat is open; open the book once it has closed. */
    static void openSoon() {
        openNextTick = true;
    }
}

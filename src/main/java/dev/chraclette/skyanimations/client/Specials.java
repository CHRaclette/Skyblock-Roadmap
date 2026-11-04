package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropTier;
import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.DyeScene;
import dev.chraclette.skyanimations.render.ScathaScene;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Drops that get their own scene instead of the cleaver cutscene: dyes and Scatha / Crystal Hollows pets. */
final class Specials {

    /** The chat message being handled right now (for colours that only exist in the formatting). */
    static Component current;

    private Specials() {
    }

    /** @return true if a special scene was queued for this drop */
    static boolean play(Drop drop, boolean test) {
        Config.Data cfg = Config.get();
        String lower = drop.name().toLowerCase(Locale.ROOT);
        Object icon = ItemLookup.icon(drop, ItemLookup.findInInventory(drop));
        String bonus = drop.bonus() == null ? "" : drop.bonus().replaceAll("[()]", "").trim();
        if (cfg.dyeScene && drop.effect() == Effect.DYE) {
            int rarity = ItemCatalog.tierColor(Runs.tier(drop.name()));
            CutsceneManager.play(new DyeScene(drop.name(), icon, drop.color(), rarity, bonus,
                    (cue, pitch, volume) -> Sounds.play(cue, Effect.DYE, pitch, volume)));
            return true;
        }
        String area = HypixelInfo.area();
        boolean hollows = area != null && area.toLowerCase(Locale.ROOT).contains("crystal hollows");
        if (cfg.scathaScene && drop.tier() == DropTier.PET && (lower.contains("scatha") || hollows)) {
            int rarity = nameColor(drop.name());
            if (rarity < 0) rarity = 0xFFAA00;
            CutsceneManager.play(new ScathaScene(drop.name(), rarityLabel(rarity), icon, rarity,
                    hollows ? "Crystal Hollows" + (bonus.isEmpty() ? "" : "  ·  " + bonus) : bonus,
                    (cue, pitch, volume) -> Sounds.play(cue, Effect.STARS, pitch, volume)));
            return true;
        }
        return false;
    }

    /** Colour of the part of the chat message that holds the name (pets are coloured by rarity). */
    private static int nameColor(String name) {
        if (current == null) return -1;
        String first = name.split(" ")[0];
        for (Component part : current.toFlatList()) {
            if (part.getString().contains(first)) {
                int c = ItemLookup.color(part);
                if (c >= 0) return c;
            }
        }
        return -1;
    }

    private static String rarityLabel(int color) {
        return switch (color) {
            case 0xFFFFFF -> "Common";
            case 0x55FF55 -> "Uncommon";
            case 0x5555FF -> "Rare";
            case 0xAA00AA -> "Epic";
            case 0xFFAA00 -> "Legendary";
            case 0xFF55FF -> "Mythic";
            default -> "";
        };
    }
}

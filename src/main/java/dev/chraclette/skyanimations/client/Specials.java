package dev.chraclette.skyanimations.client;

import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropTier;
import dev.chraclette.skyanimations.core.Effect;
import dev.chraclette.skyanimations.render.DyeScene;
import dev.chraclette.skyanimations.render.Cutscene;
import dev.chraclette.skyanimations.render.BlazeScene;
import dev.chraclette.skyanimations.render.ScathaScene;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Drops that get their own scene instead of the cleaver cutscene: dyes and Scatha / Crystal Hollows pets. */
final class Specials {

    /** Scatha head texture (same value Scatha-Pro uses for its pet item). */
    private static final String SCATHA_TEXTURE = "ewogICJ0aW1lc3RhbXAiIDogMTYyMDQ0NTc2NDQ1MSwKICAicHJvZmlsZUlkIiA6ICJmNDY0NTcxNDNkMTU0ZmEwOTkxNjBlNGJmNzI3ZGNiOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWxhcGFnbzA1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2RmMDNhZDk2MDkyZjNmNzg5OTAyNDM2NzA5Y2RmNjlkZTZiNzI3YzEyMWIzYzJkYWVmOWZmYTFjY2FlZDE4NmMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";

    static {
        HeadTextures.put("Scatha", SCATHA_TEXTURE);
    }

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
            dye(drop.name(), icon, bonus);
            return true;
        }
        if (cfg.blazeScene && drop.category() == dev.chraclette.skyanimations.core.Category.INFERNO) {
            Cutscene.Price price = Prices.lookup(drop, "");
            var e = LedgerStore.get().entry(drop.name());
            int times = e == null ? 1 : Math.max(1, e.times);
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("Your " + Runs.ordinal(times) + " " + drop.name());
            if (!bonus.isEmpty()) lines.add(bonus);
            int rarity = ItemCatalog.tierColor(Runs.tier(drop.name()));
            if (rarity == 0xFFFFFF) rarity = drop.tier().color;
            CutsceneManager.play(new BlazeScene(drop.name(), icon, rarity, price == null ? 0 : price.coins(), lines,
                    (cue, pitch, volume) -> Sounds.play(cue, Effect.INFERNO, pitch, volume)));
            return true;
        }
        String area = HypixelInfo.area();
        boolean hollows = area != null && area.toLowerCase(Locale.ROOT).contains("crystal hollows");
        if (cfg.scathaScene && drop.tier() == DropTier.PET && (lower.contains("scatha") || hollows)) {
            int rarity = nameColor(drop.name());
            if (rarity < 0) rarity = 0xFFAA00;
            if (lower.contains("scatha")) {
                ItemStack head = ItemCatalog.head("scatha-pet", SCATHA_TEXTURE);
                String label = rarityLabel(rarity);
                ItemCatalog.tagPet(head, "SCATHA", label.isEmpty() ? "LEGENDARY" : label.toUpperCase(Locale.ROOT), "Scatha", rarity);
                icon = head;
            }
            var e = LedgerStore.get().entry(drop.name());
            int times = e == null ? 1 : Math.max(1, e.times);
            Cutscene.Price price = Prices.lookup(drop, "");
            String what = lower.contains("scatha") ? "Scatha pet" : drop.name();
            CutsceneManager.play(new ScathaScene(drop.name(), rarityLabel(rarity), icon, rarity,
                    "Your " + Runs.ordinal(times) + " " + what + (bonus.isEmpty() ? "" : "  ·  " + bonus),
                    price == null ? 0 : price.coins(),
                    (cue, pitch, volume) -> Sounds.play(cue, Effect.STARS, pitch, volume)));
            return true;
        }
        return false;
    }

    /**
     * Hypixel announces every dye to the whole server: "WOW! [MVP+] Name found a Necron Dye #7!"
     * (pattern from Skyblocker's DyeSpecialEffects). Only our own name plays the scene.
     */
    static final java.util.regex.Pattern WOW = java.util.regex.Pattern.compile(
            "^WOW! (?:\\[[A-Z+]+] )?(?<player>[A-Za-z0-9_]+) found (?:a|an) (?<dye>[A-Za-z' ]+ Dye)(?: #(?<n>[\\d,]+))?!$");

    private static String lastDye = "";
    private static long lastDyeAt;

    static boolean onWow(String raw, String ownName, boolean test) {
        if (!Config.get().dyeScene) return false;
        var m = WOW.matcher(raw.replaceAll("§.", "").trim());
        if (!m.matches()) return false;
        if (!test && (ownName == null || !m.group("player").equalsIgnoreCase(ownName))) return false;
        Drop d = dev.chraclette.skyanimations.core.DropParser.parse("RARE DROP! " + m.group("dye"), null, null);
        Object icon = d == null ? null : ItemLookup.icon(d, ItemLookup.findInInventory(d));
        String n = m.group("n");
        boolean played = dye(m.group("dye"), icon, n == null ? "" : "#" + n + " ever dropped");
        if (played && !test && d != null) {
            LedgerStore.record(d);
            recordedDye = m.group("dye");
        }
        return true;
    }

    /** Plays the paint-bomb scene once per dye (the RARE DROP line and the WOW line both arrive). */
    private static String recordedDye = "";

    /** True if the WOW line already put this dye in the sticker book a moment ago. */
    static boolean alreadyRecorded(String name) {
        return name.equalsIgnoreCase(recordedDye) && System.currentTimeMillis() - lastDyeAt < 10_000;
    }

    private static boolean dye(String name, Object icon, String subtitle) {
        long now = System.currentTimeMillis();
        if (name.equalsIgnoreCase(lastDye) && now - lastDyeAt < 10_000) return false;
        recordedDye = "";
        lastDye = name;
        lastDyeAt = now;
        int[] palette = NeuRepo.dyeColors(name);
        if (palette == null) palette = new int[]{dev.chraclette.skyanimations.core.Themes.color(name, Effect.DYE)};
        int rarity = ItemCatalog.tierColor(Runs.tier(name));
        CutsceneManager.replace(name, new DyeScene(name, icon, palette, rarity, subtitle, new DyeScene.Hooks() {
            @Override
            public void sound(dev.chraclette.skyanimations.render.Cutscene.Cue cue, float pitch, float volume) {
                Sounds.play(cue, Effect.DYE, pitch, volume);
            }

            @Override
            public boolean popIn(Object icon) {
                return itemActivation(icon);
            }
        }));
        return true;
    }

    private static java.lang.reflect.Method activation;

    /**
     * Minecraft's item-activation animation (the totem of undying pop-in) with this item, the same
     * effect Skyblocker and DropVision use for rare drops. Found by reflection so a renamed method
     * can't break the build; if it's missing the scene simply shows its own icon.
     */
    static boolean itemActivation(Object icon) {
        if (!(icon instanceof ItemStack stack) || stack.isEmpty()) return false;
        try {
            Object renderer = net.minecraft.client.Minecraft.getInstance().gameRenderer;
            if (activation == null) {
                for (var m : renderer.getClass().getMethods()) {
                    if (m.getName().equals("displayItemActivation") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == ItemStack.class) {
                        activation = m;
                        break;
                    }
                }
            }
            if (activation == null) return false;
            activation.invoke(renderer, stack.copy());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAnimationsClient.LOG.debug("No item activation animation", e);
            return false;
        }
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

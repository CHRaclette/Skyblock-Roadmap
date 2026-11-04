package dev.chraclette.skyanimations.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.DropTier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** /sa ... */
final class Commands {

    private Commands() {
    }

    /** Test drops: realistic chat lines, run through the same parser as real drops. */
    static final Map<String, String> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("chimera", "RARE DROP! Enchanted Book (Chimera I) (+254% ✯ Magic Find)");
        PRESETS.put("wool", "RARE DROP! You dug out a Shimmering Wool! (+180% ✯ Magic Find)");
        PRESETS.put("dye", "CRAZY RARE DROP! (Mythological Dye) (+312% ✯ Magic Find)");
        PRESETS.put("judgement", "VERY RARE DROP! (Judgement Core) (+250% ✯ Magic Find)");
        PRESETS.put("overflux", "PRAY TO RNGESUS DROP! (Overflux Capacitor) (+180% ✯ Magic Find)");
        PRESETS.put("scythe", "CRAZY RARE DROP! (Scythe Blade) (+220% ✯ Magic Find)");
        PRESETS.put("mosquito", "CRAZY RARE DROP! (Digested Mosquito) (+200% ✯ Magic Find)");
        PRESETS.put("archfiend", "VERY RARE DROP! (High Class Archfiend Dice) (+150% ✯ Magic Find)");
        PRESETS.put("handle", "RARE REWARD! [MVP+] You found a Necron's Handle in their Bedrock Chest!");
        PRESETS.put("carmine", "INSANE DROP! (Carmine Dye) (+120% ✯ Magic Find)");
        PRESETS.put("cropie", "RARE CROP! Cropie (+23 ☘ Farming Fortune)");
        PRESETS.put("pest", "RARE DROP! Atmospheric Filter (+12% ✯ Pest Luck)");
        PRESETS.put("vacuum", "RARE DROP! Beady Eyes (+12% ✯ Magic Find)");
        PRESETS.put("feverdrop", "PRAY TO RNGESUS DROP! You dropped 12x Enchanted Hay Bale!");
        PRESETS.put("pet", "PET DROP! Ender Dragon (+200% ✯ Magic Find)");
    }

    private static int next;

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            var test = ClientCommands.literal("test").executes(ctx -> {
                String key = PRESETS.keySet().toArray(new String[0])[next++ % PRESETS.size()];
                return play(ctx, PRESETS.get(key), key);
            });
            for (String key : PRESETS.keySet()) {
                if (key.equals("vacuum")) {
                    test.then(ClientCommands.literal(key).executes(ctx -> {
                        Drop d = DropParser.parse(PRESETS.get(key), "You", null);
                        CutsceneManager.clear();
                        CutsceneManager.enqueue(d.withPest("Fly"), true);
                        say(ctx, "§7Playing §evacuum §7→ Beady Eyes from a Fly");
                        return 1;
                    }));
                    continue;
                }
                test.then(ClientCommands.literal(key).executes(ctx -> play(ctx, PRESETS.get(key), key)));
            }
            test.then(ClientCommands.literal("fever").executes(ctx -> {
                Farming.testFever();
                say(ctx, "§7Simulating a §6Crop Fever §7(20 s) with a few rolls...");
                return 1;
            }));
            test.then(ClientCommands.literal("stickers").executes(ctx -> {
                Drop d = DropParser.parse(PRESETS.get("chimera"), "You", null);
                Farming.testStickers(ItemLookup.icon(d, net.minecraft.world.item.ItemStack.EMPTY));
                say(ctx, "§7Showing every sticker level (1★ to 5★ + crown), then a badge going Bronze → Silver → Gold");
                return 1;
            }));
            test.then(ClientCommands.literal("pests").executes(ctx -> {
                Farming.testPests();
                say(ctx, "§7Simulating §cpests§7: spawn, three kills and a rare drop...");
                return 1;
            }));

            var root = dispatcher.register(ClientCommands.literal("skyanimations")
                    .executes(Commands::help)
                    .then(test)
                    .then(ClientCommands.literal("simulate")
                            .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "message"), null))))
                    .then(ClientCommands.literal("item")
                            .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String n = StringArgumentType.getString(ctx, "name");
                                        say(ctx, "§6" + n + "§7 (" + ItemCatalog.size() + " items known)");
                                        for (String l : ItemCatalog.debug(n)) say(ctx, " §7" + l);
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("where").executes(ctx -> {
                        say(ctx, "§7Profile: §f" + (HypixelInfo.profile().isEmpty() ? "?" : HypixelInfo.profile())
                                + " §7Area: §f" + (HypixelInfo.area().isEmpty() ? "?" : HypixelInfo.area())
                                + " §7SkyBlock: §f" + HypixelInfo.inSkyBlock());
                        return 1;
                    }))
                    .then(ClientCommands.literal("book").executes(ctx -> {
                        BookKey.openSoon();
                        return 1;
                    }))
                    .then(ClientCommands.literal("skip").executes(ctx -> {
                        CutsceneManager.skip();
                        return 1;
                    }))
                    .then(ClientCommands.literal("toggle").executes(ctx -> toggle(ctx, "enabled")))
                    .then(ClientCommands.literal("sound").executes(ctx -> toggle(ctx, "sound")))
                    .then(ClientCommands.literal("music").executes(ctx -> toggle(ctx, "music")))
                    .then(ClientCommands.literal("prices").executes(ctx -> toggle(ctx, "prices")))
                    .then(ClientCommands.literal("everywhere").executes(ctx -> toggle(ctx, "everywhere")))
                    .then(ClientCommands.literal("speed")
                            .then(ClientCommands.argument("value", FloatArgumentType.floatArg(0.25f, 3f))
                                    .executes(ctx -> {
                                        Config.get().speed = FloatArgumentType.getFloat(ctx, "value");
                                        Config.save();
                                        say(ctx, "Speed: " + Config.get().speed + "x");
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("volume")
                            .then(ClientCommands.argument("value", FloatArgumentType.floatArg(0f, 2f))
                                    .executes(ctx -> {
                                        Config.get().volume = FloatArgumentType.getFloat(ctx, "value");
                                        Config.save();
                                        say(ctx, "Volume: " + Config.get().volume);
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("farming")
                            .executes(Commands::farming)
                            .then(ClientCommands.argument("setting", StringArgumentType.word())
                                    .executes(Commands::toggleFarming)))
                    .then(ClientCommands.literal("mode")
                            .executes(Commands::listModes)
                            .then(ClientCommands.argument("tier", StringArgumentType.word())
                                    .then(ClientCommands.argument("mode", StringArgumentType.word())
                                            .executes(Commands::setMode))))
            );
            // short alias: /sa ...
            dispatcher.register(ClientCommands.literal("sa").executes(Commands::help).redirect(root));
        });
    }

    private static int play(CommandContext<FabricClientCommandSource> ctx, String line, String key) {
        Drop drop = DropParser.parse(line, "You", null);
        if (drop == null) {
            say(ctx, "§cThat line is not a drop message I recognise.");
            return 0;
        }
        CutsceneManager.clear();
        SkyAnimationsClient.handle(line, true);
        say(ctx, "§7Playing " + (key == null ? "" : "§e" + key + " §7") + "→ " + drop.tier().label + " §f"
                + drop.displayName() + " §8(" + drop.category().displayName + ", " + drop.effect().name().toLowerCase(Locale.ROOT) + ")");
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> ctx, String what) {
        Config.Data c = Config.get();
        boolean value = switch (what) {
            case "enabled" -> c.enabled = !c.enabled;
            case "sound" -> c.sound = !c.sound;
            case "music" -> c.music = !c.music;
            case "prices" -> c.prices = !c.prices;
            default -> !(c.onlyOnHypixel = !c.onlyOnHypixel);
        };
        Config.save();
        say(ctx, "§7" + what + ": " + (value ? "§aon" : "§coff"));
        return 1;
    }

    private static final String[][] FARMING = {
            {"feverintro", "Crop Fever intro animation"},
            {"fevertimer", "Crop Fever countdown bar"},
            {"feversummary", "summary when the fever ends"},
            {"fevertoasts", "pop-ups for small fever rolls"},
            {"pestspawn", "pop-up when pests spawn"},
            {"pestkills", "pop-up when you vacuum a pest"},
            {"vacuum", "vacuum scene for pest drops"},
    };

    private static boolean farmingValue(String key) {
        Config.Data c = Config.get();
        return switch (key) {
            case "feverintro" -> c.feverIntro;
            case "fevertimer" -> c.feverTimer;
            case "feversummary" -> c.feverSummary;
            case "fevertoasts" -> c.feverToasts;
            case "pestspawn" -> c.pestSpawn;
            case "pestkills" -> c.pestKills;
            case "vacuum" -> c.vacuum;
            default -> false;
        };
    }

    private static int farming(CommandContext<FabricClientCommandSource> ctx) {
        say(ctx, "§6Garden settings §7(click-free: /sa farming <name> toggles it)");
        for (String[] f : FARMING) {
            say(ctx, " §e" + f[0] + " " + (farmingValue(f[0]) ? "§aon" : "§coff") + " §8- " + f[1]);
        }
        say(ctx, " §efevercutscene §f" + Config.get().feverCutsceneFromRank
                + " §8- rolls from this rank get a full cutscene (2 rare, 4 crazy rare, 6 rngesus, 9 never)");
        return 1;
    }

    private static int toggleFarming(CommandContext<FabricClientCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "setting").toLowerCase(Locale.ROOT);
        Config.Data c = Config.get();
        switch (key) {
            case "feverintro" -> c.feverIntro = !c.feverIntro;
            case "fevertimer" -> c.feverTimer = !c.feverTimer;
            case "feversummary" -> c.feverSummary = !c.feverSummary;
            case "fevertoasts" -> c.feverToasts = !c.feverToasts;
            case "pestspawn" -> c.pestSpawn = !c.pestSpawn;
            case "pestkills" -> c.pestKills = !c.pestKills;
            case "vacuum" -> c.vacuum = !c.vacuum;
            case "fevercutscene" -> c.feverCutsceneFromRank = c.feverCutsceneFromRank == 4 ? 6
                    : c.feverCutsceneFromRank == 6 ? 9 : c.feverCutsceneFromRank == 9 ? 2 : 4;
            default -> {
                say(ctx, "§cUnknown setting. /sa farming lists them.");
                return 0;
            }
        }
        Config.save();
        if (key.equals("fevercutscene")) {
            say(ctx, "§7fevercutscene: §f" + c.feverCutsceneFromRank);
        } else {
            say(ctx, "§7" + key + ": " + (farmingValue(key) ? "§aon" : "§coff"));
        }
        return 1;
    }

    private static int listModes(CommandContext<FabricClientCommandSource> ctx) {
        say(ctx, "§6Cutscene per drop type §7(full / compact / off):");
        for (DropTier tier : DropTier.values()) {
            say(ctx, " §e" + tier.key() + "§7: " + Config.mode(tier).name().toLowerCase(Locale.ROOT));
        }
        say(ctx, "§7Change with §f/sa mode <type> <full|compact|off>");
        return 1;
    }

    private static int setMode(CommandContext<FabricClientCommandSource> ctx) {
        DropTier tier = DropTier.fromKey(StringArgumentType.getString(ctx, "tier"));
        String m = StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT);
        if (tier == null) {
            say(ctx, "§cUnknown type. Use /sa mode to see them.");
            return 0;
        }
        Config.Mode mode;
        try {
            mode = Config.Mode.valueOf(m);
        } catch (IllegalArgumentException e) {
            say(ctx, "§cMode must be full, compact or off.");
            return 0;
        }
        Config.get().modes.put(tier.key(), mode);
        Config.save();
        say(ctx, "§e" + tier.key() + "§7 → " + mode.name().toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        say(ctx, "§6§lSkyAnimations §7— rare-drop cutscenes");
        say(ctx, "§f/sa test §7[" + String.join(", ", PRESETS.keySet()) + ", fever, pests, stickers]");
        say(ctx, "§f/sa farming §7Crop Fever + pest settings");
        say(ctx, "§f/sa book §7or §fJ §7open the sticker book");
        say(ctx, "§f/sa simulate <chat line> §7test any drop message");
        say(ctx, "§f/sa skip §7stop the current cutscene");
        say(ctx, "§f/sa toggle | sound | music | prices §7switch on/off");
        say(ctx, "§f/sa speed <0.25-3> §7| §f/sa volume <0-2>");
        say(ctx, "§f/sa mode §7full / compact / off per drop type");
        say(ctx, "§f/sa everywhere §7also react outside Hypixel");
        return 1;
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(Component.literal(text));
    }
}

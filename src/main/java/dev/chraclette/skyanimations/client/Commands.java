package dev.chraclette.skyanimations.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.chraclette.skyanimations.core.Drop;
import dev.chraclette.skyanimations.core.DropParser;
import dev.chraclette.skyanimations.core.DropTier;
import dev.chraclette.skyanimations.core.Ledger;
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
        PRESETS.put("scatha", "PET DROP! Scatha (+254% ✯ Magic Find)");
        PRESETS.put("necrondye", "RARE DROP! Necron Dye (+312% ✯ Magic Find)");
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
            test.then(ClientCommands.literal("nucleus").executes(ctx -> {
                        Nucleus.test(true);
                        say(ctx, "§7Simulating a §dCrystal Nucleus §7run with a rare drop...");
                        return 1;
                    })
                    .then(ClientCommands.literal("common").executes(ctx -> {
                        Nucleus.test(false);
                        say(ctx, "§7Simulating a §dCrystal Nucleus §7run without a rare drop...");
                        return 1;
                    })));
            test.then(ClientCommands.literal("chest").executes(ctx -> {
                DungeonChest.test();
                say(ctx, "§7Simulating an §5Obsidian Chest §7with a Necron's Handle...");
                return 1;
            }));
            test.then(ClientCommands.literal("wow").executes(ctx -> {
                Specials.onWow("WOW! [MVP+] You found a Rose Dye #12!", "You", true);
                say(ctx, "§7Simulating §dWOW! You found a Rose Dye§7 (an animated dye)...");
                return 1;
            }));
            test.then(ClientCommands.literal("rngmeter").executes(ctx -> {
                RngMeter.test();
                say(ctx, "§7Simulating an §dRNG Meter §7drop (Judgement Core)...");
                return 1;
            }));
            test.then(ClientCommands.literal("pests").executes(ctx -> {
                Farming.testPests();
                say(ctx, "§7Simulating §cpests§7: spawn, three kills and a rare drop...");
                return 1;
            }));

            var give = ClientCommands.literal("give").executes(ctx -> {
                say(ctx, "§f/sa give all §7every known sticker at a random level");
                say(ctx, "§f/sa give <paper|bronze|silver|gold|diamond|holo> [name] §7one sticker, or all of them");
                say(ctx, "§f/sa give reset §7remove the test stickers again");
                return 1;
            });
            give.then(ClientCommands.literal("all").executes(ctx -> giveAll(ctx, null)));
            give.then(ClientCommands.literal("reset").executes(ctx -> {
                int n = LedgerStore.clearTest();
                say(ctx, "§7Removed §f" + n + " §7test sticker" + (n == 1 ? "" : "s") + ". Your real ones are untouched.");
                return 1;
            }));
            for (Ledger.Level lv : Ledger.Level.values()) {
                String key = lv.name().toLowerCase(Locale.ROOT);
                give.then(ClientCommands.literal(key).executes(ctx -> giveAll(ctx, lv))
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name").trim();
                            LedgerStore.giveTest(name, lv, true, 0.1f);
                            LedgerStore.finishGive(1.2f);
                            say(ctx, "§7Gave §f" + name + " §7as a §" + levelCode(lv) + lv.title + " §7sticker (test). §8/sa give reset §7removes it.");
                            return 1;
                        })));
            }

            var root = dispatcher.register(ClientCommands.literal("skyanimations")
                    .executes(Commands::help)
                    .then(test)
                    .then(give)
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
                    .then(ClientCommands.literal("ironman")
                            .executes(ctx -> {
                                Config.Data c = Config.get();
                                c.ironman = switch (c.ironman) { case "auto" -> "on"; case "on" -> "off"; default -> "auto"; };
                                Config.save();
                                say(ctx, "§7Ironman prices (NPC sell price): §f" + c.ironman
                                        + (c.ironman.equals("auto") ? " §8(now " + (Prices.ironman() ? "§aon" : "§coff") + "§8)" : ""));
                                return 1;
                            }))
                    .then(ClientCommands.literal("nucleus")
                            .executes(ctx -> {
                                Config.Data c = Config.get();
                                c.nucleusOnlyRare = !c.nucleusOnlyRare;
                                Config.save();
                                say(ctx, "§7Crystal Nucleus scene: §f" + (c.nucleusOnlyRare ? "only when a rare item drops" : "every run (case only for rare items)"));
                                return 1;
                            }))
                    .then(ClientCommands.literal("hidechat").executes(ctx -> toggle(ctx, "hideChat")))
                    .then(ClientCommands.literal("apikey")
                            .executes(ctx -> {
                                say(ctx, ProfileApi.hasKey() ? "§7API key set: §f" + ProfileApi.mask(Config.get().apiKey) + " §8(/sa apikey clear)"
                                        : "§7No API key. Make a §fPersonal API Key§7 on §fdeveloper.hypixel.net§7, then §f/sa apikey <key>");
                                return 1;
                            })
                            .then(ClientCommands.argument("key", StringArgumentType.word()).executes(ctx -> {
                                String key = StringArgumentType.getString(ctx, "key").trim();
                                Config.get().apiKey = key.equalsIgnoreCase("clear") ? "" : key;
                                Config.save();
                                if (!ProfileApi.hasKey()) {
                                    say(ctx, "§7API key removed. Runs are counted in game.");
                                    return 1;
                                }
                                say(ctx, "§7API key saved. Reading your profile...");
                                ProfileApi.refresh(m -> say(ctx, m));
                                return 1;
                            })))
                    .then(ClientCommands.literal("runs").executes(ctx -> {
                        var l = LedgerStore.get();
                        StringBuilder floors = new StringBuilder();
                        for (String f : new String[]{"F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"}) {
                            long n = l.runs(f);
                            if (n > 0) floors.append(" §8").append(f).append(" §f").append(n);
                        }
                        say(ctx, "§7Nucleus runs: §f" + l.runs("nucleus") + "  §7Catacombs: §f" + l.dungeonRuns() + floors
                                + (l.runsFromProfileAt > 0 ? " §8(from profile)" : " §8(counted in game)"));
                        if (ProfileApi.hasKey()) ProfileApi.refresh(m -> say(ctx, m));
                        return 1;
                    }))
                    .then(ClientCommands.literal("chests").executes(ctx -> {
                        Config.Data cfg = Config.get();
                        cfg.dungeonChests = switch (cfg.dungeonChests) {
                            case "all" -> "rare";
                            case "rare" -> "off";
                            default -> "all";
                        };
                        Config.save();
                        say(ctx, "§7Dungeon chest scenes: §f" + cfg.dungeonChests + " §8(all → rare → off)");
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
            case "hideChat" -> c.hideChat = !c.hideChat;
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
        say(ctx, "§f/sa test §7[" + String.join(", ", PRESETS.keySet()) + ", fever, pests, stickers, nucleus, chest, rngmeter, wow]");
        say(ctx, "§f/sa ironman §7prices as NPC sell prices (auto / on / off)");
        say(ctx, "§f/sa nucleus §7Crystal Nucleus scene every run or only for rare drops");
        say(ctx, "§f/sa farming §7Crop Fever + pest settings");
        say(ctx, "§f/sa book §7or §fJ §7open the sticker book");
        say(ctx, "§f/sa simulate <chat line> §7test any drop message");
        say(ctx, "§f/sa give §7[all, <level> [name], reset] §7test stickers for the book");
        say(ctx, "§f/sa runs §7run counts · §f/sa apikey <key> §7read them from your profile · §f/sa hidechat");
        say(ctx, "§f/sa skip §7stop the current cutscene");
        say(ctx, "§f/sa toggle | sound | music | prices §7switch on/off");
        say(ctx, "§f/sa speed <0.25-3> §7| §f/sa volume <0-2>");
        say(ctx, "§f/sa mode §7full / compact / off per drop type");
        say(ctx, "§f/sa everywhere §7also react outside Hypixel");
        return 1;
    }

    /** Fills the book with test stickers: every known one, at one level or at random levels. */
    private static int giveAll(CommandContext<FabricClientCommandSource> ctx, Ledger.Level level) {
        java.util.Random rnd = new java.util.Random();
        Ledger.Level[] levels = Ledger.Level.values();
        int n = 0;
        for (var names : Ledger.KNOWN.values()) {
            for (String name : names) {
                Ledger.Level lv = level != null ? level : levels[rnd.nextInt(levels.length)];
                LedgerStore.giveTest(name, lv, false, 0);
                n++;
            }
        }
        // one pop-up so you see something happen, then the badges you unlocked
        LedgerStore.giveTest("Chimera I", level != null ? level : Ledger.Level.HOLO, true, 0.1f);
        LedgerStore.finishGive(1.2f);
        say(ctx, "§7Gave §f" + n + " §7test stickers" + (level != null ? " at §" + levelCode(level) + level.title : " at random levels")
                + "§7. Open the book with §fJ§7 or §f/sa book§7. §8/sa give reset §7removes them.");
        return 1;
    }

    private static char levelCode(Ledger.Level lv) {
        return switch (lv) {
            case PAPER -> 'f';
            case BRONZE -> '6';
            case SILVER -> '7';
            case GOLD -> 'e';
            case DIAMOND -> 'b';
            case HOLO -> 'd';
        };
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(Component.literal(text));
    }
}

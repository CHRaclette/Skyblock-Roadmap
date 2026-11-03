package dev.chraclette.butcher.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.core.DropParser;
import dev.chraclette.butcher.core.DropTier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** /butcher ... */
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
        PRESETS.put("fever", "WOAH! You got CROP FEVER for 60 seconds!");
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
                test.then(ClientCommands.literal(key).executes(ctx -> play(ctx, PRESETS.get(key), key)));
            }

            dispatcher.register(ClientCommands.literal("butcher")
                    .executes(Commands::help)
                    .then(test)
                    .then(ClientCommands.literal("simulate")
                            .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "message"), null))))
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
                    .then(ClientCommands.literal("mode")
                            .executes(Commands::listModes)
                            .then(ClientCommands.argument("tier", StringArgumentType.word())
                                    .then(ClientCommands.argument("mode", StringArgumentType.word())
                                            .executes(Commands::setMode))))
            );
        });
    }

    private static int play(CommandContext<FabricClientCommandSource> ctx, String line, String key) {
        Drop drop = DropParser.parse(line, "You", null);
        if (drop == null) {
            say(ctx, "§cThat line is not a drop message I recognise.");
            return 0;
        }
        CutsceneManager.clear();
        CutsceneManager.enqueue(drop, true);
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

    private static int listModes(CommandContext<FabricClientCommandSource> ctx) {
        say(ctx, "§6Cutscene per drop type §7(full / compact / off):");
        for (DropTier tier : DropTier.values()) {
            say(ctx, " §e" + tier.key() + "§7: " + Config.mode(tier).name().toLowerCase(Locale.ROOT));
        }
        say(ctx, "§7Change with §f/butcher mode <type> <full|compact|off>");
        return 1;
    }

    private static int setMode(CommandContext<FabricClientCommandSource> ctx) {
        DropTier tier = DropTier.fromKey(StringArgumentType.getString(ctx, "tier"));
        String m = StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT);
        if (tier == null) {
            say(ctx, "§cUnknown type. Use /butcher mode to see them.");
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
        say(ctx, "§6§lButcher Drops §7— rare-drop cutscenes");
        say(ctx, "§f/butcher test §7[" + String.join(", ", PRESETS.keySet()) + "]");
        say(ctx, "§f/butcher simulate <chat line> §7test any drop message");
        say(ctx, "§f/butcher skip §7stop the current cutscene");
        say(ctx, "§f/butcher toggle | sound | music | prices §7switch on/off");
        say(ctx, "§f/butcher speed <0.25-3> §7| §f/butcher volume <0-2>");
        say(ctx, "§f/butcher mode §7full / compact / off per drop type");
        say(ctx, "§f/butcher everywhere §7also react outside Hypixel");
        return 1;
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(Component.literal(text));
    }
}

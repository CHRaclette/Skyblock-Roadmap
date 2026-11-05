package dev.chraclette.skyanimations.demo;

import dev.chraclette.skyanimations.client.Demo;
import dev.chraclette.skyanimations.core.Badges;
import dev.chraclette.skyanimations.core.Ledger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Records the demo video: plays each feature in a real client and takes one screenshot per frame. */
@SuppressWarnings("UnstableApiUsage")
public class DemoRecorder implements FabricClientGameTest {

    private static final int FPS = 20;
    private ClientGameTestContext ctx;
    private int frame;

    private void record(float seconds) {
        int n = Math.round(seconds * FPS);
        for (int i = 0; i < n; i++) {
            ctx.runOnClient(c -> Demo.step(1f / FPS));
            var path = ctx.takeScreenshot(String.format("frame_%05d", frame++));
            if (frame == 1) System.out.println("DEMO-PATH " + path);
        }
    }

    private void marker(String name) {
        System.out.println("DEMO-MARKER " + name + " at frame " + frame);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            context.waitTicks(100); // let the world load and render
            context.runOnClient(c -> {
                var sm = c.getSoundManager();
                for (String id : new String[]{"skyanimations:case_tick", "skyanimations:case_win", "minecraft:block.note_block.hat", "minecraft:ui.toast.challenge_complete"}) {
                    var ev = sm.getSoundEvent(net.minecraft.resources.Identifier.parse(id));
                    System.out.println("SOUNDCHECK " + id + " -> " + (ev == null ? "NULL" : ev.getClass().getSimpleName()));
                }
                System.out.println("SOUNDCHECK options " + c.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
                for (var src : net.minecraft.sounds.SoundSource.values()) System.out.println("SOUNDCHECK source " + src);
                c.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.parse("skyanimations:case_tick")), 1f, 1f));
                c.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f));
                System.out.println("SOUNDCHECK played");
                var inst = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.parse("skyanimations:case_win")), 1f, 1f);
                var w = inst.resolve(c.getSoundManager());
                System.out.println("SOUNDCHECK resolve " + (w == null ? "NULL" : "ok") + " sound=" + inst.getSound() + " src=" + inst.getSource() + " vol=" + inst.getVolume());
                if (inst.getSound() != null) System.out.println("SOUNDCHECK path " + inst.getSound().getPath());
                System.out.println("SOUNDCHECK res " + c.getResourceManager().getResource(net.minecraft.resources.Identifier.parse("skyanimations:sounds/case_win.ogg")).isPresent()
                        + " json " + c.getResourceManager().getResource(net.minecraft.resources.Identifier.parse("skyanimations:sounds.json")).isPresent());
            });
            context.waitTicks(20);
            if (true) return;
            context.runOnClient(c -> Demo.start());
            // give the item list and prices a moment to download (real time)
            for (int i = 0; i < 400 && !context.computeOnClient(c -> Demo.ready()); i++) {
                context.waitTick();
            }
            context.waitTicks(60);
            context.runOnClient(c -> Demo.fillBook());

            marker("intro");
            record(1.0f);

            marker("dye");
            context.runOnClient(c -> Demo.chat("CRAZY RARE DROP! (Mythological Dye) (+312% ✯ Magic Find)"));
            record(8.6f);
            context.runOnClient(c -> Demo.stickerPop(Ledger.Level.DIAMOND, Ledger.Level.HOLO,
                    "RARE DROP! Griffin Feather (+120% ✯ Magic Find)"));
            record(3.8f);

            marker("vacuum");
            context.runOnClient(c -> Demo.pestDrop("RARE DROP! Beady Eyes (+12% ✯ Magic Find)", "Fly"));
            record(8.6f);

            marker("fever");
            context.runOnClient(c -> Demo.shortFever());
            record(13.0f);

            marker("badge");
            context.runOnClient(c -> Demo.badgePop(Badges.ALL.size() - 3, Badges.Tier.GOLD));
            record(3.8f);

            marker("book");
            context.runOnClient(c -> {
                Demo.hover(-1, -1);
                Demo.openBook();
            });
            record(1.2f);
            context.runOnClient(c -> Demo.hover(0.17f, 0.56f));
            record(2.2f);
            context.runOnClient(c -> Demo.hover(0.86f, 0.10f));
            record(1.8f);
            context.runOnClient(c -> {
                Demo.hover(-1, -1);
                Demo.showBadges();
            });
            record(1.2f);
            context.runOnClient(c -> Demo.hover(0.86f, 0.53f));
            record(2.2f);
            context.runOnClient(c -> Demo.closeBook());
            marker("end");
            System.out.println("DEMO-FRAMES " + frame);
        }
    }
}

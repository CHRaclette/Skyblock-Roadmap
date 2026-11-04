package dev.chraclette.butcher.demo;

import dev.chraclette.butcher.client.Demo;
import dev.chraclette.butcher.core.Badges;
import dev.chraclette.butcher.core.Ledger;
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
            ctx.takeScreenshot(String.format("frame_%05d", frame++));
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

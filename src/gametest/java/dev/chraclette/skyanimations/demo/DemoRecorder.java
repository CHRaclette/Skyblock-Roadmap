package dev.chraclette.skyanimations.demo;

import dev.chraclette.skyanimations.client.Demo;
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
            context.runOnClient(c -> Demo.start());
            // give the item list and prices a moment to download (real time)
            for (int i = 0; i < 400 && !context.computeOnClient(c -> Demo.ready()); i++) {
                context.waitTick();
            }
            context.waitTicks(60);
            context.runOnClient(c -> Demo.fillBook());

            marker("intro");
            context.runOnClient(c -> Demo.backupInfo());
            System.out.println("CHATSTATE intro " + context.computeOnClient(c -> Demo.chatState()));
            record(0.6f);

            marker("dye");
            context.runOnClient(c -> Demo.wow("WOW! [MVP+] You found a Cyclamen Dye #48!"));
            record(6.6f);

            marker("nucleus");
            context.runOnClient(c -> Demo.nucleus(true));
            record(17.2f);

            marker("chest");
            context.runOnClient(c -> Demo.dungeonChest());
            record(17.0f);

            marker("rngmeter");
            context.runOnClient(c -> Demo.rngMeter());
            record(17.5f);

            marker("backup");
            System.out.println("CHATSTATE end " + context.computeOnClient(c -> Demo.chatState()));
            context.runOnClient(c -> Demo.backupInfo());
            record(3.0f);
            System.out.println("CHATSTATE after " + context.computeOnClient(c -> Demo.chatState()));
            marker("end");
            System.out.println("DEMO-FRAMES " + frame);
        }
    }
}

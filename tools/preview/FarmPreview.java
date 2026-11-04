import dev.chraclette.skyanimations.core.*;
import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Offline preview of a Crop Fever: intro, timer with toasts, pest pop-ups, summary. */
public class FarmPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        float fps = Float.parseFloat(args[1]);
        int gw = 480, gh = 270, scale = 2;
        List<Object> crops = List.of("item_wheat.png", "item_carrot.png", "item_potato.png", "item_melon_slice.png",
                "item_sugar_cane.png", "item_nether_wart.png", "item_cocoa_beans.png");
        FeverHud fever = new FeverHud(new FeverHud.Hooks() {
            public void sound(Cutscene.Cue cue, float pitch, float volume) { }
            public List<Object> cropIcons() { return crops; }
        });
        Toasts toasts = new Toasts();
        BufferedImage bg = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        Graphics2D bgG = bg.createGraphics();
        bgG.setPaint(new GradientPaint(0, 0, new Color(0x7FA6D6), 0, gh, new Color(0x4F7A2E)));
        bgG.fillRect(0, 0, gw, gh);
        new File(out).mkdirs();
        float end = 22f;
        // timeline: {time, kind, ...}
        Object[][] events = {
                {0.2f, "start"},
                {3.6f, "roll", "Enchanted Wheat", 6, "item_wheat.png", 1_920L, DropTier.UNCOMMON},
                {4.6f, "roll", "Enchanted Wheat", 10, "item_wheat.png", 3_200L, DropTier.RARE},
                {5.4f, "pest", "4 Pests spawned", "Plot - 14"},
                {6.3f, "roll", "Enchanted Wheat", 6, "item_wheat.png", 1_920L, DropTier.UNCOMMON},
                {7.5f, "kill", "Locust", "7x Enchanted Potato", 1_120L},
                {7.9f, "kill", "Locust", "7x Enchanted Potato", 1_120L},
                {8.8f, "roll", "Enchanted Hay Bale", 12, "item_wheat.png", 2_457_600L, DropTier.RNGESUS},
                {12.5f, "stop"},
        };
        int ei = 0;
        int frames = (int) (end * fps);
        for (int i = 0; i <= frames; i++) {
            float t = i / fps;
            while (ei < events.length && (float) events[ei][0] <= t) {
                Object[] e = events[ei++];
                switch ((String) e[1]) {
                    case "start" -> fever.start(t, 12, true, true, true);
                    case "stop" -> fever.end(t);
                    case "roll" -> {
                        DropTier tier = (DropTier) e[6];
                        fever.add(new FeverHud.Roll((String) e[2], (int) e[3], e[4], (long) e[5], tier.color, tier.rank));
                        toasts.add(new Toasts.Toast(tier.label, tier.color, "+" + e[3] + "x " + e[2], 0xFF8A2A, e[4], (long) e[5], 4f), t);
                    }
                    case "pest" -> toasts.add(new Toasts.Toast("PESTS!", 0xFF5555, e[2] + " in " + e[3], 0xFF5555,
                            "item_silverfish_spawn_egg.png", 0, 5f), t);
                    case "kill" -> toasts.add(new Toasts.Toast("Pest vacuumed", 0x55FF55, e[3] + " · " + e[2], 0x55FF55,
                            "item_potato.png", (long) e[4], 4f), t);
                }
            }
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.drawImage(bg, 0, 0, null);
            Preview.J2D c = new Preview.J2D(g, gw, gh);
            fever.render(c, t, false);
            toasts.render(c, t);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

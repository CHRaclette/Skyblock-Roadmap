import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Preview for the new scenes. args: out mode fps */
public class ScenePreview {
    public static void main(String[] args) throws Exception {
        String out = args[0], mode = args[1];
        float fps = Float.parseFloat(args[2]);
        Props.headSkins = n -> n.toLowerCase().contains("scatha") || n.toLowerCase().contains("dragon") ? Skins.head("textures/entity/player/wide/steve.png") : null;
        Scene s;
        String bg = "cave";
        switch (mode) {
            case "blaze" -> s = new BlazeScene("High Class Archfiend Dice", null, 0xFFAA00, 25_998_766,
                    List.of("1 in 37 Inferno Demonlord kills", "Your 1st High Class Archfiend Dice"), (c, p, v) -> { });
            case "corpse", "corpsedye" -> {
                boolean dye = mode.equals("corpsedye");
                List<RunScene.Loot> loot = new java.util.ArrayList<>(List.of(
                        new RunScene.Loot("Glacite Amalgamation", 1, "item_prismarine_crystals.png", 0x5555FF, 300_000, false),
                        new RunScene.Loot("Suspicious Scrap", 16, "item_iron_nugget.png", 0x5555FF, 50_000, false),
                        new RunScene.Loot("Refined Umber", 1, "item_gold_nugget.png", 0xAA00AA, 200_000, false),
                        new RunScene.Loot("Blue Goblin Egg", 1, "item_egg.png", 0x5555FF, 60_000, false),
                        new RunScene.Loot("Flawless Onyx Gemstone", 1, "hypixel_skyblock:textures/item/collections/gemstone/onyx/flawless_onyx_gem.png", 0xAA00AA, 2_000_000, false)));
                loot.add(dye ? new RunScene.Loot("Frostbitten Dye", 1, "item_light_blue_dye.png", 0xFFAA00, 480_000_000, false)
                        : new RunScene.Loot("Shattered Locket", 1, "item_heart_of_the_sea.png", 0xFFAA00, 479_999_999, false));
                s = new CorpseScene("VANGUARD", loot, loot.size() - 1, "item_iron_pickaxe.png",
                        List.of("1 in 52 corpses", "Your 1st ever " + loot.get(loot.size() - 1).name()),
                        dye ? new int[]{0xE7489B} : null, (c, p, v) -> { });
            }
            case "pet" -> s = new PetScene("[Lvl 1] Ender Dragon", "Legendary", null, 0xFFAA00, java.util.List.of("Your 1st Ender Dragon pet", "+200% ✯ Magic Find"), 640_000_000, (c, p, v) -> { });
            default -> s = new ScathaScene("Scatha", "Epic", null, 0xAA00AA, "Your 1st Scatha pet", 285_000_000, (c, p, v) -> { });
        }
        new File(out).mkdirs();
        int gw = 480, gh = 270, sc = 2;
        for (int i = 0; ; i++) {
            float t = i / fps;
            if (s.finished(t) || i > 20 * fps) break;
            BufferedImage img = new BufferedImage(gw * sc, gh * sc, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(sc, sc);
            g.setPaint(new GradientPaint(0, 0, new Color(0x2F3B48), 0, gh, new Color(0x22272E)));
            g.fillRect(0, 0, gw, gh);
            s.render(new Preview.J2D(g, gw, gh), t);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

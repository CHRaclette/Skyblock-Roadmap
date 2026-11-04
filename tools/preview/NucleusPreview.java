import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Offline preview of the Crystal Nucleus scene. */
public class NucleusPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        String mode = args.length > 1 ? args[1] : "rare";
        boolean rare = !mode.equals("common");
        float fps = Float.parseFloat(args.length > 2 ? args[2] : "20");
        int gw = 480, gh = 270, scale = 2;
        List<RunScene.Loot> loot = new java.util.ArrayList<>(List.of(
                new RunScene.Loot("Gemstone Powder", 2400, null, 0xFF55FF, 0, true),
                new RunScene.Loot("Mithril Powder", 1500, null, 0x00AA00, 0, true),
                new RunScene.Loot("Flawless Jade Gemstone", 2, "item_emerald.png", 0xAA00AA, 5_200_000, false),
                new RunScene.Loot("Fine Amber Gemstone", 8, "item_gold_nugget.png", 0x5555FF, 160_000, false),
                new RunScene.Loot("Fine Topaz Gemstone", 6, "item_glowstone_dust.png", 0x5555FF, 120_000, false),
                new RunScene.Loot("Flawless Sapphire Gemstone", 1, "item_lapis_lazuli.png", 0xAA00AA, 2_600_000, false),
                new RunScene.Loot("Gemstone Chamber", 1, "item_echo_shard.png", 0x5555FF, 400_000, false)));
        int rareIdx = -1;
        if (rare) {
            loot.add(new RunScene.Loot("Jungle Heart", 1, "item_heart_of_the_sea.png", 0xFFAA00, 9_800_000, false));
            rareIdx = loot.size() - 1;
        }
        List<RunScene.ReelItem> pool = List.of(
                new RunScene.ReelItem("Jungle Heart", "item_heart_of_the_sea.png", 0xFFAA00),
                new RunScene.ReelItem("Divan Fragment", "item_prismarine_crystals.png", 0xAA00AA),
                new RunScene.ReelItem("Treasurite", "item_redstone.png", 0xAA00AA),
                new RunScene.ReelItem("Prehistoric Egg", "item_egg.png", 0x55FF55),
                new RunScene.ReelItem("Pickonimbus 2000", "item_diamond.png", 0xFF55FF),
                new RunScene.ReelItem("Precursor Apparatus", "item_quartz.png", 0x5555FF),
                new RunScene.ReelItem("Bal", "item_totem_of_undying.png", 0xFFAA00));
        RunScene.Style style = RunScene.Style.nucleus();
        if (mode.equals("chest")) {
            loot = new java.util.ArrayList<>(List.of(
                    new RunScene.Loot("Wither Essence", 12, "item_quartz.png", 0xFF55FF, 36_000, false),
                    new RunScene.Loot("Undead Essence", 40, "item_redstone.png", 0xFF55FF, 20_000, false),
                    new RunScene.Loot("Enchanted Book (Ultimate Wise I)", 1, "item_lapis_lazuli.png", 0xFF55FF, 300_000, false),
                    new RunScene.Loot("Wither Blood", 1, "item_redstone.png", 0xAA00AA, 2_000_000, false),
                    new RunScene.Loot("Necron's Handle", 1, "item_prismarine_crystals.png", 0xAA00AA, 520_000_000, false)));
            rareIdx = 4;
            style = RunScene.Style.chest("Obsidian Chest", "Master Catacombs - Floor VII", RunScene.chestColor("Obsidian"));
        } else if (mode.equals("meter")) {
            loot = new java.util.ArrayList<>(List.of(new RunScene.Loot("Judgement Core", 1, "item_echo_shard.png", 0xFFAA00, 600_000_000, false)));
            rareIdx = 0;
            style = RunScene.Style.meter("Voidgloom Seraph");
        }
        RunScene s = new RunScene(style, loot, rareIdx, pool, (cue, p, v) -> { }, true);
        BufferedImage bg = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        Graphics2D bgG = bg.createGraphics();
        bgG.setPaint(new GradientPaint(0, 0, new Color(0x3A2E5A), 0, gh, new Color(0x2A2A30)));
        bgG.fillRect(0, 0, gw, gh);
        new File(out).mkdirs();
        int frames = (int) (s.duration() * fps);
        for (int i = 0; i <= frames; i++) {
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.drawImage(bg, 0, 0, null);
            s.render(new Preview.J2D(g, gw, gh), i / fps);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
        System.out.println("duration " + s.duration());
    }
}

import dev.chraclette.butcher.core.*;
import dev.chraclette.butcher.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Offline preview of the sticker book with made-up progress. */
public class BookPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        String chapter = args.length > 1 ? args[1] : "DIANA";
        int frames = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int mx = args.length > 3 ? Integer.parseInt(args[3]) : -100, my = args.length > 4 ? Integer.parseInt(args[4]) : -100;
        Ledger ledger = new Ledger();
        long now = System.currentTimeMillis();
        String[][] drops = {
                {"RARE DROP! Griffin Feather (+120% Magic Find)", "200"},
                {"RARE DROP! Daedalus Stick (+180% Magic Find)", "70"},
                {"RARE DROP! Enchanted Book (Chimera I) (+254% Magic Find)", "28"},
                {"RARE DROP! You dug out a Shimmering Wool!", "12"},
                {"RARE DROP! Crochet Tiger Plushie", "4"},
                {"RARE DROP! Dwarf Turtle Shelmet", "2"},
                {"CRAZY RARE DROP! (Mythological Dye) (+312% Magic Find)", "1"},
                {"RARE DROP! Minos Relic", "1"},
        };
        for (String[] d : drops) {
            Drop drop = DropParser.parse(d[0], "Steve", Category.DIANA);
            for (int i = 0; i < Integer.parseInt(d[1]); i++) ledger.record(drop, 1_500_000, now);
        }
        for (Ledger.Entry e : ledger.stickers.values()) {
            e.firstAt = now - 3L * 24 * 3600_000 - 5 * 3600_000;
            e.lastAt = now - (2 * 3600_000 + 14 * 60_000);
        }
        ledger.pestsVacuumed = 1234;
        ledger.fevers = 12;
        ledger.jackpots = 1;
        BookView view = new BookView(ledger, name -> {
            String n = name.toLowerCase().replace("badge:", "");
            if (n.contains("chimera")) return "item_enchanted_book.png";
            if (n.contains("dye")) return "item_lime_dye.png";
            if (n.contains("wool")) return "block_yellow_wool.png";
            if (n.contains("feather")) return "item_feather.png";
            return "item_nether_star.png";
        });
        if (chapter.equals("BADGES")) view.selectBadges(); else view.select(Ledger.Chapter.valueOf(chapter));
        int gw = 480, gh = 270, scale = 2;
        new File(out).mkdirs();
        BufferedImage warm = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        view.render(new Preview.J2D(warm.createGraphics(), gw, gh), mx, my, 0f);
        for (int i = 0; i < frames; i++) {
            float t = 1f + i / 24f;
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setPaint(new GradientPaint(0, 0, new Color(0x7FA6D6), 0, gh, new Color(0x4F7A2E)));
            g.fillRect(0, 0, gw, gh);
            view.render(new Preview.J2D(g, gw, gh), mx, my, t);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

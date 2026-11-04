import dev.chraclette.butcher.core.Ledger;
import dev.chraclette.butcher.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** All sticker levels side by side, then each one slapped onto the screen. */
public class StickerPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        float fps = 24;
        int gw = 480, gh = 270, scale = 2;
        new File(out).mkdirs();
        Ledger.Level[] lv = Ledger.Level.values();
        int frame = 0;
        // 1) row of all levels, 3 s
        for (int i = 0; i < 3 * fps; i++, frame++) {
            float t = i / fps;
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setColor(new Color(0xF3E9D2));
            g.fillRect(0, 0, gw, gh);
            Preview.J2D c = new Preview.J2D(g, gw, gh);
            for (int k = 0; k < lv.length; k++) {
                float cx = 45 + k * 78;
                Sticker.draw(c, cx, 120, 52, lv[k], "item_enchanted_book.png", lv[k].points, t, false, 12345 + k * 77);
                Fx.centered(c, lv[k].title, cx, 160, 1f, 0xFF3A2A1A, true);
                Fx.centered(c, lv[k].points + "+ pts", cx, 172, 0.75f, 0xFF7A6248, false);
            }
            Fx.centered(c, "Sticker levels", gw / 2f, 40, 1.6f, 0xFF3A2A1A, true);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", frame)));
        }
        // 2) slap: new sticker, then holo upgrade
        StickerPop[] pops = {
                new StickerPop("Chimera I", null, Ledger.Level.PAPER, "item_enchanted_book.png", 1, 0),
                new StickerPop("Chimera I", Ledger.Level.DIAMOND, Ledger.Level.HOLO, "item_enchanted_book.png", 75, 0)};
        BufferedImage bg = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        Graphics2D bgG = bg.createGraphics();
        bgG.setPaint(new GradientPaint(0, 0, new Color(0x7FA6D6), 0, gh, new Color(0x4F7A2E)));
        bgG.fillRect(0, 0, gw, gh);
        for (StickerPop p : pops) {
            for (int i = 0; i < StickerPop.DURATION * fps; i++, frame++) {
                float t = i / fps;
                BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.scale(scale, scale);
                g.drawImage(bg, 0, 0, null);
                p.render(new Preview.J2D(g, gw, gh), t);
                g.dispose();
                ImageIO.write(img, "png", new File(out, String.format("f%04d.png", frame)));
            }
        }
    }
}

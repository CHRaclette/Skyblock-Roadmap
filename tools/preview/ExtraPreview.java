import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Offline preview of the dye and Scatha scenes. args: outDir dye|scatha fps */
public class ExtraPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        float fps = Float.parseFloat(args[2]);
        int gw = 480, gh = 270, scale = 2;
        Scene s;
        float dur;
        if (args[1].equals("dye")) {
            s = new DyeScene("Necron Dye", "item_redstone.png", 0xE7413C, 0xAA00AA, "+312% Magic Find", (cue, p, v) -> { });
            dur = 4.75f;
        } else {
            s = new ScathaScene("Scatha", "Legendary", "item_totem_of_undying.png", 0xFFAA00, "Crystal Hollows", (cue, p, v) -> { });
            dur = 6.0f;
        }
        new File(out).mkdirs();
        for (int i = 0; i <= dur * fps; i++) {
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setPaint(new GradientPaint(0, 0, new Color(0x3A2E5A), 0, gh, new Color(0x2A2A30)));
            g.fillRect(0, 0, gw, gh);
            s.render(new Preview.J2D(g, gw, gh), i / fps);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

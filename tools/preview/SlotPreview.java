import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SlotPreview {
    public static void main(String[] args) throws Exception {
        int roll = Integer.parseInt(args[1]);
        boolean hc = args[2].equals("hc");
        float fps = Float.parseFloat(args[3]);
        SlotScene s = new SlotScene(roll, hc, "+" + roll * 10 + "❤", (cue, p, v) -> { });
        new File(args[0]).mkdirs();
        int gw = 480, gh = 270, sc = 2;
        for (int i = 0; ; i++) {
            float t = i / fps;
            if (s.finished(t)) break;
            BufferedImage img = new BufferedImage(gw * sc, gh * sc, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(sc, sc);
            g.setPaint(new GradientPaint(0, 0, new Color(0x3A2E5A), 0, gh, new Color(0x2A2A30)));
            g.fillRect(0, 0, gw, gh);
            s.render(new Preview.J2D(g, gw, gh), t);
            g.dispose();
            ImageIO.write(img, "png", new File(args[0], String.format("f%04d.png", i)));
        }
    }
}

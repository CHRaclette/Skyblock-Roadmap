import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Preview of the 3D drop models: dice, high class dice, judgement core, a head. */
public class PropsPreview {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        float fps = 20;
        Props.headSkins = n -> n.equals("Warden Heart") ? Skins.head("textures/entity/player/wide/steve.png") : null;
        String[] names = {"Archfiend Dice", "High Class Archfiend Dice", "Judgement Core", "Warden Heart"};
        new File(out).mkdirs();
        int gw = 480, gh = 160, scale = 2;
        for (int i = 0; i <= 3 * fps; i++) {
            float t = i / fps;
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.setColor(new Color(0x1A1622));
            g.fillRect(0, 0, gw, gh);
            Preview.J2D c = new Preview.J2D(g, gw, gh);
            for (int k = 0; k < names.length; k++) {
                Props.draw(c, names[k], 60 + k * 120, 80, 64, t);
                Fx.centered(c, names[k], 60 + k * 120, 140, 0.8f, 0xFFFFFFFF, true);
            }
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

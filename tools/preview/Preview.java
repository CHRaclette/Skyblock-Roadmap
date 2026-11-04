import dev.chraclette.skyanimations.core.*;
import dev.chraclette.skyanimations.render.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** Offline preview: renders the cutscene with Java2D using the vanilla textures. */
public class Preview {

    static final Map<String, BufferedImage> TEX = new HashMap<>();

    static BufferedImage tex(String path) {
        return TEX.computeIfAbsent(path, p -> {
            String f = p.replace("textures/", "").replace("/", "_");
            try {
                if (p.contains(":")) {
                    String[] parts = p.split(":", 2);
                    File f1 = new File(System.getProperty("res", "src/main/resources") + "/assets/" + parts[0] + "/" + parts[1]);
                    if (!f1.exists()) f1 = new File(System.getProperty("pack", "/tmp/hpack/x") + "/assets/" + parts[0] + "/" + parts[1]);
                    return ImageIO.read(f1);
                }
                return ImageIO.read(new File(System.getProperty("assets", "/home/claude/assets") + "/" + f));
            } catch (Exception e) {
                System.err.println("missing " + p);
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                    img.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0xFFFF00FF : 0xFF000000);
                return img;
            }
        });
    }

    static class J2D implements dev.chraclette.skyanimations.render.Canvas {
        final Graphics2D g;
        final int w, h;
        final Deque<AffineTransform> stack = new ArrayDeque<>();
        final Font font = new Font(Font.MONOSPACED, Font.BOLD, 8);

        J2D(Graphics2D g, int w, int h) {
            this.g = g;
            this.w = w;
            this.h = h;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setFont(font);
        }

        public int width() { return w; }
        public int height() { return h; }
        public void push() { stack.push(g.getTransform()); }
        public void pop() { g.setTransform(stack.pop()); }
        public void translate(float x, float y) { g.translate(x, y); }
        public void scale(float x, float y) { g.scale(x, y); }
        public void rotate(float r) { g.rotate(r); }
        public void transform(float a, float b, float c, float d, float e, float f) {
            g.transform(new AffineTransform(a, b, c, d, e, f));
        }
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            g.setColor(new Color(argb, true));
            g.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
        public void blit(String t, int x, int y, int u, int v, int bw, int bh, int tw, int th) {
            BufferedImage img = tex(t);
            g.drawImage(img, x, y, x + bw, y + bh, u, v, u + bw, v + bh, null);
        }
        public boolean hasTexture(String t) {
            if (!t.contains(":")) return true;
            String[] parts = t.split(":", 2);
            return new File(System.getProperty("res", "src/main/resources") + "/assets/" + parts[0] + "/" + parts[1]).exists()
                    || new File(System.getProperty("pack", "/tmp/hpack/x") + "/assets/" + parts[0] + "/" + parts[1]).exists();
        }
        public void item(Object icon, int x, int y) {
            blit((String) icon, x, y, 0, 0, 16, 16, 16, 16);
        }
        public void text(String s, int x, int y, int argb, boolean shadow) {
            if (shadow) {
                g.setColor(new Color((argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2), true));
                g.drawString(s, x + 1, y + 8);
            }
            g.setColor(new Color(argb, true));
            g.drawString(s, x, y + 7);
        }
        public int textWidth(String s) { return g.getFontMetrics(font).stringWidth(s); }
    }

    public static void main(String[] args) throws Exception {
        String line = args[0];
        String iconPath = args[1];
        String out = args[2];
        float fps = Float.parseFloat(args.length > 3 ? args[3] : "15");
        int gw = 480, gh = 270, scale = 2;
        Drop drop = DropParser.parse(line, "Steve", null);
        if (args.length > 5) drop = drop.withPest(args[5]);
        System.out.println(drop);
        BufferedImage bg = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        Graphics2D bgG = bg.createGraphics();
        bgG.setPaint(new GradientPaint(0, 0, new Color(0x6A8CB8), 0, gh, new Color(0x3E5A2E)));
        bgG.fillRect(0, 0, gw, gh);
        Cutscene cs = new Cutscene(drop, new Cutscene.Hooks() {
            public Object icon() { return iconPath; }
            public int nameColor() { return -1; }
            public Cutscene.Price price() { return new Cutscene.Price(19_342_589, "bazaar instant sell"); }
            public void sound(Cutscene.Cue cue, float pitch, float volume) { }
        }, args.length > 4 && args[4].equals("compact"), true, true);
        new File(out).mkdirs();
        int frames = (int) (cs.duration() * fps);
        for (int i = 0; i <= frames; i++) {
            float t = i / fps;
            BufferedImage img = new BufferedImage(gw * scale, gh * scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            g.drawImage(bg, 0, 0, null);
            cs.render(new J2D(g, gw, gh), t);
            g.dispose();
            ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
        }
    }
}

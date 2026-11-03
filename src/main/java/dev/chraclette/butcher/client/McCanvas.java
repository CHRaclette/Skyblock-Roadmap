package dev.chraclette.butcher.client;

import dev.chraclette.butcher.render.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.Map;

/** Canvas on top of the HUD graphics. */
final class McCanvas implements Canvas {

    private static final Map<String, Identifier> IDS = new HashMap<>();

    private final GuiGraphicsExtractor g;

    McCanvas(GuiGraphicsExtractor g) {
        this.g = g;
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public void push() {
        g.pose().pushMatrix();
    }

    @Override
    public void pop() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        g.pose().scale(x, y);
    }

    @Override
    public void rotate(float radians) {
        g.pose().rotate(radians);
    }

    @Override
    public void transform(float a, float b, float c, float d, float e, float f) {
        g.pose().mul(new Matrix3x2f(a, b, c, d, e, f));
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if ((argb >>> 24) == 0) return;
        g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH) {
        Identifier id = IDS.computeIfAbsent(texture, Identifier::withDefaultNamespace);
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (float) u, (float) v, w, h, texW, texH);
    }

    @Override
    public void item(Object icon, int x, int y) {
        if (icon instanceof ItemStack stack && !stack.isEmpty()) {
            g.item(stack, x, y);
        }
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) < 4) return;
        g.text(Minecraft.getInstance().font, text, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }
}

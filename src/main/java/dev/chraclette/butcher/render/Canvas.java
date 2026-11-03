package dev.chraclette.butcher.render;

/**
 * The few drawing operations the cutscene needs. Minecraft implements this on top of the HUD
 * graphics; the offline preview implements it with Java2D. Coordinates are GUI pixels.
 */
public interface Canvas {

    int width();

    int height();

    void push();

    void pop();

    void translate(float x, float y);

    void scale(float x, float y);

    void rotate(float radians);

    /** Multiplies the current transform by x' = a*x + c*y + e, y' = b*x + d*y + f. */
    void transform(float a, float b, float c, float d, float e, float f);

    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draws the w*h region at (u,v) of a texture of size texW*texH at (x,y), 1 texel = 1 unit. */
    void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH);

    /** Draws an item icon (16x16 units) with its top-left at (x,y). */
    void item(Object icon, int x, int y);

    void text(String text, int x, int y, int argb, boolean shadow);

    int textWidth(String text);
}

package dev.chraclette.skyanimations.render;

/** Anything the cutscene player can show: the drop cutscene, the Nucleus opening, ... */
public interface Scene {

    void render(Canvas c, float t);

    boolean finished(float t);
}

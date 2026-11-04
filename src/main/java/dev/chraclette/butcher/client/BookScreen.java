package dev.chraclette.butcher.client;

import dev.chraclette.butcher.core.Drop;
import dev.chraclette.butcher.core.DropParser;
import dev.chraclette.butcher.render.BookView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** The sticker book. Open with J or /butcher book. */
public final class BookScreen extends Screen {

    private final BookView view;
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final long opened = System.nanoTime();

    public BookScreen() {
        super(Component.literal("Butcher's Sticker Book"));
        this.view = new BookView(LedgerStore.get(), this::icon);
    }

    private Object icon(String name) {
        return icons.computeIfAbsent(name, n -> {
            Drop d = DropParser.parse("RARE DROP! " + n, null, null);
            if (d == null) return ItemStack.EMPTY;
            return ItemLookup.icon(d, ItemLookup.findInInventory(d));
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // the book dims the screen itself
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        float t = (System.nanoTime() - opened) / 1_000_000_000f;
        view.render(new McCanvas(graphics), mouseX, mouseY, t);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (view.click(event.x(), event.y())) {
            Sounds.play(dev.chraclette.butcher.render.Cutscene.Cue.PAGE, dev.chraclette.butcher.core.Effect.STARS, 1f, 0.8f);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        view.scroll(vertical);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

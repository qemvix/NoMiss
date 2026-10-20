package com.nomiss.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Shared scrollable rows, including keyboard focus and narration for every button. */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
    SettingsList(Minecraft client, int width, int height, int top) { super(client, width, height, top, 28); }
    void row(Button... buttons) { addEntry(new Row(buttons)); }
    void note(Component text) { var label = Button.builder(text, button -> {}).build(); label.active = false; row(label); }
    @Override public int getRowWidth() { return getWidth() - 16; }
    static final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final List<Button> buttons;
        Row(Button[] buttons) { this.buttons = List.of(buttons); }
        @Override public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int gap = 4, x = getContentX();
            int small = buttons.size() == 3 ? 44 : 52;
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);
                int w = i == 0 ? getContentWidth() - (buttons.size() - 1) * (small + gap) : small;
                button.setX(x); button.setY(getContentY()); button.setWidth(w); button.setHeight(22);
                button.extractRenderState(graphics, mouseX, mouseY, delta); x += w + gap;
            }
        }
        @Override public List<? extends GuiEventListener> children() { return buttons; }
        @Override public List<? extends NarratableEntry> narratables() { return buttons; }
        @Override public void visitWidgets(java.util.function.Consumer<AbstractWidget> visitor) { buttons.forEach(visitor); }
    }
}

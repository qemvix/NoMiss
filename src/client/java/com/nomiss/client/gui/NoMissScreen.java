package com.nomiss.client.gui;

import com.nomiss.client.NoMissClient;
import com.nomiss.config.NoMissConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Shared by Accessibility, Mod Menu and the in-game shortcut. All changes save immediately. */
public final class NoMissScreen extends Screen {
    private final Screen parent;
    private final NoMissConfig config = NoMissClient.config();
    private SlotList slots;
    private boolean saveFailed;
    private int contentWidth;
    private Component feedback;

    public NoMissScreen(Screen parent) {
        super(Component.translatable("nomiss.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        double scroll = slots == null ? 0 : slots.scrollAmount();
        contentWidth = Math.min(440, width - 24);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - 8) / 2;
        slots = addRenderableWidget(new SlotList(minecraft, contentWidth, Math.max(32, height - 138), 54));
        slots.setX(left);
        for (var option : NoMissConfig.Option.values()) slots.addOption(option);
        slots.addKeyBinds();
        for (int i = 0; i < NoMissConfig.MAX_ITEMS; i++) slots.addSlot(i);
        slots.setScrollAmount(scroll);
        var addHeld = addRenderableWidget(Button.builder(Component.translatable("nomiss.add_held"), button -> {
            feedback = NoMissClient.addHeldItem(); rebuildWidgets();
        }).bounds(left, height - 76, contentWidth, 20)
            .tooltip(Tooltip.create(Component.translatable(minecraft.player == null ? "nomiss.add_held.no_world" : "nomiss.add_held.tooltip"))).build());
        addHeld.active = minecraft.player != null;
        addRenderableWidget(Button.builder(Component.translatable("nomiss.defaults"), button -> {
            config.resetItems(); save(); rebuildWidgets();
        }).bounds(left, height - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nomiss.clear"), button -> {
            config.clearItems(); save(); rebuildWidgets();
        }).bounds(left + half + 8, height - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left, height - 28, contentWidth, 20).build());
    }

    private void save() { feedback = null; saveFailed = !NoMissClient.saveConfig(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        graphics.textRenderer().acceptScrollingWithDefaultCenter(saveFailed
            ? Component.translatable("nomiss.save_failed")
            : feedback != null ? feedback : Component.translatable("nomiss.count", config.items().size(), NoMissConfig.MAX_ITEMS),
            (width - contentWidth) / 2, (width + contentWidth) / 2, 30, 50);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private final class SlotList extends ContainerObjectSelectionList<SettingsEntry> {
        private SlotList(Minecraft client, int width, int height, int top) { super(client, width, height, top, 30); }
        private void addSlot(int slot) { addEntry(new SlotEntry(slot)); }
        private void addOption(NoMissConfig.Option option) { addEntry(new OptionEntry(option)); }
        private void addKeyBinds() { addEntry(new KeyBindsEntry()); }
        @Override public int getRowWidth() { return getWidth() - 16; }
    }

    private abstract class SettingsEntry extends ContainerObjectSelectionList.Entry<SettingsEntry> {}

    private final class OptionEntry extends SettingsEntry {
        private final Button toggle;
        private final Button reset;

        private OptionEntry(NoMissConfig.Option option) {
            toggle = Button.builder(label(option), button -> {
                config.setOption(option, !config.option(option)); save();
                button.setMessage(label(option)); updateReset(option);
            }).bounds(0, 0, 100, 22).tooltip(Tooltip.create(Component.translatable(option.translationKey + ".tooltip"))).build();
            reset = Button.builder(Component.translatable("controls.reset"), button -> {
                config.resetOption(option); save(); toggle.setMessage(label(option)); button.active = false;
            }).bounds(0, 0, 52, 22).tooltip(Tooltip.create(Component.translatable("nomiss.reset.tooltip"))).build();
            reset.active = config.option(option) != option.defaultValue;
        }

        private void updateReset(NoMissConfig.Option option) { reset.active = config.option(option) != option.defaultValue; }

        private Component label(NoMissConfig.Option option) {
            return Component.translatable(option.translationKey, Component.translatable(config.option(option) ? "options.on" : "options.off"));
        }

        @Override public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            toggle.setX(getContentX()); toggle.setY(getContentY()); toggle.setWidth(getContentWidth() - 56);
            reset.setX(getContentRight() - 52); reset.setY(getContentY());
            toggle.extractRenderState(graphics, mouseX, mouseY, delta); reset.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        @Override public List<? extends GuiEventListener> children() { return List.of(toggle, reset); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(toggle, reset); }
        @Override public void visitWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> visitor) {
            visitor.accept(toggle); visitor.accept(reset);
        }
    }

    private final class KeyBindsEntry extends SettingsEntry {
        private final Button button = Button.builder(Component.translatable("nomiss.keybinds"), button ->
            minecraft.gui.setScreen(new KeyBindsScreen(NoMissScreen.this, minecraft.options))).bounds(0, 0, 100, 22)
            .tooltip(Tooltip.create(Component.translatable("nomiss.keybinds.tooltip"))).build();
        @Override public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            button.setX(getContentX()); button.setY(getContentY()); button.setWidth(getContentWidth());
            button.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        @Override public List<? extends GuiEventListener> children() { return List.of(button); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }
        @Override public void visitWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> visitor) { visitor.accept(button); }
    }

    private final class SlotEntry extends SettingsEntry {
        private final Button select;
        private final Button clear;
        private final ItemStack stack;
        private final Component label;

        private SlotEntry(int slot) {
            String id = config.itemAt(slot);
            Identifier key = Identifier.tryParse(id);
            stack = key != null && BuiltInRegistries.ITEM.containsKey(key)
                ? ItemPreview.of(BuiltInRegistries.ITEM.getValue(key)) : ItemStack.EMPTY;
            label = Component.translatable("nomiss.slot", slot + 1).append(": ").append(id.isEmpty()
                ? Component.translatable("nomiss.empty")
                : stack.isEmpty() ? Component.literal(id) : stack.getHoverName());
            select = Button.builder(Component.translatable("nomiss.select"), button ->
                minecraft.gui.setScreen(new ItemPickerScreen(NoMissScreen.this, slot))).bounds(0, 0, 100, 22).build();
            select.setTooltip(Tooltip.create(label));
            clear = Button.builder(Component.literal("×"), button -> {
                config.setItem(slot, ""); save(); rebuildWidgets();
            }).bounds(0, 0, 22, 22).tooltip(Tooltip.create(Component.translatable("nomiss.clear_slot", slot + 1))).build();
            clear.active = !id.isEmpty();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int selectWidth = Math.min(100, getContentWidth() / 3);
            clear.setX(getContentRight() - 22); clear.setY(getContentY());
            select.setX(getContentRight() - selectWidth - 26); select.setY(getContentY()); select.setWidth(selectWidth);
            graphics.item(stack, getContentX(), getContentY() + 3);
            graphics.textRenderer().acceptScrollingWithDefaultCenter(label, getContentX() + 22,
                select.getX() - 6, getContentY(), getContentY() + 22);
            select.extractRenderState(graphics, mouseX, mouseY, delta);
            clear.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children() { return List.of(select, clear); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(select, clear); }
        @Override public void visitWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> visitor) {
            visitor.accept(select); visitor.accept(clear);
        }
    }
}

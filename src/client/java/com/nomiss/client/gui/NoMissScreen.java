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
        addRenderableWidget(Button.builder(NoMissClient.enabledLabel(), button -> {
            config.setEnabled(!config.enabled()); save(); button.setMessage(NoMissClient.enabledLabel());
        }).bounds(left, 30, contentWidth, 20).tooltip(Tooltip.create(Component.translatable("nomiss.enabled.tooltip"))).build());
        addRenderableWidget(Button.builder(optionLabel("nomiss.cooldown", config.blockDuringCooldown()), button -> {
            config.setBlockDuringCooldown(!config.blockDuringCooldown()); save();
            button.setMessage(optionLabel("nomiss.cooldown", config.blockDuringCooldown()));
        }).bounds(left, 54, half, 20).tooltip(Tooltip.create(Component.translatable("nomiss.cooldown.tooltip"))).build());
        addRenderableWidget(Button.builder(optionLabel("nomiss.blocks", config.allowBlockBreaking()), button -> {
            config.setAllowBlockBreaking(!config.allowBlockBreaking()); save();
            button.setMessage(optionLabel("nomiss.blocks", config.allowBlockBreaking()));
        }).bounds(left + half + 8, 54, half, 20).tooltip(Tooltip.create(Component.translatable("nomiss.blocks.tooltip"))).build());
        slots = addRenderableWidget(new SlotList(minecraft, contentWidth, Math.max(32, height - 156), 96));
        slots.setX(left);
        for (int i = 0; i < NoMissConfig.MAX_ITEMS; i++) slots.addSlot(i);
        slots.setScrollAmount(scroll);
        addRenderableWidget(Button.builder(Component.translatable("nomiss.defaults"), button -> {
            config.resetItems(); save(); rebuildWidgets();
        }).bounds(left, height - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nomiss.clear"), button -> {
            config.clearItems(); save(); rebuildWidgets();
        }).bounds(left + half + 8, height - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left, height - 28, contentWidth, 20).build());
    }

    private void save() { saveFailed = !NoMissClient.saveConfig(); }

    private Component optionLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 81, saveFailed
            ? Component.translatable("nomiss.save_failed")
            : Component.translatable("nomiss.count", config.items().size(), NoMissConfig.MAX_ITEMS));
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private final class SlotList extends ContainerObjectSelectionList<SlotEntry> {
        private SlotList(Minecraft client, int width, int height, int top) { super(client, width, height, top, 30); }
        private void addSlot(int slot) { addEntry(new SlotEntry(slot)); }
        @Override public int getRowWidth() { return getWidth() - 16; }
    }

    private final class SlotEntry extends ContainerObjectSelectionList.Entry<SlotEntry> {
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

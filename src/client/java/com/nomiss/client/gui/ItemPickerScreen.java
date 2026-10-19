package com.nomiss.client.gui;

import com.nomiss.client.NoMissClient;
import com.nomiss.config.NoMissConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemPickerScreen extends Screen {
    private final Screen parent;
    private final int slot;
    private final NoMissConfig config = NoMissClient.config();
    private final List<ItemChoice> catalog = new ArrayList<>();
    private ItemList itemList;
    private String query = "";
    private boolean saveFailed;

    public ItemPickerScreen(Screen parent, int slot) {
        super(Component.translatable("nomiss.picker", slot + 1));
        this.parent = parent;
        this.slot = slot;
        for (var id : BuiltInRegistries.ITEM.keySet()) {
            if (id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) continue;
            ItemStack stack = ItemPreview.of(BuiltInRegistries.ITEM.getValue(id));
            catalog.add(new ItemChoice(id.toString(), stack.getHoverName(), stack));
        }
        catalog.sort(Comparator.comparing(choice -> choice.name().getString().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        double scroll = itemList == null ? 0 : itemList.scrollAmount();
        int contentWidth = Math.min(440, width - 24);
        int left = (width - contentWidth) / 2;
        var search = new EditBox(font, left, 32, contentWidth, 20, Component.translatable("nomiss.search"));
        search.setMaxLength(128); search.setHint(Component.translatable("nomiss.search")); search.setValue(query);
        search.setResponder(value -> { query = value; refreshItems(); });
        addRenderableWidget(search);
        itemList = addRenderableWidget(new ItemList(minecraft, contentWidth, Math.max(32, height - 112), 76));
        itemList.setX(left); refreshItems(); itemList.setScrollAmount(scroll);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(left, height - 28, contentWidth, 20).build());
        setInitialFocus(search);
    }

    private void refreshItems() {
        if (itemList == null) return;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        itemList.replaceEntries(catalog.stream()
            .filter(choice -> choice.id().contains(needle) || choice.name().getString().toLowerCase(Locale.ROOT).contains(needle))
            .map(ItemEntry::new).toList());
        itemList.setScrollAmount(0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 60, Component.translatable(
            saveFailed ? "nomiss.save_failed" : "nomiss.picker.hint"));
        if (itemList.children().isEmpty()) graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 90,
            Component.translatable("nomiss.no_results"));
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    private record ItemChoice(String id, Component name, ItemStack stack) {}
    private final class ItemList extends ContainerObjectSelectionList<ItemEntry> {
        private ItemList(Minecraft client, int width, int height, int top) { super(client, width, height, top, 28); }
        @Override public int getRowWidth() { return getWidth() - 16; }
    }
    private final class ItemEntry extends ContainerObjectSelectionList.Entry<ItemEntry> {
        private final ItemChoice choice;
        private final Button button;
        private ItemEntry(ItemChoice choice) {
            this.choice = choice;
            int usedSlot = config.slotOf(choice.id());
            boolean usedElsewhere = usedSlot >= 0 && usedSlot != slot;
            button = Button.builder(choice.name(), pressed -> {
                if (config.setItem(slot, choice.id())) {
                    saveFailed = !NoMissClient.saveConfig();
                    if (!saveFailed) onClose();
                }
            }).bounds(0, 0, 200, 22).tooltip(Tooltip.create(usedElsewhere
                ? Component.translatable("nomiss.already_selected", usedSlot + 1) : Component.literal(choice.id()))).build();
            button.active = !usedElsewhere;
        }
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            button.setX(getContentX() + 24); button.setY(getContentY()); button.setWidth(getContentWidth() - 24);
            graphics.item(choice.stack(), getContentX() + 2, getContentY() + 3);
            button.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        @Override public List<? extends GuiEventListener> children() { return List.of(button); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }
        @Override public void visitWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> visitor) { visitor.accept(button); }
    }
}

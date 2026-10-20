package com.nomiss.client.gui;

import com.nomiss.client.NoMissClient;
import com.nomiss.client.WeaponMatcher;
import com.nomiss.config.WeaponSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

final class WeaponRuleScreen extends Screen {
    private final Screen parent;
    private final int slot;
    private Component feedback;
    WeaponRuleScreen(Screen parent, int slot) { super(Component.translatable("nomiss.slot", slot + 1)); this.parent = parent; this.slot = slot; }
    static Component label(WeaponSelection selection) {
        return switch (selection.kind()) {
            case EMPTY -> Component.translatable("nomiss.empty");
            case CATEGORY -> Component.translatable("nomiss.category." + selection.value());
            case EXACT -> Component.translatable("nomiss.exact.label", selection.label());
            case ITEM -> {
                var id = Identifier.tryParse(selection.value());
                yield id != null && BuiltInRegistries.ITEM.containsKey(id) ? ItemPreview.of(BuiltInRegistries.ITEM.getValue(id)).getHoverName() : Component.literal(selection.value());
            }
        };
    }
    @Override protected void init() {
        int w = Math.min(440, width - 24), x = (width - w) / 2;
        var list = addRenderableWidget(new SettingsList(minecraft, w, Math.max(28, height - 116), 76)); list.setX(x);
        list.row(Button.builder(Component.translatable("nomiss.select.category_item"), b -> minecraft.gui.setScreen(new ItemPickerScreen(this, slot))).build());
        var exact = Button.builder(Component.translatable("nomiss.exact.capture"), b -> {
            var held = Minecraft.getInstance().player.getMainHandItem();
            var captured = WeaponMatcher.exactSelection(held);
            if (captured.isEmpty()) { feedback = Component.translatable("nomiss.exact.failed"); return; }
            var old = NoMissClient.config().selection(slot);
            var selection = captured.get().withRules(old.cooldown(), old.blocks());
            if (!NoMissClient.config().setSelection(slot, selection)) feedback = Component.translatable("nomiss.selection.duplicate");
            else save(); rebuildWidgets();
        }).tooltip(Tooltip.create(Component.translatable("nomiss.exact.tooltip"))).build();
        exact.active = minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty(); list.row(exact);
        var selection = NoMissClient.config().selection(slot);
        var cooldown = Button.builder(ruleLabel("nomiss.rule.cooldown", selection.cooldown()), b -> change(selection.withRules(selection.cooldown().next(), selection.blocks()))).build();
        var blocks = Button.builder(ruleLabel("nomiss.rule.blocks", selection.blocks()), b -> change(selection.withRules(selection.cooldown(), selection.blocks().next()))).build();
        cooldown.active = blocks.active = !selection.isEmpty(); list.row(cooldown); list.row(blocks);
        var reset = Button.builder(Component.translatable("nomiss.rule.reset"), b -> change(selection.withRules(WeaponSelection.Override.INHERIT, WeaponSelection.Override.INHERIT))).build(); reset.active = !selection.isEmpty(); list.row(reset);
        list.note(Component.translatable("nomiss.rule.inherit_hint"));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, height - 28, w, 20).build());
    }
    private Component ruleLabel(String key, WeaponSelection.Override value) { return Component.translatable(key, Component.translatable("nomiss.rule." + value.name().toLowerCase(java.util.Locale.ROOT))); }
    private void save() { feedback = NoMissClient.saveConfig() ? null : Component.translatable("nomiss.save_failed"); }
    private void change(WeaponSelection selection) { NoMissClient.config().setSelection(slot, selection); save(); rebuildWidgets(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta); g.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        g.textRenderer().acceptScrollingWithDefaultCenter(feedback != null ? feedback : label(NoMissClient.config().selection(slot)), 12, width - 12, 38, 64);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}

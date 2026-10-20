package com.nomiss.client.gui;

import com.nomiss.client.NoMissClient;
import com.nomiss.config.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.function.*;

/** One configuration entry point for Accessibility, Mod Menu and the in-game shortcut. */
public final class NoMissScreen extends Screen {
    public enum Page { GENERAL, WEAPONS, PROFILES, HUD }
    private final Screen parent;
    private Page page;
    private SettingsList rows;
    private Component feedback;
    private int contentWidth;
    public NoMissScreen(Screen parent) { this(parent, Page.GENERAL); }
    public NoMissScreen(Screen parent, Page page) { super(Component.translatable("nomiss.settings")); this.parent = parent; this.page = page; }
    private NoMissConfig config() { return NoMissClient.config(); }
    @Override protected void init() {
        double scroll = rows == null ? 0 : rows.scrollAmount();
        contentWidth = Math.min(440, width - 24); int left = (width - contentWidth) / 2;
        int tabWidth = (contentWidth - 12) / 4;
        for (var tab : Page.values()) {
            var button = addRenderableWidget(Button.builder(Component.translatable("nomiss.page." + tab.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                page = tab; rows = null; feedback = null; rebuildWidgets();
            }).bounds(left + tab.ordinal() * (tabWidth + 4), 30, tabWidth, 20).build());
            button.active = tab != page;
        }
        rows = addRenderableWidget(new SettingsList(minecraft, contentWidth, Math.max(28, height - 118), 78)); rows.setX(left);
        switch (page) { case GENERAL -> general(); case WEAPONS -> weapons(); case PROFILES -> profiles(); case HUD -> hud(); }
        rows.setScrollAmount(scroll);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(left, height - 28, contentWidth, 20).build());
    }
    private Button action(Component label, Runnable run) { return Button.builder(label, b -> run.run()).build(); }
    private Button action(String key, Runnable run) { return action(Component.translatable(key), run); }
    private void save() { feedback = NoMissClient.saveConfig() ? null : Component.translatable("nomiss.save_failed"); }
    private void change(Runnable run) { run.run(); save(); rebuildWidgets(); }
    private void option(NoMissConfig.Option option) {
        var toggle = action(Component.translatable(option.translationKey, Component.translatable(config().option(option) ? "options.on" : "options.off")), () -> change(() -> config().setOption(option, !config().option(option))));
        toggle.setTooltip(Tooltip.create(Component.translatable(option.translationKey + ".tooltip")));
        var reset = action("controls.reset", () -> change(() -> config().resetOption(option))); reset.active = config().option(option) != option.defaultValue;
        reset.setTooltip(Tooltip.create(Component.translatable("nomiss.reset.tooltip"))); rows.row(toggle, reset);
    }
    private void general() {
        option(NoMissConfig.Option.ENABLED); option(NoMissConfig.Option.COOLDOWN); option(NoMissConfig.Option.BLOCKS);
        rows.row(action("nomiss.keybinds", () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))));
        rows.note(Component.translatable("nomiss.profile.defaults_hint"));
        rows.note(Component.translatable("nomiss.priority_hint"));
    }
    private void weapons() {
        var add = action("nomiss.add_held", () -> { feedback = NoMissClient.addHeldItem(); rebuildWidgets(); });
        var exact = action("nomiss.exact.short", () -> { feedback = NoMissClient.addHeldItem(true); rebuildWidgets(); });
        add.active = exact.active = minecraft.player != null;
        exact.setTooltip(Tooltip.create(Component.translatable("nomiss.exact.tooltip"))); rows.row(add, exact);
        for (int i = 0; i < NoMissConfig.MAX_ITEMS; i++) {
            final int slot = i; var selection = config().selection(slot);
            var choose = action(Component.translatable("nomiss.slot", slot + 1).append(": ").append(WeaponRuleScreen.label(selection)), () -> minecraft.gui.setScreen(new WeaponRuleScreen(this, slot)));
            choose.setTooltip(Tooltip.create(WeaponRuleScreen.label(selection)));
            var clear = action(Component.literal("×"), () -> change(() -> config().setSelection(slot, WeaponSelection.empty())));
            clear.active = !selection.isEmpty(); clear.setTooltip(Tooltip.create(Component.translatable("nomiss.clear_slot", slot + 1)));
            rows.row(choose, clear);
        }
        rows.row(action("nomiss.defaults", () -> change(() -> config().resetItems())));
        rows.row(action("nomiss.clear", () -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) { config().clearItems(); save(); } minecraft.gui.setScreen(this);
        }, Component.translatable("nomiss.clear"), Component.translatable("nomiss.clear.confirm")))));
    }
    private void profiles() {
        option(NoMissConfig.Option.AUTO_SERVER);
        if (config().currentServer() != null) {
            String assigned = config().serverProfiles().get(config().currentServer());
            String name = config().profiles().stream().filter(p -> p.id().equals(assigned)).map(NoMissProfile::name).findFirst().orElse("—");
            rows.note(Component.translatable("nomiss.server.assignment", config().currentServer(), name));
        } else rows.note(Component.translatable("nomiss.server.unavailable"));
        var remember = action("nomiss.server.remember", () -> change(() -> config().rememberServer()));
        var forget = action("nomiss.server.forget", () -> change(() -> config().forgetServer()));
        remember.active = forget.active = config().currentServer() != null; rows.row(remember); rows.row(forget);
        rows.row(action("nomiss.profile.create", () -> prompt("nomiss.profile.create", "", name -> { var p = config().addProfile(name, false); config().activateProfile(p.id()); save(); })));
        rows.row(action("nomiss.profile.duplicate", () -> prompt("nomiss.profile.duplicate", config().activeProfile().name(), name -> { var p = config().addProfile(name, true); config().activateProfile(p.id()); save(); })));
        rows.row(action("nomiss.profile.preset", () -> minecraft.gui.setScreen(new PresetScreen(this))));
        for (var profile : config().profiles()) {
            var activate = action(Component.literal((profile == config().activeProfile() ? "✓ " : "") + profile.name()), () -> change(() -> config().activateProfile(profile.id())));
            activate.setTooltip(Tooltip.create(Component.translatable("nomiss.profile.use", profile.name())));
            var rename = action("nomiss.profile.rename", () -> prompt("nomiss.profile.rename", profile.name(), name -> { profile.rename(name); save(); }));
            var delete = action(Component.literal("×"), () -> minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes) { config().deleteProfile(profile.id()); save(); } minecraft.gui.setScreen(this);
            }, Component.translatable("nomiss.profile.delete"), Component.translatable("nomiss.profile.delete.confirm", profile.name()))));
            delete.active = config().profiles().size() > 1; delete.setTooltip(Tooltip.create(Component.translatable("nomiss.profile.delete"))); rows.row(activate, rename, delete);
        }
        rows.row(action("nomiss.export", () -> { minecraft.keyboardHandler.setClipboard(config().toJson(true)); feedback = Component.translatable("nomiss.export.done"); }));
        rows.row(action("nomiss.import", this::previewImport));
    }
    private void prompt(String key, String initial, Consumer<String> accept) { minecraft.gui.setScreen(new TextPromptScreen(this, Component.translatable(key), initial, accept)); }
    private void previewImport() {
        try {
            var imported = NoMissConfig.parse(minecraft.keyboardHandler.getClipboard(), true);
            minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    try { NoMissClient.importSettings(imported); feedback = Component.translatable("nomiss.import.done"); }
                    catch (IOException e) { feedback = Component.translatable("nomiss.import.failed"); }
                }
                minecraft.gui.setScreen(this);
            }, Component.translatable("nomiss.import"), Component.translatable("nomiss.import.confirm", imported.profiles().size())));
        } catch (IOException e) { feedback = Component.translatable("nomiss.import.invalid"); }
    }
    private void hud() {
        option(NoMissConfig.Option.STATUS); option(NoMissConfig.Option.BLOCKED_HINTS); option(NoMissConfig.Option.PROTECTED_ITEM);
        var h = config().hud();
        rows.row(action(Component.translatable("nomiss.hud.corner", Component.translatable("nomiss.corner." + h.corner().name().toLowerCase(java.util.Locale.ROOT))), () -> change(() -> h.setCorner(HudSettings.Corner.values()[(h.corner().ordinal() + 1) % 4]))),
            action("controls.reset", () -> change(() -> h.setCorner(HudSettings.Corner.TOP_LEFT))));
        rows.row(action(Component.translatable("nomiss.hud.scale", h.scale()), () -> change(() -> h.setScale(h.scale() >= 200 ? 50 : Math.min(200, h.scale() + 25)))),
            action("controls.reset", () -> change(() -> h.setScale(100))));
        rows.row(action(Component.translatable("nomiss.hud.opacity", h.opacity()), () -> change(() -> h.setOpacity(h.opacity() >= 100 ? 20 : Math.min(100, h.opacity() + 20)))),
            action("controls.reset", () -> change(() -> h.setOpacity(100))));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.textRenderer().accept(TextAlignment.CENTER, width / 2, 12, title);
        Component line = feedback != null ? feedback : Component.translatable("nomiss.page.summary", config().activeProfile().name(), config().items().size(), NoMissConfig.MAX_ITEMS);
        graphics.textRenderer().acceptScrollingWithDefaultCenter(line, (width - contentWidth) / 2, (width + contentWidth) / 2, 54, 74);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}

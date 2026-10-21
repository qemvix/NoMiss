package com.nomiss.legacy;

import com.nomiss.config.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.*;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.function.*;

/** Native legacy widgets with paginated settings; no modern GUI/Fabric API dependency. */
public class LegacyScreen extends Screen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final String[] tabs = {"General", "Weapons", "Profiles", "HUD"};
    private int tab, page;
    private String feedback = "";
    public LegacyScreen(Screen parent) { this.parent = parent; }
    private NoMissConfig config() { return LegacyClient.config(); }
    private record Row(String label, Runnable action, ItemStack icon) {}
    private void row(String label, Runnable action) { rows.add(new Row(label, action, null)); }
    private void change(Runnable action) { action.run(); feedback = LegacyClient.save() ? "" : "Could not save settings"; rebuild(); }
    private void rebuild() { buttons.clear(); init(); }
    @Override public void init() {
        rows.clear(); actions.clear(); int left = Math.max(4, (width - 420) / 2), w = Math.min(420, width - 8);
        for (int i = 0; i < 4; i++) {
            final int selected = i;
            add(i, left + i * (w / 4), 28, w / 4 - 2, tabs[i], () -> { tab = selected; page = 0; rebuild(); });
        }
        switch (tab) { case 0 -> general(); case 1 -> weapons(); case 2 -> profiles(); case 3 -> hud(); }
        int capacity = Math.max(1, (height - 116) / 24), pages = Math.max(1, (rows.size() + capacity - 1) / capacity);
        page = Math.min(page, pages - 1);
        for (int i = page * capacity; i < Math.min(rows.size(), (page + 1) * capacity); i++) {
            var row = rows.get(i); add(100 + i, left, 62 + (i % capacity) * 24, w, row.label(), row.action());
        }
        add(10, left, height - 48, w / 2 - 2, "Previous (" + (page + 1) + "/" + pages + ")", () -> { if (page > 0) { page--; rebuild(); } });
        add(11, left + w / 2, height - 48, w / 2, "Next", () -> { if (page + 1 < pages) { page++; rebuild(); } });
        add(12, left, height - 24, w, "Done", () -> client.setScreen(parent));
    }
    private void add(int id, int x, int y, int width, String label, Runnable action) {
        buttons.add(new ButtonWidget(id, x, y, width, 20, label)); actions.put(id, action);
    }
    @Override protected void buttonClicked(ButtonWidget button) {
        if (button.active && actions.containsKey(button.id)) actions.get(button.id).run();
    }
    @Override protected void keyPressed(char typed, int code) { if (code == 1) client.setScreen(parent); else super.keyPressed(typed, code); }
    @Override public void render(int mouseX, int mouseY, float delta) {
        renderBackground(); drawCenteredString(textRenderer, "NoMiss 1.2 | " + config().activeProfile().name(), width / 2, 10, 0xFFFFFF);
        super.render(mouseX, mouseY, delta);
        if (!feedback.isEmpty()) drawCenteredString(textRenderer, feedback, width / 2, 50, 0xFFFF80);
    }
    private void toggle(String name, NoMissConfig.Option option) {
        row(name + ": " + (config().option(option) ? "ON" : "OFF"), () -> change(() -> config().setOption(option, !config().option(option))));
    }
    private void general() {
        toggle("NoMiss", NoMissConfig.Option.ENABLED); toggle("Allow block breaking", NoMissConfig.Option.BLOCKS);
        // No cooldown setting exists before Minecraft 1.9.
        if (LegacyClient.keys != null) for (int i = 0; i < LegacyClient.keys.length; i++) {
            final int index = i; var key = LegacyClient.keys[i];
            int code = key.getCode();
            row(LegacyClient.KEY_NAMES[i] + ": " + (code == 0 ? "Unbound" : code < 0 ? "Mouse " + (code + 101) : Keyboard.getKeyName(code)),
                () -> client.setScreen(new KeyCapture(this, index)));
        }
    }
    private void weapons() {
        row("Add held item type", () -> { feedback = LegacyClient.addHeld(false); rebuild(); });
        row("Add exact held item", () -> { feedback = LegacyClient.addHeld(true); rebuild(); });
        for (int i = 0; i < NoMissConfig.MAX_ITEMS; i++) {
            final int slot = i;
            row("Weapon " + (slot + 1) + ": " + label(config().selection(slot)), () -> client.setScreen(new Rules(this, slot)));
        }
        row("Restore default weapons", () -> change(() -> config().resetItems()));
        row("Clear all weapons", () -> confirm("Clear all 20 slots?", () -> change(() -> config().clearItems())));
    }
    private static String label(WeaponSelection selection) {
        return switch (selection.kind()) { case EMPTY -> "Select item/category"; case CATEGORY -> "All " + selection.value(); case ITEM -> selection.value(); case EXACT -> "Exact: " + selection.label(); };
    }
    private void prompt(String title, String initial, Consumer<String> action) { client.setScreen(new Prompt(this, title, initial, action)); }
    private void confirm(String title, Runnable action) { client.setScreen(new Confirmation(this, title, action)); }
    private void profiles() {
        toggle("Automatic server profiles", NoMissConfig.Option.AUTO_SERVER);
        if (config().currentServer() != null) {
            row("Remember this profile for " + config().currentServer(), () -> change(() -> config().rememberServer()));
            row("Forget server assignment", () -> change(() -> config().forgetServer()));
        }
        row("Create profile", () -> prompt("New profile", "", name -> change(() -> { var p = config().addProfile(name, false); config().activateProfile(p.id()); })));
        row("Duplicate current profile", () -> prompt("Duplicate profile", config().activeProfile().name(), name -> change(() -> { var p = config().addProfile(name, true); config().activateProfile(p.id()); })));
        for (String preset : List.of("Survival", "PvP", "Modpack")) row("Add " + preset + " preset", () -> {
            try { change(() -> {
                var profile = config().addProfile(preset, false); profile.setCooldown(false); profile.setBlocks(!preset.equals("PvP"));
                if (preset.equals("Modpack")) profile.setSlot(1, WeaponSelection.category("axes"));
                config().activateProfile(profile.id());
            }); } catch (IllegalArgumentException e) { feedback = e.getMessage(); }
        });
        for (var profile : config().profiles()) {
            row((profile == config().activeProfile() ? "* " : "") + "Use " + profile.name(), () -> change(() -> config().activateProfile(profile.id())));
            row("Rename " + profile.name(), () -> prompt("Rename profile", profile.name(), name -> change(() -> profile.rename(name))));
            if (config().profiles().size() > 1) row("Delete " + profile.name(), () -> confirm("Delete " + profile.name() + "?", () -> change(() -> config().deleteProfile(profile.id()))));
        }
        row("Export settings to clipboard", () -> { setClipboard(config().toJson(true)); feedback = "Copied settings"; });
        row("Import settings from clipboard", () -> {
            try {
                var replacement = NoMissConfig.parse(getClipboard(), true);
                confirm("Import " + replacement.profiles().size() + " profiles?", () -> {
                    try { LegacyClient.importSettings(replacement); feedback = "Imported; previous settings backed up"; }
                    catch (java.io.IOException e) { feedback = "Import failed; previous settings kept"; }
                    rebuild();
                });
            } catch (java.io.IOException e) { feedback = "Invalid settings in clipboard"; }
        });
    }
    private void hud() {
        toggle("Show status", NoMissConfig.Option.STATUS); toggle("Show protected item", NoMissConfig.Option.PROTECTED_ITEM); toggle("Show blocked attacks", NoMissConfig.Option.BLOCKED_HINTS);
        var hud = config().hud();
        row("Corner: " + hud.corner(), () -> change(() -> hud.setCorner(HudSettings.Corner.values()[(hud.corner().ordinal() + 1) % 4])));
        row("Size: " + hud.scale() + "%", () -> change(() -> hud.setScale(hud.scale() >= 200 ? 50 : Math.min(200, hud.scale() + 25))));
        row("Opacity: " + hud.opacity() + "%", () -> change(() -> hud.setOpacity(hud.opacity() >= 100 ? 20 : Math.min(100, hud.opacity() + 20))));
        row("Reset HUD position/size/opacity", () -> change(() -> { hud.setCorner(HudSettings.Corner.TOP_LEFT); hud.setScale(100); hud.setOpacity(100); }));
    }
    private static class Child extends Screen {
        protected final Screen parent;
        Child(Screen parent) { this.parent=parent; }
        @Override protected void keyPressed(char typed, int code) { if (code == 1) client.setScreen(parent); else super.keyPressed(typed, code); }
        @Override public void render(int x, int y, float delta) { renderBackground(); super.render(x,y,delta); }
    }
    private static class KeyCapture extends Child {
        private final int index;
        KeyCapture(Screen parent, int index) { super(parent); this.index=index; }
        @Override public void render(int x, int y, float delta) {
            super.render(x,y,delta); drawCenteredString(textRenderer,"Press a key for " + LegacyClient.KEY_NAMES[index] + " (Escape = unbind)",width/2,height/2,0xFFFFFF);
        }
        @Override protected void keyPressed(char typed, int code) {
            var key = LegacyClient.keys[index]; code = code == 1 ? 0 : code; key.setCode(code);
            KeyBinding.updateKeysByCode(); client.options.save(); client.setScreen(parent);
        }
    }
    private static class Confirmation extends Child {
        private final String title; private final Runnable accept;
        Confirmation(Screen parent, String title, Runnable accept) { super(parent); this.title=title; this.accept=accept; }
        @Override public void init() { buttons.add(new ButtonWidget(1,width/2-102,height/2,100,20,"Confirm")); buttons.add(new ButtonWidget(2,width/2+2,height/2,100,20,"Cancel")); }
        @Override protected void buttonClicked(ButtonWidget button) { if (button.id == 1) accept.run(); client.setScreen(parent); }
        @Override public void render(int x,int y,float delta) { super.render(x,y,delta); drawCenteredString(textRenderer,title,width/2,height/2-28,0xFFFFFF); }
    }
    private static class Prompt extends Child {
        private final String title; private String value, error=""; private final Consumer<String> accept;
        Prompt(Screen parent,String title,String initial,Consumer<String> accept) { super(parent); this.title=title; value=initial; this.accept=accept; }
        @Override public void init() { buttons.add(new ButtonWidget(1,width/2-102,height/2+20,100,20,"Save")); buttons.add(new ButtonWidget(2,width/2+2,height/2+20,100,20,"Cancel")); }
        @Override protected void buttonClicked(ButtonWidget button) { if (button.id == 2) client.setScreen(parent); else submit(); }
        private void submit() { try { accept.accept(value); client.setScreen(parent); } catch (IllegalArgumentException e) { error=e.getMessage(); } }
        @Override protected void keyPressed(char typed,int code) {
            if (code == 1) { client.setScreen(parent); return; }
            if (code == 28) { submit(); return; }
            if (code == 14) { if (!value.isEmpty()) value=value.substring(0,value.length()-1); return; }
            if (code == 47 && (Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157))) { String clipboard = getClipboard(); value=clipboard.substring(0,Math.min(48,clipboard.length())); return; }
            if (!Character.isISOControl(typed) && value.length()<48) value+=typed;
        }
        @Override public void render(int x,int y,float delta) {
            super.render(x,y,delta); drawCenteredString(textRenderer,title,width/2,height/2-40,0xFFFFFF);
            drawCenteredString(textRenderer,value+"_",width/2,height/2-12,0xFFFFFF); drawCenteredString(textRenderer,error,width/2,height/2+48,0xFF8080);
        }
    }
    private static class Rules extends Child {
        private final int slot;
        Rules(Screen parent,int slot) { super(parent); this.slot=slot; }
        @Override public void init() {
            buttons.add(new ButtonWidget(1,width/2-150,60,300,20,"Select item/category"));
            buttons.add(new ButtonWidget(2,width/2-150,84,300,20,"Capture exact held item"));
            buttons.add(new ButtonWidget(3,width/2-150,108,300,20,"Block breaking: " + LegacyClient.config().selection(slot).blocks()));
            buttons.add(new ButtonWidget(4,width/2-150,132,300,20,"Reset weapon rules"));
            buttons.add(new ButtonWidget(5,width/2-150,156,300,20,"Clear slot"));
            buttons.add(new ButtonWidget(6,width/2-150,height-28,300,20,"Done"));
        }
        @Override protected void buttonClicked(ButtonWidget button) {
            var config=LegacyClient.config(); var old=config.selection(slot);
            if (button.id==1) { client.setScreen(new Picker(this,slot)); return; }
            if (button.id==6) { client.setScreen(parent); return; }
            if (button.id==2) { var held=LegacyClient.held(); if (held!=null) LegacyMatcher.exact(held).ifPresent(s -> config.setSelection(slot,s.withRules(old.cooldown(),old.blocks()))); }
            if (button.id==3) config.setSelection(slot,old.withRules(old.cooldown(),old.blocks().next()));
            if (button.id==4) config.setSelection(slot,old.withRules(WeaponSelection.Override.INHERIT,WeaponSelection.Override.INHERIT));
            if (button.id==5) config.setSelection(slot,WeaponSelection.empty());
            LegacyClient.save(); buttons.clear(); init();
        }
        @Override public void render(int x,int y,float delta) { super.render(x,y,delta); drawCenteredString(textRenderer,"Weapon " +(slot+1)+": "+label(LegacyClient.config().selection(slot)),width/2,30,0xFFFFFF); }
    }
    private static class Picker extends Child {
        private record Choice(WeaponSelection selection,String name,ItemStack icon) {}
        private final List<Choice> catalog=new ArrayList<>();
        private List<Choice> visible=List.of(); private final int slot;
        private String query="", error=""; private int page,capacity;
        Picker(Screen parent,int slot) {
            super(parent); this.slot=slot;
            for (Object value : Item.REGISTRY) if (value instanceof Item item) {
                var stack=new ItemStack(item); catalog.add(new Choice(WeaponSelection.item(LegacyMatcher.id(stack)),stack.getCustomName(),stack));
            }
            catalog.sort(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)));
            Item[] icons={Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE};
            String[] groups={"swords","axes","pickaxes","shovels","hoes"};
            for (int i=groups.length-1;i>=0;i--) catalog.add(0,new Choice(WeaponSelection.category(groups[i]),"All "+groups[i],new ItemStack(icons[i])));
        }
        @Override public void init() {
            buttons.clear(); capacity=Math.max(1,(height-118)/24);
            String needle=query.toLowerCase(Locale.ROOT);
            visible=catalog.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(needle)||c.selection().key().contains(needle)).toList();
            int pages=Math.max(1,(visible.size()+capacity-1)/capacity); page=Math.min(page,pages-1);
            for (int i=page*capacity;i<Math.min(visible.size(),(page+1)*capacity);i++) buttons.add(new ButtonWidget(100+i,width/2-126,58+(i%capacity)*24,276,20,visible.get(i).name()));
            buttons.add(new ButtonWidget(1,width/2-150,height-48,148,20,"Previous")); buttons.add(new ButtonWidget(2,width/2+2,height-48,148,20,"Next"));
            buttons.add(new ButtonWidget(3,width/2-150,height-24,300,20,"Cancel"));
        }
        @Override protected void buttonClicked(ButtonWidget button) {
            if (button.id==1) { page=Math.max(0,page-1); init(); }
            else if (button.id==2) { if ((page+1)*capacity<visible.size()) page++; init(); }
            else if (button.id==3) client.setScreen(parent);
            else {
                var config=LegacyClient.config(); var choice=visible.get(button.id-100).selection(); var old=config.selection(slot);
                if (!config.setSelection(slot,choice.withRules(old.cooldown(),old.blocks()))) error="Already selected in another slot";
                else if (!LegacyClient.save()) error="Could not save settings";
                else client.setScreen(parent);
            }
        }
        @Override protected void keyPressed(char typed,int code) {
            if (code==1) { client.setScreen(parent); return; }
            if (code==14) { if (!query.isEmpty()) query=query.substring(0,query.length()-1); }
            else if (!Character.isISOControl(typed)&&query.length()<128) query+=typed;
            page=0;init();
        }
        @Override public void render(int mouseX,int mouseY,float delta) {
            super.render(mouseX,mouseY,delta); drawCenteredString(textRenderer,"Select item/category | Weapon "+(slot+1),width/2,10,0xFFFFFF);
            fill(width/2-150,28,width/2+150,48,0xFF202020); drawWithShadow(textRenderer,"Search: "+query+"_",width/2-146,34,0xFFFFFF);
            for (int i=page*capacity;i<Math.min(visible.size(),(page+1)*capacity);i++) {
                var choice=visible.get(i); int x=width/2-148,y=60+(i%capacity)*24;
                itemRenderer.renderInGuiWithOverrides(choice.icon(), x, y);
            }
            if (!error.isEmpty()) drawCenteredString(textRenderer,error,width/2,height-62,0xFF8080);
        }
    }
}

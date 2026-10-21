#!/usr/bin/env python3
"""Generate version-specific Minecraft API adapters without editing the 26.2/26.3 sources."""
import argparse
from pathlib import Path
import re
import shutil

from common import ROOT, copy_shared, metadata, replace_once
from targets import MODERN, version


def remove_braced(text, anchor):
    start = text.index(anchor)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[:start] + text[end:]


def generate(mc, output):
    target, v = MODERN[mc], version(mc)
    if output.exists():
        shutil.rmtree(output)
    java, resources = copy_shared(target, output)
    shutil.copytree(ROOT / 'src/client/java', java, dirs_exist_ok=True)
    metadata(target, resources)
    for path in java.rglob('*.java'):
        text = path.read_text(encoding='utf-8')
        text = text.replace('.gui.setScreen(', '.setScreen(').replace('.gui.screen()', '.screen')
        text = text.replace('client.gui.hud.isHidden()', 'client.options.hideGui')
        text = text.replace('net.minecraft.client.gui.Hud;', 'net.minecraft.client.gui.Gui;')
        text = text.replace('@Mixin(Hud.class)', '@Mixin(Gui.class)')
        if path.name == 'NoMissClient.java':
            text = text.replace('client.player.sendOverlayMessage(', 'notify(')
            text = text.replace('    public static NoMissConfig config()',
                                '    private static void notify(Component text) { Minecraft.getInstance().gui.setOverlayMessage(text, false); }\n\n    public static NoMissConfig config()')
        if path.name == 'PresetScreen.java':
            text = text.replace('p.setSlot(3, WeaponSelection.category("axes"))', f'p.setSlot({len(target.categories)}, WeaponSelection.category("axes"))')
        if v < (26,):
            text = text.replace('GuiGraphicsExtractor', 'GuiGraphics').replace('extractRenderState', 'render').replace('extractContent', 'renderContent')
            text = text.replace('client.keymapping.v1.KeyMappingHelper', 'client.keybinding.v1.KeyBindingHelper')
            text = text.replace('KeyMappingHelper.registerKeyMapping', 'KeyBindingHelper.registerKeyBinding')
            text = text.replace('graphics.item(', 'graphics.renderItem(').replace('graphics.text(', 'graphics.drawString(')
            if path.name == 'ItemPreview.java':
                text = 'package com.nomiss.client.gui;\nimport net.minecraft.world.item.*;\nfinal class ItemPreview { static ItemStack of(Item item) { return new ItemStack(item); } }\n'
            if path.name == 'MinecraftMixin.java':
                text = text.replace('client.player.getAttackRangeWith(held)', 'client.player.entityAttackRange()')
                text = text.replace('isWithinAttackRange(held, entityHit', 'isWithinAttackRange(entityHit')
        if v < (1, 21, 11):
            text = text.replace('Identifier', 'ResourceLocation')
            text = text.replace('import net.minecraft.client.gui.TextAlignment;\n', '')
            text = re.sub(r'(graphics|g)\.textRenderer\(\)\.accept\(TextAlignment.CENTER, ', r'CompatText.center(\1, ', text)
            text = re.sub(r'(graphics|g)\.textRenderer\(\)\.acceptScrollingWithDefaultCenter\(', r'CompatText.between(\1, ', text)
            if path.name == 'MinecraftMixin.java':
                text = remove_braced(text, '        if (target != AttackProtection.Target.BLOCK || !rules.blocks())')
                text = re.sub(r'^import .*\b(?:DataComponents|ProjectileUtil|PiercingWeapon|ClipContext|EntityHitResult);\n', '', text, flags=re.M)
            text = text.replace('ItemTags.SPEARS', 'TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "spears"))')
            text = text.replace(' || stack.has(DataComponents.PIERCING_WEAPON)', '')
            # Only show weapon groups that exist in this Minecraft edition.
            if path.name == 'ItemPickerScreen.java':
                groups = ['swords', 'axes', 'pickaxes', 'shovels', 'hoes']
                icons = ['DIAMOND_SWORD', 'DIAMOND_AXE', 'DIAMOND_PICKAXE', 'DIAMOND_SHOVEL', 'DIAMOND_HOE']
                if 'maces' in target.categories:
                    groups.insert(2, 'maces'); icons.insert(2, 'MACE')
                text = re.sub(r'String\[\] groups = \{.*?\};', 'String[] groups = {' + ', '.join(f'"{g}"' for g in groups) + '};', text)
                text = re.sub(r'Item\[\] icons = \{.*?\};', 'Item[] icons = {' + ', '.join('Items.' + icon for icon in icons) + '};', text)
        if v < (1, 21, 9):
            text = re.sub(r'KeyMapping.Category category = KeyMapping.Category.register\(.*?\);', 'String category = "key.categories.nomiss";', text)
            text = text.replace('catalog.addFirst(', 'catalog.add(0, ')
            text = re.sub(r'^\s*@Override public void visitWidgets\([^\n]+\n', '\n', text, flags=re.M)
            text = text.replace('public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta)',
                                'public void render(GuiGraphics graphics, int index, int rowY, int rowX, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float delta)')
            text = text.replace('getContentX()', 'rowX').replace('getContentY()', 'rowY').replace('getContentWidth()', 'rowWidth')
            text = text.replace(' button.setHeight(22);', '')
        if v < (1, 21, 6):
            text = text.replace('.pose().pushMatrix()', '.pose().pushPose()').replace('.pose().popMatrix()', '.pose().popPose()')
            text = text.replace('.pose().scale(scale, scale)', '.pose().scale(scale, scale, 1F)')
        if v < (1, 21, 4):
            text = text.replace('.scrollAmount()', '.getScrollAmount()')
        if v < (1, 21, 2):
            text = text.replace('BuiltInRegistries.ITEM.getValue(', 'BuiltInRegistries.ITEM.get(')
            text = text.replace('client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE)',
                                'net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess())')
            if path.name == 'ItemPickerScreen.java':
                text = text.replace('private final class ItemList extends ContainerObjectSelectionList<ItemEntry> {',
                                    'private final class ItemList extends ContainerObjectSelectionList<ItemEntry> {\n        @Override public void replaceEntries(java.util.Collection<ItemEntry> entries) { super.replaceEntries(entries); }')
        if v < (1, 21):
            text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(').replace('ResourceLocation.parse(', 'new ResourceLocation(')
            text = text.replace('import net.minecraft.client.DeltaTracker;\n', '').replace('DeltaTracker delta', 'float delta')
            text = text.replace('.screens.options.', '.screens.')
            if path.name == 'AccessibilityOptionsScreenMixin.java':
                text = (ROOT / 'backports/templates/fabric/AccessibilityOptionsScreenMixin.java').read_text(encoding='utf-8')
        if v < (1, 20, 5) and path.name == 'WeaponMatcher.java':
            text = re.sub(r'^import net.minecraft.core.component.*\n', '', text, flags=re.M)
            start, end = text.index('            var components ='), text.index('            var digest =')
            text = text[:start] + '''            var tag = stack.hasTag() ? stack.getTag().copy() : new net.minecraft.nbt.CompoundTag();
            tag.remove("Damage"); tag.remove("RepairCost");
            var json = Optional.of(ComponentFingerprint.nbt(tag));
''' + text[end:]
            text = text.replace('new TreeMap<>(element.getAsJsonObject().asMap()).forEach((key, value) -> result.add(key, canonical(value)));',
                                'var sorted = new TreeMap<String, JsonElement>(); element.getAsJsonObject().entrySet().forEach(e -> sorted.put(e.getKey(), e.getValue())); sorted.forEach((key, value) -> result.add(key, canonical(value)));')
            for plural, singular in [('SWORDS', 'Sword'), ('AXES', 'Axe'), ('PICKAXES', 'Pickaxe'), ('SHOVELS', 'Shovel'), ('HOES', 'Hoe')]:
                text = text.replace(f'tagged(stack, ItemTags.{plural},', f'stack.getItem() instanceof net.minecraft.world.item.{singular}Item || tagged(stack, TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "{plural.lower()}")),')
        if v < (1, 20, 3):
            text = text.replace('super(client, width, height, top, 28)', 'super(client, width, top + height, top, top + height, 28)')
            text = text.replace('return getWidth() - 16', 'return width - 16')
            for variable in ('rows', 'itemList', 'list'):
                text = text.replace(f'{variable}.setX(', f'{variable}.setLeftPos(')
        if v < (1, 20):
            text = text.replace('import net.minecraft.client.gui.GuiGraphics;', 'import com.mojang.blaze3d.vertex.PoseStack;').replace('GuiGraphics ', 'PoseStack ')
            text = text.replace('graphics.guiWidth()', 'client.getWindow().getGuiScaledWidth()').replace('graphics.guiHeight()', 'client.getWindow().getGuiScaledHeight()')
            text = text.replace('graphics.pose()', 'graphics')
            text = text.replace('graphics.fill(', 'net.minecraft.client.gui.GuiComponent.fill(graphics, ')
            text = text.replace('graphics.drawString(client.font, ', 'client.font.drawShadow(graphics, ')
            text = text.replace('graphics.renderItem(choice.stack(), ', 'Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(choice.stack(), ')
        if v < (1, 19, 3):
            text = text.replace('import net.minecraft.core.registries.BuiltInRegistries;', 'import net.minecraft.core.Registry;')
            text = text.replace('import net.minecraft.core.registries.Registries;\n', '')
            text = text.replace('BuiltInRegistries.ITEM', 'Registry.ITEM').replace('Registries.ITEM', 'Registry.ITEM_REGISTRY')
            text = re.sub(r'\bButton\b', 'PortButton', text)
            text = re.sub(r'\bTooltip\b', 'PortTooltip', text)
            text = re.sub(r'import net.minecraft.client.gui.components.Port(?:Button|Tooltip);\n', '', text)
            if 'PortButton' in text and path.parent.name != 'gui':
                text = text.replace('package com.nomiss.client.mixin;', 'package com.nomiss.client.mixin;\nimport com.nomiss.client.gui.PortButton;\nimport com.nomiss.client.gui.PortTooltip;')
            text = text.replace('search.setHint(Component.translatable("nomiss.search")); ', '')
        path.write_text(text, encoding='utf-8')
    template_dir = ROOT / 'backports/templates/fabric'
    if v < (1, 21, 11):
        text = (template_dir / 'CompatText.java').read_text(encoding='utf-8')
        if v < (1, 20):
            text = text.replace('import net.minecraft.client.gui.GuiGraphics;', 'import com.mojang.blaze3d.vertex.PoseStack;').replace('GuiGraphics ', 'PoseStack ')
            text = text.replace('graphics.drawCenteredString(', 'net.minecraft.client.gui.GuiComponent.drawCenteredString(graphics, ')
        (java / 'com/nomiss/client/gui/CompatText.java').write_text(text, encoding='utf-8')
    if v < (1, 20, 5):
        shutil.copyfile(template_dir / 'ComponentFingerprint.java', java / 'com/nomiss/client/ComponentFingerprint.java')
    if v < (1, 19, 3):
        for name in ('PortButton.java', 'PortTooltip.java'):
            shutil.copyfile(template_dir / name, java / 'com/nomiss/client/gui' / name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('minecraft', choices=MODERN)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    generate(args.minecraft, args.output.resolve())

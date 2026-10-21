#!/usr/bin/env python3
"""Generate the LWJGL2/Legacy Fabric client adapters. Requires API compilation before release."""
import argparse
import json
from pathlib import Path
import shutil

from common import ROOT, copy_shared, metadata
from targets import LEGACY, version


def generate(mc, output):
    target, v = LEGACY[mc], version(mc)
    if output.exists():
        shutil.rmtree(output)
    java, resources = copy_shared(target, output)
    metadata(target, resources, legacy=True)
    translations = json.loads((resources / 'assets/nomiss/lang/en_us.json').read_text(encoding='utf-8'))
    (resources / 'assets/nomiss/lang/en_US.lang').write_text(''.join(f'{key}={value}\n' for key, value in translations.items()), encoding='utf-8')
    shutil.copytree(ROOT / 'backports/templates/legacy', java, dirs_exist_ok=True)
    for path in java.rglob('*.java'):
        text = path.read_text(encoding='utf-8')
        if v < (1, 8, 2):
            text = text.replace('new Window(client)', 'new Window(client, client.width, client.height)')
            text = text.replace('client.result.type', 'client.result.hitResult')
        if v < (1, 8):
            text = text.replace('client.player', 'client.field_3805')
            text = text.replace('Item.REGISTRY.getIdentifier(', 'Item.REGISTRY.getId(')
            text = text.replace('.drawWithShadow(', '.method_956(')
            text = text.replace('itemRenderer.renderInGuiWithOverrides(choice.icon(), x, y)', 'itemRenderer.method_6920(textRenderer, client.getTextureManager(), choice.icon(), x, y)')
        if v < (1, 7):
            text = text.replace('Items.DIAMOND_', 'Item.DIAMOND_')
            text = text.replace('new Window(client, client.width, client.height)', 'new Window(client.options, client.width, client.height)')
            text = text.replace(', "key.categories.nomiss")', ')')
            text = text.replace('key.getCode()', 'key.code')
            text = text.replace('key.setCode(code)', 'key.code = code')
            text = text.replace('Item.REGISTRY.getId(stack.getItem()).toString()', '"legacy:item_" + stack.getItem().id')
            text = text.replace('for (Object value : Item.REGISTRY)', 'for (Object value : Item.ITEMS)')
            text = text.replace('itemRenderer.method_6920(', 'itemRenderer.method_5764(')
            if path.name == 'LegacyScreen.java':
                text = text.replace('public class LegacyScreen extends Screen {',
                                    'public class LegacyScreen extends Screen {\n    private static final net.minecraft.client.render.item.ItemRenderer itemRenderer = new net.minecraft.client.render.item.ItemRenderer();')
            if path.name == 'ClientMixin.java':
                text = text.replace('method = "doAttack"', 'method = "method_2941"')
                text = text.replace('private void nomiss$attack(CallbackInfo ci)', 'private void nomiss$attack(int button, CallbackInfo ci)')
                text = text.replace('if (LegacyClient.blockAttack())', 'if (button == 0 && LegacyClient.blockAttack())')
                text = text.replace('method = "handleBlockBreaking"', 'method = "method_2925"')
                text = text.replace('private void nomiss$mine(boolean active, CallbackInfo ci)', 'private void nomiss$mine(int button, boolean active, CallbackInfo ci)')
                text = text.replace('if (active && LegacyClient.blockMining())', 'if (button == 0 && active && LegacyClient.blockMining())')
        path.write_text(text, encoding='utf-8')
    mixins = {'required': True, 'package': 'com.nomiss.legacy.mixin', 'compatibilityLevel': 'JAVA_17',
              'client': ['ClientMixin', 'OptionsMixin', 'HudMixin', 'SettingsMixin', 'NbtCompoundAccessor', 'NbtListAccessor', 'MinecraftAccessor'],
              'injectors': {'defaultRequire': 1}}
    (resources / 'nomiss.legacy.mixins.json').write_text(json.dumps(mixins, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('minecraft', choices=LEGACY)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    generate(args.minecraft, args.output.resolve())

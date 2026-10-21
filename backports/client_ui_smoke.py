"""Build a separate, production-namespace UI test mod. Never part of downloads."""
import json
from pathlib import Path
import subprocess
import zipfile
from audit_jar import class_info
from build_matrix import find_game_jar
from common import ROOT
from targets import TARGETS


def build_ui_smoke(mc, mod, run, cache, jdk):
    legacy = TARGETS[mc].loader == 'legacy-fabric'
    settings = 'com/nomiss/legacy/LegacyScreen' if legacy else 'com/nomiss/client/gui/NoMissScreen'
    picker = 'com/nomiss/legacy/LegacyScreen$Picker' if legacy else 'com/nomiss/client/gui/ItemPickerScreen'
    mixin = 'com/nomiss/legacy/mixin/ClientMixin' if legacy else 'com/nomiss/client/mixin/MinecraftMixin'
    with zipfile.ZipFile(mod) as archive:
        settings_info = class_info(archive.read(settings + '.class'))
        client_mixin = class_info(archive.read(mixin + '.class'))
    owner = next(a for a in client_mixin['annotations'] if a['type'].endswith('/Mixin;'))['values']['value'][0][1:-1]
    screen = settings_info['parent']
    render = next(m for m in settings_info['methods'] if m['descriptor'].endswith('IIF)V'))
    game_jar = find_game_jar(Path.home() / '.gradle', mc)
    with zipfile.ZipFile(game_jar) as archive:
        client = class_info(archive.read(owner + '.class'))
    names = {}
    stack_names = {}
    if mc.startswith('26.'):
        names = {'tick':'tick', 'setScreen':'setScreen', 'screen':'screen'}
    else:
        for mapping in (Path.home() / '.gradle/caches/fabric-loom' / mc).rglob('mappings.jar'):
            with zipfile.ZipFile(mapping) as archive:
                lines = archive.read('mappings/mappings.tiny').decode().splitlines()
            namespaces = lines[0].split('\t')[3:]
            if 'named' not in namespaces or 'intermediary' not in namespaces:
                continue
            ni, ri = namespaces.index('named'), namespaces.index('intermediary')
            relevant = False
            stack_relevant = False
            for line in lines[1:]:
                fields = line.split('\t')
                if fields[0] == 'c':
                    relevant = fields[1 + ri] == owner
                    stack_relevant = fields[1 + ni] in ('net/minecraft/item/ItemStack', 'net/minecraft/world/item/ItemStack')
                elif stack_relevant and len(fields) > 3 + ni and fields[1] == 'm':
                    if fields[3 + ni] in ('setDamage', 'setRepairCost', 'setCustomName'):
                        stack_names[fields[3 + ni]] = fields[3 + ri]
                elif relevant and len(fields) > 3 + ni and fields[1] in ('m', 'f'):
                    named = fields[3 + ni]
                    if named in ('tick', 'setScreen', 'screen', 'currentScreen'):
                        names[named] = fields[3 + ri]
    field_name = names.get('currentScreen') if legacy else names.get('screen')
    assert field_name and names.get('tick') and names.get('setScreen'), f'Missing client API names: {names}'
    assert any(f['name'] == field_name and f['descriptor'] == 'L' + screen + ';' for f in client['fields'])
    assert any(m['name'] == names['tick'] and m['descriptor'] == '()V' for m in client['methods'])
    assert any(m['name'] == names['setScreen'] and m['descriptor'] == '(L' + screen + ';)V' for m in client['methods'])
    source = run / 'ui-smoke-src/com/nomiss/smoke'
    source.mkdir(parents=True, exist_ok=True)
    (source / 'UiChecks.java').write_text((ROOT / 'backports/tests/client/UiChecks.java').read_text(encoding='utf-8-sig'), encoding='utf-8')
    header = 'package com.nomiss.smoke.mixin; import com.nomiss.smoke.UiChecks; import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.*; import org.spongepowered.asm.mixin.injection.callback.CallbackInfo; '
    (source / 'TickHook.java').write_text(header + '@Mixin(targets=' + json.dumps(owner.replace('/', '.')) + ', remap=false) public class TickHook { @Inject(method=' + json.dumps(names['tick'] + '()V') + ', at=@At("TAIL"), remap=false) private void check(CallbackInfo ci) { UiChecks.tick(this); } }', encoding='utf-8')
    (source / 'FrameHook.java').write_text(header + '@Mixin(targets={' + ','.join(json.dumps(c.replace('/', '.')) for c in (settings, picker)) + '}, remap=false) public class FrameHook { @Inject(method=' + json.dumps(render['name'] + render['descriptor']) + ', at=@At("TAIL"), remap=false) private void frame(CallbackInfo ci) { UiChecks.frame(this); } }', encoding='utf-8')
    mixin_jar = next((cache / 'libraries/net/fabricmc/sponge-mixin').rglob('*.jar'))
    classes = run / 'ui-smoke-classes-v2'
    classes.mkdir(exist_ok=True)
    subprocess.run([str(jdk / 'bin/javac.exe'), '--release', '17', '-proc:none', '-cp', str(mixin_jar), '-d', str(classes), *map(str, source.glob('*.java'))], check=True)
    jar = run / 'mods/nomiss-client-smoke.jar'
    with zipfile.ZipFile(jar, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in classes.rglob('*.class'):
            archive.write(path, path.relative_to(classes).as_posix())
        archive.writestr('fabric.mod.json', json.dumps({'schemaVersion':1,'id':'nomiss-client-smoke','version':'1.0','environment':'client','mixins':['nomiss-smoke.mixins.json'],'depends':{'nomiss':'1.2','minecraft':mc}}))
        archive.writestr('nomiss-smoke.mixins.json', json.dumps({'required':True,'package':'com.nomiss.smoke.mixin','compatibilityLevel':'JAVA_17','client':['TickHook','FrameHook'],'injectors':{'defaultRequire':1}}))
    extra = ['-Dnomiss.smoke.' + key + '=' + value for key, value in stack_names.items()]
    return extra + ['-Dnomiss.smoke.screenClass=' + screen.replace('/', '.'), '-Dnomiss.smoke.screenField=' + field_name, '-Dnomiss.smoke.setScreen=' + names['setScreen'], '-Dnomiss.smoke.legacy=' + str(legacy).lower()]

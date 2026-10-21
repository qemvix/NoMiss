"""Exercise release guards against small compiled fixtures, without Minecraft downloads."""
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_jar import audit


class ArtifactAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        declarations = {
            'org/spongepowered/asm/mixin/Mixin.java': 'package org.spongepowered.asm.mixin; public @interface Mixin { Class<?>[] value(); }',
            'org/spongepowered/asm/mixin/injection/Inject.java': 'package org.spongepowered.asm.mixin.injection; public @interface Inject { String[] method(); }',
            'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': 'package org.spongepowered.asm.mixin.injection.callback; public class CallbackInfo {}',
            'net/minecraft/client/Game.java': 'package net.minecraft.client; public class Game { public void method_1() {} public void method_2(boolean active) {} public void method_3() {} }',
            'com/nomiss/client/mixin/Example.java': '''package com.nomiss.client.mixin;
                import org.spongepowered.asm.mixin.Mixin;
                import org.spongepowered.asm.mixin.injection.Inject;
                import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
                @Mixin(net.minecraft.client.Game.class) public class Example {
                  @Inject(method="attack") void attack(CallbackInfo ci) {}
                  @Inject(method="mine") void mine(boolean active, CallbackInfo ci) {}
                  @Inject(method="hud") void hud(CallbackInfo ci) {}
                }''',
        }
        sources = []
        for name, text in declarations.items():
            path = cls.root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text); sources.append(str(path))
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else 'javac'
        subprocess.run([javac, '--release', '17', '-d', str(cls.root / 'classes'), *sources], check=True)
        cls.game = cls.root / 'minecraft.jar'
        with zipfile.ZipFile(cls.game, 'w') as bundle:
            bundle.write(cls.root / 'classes/net/minecraft/client/Game.class', 'net/minecraft/client/Game.class')
        cls.entries = {
            'fabric.mod.json': json.dumps({'id':'nomiss','version':'1.2','depends':{'minecraft':'1.20.4','java':'>=17'},'mixins':['nomiss.mixins.json'],
                                         'jars':[{'file':f'META-INF/jars/{i}.jar'} for i in range(4)]}).encode(),
            'nomiss.mixins.json': json.dumps({'package':'com.nomiss.client.mixin','compatibilityLevel':'JAVA_17','client':['Example'],'refmap':'nomiss.refmap.json'}).encode(),
            'nomiss.refmap.json': json.dumps({'mappings': {'com/nomiss/client/mixin/Example': {
                'attack':'Lnet/minecraft/client/Game;method_1()V',
                'mine':'Lnet/minecraft/client/Game;method_2(Z)V',
                'hud':'Lnet/minecraft/client/Game;method_3()V'}}}).encode(),
            'com/nomiss/client/mixin/Example.class': (cls.root / 'classes/com/nomiss/client/mixin/Example.class').read_bytes(),
        }
        for i, name in enumerate(['fabric-api-base','fabric-lifecycle-events-v1','fabric-key-binding-api-v1','fabric-resource-loader-v0']):
            data = io.BytesIO()
            with zipfile.ZipFile(data, 'w') as bundle: bundle.writestr('fabric.mod.json', json.dumps({'id': name,'version':'1.0','depends':{'minecraft':'*'}}))
            cls.entries[f'META-INF/jars/{i}.jar'] = data.getvalue()

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_audit(self, entries):
        path = self.root / 'nomiss-1.20.4-fabric-1.2.jar'
        with zipfile.ZipFile(path, 'w') as bundle:
            for name, data in entries.items(): bundle.writestr(name, data)
        return audit(path, '1.20.4', self.game)

    def test_valid_package_and_refmap(self):
        result = self.run_audit(self.entries)
        self.assertEqual(3, result['injections_checked'])
        self.assertFalse(result['gameplay_tested'])

    def test_wrong_minecraft_selector_is_rejected(self):
        entries = self.entries.copy(); data = json.loads(entries['fabric.mod.json']); data['depends']['minecraft']='*'
        entries['fabric.mod.json']=json.dumps(data).encode()
        with self.assertRaisesRegex(AssertionError, 'selector'): self.run_audit(entries)

    def test_too_new_bytecode_is_rejected(self):
        entries=self.entries.copy(); name='com/nomiss/client/mixin/Example.class'; data=bytearray(entries[name]); data[6:8]=(65).to_bytes(2,'big'); entries[name]=bytes(data)
        with self.assertRaisesRegex(AssertionError,'minimum Java'): self.run_audit(entries)

    def test_missing_injection_target_is_rejected(self):
        entries=self.entries.copy(); data=json.loads(entries['nomiss.refmap.json']); data['mappings']['com/nomiss/client/mixin/Example']['attack']='missing'
        entries['nomiss.refmap.json']=json.dumps(data).encode()
        with self.assertRaisesRegex(AssertionError,'Missing injection'): self.run_audit(entries)

    def test_wrong_handler_parameters_are_rejected(self):
        entries=self.entries.copy(); data=json.loads(entries['nomiss.refmap.json']); data['mappings']['com/nomiss/client/mixin/Example']['mine']='method_1'
        entries['nomiss.refmap.json']=json.dumps(data).encode()
        with self.assertRaisesRegex(AssertionError,'parameter mismatch'): self.run_audit(entries)

    def test_wrong_resource_loader_generation_is_rejected(self):
        entries=self.entries.copy(); data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as bundle: bundle.writestr('fabric.mod.json',json.dumps({'id':'fabric-resource-loader-v1','version':'1.0'}))
        entries['META-INF/jars/3.jar']=data.getvalue()
        with self.assertRaisesRegex(AssertionError,'modules not bundled'): self.run_audit(entries)

    def test_unbundled_api_dependency_is_rejected(self):
        entries=self.entries.copy(); data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as bundle: bundle.writestr('fabric.mod.json',json.dumps({'id':'fabric-api-base','version':'1.0','depends':{'missing-module':'*'}}))
        entries['META-INF/jars/0.jar']=data.getvalue()
        with self.assertRaisesRegex(AssertionError,'Unbundled dependency'): self.run_audit(entries)


if __name__ == '__main__': unittest.main()

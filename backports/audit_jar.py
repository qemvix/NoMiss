#!/usr/bin/env python3
"""Audit a remapped port against its actual production Minecraft JAR before collecting it."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

from targets import TARGETS, version


class Reader:
    def __init__(self, data): self.stream = io.BytesIO(data)
    def u1(self): return struct.unpack('>B', self.stream.read(1))[0]
    def u2(self): return struct.unpack('>H', self.stream.read(2))[0]
    def u4(self): return struct.unpack('>I', self.stream.read(4))[0]


def class_info(data):
    r = Reader(data)
    if r.u4() != 0xCAFEBABE: raise ValueError('Invalid class header')
    r.u2(); major = r.u2()
    cp = [None] * r.u2()
    i = 1
    while i < len(cp):
        tag = r.u1()
        if tag == 1: cp[i] = r.stream.read(r.u2()).decode('utf-8', errors='replace')
        elif tag in (7, 8, 16, 19, 20): cp[i] = r.u2()
        elif tag in (3, 4): cp[i] = r.stream.read(4)
        elif tag in (5, 6): r.stream.read(8); i += 1
        elif tag in (9, 10, 11, 12, 17, 18): r.stream.read(4)
        elif tag == 15: r.stream.read(3)
        else: raise ValueError(f'Unknown constant pool tag {tag}')
        i += 1
    def element(reader):
        tag = chr(reader.u1())
        if tag in 'BCDFIJSZs': return cp[reader.u2()]
        if tag == 'e': return {'enum': cp[reader.u2()], 'value': cp[reader.u2()]}
        if tag == 'c': return cp[reader.u2()]
        if tag == '@': return annotation(reader)
        if tag == '[': return [element(reader) for _ in range(reader.u2())]
        raise ValueError(f'Unknown annotation tag {tag}')
    def annotation(reader):
        type_name = cp[reader.u2()]
        return {'type': type_name, 'values': {cp[reader.u2()]: element(reader) for _ in range(reader.u2())}}
    def attributes():
        annotations = []
        for _ in range(r.u2()):
            name, size = cp[r.u2()], r.u4()
            content = r.stream.read(size)
            if name in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                nested = Reader(content)
                annotations.extend(annotation(nested) for _ in range(nested.u2()))
        return annotations
    r.u2(); name = cp[cp[r.u2()]]; parent_index = r.u2()
    parent = cp[cp[parent_index]] if parent_index else None
    for _ in range(r.u2()): r.u2()
    def members():
        result = []
        for _ in range(r.u2()):
            flags, name, descriptor = r.u2(), cp[r.u2()], cp[r.u2()]
            result.append({'name': name, 'descriptor': descriptor, 'annotations': attributes(), 'flags': flags})
        return result
    fields, methods = members(), members()
    return {'name': name, 'parent': parent, 'major': major, 'fields': fields, 'methods': methods, 'annotations': attributes()}


def arguments(descriptor):
    position, result = 1, []
    while descriptor[position] != ')':
        start = position
        while descriptor[position] == '[': position += 1
        if descriptor[position] == 'L': position = descriptor.index(';', position) + 1
        else: position += 1
        result.append(descriptor[start:position])
    return result


def audit(path, mc, minecraft_jar):
    target = TARGETS[mc]
    with zipfile.ZipFile(path) as mod, zipfile.ZipFile(minecraft_jar) as game:
        metadata = json.loads(mod.read('fabric.mod.json'))
        assert metadata['id'] == 'nomiss' and metadata['version'] == '1.2'
        assert metadata['depends']['minecraft'] == mc, 'Minecraft selector must be exact'
        assert metadata['depends']['java'] == f'>={target.java}'
        classes = {name[:-6]: class_info(mod.read(name)) for name in mod.namelist() if name.startswith('com/nomiss/') and name.endswith('.class')}
        assert classes, 'No NoMiss classes'
        assert all(info['major'] <= target.java + 44 for info in classes.values()), 'Class exceeds minimum Java version'
        game_cache = {}
        def game_class(name):
            if name not in game_cache:
                game_cache[name] = class_info(game.read(name + '.class'))
            return game_cache[name]
        def find_field(owner, name):
            while owner:
                info = game_class(owner)
                match = next((f for f in info['fields'] if f['name'] == name), None)
                if match: return match
                owner = info['parent']
            return None
        checked_injections = 0
        for mixin_value in metadata['mixins']:
            mixin_name = mixin_value if isinstance(mixin_value, str) else mixin_value['config']
            config = json.loads(mod.read(mixin_name))
            assert config['compatibilityLevel'] == f'JAVA_{target.java}'
            refmap = json.loads(mod.read(config['refmap'])).get('mappings', {}) if config.get('refmap') else {}
            for short in config.get('client', []) + config.get('mixins', []):
                name = (config['package'] + '.' + short).replace('.', '/')
                info = classes[name]
                references = refmap.get(name, refmap.get(name.replace('/', '.'), {}))
                mixin = next(a for a in info['annotations'] if a['type'].endswith('/Mixin;'))
                owners = mixin['values'].get('value', [])
                if isinstance(owners, str): owners = [owners]
                assert owners, f'No concrete target in {name}'
                for descriptor in owners:
                    owner = descriptor[1:-1]
                    actual = game_class(owner)
                    for method in info['methods']:
                        for annotation in method['annotations']:
                            if annotation['type'].endswith('/Inject;'):
                                selectors = annotation['values']['method']
                                if isinstance(selectors, str): selectors = [selectors]
                                for selector in selectors:
                                    selector = references.get(selector, selector)
                                    if selector.startswith('L') and ';' in selector:
                                        selector = selector.split(';', 1)[1]
                                    method_name = selector.split('(')[0]
                                    matches = [m for m in actual['methods'] if m['name'] == method_name and ('(' not in selector or m['name'] + m['descriptor'] == selector)]
                                    assert matches, f'Missing injection target {owner}.{selector}'
                                    handler_args = arguments(method['descriptor'])
                                    callback = next((i for i, arg in enumerate(handler_args) if '/callback/CallbackInfo' in arg), None)
                                    assert callback is not None, f'No callback in {name}.{method["name"]}'
                                    # A callback-only handler is valid for every target signature.
                                    assert callback == 0 or any(handler_args[:callback] == arguments(m['descriptor']) for m in matches), f'Injection parameter mismatch in {name}.{method["name"]}'
                                    checked_injections += 1
                            if annotation['type'].endswith('/Accessor;'):
                                field_name = annotation['values']['value']
                                field_ref = references.get(field_name, references.get(field_name + ':' + method['descriptor'][2:], field_name))
                                if field_ref.startswith('L') and ';' in field_ref: field_ref = field_ref.split(';', 1)[1]
                                field = find_field(owner, field_ref.split(':')[0])
                                assert field, f'Missing accessor field in {name}'
                                assert method['descriptor'] == '()' + field['descriptor'], f'Accessor type mismatch in {name}'
        assert checked_injections >= 3, 'Expected attack, mining and HUD injections'
        nested = []
        for item in metadata.get('jars', []):
            with zipfile.ZipFile(io.BytesIO(mod.read(item['file']))) as bundle:
                nested.append(json.loads(bundle.read('fabric.mod.json')))
        if target.loader == 'fabric':
            ids = {item['id'] for item in nested}
            expected = {'fabric-api-base', 'fabric-lifecycle-events-v1',
                        'fabric-key-mapping-api-v1' if mc.startswith('26.') else 'fabric-key-binding-api-v1',
                        'fabric-resource-loader-v1' if version(mc) >= (1, 21, 9) else 'fabric-resource-loader-v0'}
            assert expected <= ids, 'Required Fabric API modules not bundled'
            available = ids | {'minecraft', 'java', 'fabricloader', 'fabric-loader'}
            for item in nested:
                assert set(item.get('depends', {})) <= available, f'Unbundled dependency in {item["id"]}'
        else:
            assert 'com/nomiss/libs/gson/Gson.class' in mod.namelist(), 'Legacy Gson not relocated/bundled'
    return {'minecraft': mc, 'java': target.java, 'loader': target.loader,
            'filename': path.name, 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'injections_checked': checked_injections, 'package_audit_passed': True,
            'gameplay_tested': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('minecraft', choices=TARGETS)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--minecraft-jar', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(audit(args.jar, args.minecraft, args.minecraft_jar), indent=2))

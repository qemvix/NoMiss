#!/usr/bin/env python3
"""Launch an audited production mod in an isolated client directory. Not a gameplay test.

Uses official Mojang/Fabric metadata and verifies downloads. Never reads account tokens,
modifies an installed Minecraft profile, or copies the user's worlds/configuration.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import zipfile

from common import ROOT
from targets import TARGETS


def fetch_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def verified_file(path, url, checksum, algorithm='sha1', source=None):
    def valid(candidate):
        return candidate.is_file() and hashlib.new(algorithm, candidate.read_bytes()).hexdigest() == checksum
    path.parent.mkdir(parents=True, exist_ok=True)
    if valid(path):
        return path
    if source and valid(source):
        shutil.copyfile(source, path)
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.new(algorithm, data).hexdigest() != checksum:
            raise ValueError(f'Checksum mismatch: {url}')
        part = path.with_suffix(path.suffix + '.part')
        part.write_bytes(data)
        part.replace(path)
    return path


def allowed(rules):
    result = not rules
    for rule in rules or []:
        target_os = rule.get('os', {})
        if target_os.get('name', 'windows') != 'windows':
            continue
        if target_os.get('arch') and target_os['arch'] not in ('x86_64', 'amd64'):
            continue
        if target_os.get('version') and not re.search(target_os['version'], platform.version()):
            continue
        if any(value is not False for value in rule.get('features', {}).values()):
            continue
        result = rule['action'] == 'allow'
    return result


def coordinate(name):
    group, artifact, release, *classifier = name.split(':')
    suffix = '-' + classifier[0] if classifier else ''
    return f"{group.replace('.', '/')}/{artifact}/{release}/{artifact}-{release}{suffix}.jar"


def prepare(mc, mod, cache, assets_source):
    target = TARGETS[mc]
    manifest = fetch_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
    release = next(v for v in manifest['versions'] if v['id'] == mc and v['type'] == 'release')
    metadata_path = verified_file(cache / f'metadata/{mc}.json', release['url'], release['sha1'])
    game = json.loads(metadata_path.read_text(encoding='utf-8'))
    domain = 'meta.legacyfabric.net' if target.loader == 'legacy-fabric' else 'meta.fabricmc.net'
    loader = '0.20.1' if target.loader == 'legacy-fabric' else '0.19.5'
    profile = fetch_json(f'https://{domain}/v2/versions/loader/{mc}/{loader}/profile/json')
    run = ROOT / 'backports/build/client-smoke' / mc
    natives = run / 'natives'
    natives.mkdir(parents=True, exist_ok=True)
    (run / 'mods').mkdir(exist_ok=True)
    # Refuse to silently mix different test builds/mods in one isolated client.
    for old in (run / 'mods').glob('*.jar'):
        if old.name not in (mod.name, 'nomiss-client-smoke.jar'):
            raise ValueError(f'Unexpected mod in isolated smoke directory: {old}')
    shutil.copyfile(mod, run / 'mods' / mod.name)
    client = game['downloads']['client']
    game_jar = verified_file(cache / f'game/{mc}.jar', client['url'], client['sha1'])
    merged = {}
    for library in game['libraries'] + profile['libraries']:
        if allowed(library.get('rules')):
            parts = library['name'].split(':')
            merged[tuple(parts[:2] + parts[3:])] = library
    classpath = []
    for library in merged.values():
        downloads = library.get('downloads', {})
        artifact = downloads.get('artifact')
        if artifact:
            path = verified_file(cache / 'libraries' / artifact['path'], artifact['url'], artifact['sha1'])
            classpath.append(path)
        elif not library.get('natives'):
            relative = coordinate(library['name'])
            url = library.get('url', 'https://libraries.minecraft.net/') + relative
            algorithm = 'sha256' if library.get('sha256') else 'sha1'
            checksum = library.get(algorithm)
            if not checksum:
                try:
                    checksum = urllib.request.urlopen(url + '.' + algorithm, timeout=30).read().decode().split()[0]
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        raise
                    algorithm = 'sha1'
                    checksum = urllib.request.urlopen(url + '.sha1', timeout=30).read().decode().split()[0]
            path = verified_file(cache / 'libraries' / relative, url, checksum.lower(), algorithm)
            classpath.append(path)
        classifier = library.get('natives', {}).get('windows', '').replace('${arch}', '64')
        if classifier:
            native = downloads.get('classifiers', {}).get(classifier)
            if native:
                path = verified_file(cache / 'libraries' / native['path'], native['url'], native['sha1'])
            else:
                relative = coordinate(library['name'] + ':' + classifier)
                url = library.get('url', 'https://libraries.minecraft.net/') + relative
                checksum = urllib.request.urlopen(url + '.sha1', timeout=30).read().decode().split()[0]
                path = verified_file(cache / 'libraries' / relative, url, checksum.lower())
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.lower().endswith('.dll'):
                        (natives / Path(name).name).write_bytes(archive.read(name))
    classpath.append(game_jar)
    index = game['assetIndex']
    assets = cache / 'assets'
    index_path = verified_file(assets / 'indexes' / (index['id'] + '.json'), index['url'], index['sha1'], source=assets_source / 'indexes' / (index['id'] + '.json'))
    asset_index = json.loads(index_path.read_text(encoding='utf-8'))
    objects = {info['hash'] for info in asset_index['objects'].values()}
    print(f'{mc}: checking {len(objects)} official assets', flush=True)
    def asset(digest):
        relative = f'objects/{digest[:2]}/{digest}'
        return verified_file(assets / relative, f'https://resources.download.minecraft.net/{digest[:2]}/{digest}', digest, source=assets_source / relative)
    with ThreadPoolExecutor(max_workers=12) as pool:
        for count, _ in enumerate(pool.map(asset, sorted(objects)), 1):
            if count % 1000 == 0:
                print(f'{mc}: {count}/{len(objects)} assets verified', flush=True)
    game_assets = assets
    if asset_index.get('virtual') or asset_index.get('map_to_resources'):
        game_assets = assets / 'virtual' / index['id']
        for name, info in asset_index['objects'].items():
            dest = game_assets / name
            if not dest.resolve().is_relative_to(game_assets.resolve()):
                raise ValueError('Unsafe asset path')
            dest.parent.mkdir(parents=True, exist_ok=True)
            source = assets / f"objects/{info['hash'][:2]}/{info['hash']}"
            if not dest.exists():
                shutil.copyfile(source, dest)
        if asset_index.get('map_to_resources'):
            shutil.copytree(game_assets, run / 'resources', dirs_exist_ok=True)
    args = ['--username', 'NoMissTest', '--version', mc, '--gameDir', str(run), '--assetsDir', str(assets), '--assetIndex', index['id'], '--uuid', '00000000000000000000000000000001', '--accessToken', '0', '--userType', 'legacy', '--versionType', 'NoMiss smoke test', '--width', '854', '--height', '480']
    if 'minecraftArguments' in game:
        values = {'auth_player_name':'NoMissTest','version_name':mc,'game_directory':str(run),'assets_root':str(assets),'assets_index_name':index['id'],'auth_uuid':'00000000000000000000000000000001','auth_access_token':'0','user_properties':'{}','user_type':'legacy','game_assets':str(game_assets)}
        args = [values.get(value[2:-1], value) if value.startswith('${') else value for value in game['minecraftArguments'].split()]
    return run, natives, classpath, profile['mainClass'], args


def launch(mc, mod, java_home, cache, assets_source, seconds, ui_jdk=None):
    run, natives, classpath, main, args = prepare(mc, mod, cache, assets_source)
    extra = []
    if ui_jdk:
        from client_ui_smoke import build_ui_smoke
        extra = build_ui_smoke(mc, mod, run, cache, ui_jdk)
    elif (run / 'mods/nomiss-client-smoke.jar').exists():
        raise ValueError('A UI smoke mod exists; use --ui-jdk or a fresh run directory')
    command = [str(java_home / 'bin/java.exe'), *extra, '-Xmx2G', '-Djava.library.path=' + str(natives), '-Dorg.lwjgl.librarypath=' + str(natives), '-Dfabric.log.level=info', '-cp', os.pathsep.join(map(str, classpath)), main, *args]
    version = subprocess.run([str(java_home / 'bin/java.exe'), '-version'], capture_output=True, text=True, check=True).stderr.strip()
    (run / 'launch-command.json').write_text(json.dumps(command, indent=2) + '\n', encoding='utf-8')
    log_path = run / 'console.log'
    print(f'{mc}: launching production {mod.name} in {run}', flush=True)
    with log_path.open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT)
        try:
            return_code = process.wait(timeout=seconds)
            alive = False
        except subprocess.TimeoutExpired:
            alive = True
            process.terminate()
            try:
                return_code = process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                return_code = process.wait(timeout=15)
    contents = log_path.read_text(encoding='utf-8', errors='replace')
    ui_passed = 'NOMISS_UI_SMOKE_PASS 6' in contents if ui_jdk else None
    result = {'ui_smoke_passed':ui_passed, 'minecraft':mc, 'java_runtime':version, 'mod_sha256':hashlib.sha256(mod.read_bytes()).hexdigest(), 'survived_seconds':seconds if alive else None, 'alive_at_timeout':alive, 'exit_code':return_code, 'gameplay_tested':False, 'log':str(log_path.relative_to(ROOT))}
    (run / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2), flush=True)
    if not alive or (ui_jdk and not ui_passed):
        print(log_path.read_text(encoding='utf-8', errors='replace')[-12000:])
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('minecraft', choices=TARGETS)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=ROOT / 'backports/build/smoke-cache')
    parser.add_argument('--assets-source', type=Path, default=Path(os.environ.get('APPDATA', str(Path.home()))) / '.minecraft/assets')
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--ui-jdk', type=Path, help='Compile a separate in-client settings/picker render smoke mod with this JDK')
    a = parser.parse_args()
    if os.name != 'nt':
        parser.error('This smoke launcher currently targets Windows x64 only')
    launch(a.minecraft, a.jar.resolve(), a.java_home.resolve(), a.cache.resolve(), a.assets_source.resolve(), a.seconds, a.ui_jdk.resolve() if a.ui_jdk else None)

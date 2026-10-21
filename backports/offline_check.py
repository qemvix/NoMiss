#!/usr/bin/env python3
"""Check generated source syntax and shared rules. This does NOT validate Minecraft APIs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request

from common import ROOT
from generate_fabric import generate as modern
from generate_legacy import generate as legacy
from targets import TARGETS


def dependency(cache, name, url):
    path = cache / name
    checksum = cache / (name + '.checksum.json')
    if not checksum.exists():
        try:
            value = urllib.request.urlopen(url + '.sha256', timeout=30).read().decode().split()[0]
            algorithm = 'sha256'
        except urllib.error.HTTPError as error:
            if error.code != 404: raise
            value = urllib.request.urlopen(url + '.sha1', timeout=30).read().decode().split()[0]
            algorithm = 'sha1'
        checksum.write_text(json.dumps({'algorithm': algorithm, 'value': value.lower()}))
    reference = json.loads(checksum.read_text())
    if not path.exists():
        path.write_bytes(urllib.request.urlopen(url, timeout=30).read())
    if hashlib.new(reference['algorithm'], path.read_bytes()).hexdigest() != reference['value']:
        raise ValueError(f'Checksum mismatch: {path}')
    return path


def check(java_home, cache):
    cache.mkdir(parents=True, exist_ok=True)
    gson = dependency(cache, 'gson-2.8.9.jar', 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar')
    junit = dependency(cache, 'junit-1.14.0.jar', 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.14.0/junit-platform-console-standalone-1.14.0.jar')
    output = ROOT / 'backports/build/offline-check'
    output.mkdir(parents=True, exist_ok=True)
    results = []
    all_sources = []
    for mc, target in TARGETS.items():
        generated = output / mc / 'generated'
        (modern if target.loader == 'fabric' else legacy)(mc, generated)
        all_sources.append(str(generated / 'java'))
        classes = output / mc / 'classes'
        classes.mkdir(parents=True, exist_ok=True)
        sources = list((generated / 'java/com/nomiss/config').glob('*.java')) + list((ROOT / 'backports/tests/java').rglob('*.java'))
        selections = ['--select-class', 'com.nomiss.config.BackportConfigTest']
        if target.loader == 'legacy-fabric':
            sources += [generated / 'java/com/nomiss/legacy/LegacyTarget.java']
            sources += list((ROOT / 'backports/tests/legacy').rglob('*.java'))
            selections += ['--select-class', 'com.nomiss.legacy.LegacyTargetTest']
        with (output / mc / 'core-tests.log').open('w') as log:
            subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-cp', f'{gson}{os.pathsep}{junit}', '-d', str(classes), *map(str, sources)], check=True, stdout=log, stderr=subprocess.STDOUT)
            subprocess.run([str(java_home / 'bin/java'), '-Dnomiss.categories=' + ','.join(target.categories), '-jar', str(junit),
                            'execute', '--class-path', f'{classes}{os.pathsep}{gson}', *selections, '--fail-if-no-tests',
                            '--details', 'summary', '--reports-dir', str(output / mc / 'test-results')], check=True, stdout=log, stderr=subprocess.STDOUT)
        results.append({'minecraft': mc, 'core_tests': 7 if target.loader == 'legacy-fabric' else 4,
                        'minecraft_api_compiled': False, 'game_launched': False})
        print(f'{mc}: shared configuration tests passed; game API build pending', flush=True)
    subprocess.run([str(java_home / 'bin/java'), str(ROOT / 'backports/tests/ParseJava.java'), *all_sources], check=True)
    (output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'Checked {len(results)} generated configurations. These are not installable mod build results.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')))
    parser.add_argument('--cache', type=Path, default=ROOT / 'backports/build/check-dependencies')
    args = parser.parse_args()
    check(args.java_home.resolve(), args.cache.resolve())

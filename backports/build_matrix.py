#!/usr/bin/env python3
"""Run exact-version Gradle builds and collect audited downloads. Never publishes a release."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

from audit_jar import audit
from common import ROOT
from targets import TARGETS


def find_game_jar(cache, mc):
    loom = cache / 'caches/fabric-loom'
    if mc.startswith('26.'):
        candidates = list((loom / mc).glob('minecraft-client.jar'))
    else:
        candidates = [p for p in (loom / 'minecraftMaven').rglob('*.jar')
                      if p.parent.name.startswith(mc + '-') and p.parent.parent.name.endswith('-intermediary')
                      and not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if len(candidates) != 1:
        raise ValueError(f'Expected one production Minecraft JAR for {mc}, found {len(candidates)}. Audit manually if the Loom cache layout changed.')
    return candidates[0]


def build(versions, java_home, cache, keep_going):
    output = ROOT / 'backports/build/matrix'
    output.mkdir(parents=True, exist_ok=True)
    dist = output / 'downloads'
    dist.mkdir(exist_ok=True)
    env = os.environ.copy()
    env.update(JAVA_HOME=str(java_home), GRADLE_USER_HOME=str(cache))
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']
    results = []
    for mc in versions:
        target = TARGETS[mc]
        project = ROOT / 'backports' / ('fabric' if target.loader == 'fabric' else 'legacy')
        artifact = dist / target.filename
        # A failed rebuild must never leave an old file looking newly validated.
        artifact.unlink(missing_ok=True)
        log_path = output / f'{mc}.log'
        print(f'Building Minecraft {mc} ({target.loader})', flush=True)
        result = {'minecraft': mc, 'status': 'failed', 'log': str(log_path.relative_to(ROOT))}
        try:
            with log_path.open('w', encoding='utf-8') as log:
                wrapper = [str(ROOT / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(ROOT / 'gradlew')]
                subprocess.run([*wrapper, '-p', str(project), f'-Pminecraft_version={mc}', f'-Ppython_executable={sys.executable}',
                                'clean', 'build', '--no-daemon', '--max-workers=2', '--console=plain'],
                               env=env, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
            xml = list((project / 'build/test-results/test').glob('TEST-*.xml'))
            assert xml, 'No test reports'
            reports = [ET.parse(path).getroot() for path in xml]
            test_count = sum(int(r.attrib['tests']) - int(r.attrib.get('skipped', 0)) for r in reports)
            assert test_count >= (7 if target.loader == 'legacy-fabric' else 4), 'Missing executed tests'
            assert all(int(r.attrib.get('failures', 0)) == 0 and int(r.attrib.get('errors', 0)) == 0 for r in reports)
            source = project / 'build/libs' / target.filename
            result = audit(source, mc, find_game_jar(cache, mc)) | {'status': 'built-and-audited', 'tests_passed': test_count}
            shutil.copyfile(source, artifact)
            shutil.copytree(project / 'build/test-results', output / f'{mc}-test-results', dirs_exist_ok=True)
        except (subprocess.CalledProcessError, OSError, ValueError, AssertionError, KeyError) as error:
            result['error'] = str(error)
            print(f'{mc}: failed; see {log_path}', flush=True)
        results.append(result)
        (output / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
        if result['status'] == 'failed' and not keep_going: break
    passed = [r for r in results if r['status'] == 'built-and-audited']
    (output / 'SHA256SUMS').write_text(''.join(f'{r["sha256"]}  {r["filename"]}\n' for r in passed), encoding='utf-8')
    if len(passed) != len(versions): raise SystemExit(1)
    print(f'{len(passed)} version builds and package audits passed. Client launch/play tests remain separate.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('versions', nargs='*', choices=list(TARGETS), help='Default: every requested backport')
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')))
    parser.add_argument('--gradle-cache', type=Path, default=Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))))
    parser.add_argument('--keep-going', action='store_true')
    args = parser.parse_args()
    build(args.versions or list(TARGETS), args.java_home.resolve(), args.gradle_cache.resolve(), args.keep_going)

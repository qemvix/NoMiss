"""Publish the checksum-pinned NoMiss v1.2 release from a local PC.

Default: validate files and make a read-only plan. --publish explicitly enables
uploads. Credentials come only from MODRINTH_TOKEN or a hidden terminal prompt.
No GitHub credentials, browser sessions, or installed Minecraft data are read.
"""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'backports/build/release-staging/assets'
API = 'https://api.modrinth.com/v2'
RELEASE = 'https://github.com/qemvix/NoMiss/releases/download/v1.2/'
USER_AGENT = 'qemvix/NoMiss-publisher/1.2 (https://github.com/qemvix/NoMiss)'
VALIDATION = 'https://github.com/qemvix/NoMiss/blob/203ed4eb65c0e1ca951f4e2151aa4bbb40911c26/backports/VALIDATION.md'


class ApiError(RuntimeError):
    def __init__(self, status, message):
        self.status = status
        super().__init__(f'Modrinth HTTP {status}: {message}')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward Authorization to another host or an unexpected redirect.
        return None


class Modrinth:
    def __init__(self, token=''):
        self.token = token
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, route, data=None, content_type=None):
        if not route.startswith('/') or route.startswith('//'):
            raise ValueError('Expected an API-relative route')
        headers = {'User-Agent': USER_AGENT}
        if self.token:
            headers['Authorization'] = self.token
        if content_type:
            headers['Content-Type'] = content_type
        for attempt in range(3):
            req = urllib.request.Request(API + route, data=data, headers=headers)
            try:
                with self.opener.open(req, timeout=90) as response:
                    return json.load(response)
            except urllib.error.HTTPError as error:
                # Only GET is retried. A failed upload may already have reached
                # the server; rerunning checks the project's hashes first.
                if data is None and error.code == 429 and attempt < 2:
                    try:
                        seconds = min(30, max(1, int(error.headers.get('Retry-After', '5'))))
                    except ValueError:
                        seconds = 5
                    error.close()
                    time.sleep(seconds)
                    continue
                try:
                    message = error.read(2048).decode('utf-8', errors='replace')
                finally:
                    error.close()
                if self.token:
                    message = message.replace(self.token, '[redacted]')
                raise ApiError(error.code, message) from None


def download(name, path, expected=None):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', name):
        raise ValueError('Unsafe release asset name')
    req = urllib.request.Request(RELEASE + name, headers={'User-Agent': USER_AGENT})
    with urllib.request.urlopen(req, timeout=90) as response:
        data = response.read()
    if expected and hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'Published checksum mismatch: {name}')
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.download')
    temporary.write_bytes(data)
    temporary.replace(path)


def trusted_checksums():
    entries = {}
    for line in (ROOT / 'backports/v1.2-SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
        digest, filename = line.split()
        if not re.fullmatch('[0-9a-f]{64}', digest) or Path(filename).name != filename:
            raise ValueError('Invalid checked-in checksum list')
        if filename in entries:
            raise ValueError(f'Duplicate checksum entry: {filename}')
        entries[filename] = digest
    return entries


def load_editions(asset_dir, checksums):
    manifest_path = asset_dir / 'validation-report.json'
    if not manifest_path.exists():
        download('validation-report.json', manifest_path)
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if manifest.get('mod') != 'NoMiss' or manifest.get('version') != '1.2':
        raise ValueError('Expected the NoMiss 1.2 validation manifest')
    editions = []
    for original in manifest['editions']:
        e = dict(original)
        required_tests = 7 if e['loader'] == 'legacy-fabric' else 4
        if not e.get('package_audit_passed') or e.get('tests_passed', 0) < required_tests:
            raise ValueError(f'Unverified edition: {e["minecraft"]}')
        if checksums.get(e['filename']) != e['sha256']:
            raise ValueError(f'Manifest not pinned by repository checksums: {e["filename"]}')
        e['game_versions'] = [e['minecraft']]
        e['original'] = False
        editions.append(e)
    existing = dict(manifest['existing_26_2_26_3'])
    if checksums.get(existing['filename']) != existing['sha256']:
        raise ValueError('Original 26.2/26.3 artifact checksum changed')
    existing.update(minecraft='26.2 / 26.3', game_versions=['26.2', '26.3'],
                    loader='fabric', java=25, original=True)
    editions.append(existing)
    names = [e['filename'] for e in editions]
    if len(names) != len(set(names)) or set(names) != set(checksums):
        raise ValueError('Manifest must contain exactly the checksum-pinned release files')
    return editions


def prepare_file(e, asset_dir):
    path = asset_dir / e['filename']
    if not path.exists():
        download(e['filename'], path, e['sha256'])
    data = path.read_bytes()
    if len(data) != e['bytes'] or hashlib.sha256(data).hexdigest() != e['sha256']:
        raise ValueError(f'Local file differs from the verified release: {path.name}')
    with zipfile.ZipFile(path) as jar:
        mod = json.loads(jar.read('fabric.mod.json'))
    if mod.get('id') != 'nomiss' or mod.get('version') != '1.2':
        raise ValueError(f'Wrong mod/version in {path.name}')
    depends = mod.get('depends', {})
    if not e['original'] and depends.get('minecraft') != e['minecraft']:
        raise ValueError(f'Wrong Minecraft selector in {path.name}')
    if depends.get('java') != f'>={e["java"]}':
        raise ValueError(f'Wrong minimum Java in {path.name}')
    e['sha512'] = hashlib.sha512(data).hexdigest()
    e['path'] = str(path)
    e['loader_requirement'] = depends.get('fabricloader', '')
    e['optional_modmenu'] = 'modmenu' in mod.get('suggests', {})
    return e


def version_number(e):
    mc = '26.2-26.3' if e['original'] else e['minecraft']
    return f'1.2+mc{mc}.{e["loader"]}'


def find_existing(e, versions):
    for version in versions:
        for file in version.get('files', []):
            if file.get('hashes', {}).get('sha512') == e['sha512']:
                if (set(version['game_versions']) != set(e['game_versions']) or
                        set(version['loaders']) != {e['loader']}):
                    raise ValueError(f'Existing identical file has different compatibility tags: {e["filename"]}')
                return version
    for version in versions:
        if (version.get('version_number') == version_number(e) or
                any(f.get('filename') == e['filename'] for f in version.get('files', []))):
            raise ValueError(f'Existing version/file conflicts with {e["filename"]}; nothing is overwritten')
    return None


def make_payload(e, project_id, channel, modmenu_id=None):
    loader = 'Legacy Fabric' if e['loader'] == 'legacy-fabric' else 'Fabric'
    notes = [f'NoMiss **1.2 - the updates update** for Minecraft **{e["minecraft"]}**.',
             f'Client only. Requires **{loader} {e["loader_requirement"]}** and **Java {e["java"]}+**.',
             'The mod version remains 1.2. Cooldown protection defaults OFF.']
    if e['original']:
        notes.append('Preserves the existing verified 26.2/26.3 download unchanged.')
    else:
        notes.append(f'Compiled against this exact Minecraft release; {e["tests_passed"]} unit tests passed; production package and mixin audit passed.')
        notes.append('Client launched: ' + ('yes' if e.get('client_launched') else 'not tested for this edition') +
                     '. Settings/picker UI checks: ' + ('passed' if e.get('ui_smoke_passed') else 'not tested for this edition') + '.')
        notes.append('Full in-world gameplay testing is incomplete; do not interpret compilation or UI checks as a complete gameplay test.')
    if e['loader'] == 'legacy-fabric':
        notes.append('Legacy adaptation: vanilla Options menu and rebindable shortcut replace Accessibility/modern Mod Menu. Native paginated settings and searchable item icons are included. Pre-1.9 cooldown controls are omitted and imported cooldown settings are ignored during attacks.')
    notes.extend([f'SHA-256: `{e["sha256"]}`', f'[Detailed validation and omissions]({VALIDATION})'])
    dependencies = []
    if e['optional_modmenu'] and modmenu_id:
        dependencies.append({'project_id': modmenu_id, 'dependency_type': 'optional'})
    return {'project_id': project_id, 'name': f'1.2 - the updates update ({e["minecraft"]}, {loader})',
            'version_number': version_number(e), 'changelog': '\n\n'.join(notes),
            'game_versions': e['game_versions'], 'loaders': [e['loader']],
            'version_type': channel, 'featured': False, 'status': 'listed',
            'environment': 'client_only', 'dependencies': dependencies,
            'file_parts': ['file'], 'primary_file': 'file'}


def multipart(payload, e):
    data = Path(e['path']).read_bytes()
    # Recheck immediately before posting, in case the file changed after planning.
    if hashlib.sha256(data).hexdigest() != e['sha256']:
        raise ValueError(f'File changed after validation: {e["filename"]}')
    boundary = 'NoMiss' + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n').encode()
    body += json.dumps(payload).encode('utf-8')
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{e["filename"]}"\r\n'
             'Content-Type: application/java-archive\r\n\r\n').encode()
    body += data + f'\r\n--{boundary}--\r\n'.encode()
    return body, f'multipart/form-data; boundary={boundary}'


def verify_created(e, version, project_id):
    if version.get('project_id') != project_id:
        raise ValueError('Upload response refers to a different project')
    if find_existing(e, [version]) is None:
        raise ValueError(f'Uploaded hash not confirmed for {e["filename"]}')


def save_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def run(args, token):
    # Write before the first API call so early authentication failures cannot
    # leave an old dry-run report looking like the result of a new upload.
    report = {'project': args.project, 'mode': 'publish' if args.publish else 'dry-run',
              'channel': args.channel, 'local_files_verified': 0, 'entries': [],
              'complete': False, 'phase': 'validating_local_files',
              'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
    save_report(args.report, report)
    try:
        return _run(args, token, report)
    except Exception as error:
        message = str(error)
        if token:
            message = message.replace(token, '[redacted]')
        report.update(complete=False, error=message)
        save_report(args.report, report)
        raise


def _run(args, token, report):
    asset_dir = args.assets.resolve()
    editions = [prepare_file(e, asset_dir) for e in load_editions(asset_dir, trusted_checksums())]
    print(f'Local preflight: {len(editions)} verified JARs covering {sum(len(e["game_versions"]) for e in editions)} Minecraft releases.')
    report.update(local_files_verified=len(editions), phase='checking_modrinth_tags')
    save_report(args.report, report)
    api = Modrinth(token)
    game_versions = {x['version'] for x in api.request('/tag/game_version')}
    loaders = {x['name'] for x in api.request('/tag/loader')}
    for e in editions:
        if not set(e['game_versions']) <= game_versions or e['loader'] not in loaders:
            raise ValueError(f'Unsupported Modrinth compatibility tag: {e["filename"]}')
    report['phase'] = 'checking_project_access'
    save_report(args.report, report)
    try:
        project = api.request('/project/' + args.project)
        report['phase'] = 'checking_existing_versions'
        save_report(args.report, report)
        versions = api.request('/project/' + project['id'] + '/version')
    except ApiError as error:
        if error.status == 404 and not token and not args.publish:
            report.update(phase='authentication_required', remote_check='authentication_required', reason='Private/draft project requires its owner token.')
            for e in editions:
                report['entries'].append({'filename': e['filename'], 'sha256': e['sha256'],
                                         'metadata': make_payload(e, args.project, args.channel),
                                         'action': 'pending_authenticated_duplicate_check'})
            save_report(args.report, report)
            print('Read-only plan saved. Private project duplicate/permission checks require a token.')
            print(f'Report: {args.report}')
            return 0
        raise
    if project.get('project_type') != 'mod':
        raise ValueError('The destination must be a Modrinth mod project')
    print(f'Destination: {project["title"]} ({project["id"]}); status: {project.get("status", "unknown")}')
    modmenu_id = api.request('/project/modmenu')['id'] if any(e['optional_modmenu'] for e in editions) else None
    report['phase'] = 'planning_uploads'
    # Plan every entry before the first upload. Any conflict aborts the whole run.
    for e in editions:
        match = find_existing(e, versions)
        report['entries'].append({'filename': e['filename'], 'sha256': e['sha256'],
                                  'metadata': make_payload(e, project['id'], args.channel, modmenu_id),
                                  'action': 'skip' if match else 'upload',
                                  'version_id': match['id'] if match else None})
    save_report(args.report, report)
    for e, entry in zip(editions, report['entries']):
        print(f'{entry["action"].upper():6} {e["filename"]}')
        if entry['action'] == 'skip' or not args.publish:
            continue
        try:
            report.update(phase='uploading', current_file=e['filename'])
            save_report(args.report, report)
            data, content_type = multipart(entry['metadata'], e)
            created = api.request('/version', data, content_type)
            entry.update(action='uploaded_pending_verification', version_id=created['id'])
            save_report(args.report, report)
            # Check the server's stored bytes/tags again via a separate GET.
            verify_created(e, api.request('/version/' + created['id']), project['id'])
            entry['action'] = 'uploaded'
            save_report(args.report, report)
        except Exception:
            save_report(args.report, report)
            raise
    report.update(complete=True, phase='complete')
    report.pop('current_file', None)
    save_report(args.report, report)
    print('Upload complete.' if args.publish else 'Dry run complete. No versions were uploaded.')
    print(f'Report: {args.report}')
    return 0



def prompt_token_dialog(service="Modrinth"):
    """Use a normal masked entry so Windows Ctrl+V has visible feedback."""
    try:
        import tkinter as tk
        from tkinter import ttk
    except ImportError as error:
        raise RuntimeError('The token dialog needs tkinter. Use --prompt-token in a local terminal instead.') from error
    try:
        window = tk.Tk()
    except tk.TclError as error:
        raise RuntimeError('Cannot open the token dialog. Use --prompt-token in a local terminal instead.') from error
    window.title(f'NoMiss - {service} token')
    window.resizable(False, False)
    frame = ttk.Frame(window, padding=20)
    frame.pack(fill='both', expand=True)
    ttk.Label(frame, text=f'Paste your {service} token below with Ctrl+V.').pack(anchor='w')
    ttk.Label(frame, text='It appears as dots and is not saved.').pack(anchor='w', pady=(0, 10))
    entry = ttk.Entry(frame, width=64, show='*')
    entry.pack(fill='x')
    error_text = tk.StringVar()
    ttk.Label(frame, textvariable=error_text).pack(anchor='w', pady=(5, 0))
    result = None

    def accept(event=None):
        nonlocal result
        value = entry.get().strip()
        if not value:
            error_text.set('Paste a token first, or choose Cancel.')
            return
        result = value
        entry.delete(0, tk.END)
        window.destroy()

    def cancel(event=None):
        entry.delete(0, tk.END)
        window.destroy()

    buttons = ttk.Frame(frame)
    buttons.pack(anchor='e', pady=(10, 0))
    ttk.Button(buttons, text='Cancel', command=cancel).pack(side='left', padx=(0, 8))
    ttk.Button(buttons, text='Continue', command=accept).pack(side='left')
    window.bind('<Return>', accept)
    window.bind('<Escape>', cancel)
    window.protocol('WM_DELETE_WINDOW', cancel)
    entry.focus_set()
    window.lift()
    window.mainloop()
    if result is None:
        raise RuntimeError('Token entry cancelled; nothing was uploaded.')
    return result



def show_result(message, failed=False):
    """Keep the result visible after the token window closes."""
    try:
        import tkinter as tk
        from tkinter import messagebox
        root = tk.Tk()
        root.withdraw()
        try:
            if failed:
                messagebox.showerror('NoMiss upload stopped', message, parent=root)
            else:
                messagebox.showinfo('NoMiss upload complete', message, parent=root)
        finally:
            root.destroy()
    except Exception:
        # The console and saved report remain available if a dialog cannot open.
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', default='nomiss', help='Modrinth project slug or ID')
    parser.add_argument('--assets', type=Path, default=ASSETS)
    parser.add_argument('--report', type=Path, default=ROOT / 'backports/build/modrinth/report.json')
    parser.add_argument('--channel', choices=['release', 'beta', 'alpha'], default='release')
    parser.add_argument('--publish', action='store_true', help='Upload missing versions; default is read-only')
    token_options = parser.add_mutually_exclusive_group()
    token_options.add_argument('--prompt-token', action='store_true', help='Read a token from a hidden local terminal prompt')
    token_options.add_argument('--token-dialog', action='store_true', help='Paste a token into a masked local window (requires tkinter)')
    args = parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9_-]+', args.project):
        parser.error('--project must be a Modrinth slug or ID')
    token = os.environ.get('MODRINTH_TOKEN', '').strip()
    try:
        if args.token_dialog:
            token = prompt_token_dialog()
        elif args.prompt_token:
            # Refuse getpass's echoing fallback if no interactive terminal exists.
            with warnings.catch_warnings():
                warnings.simplefilter('error', getpass.GetPassWarning)
                token = getpass.getpass('Modrinth token (hidden; not saved): ').strip()
        if args.publish and not token:
            parser.error('Publishing requires MODRINTH_TOKEN, --token-dialog or --prompt-token. Never paste a token into chat.')
        result = run(args, token)
        if args.token_dialog and args.publish:
            show_result('All planned uploads are confirmed. Existing identical versions were skipped.\n\nReport: ' + str(args.report))
        return result
    except (ValueError, RuntimeError, OSError, EOFError, getpass.GetPassWarning, KeyError, zipfile.BadZipFile) as error:
        message = str(error)
        if token:
            message = message.replace(token, '[redacted]')
        if isinstance(error, ApiError) and error.status in (401, 403):
            message += '\nCheck the token permissions: Create Versions and private project/version read access, using the account that owns NoMiss.'
        elif isinstance(error, ApiError) and error.status == 404:
            message += '\nThis project is not visible to the token. Check its private-project access and the project slug/ID.'
        print('Stopped: ' + message, file=sys.stderr)
        error_path = args.report.with_name('last-error.txt')
        try:
            error_path.parent.mkdir(parents=True, exist_ok=True)
            error_path.write_text(message + '\n', encoding='utf-8')
            print('Error saved: ' + str(error_path), file=sys.stderr)
        except OSError:
            pass
        print('Previously uploaded versions are preserved. Rerun the same command to resume safely.', file=sys.stderr)
        if args.token_dialog:
            show_result(message + '\n\nError file: ' + str(error_path), failed=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())

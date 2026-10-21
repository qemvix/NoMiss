"""Local, checksum-pinned CurseForge publisher for NoMiss project 1756374.

Default is a read-only plan. --publish explicitly enables submissions. This
uses a CurseForge author UPLOAD TOKEN, not a CFCore developer API key.
"""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request
import uuid
import warnings
import zipfile

import publish_modrinth as shared

PROJECT_ID = 1756374
API = 'https://minecraft.curseforge.com'
TOKEN_URL = 'https://authors.curseforge.com/#/api-tokens'


class ApiError(RuntimeError):
    def __init__(self, status, message):
        self.status = status
        super().__init__(f'CurseForge HTTP {status}: {message}')


class CurseForge:
    def __init__(self, token):
        self.token = token
        self.opener = urllib.request.build_opener(shared.NoRedirect())

    def request(self, route, data=None, content_type=None):
        if not route.startswith('/api/'):
            raise ValueError('Expected a CurseForge API-relative route')
        headers = {'User-Agent': shared.USER_AGENT, 'X-Api-Token': self.token}
        if content_type:
            headers['Content-Type'] = content_type
        for attempt in range(3):
            request = urllib.request.Request(API + route, data=data, headers=headers)
            try:
                with self.opener.open(request, timeout=90) as response:
                    return json.load(response)
            except urllib.error.HTTPError as error:
                if data is None and error.code == 429 and attempt < 2:
                    try:
                        delay = min(30, max(1, int(error.headers.get('Retry-After', '5'))))
                    except ValueError:
                        delay = 5
                    error.close()
                    time.sleep(delay)
                    continue
                try:
                    message = error.read(2048).decode('utf-8', errors='replace')
                finally:
                    error.close()
                if self.token:
                    message = message.replace(self.token, '[redacted]')
                raise ApiError(error.code, message) from None


def tag_id(tags, name, required=True):
    matches = {tag['id'] for tag in tags if tag.get('name') == name}
    if len(matches) > 1:
        raise ValueError(f'Ambiguous CurseForge version tag: {name}')
    if not matches:
        if required:
            raise ValueError(f'CurseForge does not list the required tag: {name}')
        return None
    value = matches.pop()
    if type(value) is not int or value <= 0:
        raise ValueError(f'Invalid CurseForge tag ID for {name}')
    return value


def make_metadata(edition, tags, channel):
    # Reuse the exact edition's audited provenance, requirements, and honest
    # gameplay limitations without making any request to Modrinth.
    common = shared.make_payload(edition, 'unused', channel)
    required = [tag_id(tags, version) for version in edition['game_versions']]
    legacy_tag = tag_id(tags, 'Legacy Fabric', required=False) if edition['loader'] == 'legacy-fabric' else None
    loader_tag = legacy_tag or tag_id(tags, 'Fabric')
    required.append(loader_tag)
    for optional in [f'Java {edition["java"]}', 'Client']:
        value = tag_id(tags, optional, required=False)
        if value is not None:
            required.append(value)
    notes = common['changelog']
    if edition['loader'] == 'legacy-fabric' and legacy_tag is None:
        notes += '\n\nCurseForge catalogs this edition under Fabric. Install Legacy Fabric for this old Minecraft release, requiring Java 17+; this is not an edition for modern Minecraft.'
    metadata = {'displayName': common['name'], 'changelog': notes,
                'changelogType': 'markdown', 'gameVersions': sorted(set(required)),
                'releaseType': channel}
    if edition['optional_modmenu']:
        metadata['relations'] = {'projects': [{'slug': 'modmenu', 'type': 'optionalDependency'}]}
    return metadata


def multipart(metadata, edition):
    data = Path(edition['path']).read_bytes()
    if hashlib.sha256(data).hexdigest() != edition['sha256']:
        raise ValueError(f'File changed after validation: {edition["filename"]}')
    boundary = 'NoMissCurseForge' + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
            'Content-Type: application/json\r\n\r\n').encode()
    body += json.dumps(metadata).encode('utf-8')
    body += (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{edition["filename"]}"\r\n'
             'Content-Type: application/java-archive\r\n\r\n').encode()
    body += data + f'\r\n--{boundary}--\r\n'.encode()
    return body, f'multipart/form-data; boundary={boundary}'


def load_journal(path, project_id):
    if not path.exists():
        return {'project_id': project_id, 'files': {}}
    journal = json.loads(path.read_text(encoding='utf-8'))
    if journal.get('project_id') != project_id or not isinstance(journal.get('files'), dict):
        raise ValueError('The upload journal belongs to a different CurseForge project or is invalid')
    return journal


def existing_record(journal, edition, metadata):
    record = journal['files'].get(edition['filename'])
    if record is None:
        return None
    if record.get('sha256') != edition['sha256']:
        raise ValueError(f'Journal checksum conflict: {edition["filename"]}')
    if record.get('state') in ('sending', 'uncertain'):
        raise ValueError(f'Previous upload outcome is uncertain for {edition["filename"]}. Check the CurseForge author dashboard before retrying; automatic resubmission is stopped to avoid duplicates. Journal: backports/build/curseforge/journal.json')
    if record.get('state') == 'accepted':
        if (type(record.get('file_id')) is not int or record['file_id'] <= 0 or
                record.get('gameVersions') != metadata['gameVersions']):
            raise ValueError(f'Journal file ID/compatibility conflict: {edition["filename"]}')
        return record
    if record.get('state') != 'rejected':
        raise ValueError(f'Unknown journal state for {edition["filename"]}')
    return None


def run(args, token):
    report = {'project_id': args.project_id, 'project_url': 'https://www.curseforge.com/minecraft/mc-mods/nomiss',
              'mode': 'publish' if args.publish else 'dry-run', 'complete': False,
              'phase': 'validating_local_files', 'entries': [],
              'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
    shared.save_report(args.report, report)
    try:
        return _run(args, token, report)
    except Exception as error:
        message = str(error)
        if token:
            message = message.replace(token, '[redacted]')
        report.update(complete=False, error=message)
        shared.save_report(args.report, report)
        raise


def _run(args, token, report):
    editions = [shared.prepare_file(e, args.assets.resolve())
                for e in shared.load_editions(args.assets.resolve(), shared.trusted_checksums())]
    report['local_files_verified'] = len(editions)
    print(f'Local preflight: {len(editions)} checksum-verified JARs, project {args.project_id}.')
    if not token:
        report['phase'] = 'authentication_required'
        report['entries'] = [{'filename': e['filename'], 'sha256': e['sha256'],
                              'minecraft': e['game_versions'], 'loader': e['loader'],
                              'action': 'pending_authenticated_tag_check'} for e in editions]
        shared.save_report(args.report, report)
        print('Read-only plan saved. CurseForge requires an upload token to resolve version tags.')
        print(f'Report: {args.report}')
        return 0
    report['phase'] = 'checking_curseforge_tags'
    shared.save_report(args.report, report)
    api = CurseForge(token)
    tags = api.request('/api/game/versions')
    if not isinstance(tags, list):
        raise ValueError('CurseForge returned an unexpected game-version response')
    journal = load_journal(args.journal, args.project_id)
    report['phase'] = 'planning_submissions'
    # Build the entire plan and detect every journal/tag conflict before POST.
    for edition in editions:
        metadata = make_metadata(edition, tags, args.channel)
        match = existing_record(journal, edition, metadata)
        report['entries'].append({'filename': edition['filename'], 'sha256': edition['sha256'],
                                  'metadata': metadata, 'action': 'skip_recorded' if match else 'submit',
                                  'file_id': match['file_id'] if match else None})
    shared.save_report(args.report, report)
    for edition, entry in zip(editions, report['entries']):
        print(f'{entry["action"].upper():13} {edition["filename"]}')
        if entry['action'] == 'skip_recorded' or not args.publish:
            continue
        data, content_type = multipart(entry['metadata'], edition)
        record = {'sha256': edition['sha256'], 'gameVersions': entry['metadata']['gameVersions'],
                  'state': 'sending'}
        journal['files'][edition['filename']] = record
        # Persist intent before POST. A crash after sending but before receiving
        # the ID must not cause a duplicate upload on the next run.
        shared.save_report(args.journal, journal)
        report.update(phase='submitting', current_file=edition['filename'])
        shared.save_report(args.report, report)
        try:
            result = api.request(f'/api/projects/{args.project_id}/upload-file', data, content_type)
            file_id = result.get('id') if isinstance(result, dict) else None
            if type(file_id) is not int or file_id <= 0:
                raise ValueError('CurseForge did not return a valid submitted file ID')
        except Exception as error:
            record['state'] = 'rejected' if isinstance(error, ApiError) and 400 <= error.status < 500 else 'uncertain'
            shared.save_report(args.journal, journal)
            raise
        record.update(state='accepted', file_id=file_id)
        shared.save_report(args.journal, journal)
        entry.update(action='accepted_for_review', file_id=file_id)
        shared.save_report(args.report, report)
    report.update(complete=True, phase='complete')
    report.pop('current_file', None)
    shared.save_report(args.report, report)
    print('Submission complete. CurseForge approval is separate.' if args.publish else 'Dry run complete. No files were submitted.')
    print(f'Report: {args.report}')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project-id', type=int, default=PROJECT_ID)
    parser.add_argument('--assets', type=Path, default=shared.ASSETS)
    parser.add_argument('--report', type=Path, default=shared.ROOT / 'backports/build/curseforge/report.json')
    parser.add_argument('--journal', type=Path, default=shared.ROOT / 'backports/build/curseforge/journal.json')
    parser.add_argument('--channel', choices=['release', 'beta', 'alpha'], default='release')
    parser.add_argument('--publish', action='store_true')
    options = parser.add_mutually_exclusive_group()
    options.add_argument('--token-dialog', action='store_true')
    options.add_argument('--prompt-token', action='store_true')
    args = parser.parse_args()
    if args.project_id <= 0:
        parser.error('Project ID must be a positive integer')
    token = os.environ.get('CURSEFORGE_TOKEN', '').strip()
    try:
        if args.token_dialog:
            token = shared.prompt_token_dialog('CurseForge upload')
        elif args.prompt_token:
            with warnings.catch_warnings():
                warnings.simplefilter('error', getpass.GetPassWarning)
                token = getpass.getpass('CurseForge upload token (hidden; not saved): ').strip()
        if args.publish and not token:
            raise ValueError('Publishing requires a CurseForge upload token from ' + TOKEN_URL)
        result = run(args, token)
        if args.token_dialog and args.publish:
            shared.show_result('CurseForge accepted the planned submissions or they were already recorded. Files may still be awaiting moderation.\n\nReport: ' + str(args.report))
        return result
    except (ValueError, RuntimeError, OSError, EOFError, getpass.GetPassWarning, KeyError, zipfile.BadZipFile) as error:
        message = str(error)
        if token:
            message = message.replace(token, '[redacted]')
        if isinstance(error, ApiError) and error.status in (401, 403):
            message += '\nUse a CurseForge author UPLOAD TOKEN, not a Modrinth token or CFCore developer key. The account must be allowed to upload to project ' + str(args.project_id) + '.'
        error_path = args.report.with_name('last-error.txt')
        try:
            error_path.parent.mkdir(parents=True, exist_ok=True)
            error_path.write_text(message + '\n', encoding='utf-8')
        except OSError:
            pass
        print('Stopped: ' + message, file=sys.stderr)
        print('Error file: ' + str(error_path), file=sys.stderr)
        if args.token_dialog:
            shared.show_result(message + '\n\nError file: ' + str(error_path), failed=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())

"""Publishing safety checks; all write requests use a fake Modrinth server."""
import copy
from email.parser import BytesParser
from email.policy import default
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_modrinth as publisher


class ModrinthPublishingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.edition = dict(minecraft='1.21.11', game_versions=['1.21.11'], loader='fabric',
                            java=21, original=False, filename='nomiss-1.21.11-fabric-1.2.jar',
                            tests_passed=4, package_audit_passed=True, optional_modmenu=True,
                            loader_requirement='>=0.19.5', client_launched=True, ui_smoke_passed=True)
        self.jar(self.edition)

    def jar(self, edition, **metadata):
        path = self.root / edition['filename']
        with zipfile.ZipFile(path, 'w') as jar:
            mod = dict(id='nomiss', version='1.2', depends={'minecraft': edition['minecraft'],
                       'java': f'>={edition["java"]}', 'fabricloader': '>=0.19.5'})
            mod.update(metadata)
            jar.writestr('fabric.mod.json', json.dumps(mod))
        data = path.read_bytes()
        edition.update(path=str(path), bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                       sha512=hashlib.sha512(data).hexdigest())

    def existing(self, edition=None):
        e = edition or self.edition
        return dict(id='Version1', project_id='Project1', version_number=publisher.version_number(e),
                    game_versions=e['game_versions'], loaders=[e['loader']],
                    files=[{'filename': e['filename'], 'hashes': {'sha512': e['sha512']}}])

    def test_manifest_cannot_add_unpinned_artifact(self):
        edition = dict(self.edition, sha256='untrusted')
        manifest = {'mod': 'NoMiss', 'version': '1.2', 'editions': [edition]}
        (self.root/'validation-report.json').write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'not pinned'):
            publisher.load_editions(self.root, {edition['filename']: 'trusted'})

    def test_manifest_cannot_publish_failed_or_zero_test_build(self):
        for changes in [{'package_audit_passed': False}, {'tests_passed': 0}]:
            edition = dict(self.edition, **changes)
            manifest = {'mod': 'NoMiss', 'version': '1.2', 'editions': [edition]}
            (self.root/'validation-report.json').write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, 'Unverified edition'):
                publisher.load_editions(self.root, {edition['filename']: edition['sha256']})

    def test_matching_bytes_skip_even_if_original_version_name_differs(self):
        existing = self.existing()
        existing['version_number'] = '1.2'
        self.assertEqual(publisher.find_existing(self.edition, [existing]), existing)

    def test_identical_bytes_with_wrong_game_tags_stop(self):
        existing = self.existing()
        existing['game_versions'] = ['1.21.10']
        with self.assertRaisesRegex(ValueError, 'compatibility tags'):
            publisher.find_existing(self.edition, [existing])

    def test_filename_collision_with_different_bytes_stops(self):
        existing = self.existing()
        existing['version_number'] = 'other'
        existing['files'][0]['hashes']['sha512'] = 'changed'
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            publisher.find_existing(self.edition, [existing])

    def test_version_number_collision_with_different_bytes_stops(self):
        existing = self.existing()
        existing['files'] = []
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            publisher.find_existing(self.edition, [existing])

    def test_changed_local_file_is_rejected(self):
        Path(self.edition['path']).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'differs from'):
            publisher.prepare_file(self.edition, self.root)

    def test_wrong_exact_minecraft_selector_is_rejected(self):
        self.jar(self.edition, depends={'minecraft': '1.21.10', 'java': '>=21'})
        with self.assertRaisesRegex(ValueError, 'Minecraft selector'):
            publisher.prepare_file(self.edition, self.root)

    def test_wrong_java_requirement_is_rejected(self):
        self.jar(self.edition, depends={'minecraft': '1.21.11', 'java': '>=17'})
        with self.assertRaisesRegex(ValueError, 'minimum Java'):
            publisher.prepare_file(self.edition, self.root)

    def test_modern_payload_keeps_version_and_validation_limits(self):
        data = publisher.make_payload(self.edition, 'Project1', 'release', 'ModMenu1')
        self.assertEqual(data['game_versions'], ['1.21.11'])
        self.assertEqual(data['loaders'], ['fabric'])
        self.assertTrue(data['version_number'].startswith('1.2+'))
        self.assertEqual(data['environment'], 'client_only')
        self.assertEqual(data['dependencies'], [{'project_id': 'ModMenu1', 'dependency_type': 'optional'}])
        self.assertIn('gameplay testing is incomplete', data['changelog'])
        self.assertIn('Java 21+', data['changelog'])

    def test_legacy_payload_has_separate_loader_and_adaptations(self):
        edition = dict(self.edition, loader='legacy-fabric', java=17, optional_modmenu=False)
        data = publisher.make_payload(edition, 'Project1', 'release')
        self.assertEqual(data['loaders'], ['legacy-fabric'])
        self.assertEqual(data['dependencies'], [])
        self.assertIn('cooldown settings are ignored', data['changelog'])
        self.assertIn('vanilla Options menu', data['changelog'])

    def test_multipart_contains_exact_jar_bytes_and_json(self):
        payload = publisher.make_payload(self.edition, 'Project1', 'release')
        body, content_type = publisher.multipart(payload, self.edition)
        message = BytesParser(policy=default).parsebytes(
            f'Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n'.encode() + body)
        parts = list(message.iter_parts())
        self.assertEqual(len(parts), 2)
        self.assertEqual(json.loads(parts[0].get_payload(decode=True)), payload)
        self.assertEqual(parts[1].get_filename(), self.edition['filename'])
        self.assertEqual(parts[1].get_payload(decode=True), Path(self.edition['path']).read_bytes())

    def test_file_changed_after_plan_is_not_uploaded(self):
        Path(self.edition['path']).write_bytes(b'changed after plan')
        with self.assertRaisesRegex(ValueError, 'changed after validation'):
            publisher.multipart({}, self.edition)

    def test_upload_response_must_confirm_hash_and_project(self):
        response = self.existing()
        response['project_id'] = 'SomeoneElse'
        with self.assertRaisesRegex(ValueError, 'different project'):
            publisher.verify_created(self.edition, response, 'Project1')
        response = self.existing()
        response['files'][0]['hashes']['sha512'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            publisher.verify_created(self.edition, response, 'Project1')

    def test_post_is_never_blindly_retried_and_token_is_redacted(self):
        api = publisher.Modrinth('secret-token')
        class FailingOpener:
            calls = 0
            def open(self, req, timeout):
                self.calls += 1
                raise urllib.error.HTTPError(req.full_url, 503, 'Unavailable', {}, io.BytesIO(b'secret-token'))
        api.opener = FailingOpener()
        with self.assertRaisesRegex(publisher.ApiError, r'\[redacted\]'):
            api.request('/version', b'body')
        self.assertEqual(api.opener.calls, 1)

    def fake_api(self, editions, versions=None):
        test = self
        class FakeApi:
            def __init__(self):
                self.versions = versions or []
                self.posts = 0
            def request(self, route, data=None, content_type=None):
                if route == '/tag/game_version':
                    return [{'version': e['minecraft']} for e in editions]
                if route == '/tag/loader':
                    return [{'name': e['loader']} for e in editions]
                if route == '/project/nomiss':
                    return dict(id='Project1', project_type='mod', title='NoMiss', status='draft')
                if route == '/project/Project1/version':
                    return self.versions
                if route == '/version' and data:
                    self.posts += 1
                    created = test.existing(editions[self.posts-1])
                    created['id'] = 'Version' + str(self.posts)
                    self.versions.append(created)
                    return created
                if route.startswith('/version/'):
                    return next(v for v in self.versions if v['id'] == route.rsplit('/',1)[1])
                raise AssertionError(route)
        return FakeApi()

    def run_with_fake(self, editions, api, publish):
        args = SimpleNamespace(assets=self.root, project='nomiss', channel='release',
                               publish=publish, report=self.root/'report.json')
        with patch.object(publisher, 'Modrinth', return_value=api), \
             patch.object(publisher, 'load_editions', return_value=copy.deepcopy(editions)), \
             patch.object(publisher, 'trusted_checksums', return_value={}), \
             patch.object(publisher, 'prepare_file', side_effect=lambda e, _:e):
            return publisher.run(args, 'test-token')

    def test_dry_run_never_posts(self):
        edition = dict(self.edition, optional_modmenu=False)
        api = self.fake_api([edition])
        self.run_with_fake([edition], api, publish=False)
        self.assertEqual(api.posts, 0)

    def test_second_run_after_upload_skips_existing_version(self):
        edition = dict(self.edition, optional_modmenu=False)
        api = self.fake_api([edition])
        self.run_with_fake([edition], api, publish=True)
        self.run_with_fake([edition], api, publish=True)
        self.assertEqual(api.posts, 1)
        self.assertEqual(json.loads((self.root/'report.json').read_text())['entries'][0]['action'], 'skip')

    def test_later_conflict_stops_before_any_upload(self):
        first = dict(self.edition, optional_modmenu=False)
        second = dict(first, minecraft='1.21.10', game_versions=['1.21.10'], filename='other.jar', sha512='second')
        conflict = self.existing(second)
        conflict['files'][0]['hashes']['sha512'] = 'old-bytes'
        api = self.fake_api([first, second], [conflict])
        with self.assertRaisesRegex(ValueError, 'conflicts'):
            self.run_with_fake([first, second], api, publish=True)
        self.assertEqual(api.posts, 0)


if __name__ == '__main__':
    unittest.main()

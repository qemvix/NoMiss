"""CurseForge upload safety tests; writes use a fake author upload API."""
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

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_curseforge as publisher


class CurseForgePublishingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root/'nomiss-1.21.11-fabric-1.2.jar'
        self.path.write_bytes(b'synthetic bytes; production JARs have their own pinned hash')
        self.edition = dict(filename=self.path.name, path=str(self.path),
                            sha256=hashlib.sha256(self.path.read_bytes()).hexdigest(),
                            minecraft='1.21.11', game_versions=['1.21.11'], loader='fabric', java=21,
                            tests_passed=4, original=False, optional_modmenu=True, loader_requirement='>=0.19.5')
        self.types = [
            {'id': 100, 'name': 'Minecraft 1.21', 'slug': 'minecraft-1-21'},
            {'id': 200, 'name': 'Modloader', 'slug': 'modloader'},
            {'id': 300, 'name': 'Java', 'slug': 'java'},
            {'id': 400, 'name': 'Environment', 'slug': 'environment'}]
        self.tags = [{'id': i+1, 'name': name, 'gameVersionTypeID': (i+1)*100}
                     for i,name in enumerate(['1.21.11','Fabric','Java 21','Client'])]
        self.args = SimpleNamespace(project_id=1756374, assets=self.root,
                                    report=self.root/'report.json', journal=self.root/'journal.json',
                                    channel='release', publish=True)

    def metadata(self, edition=None, tags=None):
        return publisher.make_metadata(edition or self.edition, self.tags if tags is None else tags, 'release', self.types)

    def record(self, state='accepted'):
        return {'sha256':self.edition['sha256'], 'gameVersions':self.metadata()['gameVersions'],
                'state':state, 'file_id':12345}

    def test_modern_tags_and_optional_modmenu_are_correct(self):
        data = self.metadata()
        self.assertEqual(data['gameVersions'], [1,2,3,4])
        self.assertEqual(data['relations']['projects'], [{'slug':'modmenu','type':'optionalDependency'}])
        self.assertIn('Java 21+', data['changelog'])
        self.assertIn('gameplay testing is incomplete', data['changelog'])
        self.assertIn('1.21.11', data['displayName'])

    def test_legacy_uses_fabric_catalog_with_explicit_loader_requirement(self):
        edition = dict(self.edition, loader='legacy-fabric', java=17, optional_modmenu=False)
        data = self.metadata(edition)
        self.assertEqual(data['gameVersions'], [1,2,4])
        self.assertIn('Legacy Fabric', data['displayName'])
        self.assertIn('CurseForge catalogs this edition under Fabric', data['changelog'])
        self.assertIn('Java 17+', data['changelog'])
        self.assertNotIn('relations', data)

    def test_dedicated_legacy_tag_is_used_when_available(self):
        edition = dict(self.edition, loader='legacy-fabric', java=17, optional_modmenu=False)
        data = self.metadata(edition, self.tags+[{'id':99,'name':'Legacy Fabric','gameVersionTypeID':200}])
        self.assertIn(99,data['gameVersions'])
        self.assertNotIn(2,data['gameVersions'])

    def test_missing_java_or_client_tag_keeps_requirements_in_notes(self):
        data = self.metadata(tags=self.tags[:2])
        self.assertEqual(data['gameVersions'], [1,2])
        self.assertIn('Client only', data['changelog'])
        self.assertIn('Java 21+', data['changelog'])

    def test_missing_exact_minecraft_tag_cannot_be_replaced(self):
        with self.assertRaisesRegex(ValueError, 'required tag: 1.21.11'):
            self.metadata(tags=self.tags[1:])

    def test_ambiguous_tag_fails_closed(self):
        with self.assertRaisesRegex(ValueError,'Ambiguous'):
            self.metadata(tags=self.tags+[{'id':999,'name':'1.21.11','gameVersionTypeID':100}])

    def test_duplicate_26_1_name_uses_minecraft_family_not_bukkit(self):
        types = self.types + [
            {'id': 500, 'name': 'Minecraft 26.1', 'slug': 'minecraft-26-1'},
            {'id': 600, 'name': 'Bukkit 26.1', 'slug': 'bukkit-26-1'}]
        tags = self.tags + [
            {'id': 51, 'name': '26.1', 'gameVersionTypeID': 500},
            {'id': 61, 'name': '26.1', 'gameVersionTypeID': 600}]
        edition = dict(self.edition, minecraft='26.1', game_versions=['26.1'], java=25)
        metadata = publisher.make_metadata(edition, tags, 'release', types)
        self.assertIn(51, metadata['gameVersions'])
        self.assertNotIn(61, metadata['gameVersions'])

    def test_family_specific_type_is_preferred_over_generic_minecraft(self):
        types = self.types + [{'id': 500, 'name': 'Minecraft', 'slug': 'minecraft'}]
        tags = self.tags + [{'id': 99, 'name': '1.21.11', 'gameVersionTypeID': 500}]
        metadata = publisher.make_metadata(self.edition, tags, 'release', types)
        self.assertIn(1, metadata['gameVersions'])
        self.assertNotIn(99, metadata['gameVersions'])

    def test_missing_minecraft_scope_does_not_fall_back_to_untyped_name(self):
        with self.assertRaisesRegex(ValueError, 'required tag: 1.21.11'):
            publisher.make_metadata(self.edition, self.tags, 'release', self.types[1:])

    def test_duplicate_loader_java_environment_names_use_their_own_types(self):
        tags = self.tags + [
            {'id': 91, 'name': 'Fabric', 'gameVersionTypeID': 100},
            {'id': 92, 'name': 'Java 21', 'gameVersionTypeID': 100},
            {'id': 93, 'name': 'Client', 'gameVersionTypeID': 100}]
        self.assertEqual(self.metadata(tags=tags)['gameVersions'], [1,2,3,4])

    def test_calendar_year_family_is_valid_without_matching_bukkit(self):
        types = [
            {'id': 500, 'name': 'Minecraft 26', 'slug': 'minecraft-26'},
            {'id': 600, 'name': 'Bukkit 26', 'slug': 'bukkit-26'}]
        self.assertEqual(publisher.scoped_type_ids(types, 'minecraft', '26.1'), {500})

    def test_patch_release_uses_parent_minecraft_family(self):
        types = [{'id': 500, 'name': 'Minecraft 26.1', 'slug': 'minecraft-26-1'}]
        self.assertEqual(publisher.scoped_type_ids(types, 'minecraft', '26.1.2'), {500})

    def test_authenticated_run_saves_only_nonsecret_version_taxonomy(self):
        api = self.fake_api()
        self.args.publish = False
        self.run_fake(api)
        text = (self.root/'version-tags.json').read_text()
        saved = json.loads(text)
        self.assertEqual(saved['version_types'], self.types)
        self.assertEqual(saved['versions'], self.tags)
        self.assertNotIn('test-token', text)

    def test_multipart_uses_metadata_part_and_exact_bytes(self):
        metadata=self.metadata()
        data,content_type=publisher.multipart(metadata,self.edition)
        message=BytesParser(policy=default).parsebytes(f'Content-Type: {content_type}\r\n\r\n'.encode()+data)
        parts=list(message.iter_parts())
        self.assertEqual(parts[0].get_param('name',header='content-disposition'),'metadata')
        self.assertEqual(json.loads(parts[0].get_payload(decode=True)),metadata)
        self.assertEqual(parts[1].get_filename(),self.path.name)
        self.assertEqual(parts[1].get_payload(decode=True),self.path.read_bytes())

    def test_changed_jar_is_rejected_before_upload(self):
        self.path.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'changed after validation'):
            publisher.multipart(self.metadata(),self.edition)

    def test_journal_for_another_project_is_rejected(self):
        self.args.journal.write_text(json.dumps({'project_id':1,'files':{}}))
        with self.assertRaisesRegex(ValueError,'different CurseForge project'):
            publisher.load_journal(self.args.journal,1756374)

    def test_confirmed_record_is_skipped(self):
        record=self.record()
        journal={'files':{self.path.name:record}}
        self.assertEqual(publisher.existing_record(journal,self.edition,self.metadata()),record)

    def test_journal_hash_and_compatibility_conflicts_are_rejected(self):
        for change in [{'sha256':'other'},{'gameVersions':[999]}]:
            record=self.record();record.update(change)
            with self.assertRaisesRegex(ValueError,'conflict'):
                publisher.existing_record({'files':{self.path.name:record}},self.edition,self.metadata())

    def test_uncertain_and_interrupted_records_cannot_auto_resubmit(self):
        for state in ['sending','uncertain']:
            with self.assertRaisesRegex(ValueError,'uncertain'):
                publisher.existing_record({'files':{self.path.name:self.record(state)}},self.edition,self.metadata())

    def fake_api(self):
        test=self
        class FakeApi:
            def __init__(self):
                self.posts=0
                self.failure=None
                self.response={'id':12345}
            def request(self,route,data=None,content_type=None):
                if route=='/api/game/versions':
                    return test.tags
                if route=='/api/game/version-types':
                    return test.types
                if route=='/api/projects/1756374/upload-file' and data:
                    self.posts+=1
                    if self.failure:
                        raise self.failure
                    return self.response
                raise AssertionError(route)
        return FakeApi()

    def run_fake(self,api,token='test-token',editions=None):
        with patch.object(publisher,'CurseForge',return_value=api), \
             patch.object(publisher.shared,'load_editions',return_value=copy.deepcopy(editions or [self.edition])), \
             patch.object(publisher.shared,'prepare_file',side_effect=lambda e,_:e), \
             patch.object(publisher.shared,'trusted_checksums',return_value={}):
            return publisher.run(self.args,token)

    def test_repeated_run_skips_confirmed_submission(self):
        api=self.fake_api()
        self.run_fake(api)
        self.run_fake(api)
        self.assertEqual(api.posts,1)
        report=json.loads(self.args.report.read_text())
        self.assertTrue(report['complete'])
        self.assertEqual(report['entries'][0]['action'],'skip_recorded')

    def test_dry_run_never_posts(self):
        self.args.publish=False
        api=self.fake_api()
        self.run_fake(api)
        self.assertEqual(api.posts,0)
        self.assertFalse(self.args.journal.exists())

    def test_unauthenticated_dry_run_saves_pending_checks(self):
        self.args.publish=False
        api=self.fake_api()
        self.run_fake(api,token='')
        report=json.loads(self.args.report.read_text())
        self.assertEqual(report['phase'],'authentication_required')
        self.assertFalse(report['complete'])
        self.assertEqual(api.posts,0)

    def test_later_unavailable_tag_prevents_all_submissions(self):
        api=self.fake_api()
        other=dict(self.edition,filename='other.jar',minecraft='1.21.10',game_versions=['1.21.10'])
        with self.assertRaisesRegex(ValueError,'1.21.10'):
            self.run_fake(api,editions=[self.edition,other])
        self.assertEqual(api.posts,0)

    def test_server_error_leaves_uncertain_journal_and_blocks_retry(self):
        api=self.fake_api()
        api.failure=publisher.ApiError(503,'test-token server failure')
        with self.assertRaises(publisher.ApiError):
            self.run_fake(api)
        self.assertNotIn('test-token',self.args.report.read_text())
        self.assertEqual(json.loads(self.args.journal.read_text())['files'][self.path.name]['state'],'uncertain')
        with self.assertRaisesRegex(ValueError,'uncertain'):
            self.run_fake(api)
        self.assertEqual(api.posts,1)

    def test_explicit_rejection_can_be_retried(self):
        api=self.fake_api();api.failure=publisher.ApiError(400,'invalid request')
        with self.assertRaises(publisher.ApiError):
            self.run_fake(api)
        self.assertEqual(json.loads(self.args.journal.read_text())['files'][self.path.name]['state'],'rejected')
        api.failure=None
        self.run_fake(api)
        self.assertEqual(api.posts,2)

    def test_invalid_file_id_cannot_claim_success(self):
        api=self.fake_api();api.response={}
        with self.assertRaisesRegex(ValueError,'valid submitted file ID'):
            self.run_fake(api)
        report=json.loads(self.args.report.read_text())
        self.assertFalse(report['complete'])
        self.assertEqual(json.loads(self.args.journal.read_text())['files'][self.path.name]['state'],'uncertain')

    def test_api_uses_token_header_without_url_secret_and_no_post_retry(self):
        api=publisher.CurseForge('example-private-token')
        class FailingOpener:
            calls=0
            def open(self,request,timeout):
                self.calls+=1
                self.header=request.get_header('X-api-token')
                self.url=request.full_url
                raise urllib.error.HTTPError(request.full_url,503,'Unavailable',{},io.BytesIO(b'example-private-token'))
        api.opener=FailingOpener()
        with self.assertRaisesRegex(publisher.ApiError,r'\[redacted\]'):
            api.request('/api/projects/1756374/upload-file',b'body')
        self.assertEqual(api.opener.calls,1)
        self.assertEqual(api.opener.header,'example-private-token')
        self.assertNotIn('example-private-token',api.opener.url)


if __name__=='__main__':
    unittest.main()

"""Independent verification of pinned data-repository publication fixtures."""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'contracts/fixtures/official-v1'

class OfficialContractTests(unittest.TestCase):
    def setUp(self):
        self.vectors = json.loads((BASE/'vectors.json').read_text(encoding='utf-8'))

    def test_fixtures_match_exact_upstream_output_and_complete_digest(self):
        for kind in ('empty', 'synthetic'):
            files = self.vectors[kind]['files']
            for path, raw in files.items():
                self.assertEqual(raw, (BASE/kind/path).read_text(encoding='utf-8'))
            read = lambda path: json.loads(files[path])
            payload = {'catalogue':read('catalog/v1/catalogue.json'), 'streamDoc':read('catalog/v1/streams.json'),
                       'localeDoc':{l:read(f'catalog/v1/locales/{l}.json') for l in ('en','ta','sa')},
                       'schemaDoc':{k:read(f'schema/{k}-v1.schema.json') for k in ('catalogue','streams')}}
            digest = hashlib.sha256(json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
            self.assertEqual(digest, read('manifest.json')['contentSha256'])

    def test_language_identity_number_and_eligibility_projection(self):
        files = self.vectors['synthetic']['files']
        channels = json.loads(files['catalog/v1/catalogue.json'])['channels']
        streams = {s['id']:s for s in json.loads(files['catalog/v1/streams.json'])['streams']}
        projection = []
        for c in channels:
            joined = [streams[s] for s in c['streamIds']]
            selected = next((s for s in joined if s['playbackStatus']=='verified'), joined[0])
            projection.append({'id':c['id'], 'number':c['number'],
                               'languages':[{'tam':'ta','eng':'en','san':'sa'}.get(l,l) for l in c['languages']],
                               'streamIds':[s['id'] for s in joined], 'statuses':[s['playbackStatus'] for s in joined],
                               'playable':selected['id'], 'trial':selected['playbackStatus']=='experimental'})
        self.assertEqual(projection, self.vectors['synthetic']['expected'])

    def test_actual_empty_publication_and_ui_locales(self):
        files = self.vectors['empty']['files']
        self.assertEqual(json.loads(files['catalog/v1/catalogue.json'])['channels'], [])
        self.assertEqual(json.loads(files['catalog/v1/streams.json'])['streams'], [])
        self.assertEqual(json.loads(files['manifest.json'])['uiLocales'], ['en','ta','sa'])
        for sha in self.vectors['provenance'].values():
            self.assertRegex(sha, r'^[a-f0-9]{40}$')

    def test_production_manifest_configuration(self):
        config = json.loads((ROOT/'app-config.json').read_text(encoding='utf-8'))
        self.assertEqual(config['catalogueManifestUrl'], 'https://actionanand.github.io/vizhi-tivi-data/manifest.json')

    def test_external_schemas_are_exact_upstream_copies(self):
        for kind in ('catalogue', 'streams'):
            path = f'{kind}-v1.schema.json'
            self.assertEqual((ROOT/'contracts'/path).read_bytes(), (BASE/'empty/schema'/path).read_bytes())

if __name__ == '__main__': unittest.main()

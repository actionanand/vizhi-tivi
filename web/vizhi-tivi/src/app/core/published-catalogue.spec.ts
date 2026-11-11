import vectors from '../../../../../contracts/fixtures/official-v1/vectors.json';
import { Catalogue, playable } from './domain';
import {
  decodePublication,
  loadPublication,
  parsePublicationCache,
  publicationCache,
  publicationDigest,
  PublicationFiles,
  PUBLIC_FILES,
  PUBLIC_PATHS,
} from './published-catalogue';
import Ajv2020 from 'ajv/dist/2020';

const fresh = (): PublicationFiles => ({ ...vectors.synthetic.files });
async function changed(
  path: string,
  edit: (root: Record<string, unknown>) => void,
): Promise<PublicationFiles> {
  const files = fresh();
  const root: Record<string, unknown> = JSON.parse(files[path]);
  edit(root);
  files[path] = JSON.stringify(root);
  const manifest = JSON.parse(files['manifest.json']);
  manifest.contentSha256 = await publicationDigest(files);
  files['manifest.json'] = JSON.stringify(manifest);
  return files;
}
const rows = (root: Record<string, unknown>, field: string): Record<string, unknown>[] =>
  root[field] as Record<string, unknown>[];
describe('Official data repository v1 integration', () => {
  it('accepts the exact empty gh-pages publication and its digest', async () => {
    const c = await decodePublication(vectors.empty.files);
    expect(c.channels).toEqual([]);
    expect(c.revision).toBeNull();
    expect(c.contentSha256).toBe(
      '64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4',
    );
  });
  it('validates representative output against upstream published schemas', () => {
    const ajv = new Ajv2020({ strict: false, validateFormats: false });
    for (const kind of ['catalogue', 'streams'] as const) {
      const schema = JSON.parse(vectors.synthetic.files[PUBLIC_PATHS.schemas[kind]]);
      const validate = ajv.compile(schema);
      expect(validate(JSON.parse(vectors.synthetic.files[PUBLIC_PATHS[kind]]))).toBe(true);
    }
  });
  it('matches common mobile/TV/Web identity, language, numbering and eligibility expectations', async () => {
    const c = await decodePublication(vectors.synthetic.files);
    expect(
      c.channels.map((c) => ({
        id: c.id,
        number: c.number,
        languages: c.languages,
        streamIds: c.streams.map((s) => s.id),
        statuses: c.streams.map((s) => s.status),
        playable: playable(c)?.id,
        trial: playable(c)?.status === 'experimental',
      })),
    ).toEqual(vectors.synthetic.expected);
    expect(c.channels[0].provider).toBe('Synthetic fixture provider');
    expect(c.channels[0].source).toContain('https://example.org/evidence/');
    expect(c.channels[0].streams[0].rightsEvidenceUrl).toContain('https://example.org/rights/');
  });
  it('round-trips a complete validated offline cache', async () => {
    const files = parsePublicationCache(publicationCache(fresh()));
    expect(await decodePublication(files)).toEqual(await decodePublication(fresh()));
  });
  it('hashes locale and schema files as well as channel and stream documents', async () => {
    for (const path of [
      PUBLIC_PATHS.catalogue,
      PUBLIC_PATHS.streams,
      PUBLIC_PATHS.locales.ta,
      PUBLIC_PATHS.schemas.streams,
    ]) {
      const files = fresh();
      const root = JSON.parse(files[path]);
      root.tampered = true;
      files[path] = JSON.stringify(root);
      await expect(decodePublication(files)).rejects.toThrow();
    }
  });
  it('rejects unsupported manifest/catalogue/streams/schema versions', async () => {
    for (const path of ['manifest.json', PUBLIC_PATHS.catalogue, PUBLIC_PATHS.streams]) {
      const files = fresh();
      const root = JSON.parse(files[path]);
      root.schemaVersion = 2;
      files[path] = JSON.stringify(root);
      await expect(decodePublication(files)).rejects.toThrow();
    }
    const files = await changed(PUBLIC_PATHS.schemas.catalogue, (r) => {
      (r['properties'] as Record<string, Record<string, unknown>>)['schemaVersion']['const'] = 2;
    });
    await expect(decodePublication(files)).rejects.toThrow();
  });
  it('rejects duplicate channel IDs and number collisions even with a valid digest', async () => {
    for (const field of ['id', 'number']) {
      const files = await changed(PUBLIC_PATHS.catalogue, (r) => {
        rows(r, 'channels')[1][field] = rows(r, 'channels')[0][field];
      });
      await expect(decodePublication(files)).rejects.toThrow();
    }
  });
  it('rejects duplicate stream IDs', async () => {
    const files = await changed(PUBLIC_PATHS.streams, (r) => {
      rows(r, 'streams')[1]['id'] = rows(r, 'streams')[0]['id'];
    });
    await expect(decodePublication(files)).rejects.toThrow();
  });
  it('rejects missing, duplicate and cross-channel stream references', async () => {
    for (const refs of [['unknown-stream'], ['tam-a-trial', 'tam-a-trial'], ['eng-trial']]) {
      const files = await changed(PUBLIC_PATHS.catalogue, (r) => {
        rows(r, 'channels')[0]['streamIds'] = refs;
      });
      await expect(decodePublication(files)).rejects.toThrow();
    }
  });
  it('rejects orphan streams and invalid stream owners', async () => {
    const files = await changed(PUBLIC_PATHS.streams, (r) => {
      rows(r, 'streams')[0]['channelId'] = 'unknown-channel';
    });
    await expect(decodePublication(files)).rejects.toThrow();
  });
  it('rejects invalid playback status, format, date and schemes', async () => {
    for (const [key, value] of [
      ['playbackStatus', 'approved'],
      ['format', 'drm'],
      ['lastVerifiedAt', '2026-02-30'],
      ['url', 'http://example.com/live'],
    ]) {
      const files = await changed(PUBLIC_PATHS.streams, (r) => {
        rows(r, 'streams')[0][key] = value;
      });
      await expect(decodePublication(files)).rejects.toThrow();
    }
  });
  it('enforces ISO 639-3 and Tamil numbering without changing UI locale identifiers', async () => {
    for (const language of ['ta', 'eng']) {
      const files = await changed(PUBLIC_PATHS.catalogue, (r) => {
        rows(r, 'channels')[0]['languages'] = [language];
      });
      await expect(decodePublication(files)).rejects.toThrow();
    }
  });
  it('preserves surviving official assignments across publications', async () => {
    const old = await decodePublication(fresh());
    const files = await changed(PUBLIC_PATHS.catalogue, (r) => {
      rows(r, 'channels')[0]['number'] = 8;
    });
    await expect(decodePublication(files, old)).rejects.toThrow();
  });
  it('uses digest inequality rather than an integer/lexicographic revision', async () => {
    const old = await decodePublication(fresh());
    const files = await changed(PUBLIC_PATHS.catalogue, (r) => {
      rows(r, 'channels')[0]['name'] = 'Renamed channel';
    });
    const next = await decodePublication(files, old);
    expect(next.revision).toBeNull();
    expect(next.contentSha256).not.toBe(old.contentSha256);
    expect(next.channels[0].id).toBe(old.channels[0].id);
  });
  it('rejects mismatching manifest counts', async () => {
    const files = fresh();
    const manifest = JSON.parse(files['manifest.json']);
    manifest.channelCount++;
    files['manifest.json'] = JSON.stringify(manifest);
    await expect(decodePublication(files)).rejects.toThrow();
  });
  it('fetches only the declared publication files under an override base and brackets manifest reads', async () => {
    const files = fresh();
    const urls: string[] = [];
    const endpoint = 'https://mirror.example/data/manifest.json';
    const old: Catalogue = { schemaVersion: 1, revision: null, channels: [] };
    const result = await loadPublication(endpoint, old, async (url) => {
      urls.push(url);
      return files[url.replace('https://mirror.example/data/', '')];
    });
    expect(result?.catalogue.channels.length).toBe(4);
    expect(urls.length).toBe(9);
    expect(urls[0]).toBe(endpoint);
    expect(urls.at(-1)).toBe(endpoint);
    expect(new Set(urls.map((u) => u.replace('https://mirror.example/data/', '')))).toEqual(
      new Set(PUBLIC_FILES),
    );
  });
  it('conditionally refreshes unchanged publications with one manifest request', async () => {
    const old = await decodePublication(fresh());
    let calls = 0;
    const result = await loadPublication('https://mirror.example/manifest.json', old, async () => {
      calls++;
      return fresh()['manifest.json'];
    });
    expect(result).toBeNull();
    expect(calls).toBe(1);
  });
  it('rejects a deployment that changes while files are fetched', async () => {
    const files = fresh();
    let reads = 0;
    await expect(
      loadPublication(
        'https://mirror.example/manifest.json',
        { schemaVersion: 1, revision: null, channels: [] },
        async (url) => {
          const p = new URL(url).pathname.slice(1);
          if (p === 'manifest.json' && ++reads === 2) {
            const m = JSON.parse(files[p]);
            m.contentSha256 = '0'.repeat(64);
            return JSON.stringify(m);
          }
          return files[p];
        },
      ),
    ).rejects.toThrow('changed during refresh');
  });
  it('rejects manifest path traversal before fetching a payload', async () => {
    const files = fresh();
    const m = JSON.parse(files['manifest.json']);
    m.paths.streams = '../private.json';
    let calls = 0;
    await expect(
      loadPublication(
        'https://mirror.example/manifest.json',
        { schemaVersion: 1, revision: null, channels: [] },
        async () => {
          calls++;
          return JSON.stringify(m);
        },
      ),
    ).rejects.toThrow();
    expect(calls).toBe(1);
  });
});

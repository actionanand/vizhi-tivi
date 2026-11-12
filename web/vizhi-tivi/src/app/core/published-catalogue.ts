import config from '../../../../../app-config.json';
import { Catalogue, Channel, Stream, languageCode, safeUrl, stableId } from './domain';

export const OFFICIAL_MANIFEST_URL = config.catalogueManifestUrl;
export const PUBLICATION_KEY = 'vizhi-tivi.publication.v1';
export const PUBLIC_PATHS = {
  catalogue: 'catalog/v1/catalogue.json',
  streams: 'catalog/v1/streams.json',
  schemas: {
    catalogue: 'schema/catalogue-v1.schema.json',
    streams: 'schema/streams-v1.schema.json',
  },
  locales: {
    en: 'catalog/v1/locales/en.json',
    ta: 'catalog/v1/locales/ta.json',
    sa: 'catalog/v1/locales/sa.json',
  },
} as const;
export const PUBLIC_FILES = [
  'manifest.json',
  PUBLIC_PATHS.catalogue,
  PUBLIC_PATHS.streams,
  ...Object.values(PUBLIC_PATHS.locales),
  ...Object.values(PUBLIC_PATHS.schemas),
];
export type PublicationFiles = Record<string, string>;

function obj(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected object');
  return value as Record<string, unknown>;
}
function keys(row: Record<string, unknown>, expected: string[]): void {
  if (Object.keys(row).sort().join('|') !== [...expected].sort().join('|'))
    throw Error('Incompatible fields');
}
function str(value: unknown, empty = false): string {
  if (typeof value !== 'string' || (!empty && !value.trim())) throw Error('Expected string');
  return value;
}
function integer(value: unknown, min = 0): number {
  if (!Number.isInteger(value) || (value as number) < min || (value as number) > 2147483647)
    throw Error('Invalid integer');
  return value as number;
}
function array(value: unknown): unknown[] {
  if (!Array.isArray(value) || value.length > 10000) throw Error('Invalid record array');
  return value;
}
function strings(value: unknown): string[] {
  return array(value).map((v) => str(v));
}
function unique(values: string[]): void {
  if (new Set(values).size !== values.length) throw Error('Duplicate reference/identity');
}
function id(value: unknown): string {
  const s = str(value);
  if (!/^[a-z0-9][a-z0-9-]{2,99}$/.test(s)) throw Error('Invalid stable ID');
  return s;
}
function https(value: unknown): string {
  const s = str(value);
  if (!/^https:\/\/[^\s\\]+$/i.test(s) || !safeUrl(s) || new URL(s).hash)
    throw Error('Expected HTTPS URL');
  return s;
}
function nullable(value: unknown): string {
  return value === null ? '' : str(value, true);
}
function version(value: unknown, field: 'channels' | 'streams'): unknown[] {
  const root = obj(value);
  keys(root, ['schemaVersion', field]);
  if (root['schemaVersion'] !== 1) throw Error('Unsupported API schema');
  return array(root[field]);
}
export function publicationManifest(raw: string): Record<string, unknown> {
  const m = obj(JSON.parse(raw));
  if (m['schemaVersion'] !== 1 || !/^[a-f0-9]{64}$/.test(str(m['contentSha256'])))
    throw Error('Unsupported manifest');
  integer(m['channelCount']);
  integer(m['streamCount']);
  if (JSON.stringify(m['uiLocales']) !== JSON.stringify(['en', 'ta', 'sa']))
    throw Error('Unsupported UI locales');
  const numbering = obj(m['numbering']);
  keys(numbering, ['tamil', 'other']);
  const tamil = obj(numbering['tamil']);
  const other = obj(numbering['other']);
  keys(tamil, ['start', 'end']);
  keys(other, ['start']);
  if (tamil['start'] !== 1 || tamil['end'] !== 250 || other['start'] !== 251)
    throw Error('Unsupported numbering policy');
  const paths = obj(m['paths']);
  keys(paths, ['catalogue', 'streams', 'locales', 'schemas']);
  for (const key of ['catalogue', 'streams'] as const)
    if (paths[key] !== PUBLIC_PATHS[key]) throw Error('Unsupported publication path');
  for (const key of ['locales', 'schemas'] as const) {
    const entries = obj(paths[key]);
    keys(entries, Object.keys(PUBLIC_PATHS[key]));
    for (const [name, path] of Object.entries(PUBLIC_PATHS[key]))
      if (entries[name] !== path) throw Error('Unsupported publication path');
  }
  return m;
}
export function parsePublicationCache(raw: string): PublicationFiles {
  const cache = obj(JSON.parse(raw));
  keys(cache, ['cacheVersion', 'files']);
  if (cache['cacheVersion'] !== 1) throw Error('Unsupported cache version');
  const files = obj(cache['files']);
  keys(files, PUBLIC_FILES);
  return Object.fromEntries(Object.entries(files).map(([k, v]) => [k, str(v)]));
}
export function publicationCache(files: PublicationFiles): string {
  return JSON.stringify({ cacheVersion: 1, files });
}

/** Hash the publisher's exact JSON.stringify wrapper, including locales and schemas. */
export async function publicationDigest(files: PublicationFiles): Promise<string> {
  const read = (path: string): unknown => JSON.parse(str(files[path]));
  return stableId(
    JSON.stringify({
      catalogue: read(PUBLIC_PATHS.catalogue),
      streamDoc: read(PUBLIC_PATHS.streams),
      localeDoc: Object.fromEntries(
        Object.entries(PUBLIC_PATHS.locales).map(([l, path]) => [l, read(path)]),
      ),
      schemaDoc: Object.fromEntries(
        Object.entries(PUBLIC_PATHS.schemas).map(([s, path]) => [s, read(path)]),
      ),
    }),
  );
}
export async function decodePublication(
  files: PublicationFiles,
  previous?: Catalogue,
): Promise<Catalogue> {
  keys(files, PUBLIC_FILES);
  if (
    Object.values(files).some((v) => typeof v !== 'string' || v.length > 5_000_000) ||
    Object.values(files).reduce((n, v) => n + v.length, 0) > 10_000_000
  )
    throw Error('Publication too large');
  const manifest = publicationManifest(files['manifest.json']);
  const catalogueRows = version(JSON.parse(files[PUBLIC_PATHS.catalogue]), 'channels');
  const streamRows = version(JSON.parse(files[PUBLIC_PATHS.streams]), 'streams');
  if (
    catalogueRows.length !== manifest['channelCount'] ||
    streamRows.length !== manifest['streamCount']
  )
    throw Error('Publication count mismatch');
  for (const path of Object.values(PUBLIC_PATHS.schemas)) {
    const schema = obj(JSON.parse(files[path]));
    if (obj(obj(schema['properties'])['schemaVersion'])['const'] !== 1)
      throw Error('Incompatible published schema');
  }
  let localeKeys: string[] | undefined;
  for (const path of Object.values(PUBLIC_PATHS.locales)) {
    const locale = obj(JSON.parse(files[path]));
    const current = Object.keys(locale).sort();
    if (
      !current.length ||
      Object.values(locale).some((v) => typeof v !== 'string' || !v.trim()) ||
      (localeKeys && JSON.stringify(current) !== JSON.stringify(localeKeys))
    )
      throw Error('Incompatible locale document');
    localeKeys = current;
  }
  if ((await publicationDigest(files)) !== manifest['contentSha256'])
    throw Error('Publication integrity mismatch');
  const streams = new Map<string, { channelId: string; stream: Stream }>();
  for (const value of streamRows) {
    const s = obj(value);
    keys(s, [
      'id',
      'channelId',
      'url',
      'format',
      'playbackStatus',
      'lastVerifiedAt',
      'rightsEvidenceUrl',
    ]);
    const streamId = id(s['id']);
    if (streams.has(streamId)) throw Error('Duplicate stream ID');
    if (
      !['hls', 'dash', 'progressive'].includes(str(s['format'])) ||
      !['verified', 'experimental'].includes(str(s['playbackStatus']))
    )
      throw Error('Unsupported playback status/format');
    const date = str(s['lastVerifiedAt']);
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      new Date(date + 'T00:00:00Z').toISOString().slice(0, 10) !== date
    )
      throw Error('Invalid verification date');
    streams.set(streamId, {
      channelId: id(s['channelId']),
      stream: {
        id: streamId,
        url: https(s['url']),
        format: s['format'] as Stream['format'],
        status: s['playbackStatus'] as Stream['status'],
        permitted: true,
        lastVerifiedAt: date,
        rightsEvidenceUrl: https(s['rightsEvidenceUrl']),
      },
    });
  }
  const ids = new Set<string>();
  const numbers = new Set<number>();
  const usedStreams = new Set<string>();
  const channels: Channel[] = catalogueRows.map((value) => {
    const c = obj(value);
    keys(c, [
      'id',
      'number',
      'name',
      'languages',
      'country',
      'region',
      'category',
      'broadcaster',
      'website',
      'logoUrl',
      'sourceUrls',
      'streamIds',
    ]);
    const channelId = id(c['id']);
    const number = integer(c['number'], 1);
    if (ids.has(channelId) || numbers.has(number)) throw Error('Duplicate channel ID/number');
    ids.add(channelId);
    numbers.add(number);
    const languages = strings(c['languages']);
    unique(languages);
    if (
      !languages.length ||
      languages.some((l) => !/^[a-z]{3}$/.test(l)) ||
      (languages.includes('tam') ? number > 250 : number < 251)
    )
      throw Error('Invalid language/number range');
    const refs = strings(c['streamIds']);
    unique(refs);
    const joined = refs.map((ref) => {
      const found = streams.get(ref);
      if (!found || found.channelId !== channelId || usedStreams.has(ref))
        throw Error('Invalid stream reference');
      usedStreams.add(ref);
      return found.stream;
    });
    const source = strings(c['sourceUrls']);
    if (!source.length) throw Error('Missing provenance');
    source.forEach(https);
    https(c['website']);
    const logo = nullable(c['logoUrl']);
    if (logo) https(logo);
    const country = str(c['country']);
    if (!/^[A-Z]{2}$/.test(country)) throw Error('Invalid country');
    const category = str(c['category']);
    if (
      ![
        'general',
        'news',
        'sports',
        'music',
        'kids',
        'entertainment',
        'education',
        'culture',
        'religious',
        'other',
      ].includes(category)
    )
      throw Error('Invalid category');
    const old = previous?.contentSha256
      ? previous.channels.find((old) => old.id === channelId)
      : undefined;
    if (old && old.number !== number) throw Error('Surviving published assignment changed');
    return {
      id: channelId,
      number,
      name: str(c['name']),
      languages: languages.map(languageCode),
      country,
      region: nullable(c['region']),
      category,
      provider: nullable(c['broadcaster']),
      source: source.join('\n'),
      logo,
      streams: joined,
    };
  });
  if (usedStreams.size !== streams.size) throw Error('Orphan stream record');
  return {
    schemaVersion: 1,
    revision: null,
    contentSha256: str(manifest['contentSha256']),
    channels,
  };
}

/** Bracket all files with manifest reads: no mixed deployment can enter the cache. */
export async function loadPublication(
  endpoint: string,
  previous: Catalogue,
  fetchText: (url: string) => Promise<string>,
): Promise<{ files: PublicationFiles; catalogue: Catalogue } | null> {
  https(endpoint);
  const first = await fetchText(endpoint);
  const manifest = publicationManifest(first);
  if (manifest['contentSha256'] === previous.contentSha256) {
    if (
      manifest['channelCount'] !== previous.channels.length ||
      manifest['streamCount'] !== previous.channels.reduce((n, c) => n + c.streams.length, 0)
    )
      throw Error('Unchanged publication count mismatch');
    return null;
  }
  const files: PublicationFiles = { 'manifest.json': first };
  const base = new URL('.', endpoint);
  await Promise.all(
    PUBLIC_FILES.filter((p) => p !== 'manifest.json').map(async (path) => {
      files[path] = await fetchText(new URL(path, base).href);
    }),
  );
  const last = publicationManifest(await fetchText(endpoint));
  // Compare values, independent of the manifest object's key formatting/order.
  const changed =
    Object.keys(manifest).some((k) => JSON.stringify(manifest[k]) !== JSON.stringify(last[k])) ||
    Object.keys(last).length !== Object.keys(manifest).length;
  if (changed) throw Error('Publication changed during refresh');
  return { files, catalogue: await decodePublication(files, previous) };
}

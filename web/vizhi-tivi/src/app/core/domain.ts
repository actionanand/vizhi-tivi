export interface Stream {
  url: string;
  format: 'hls' | 'dash' | 'progressive';
  status: 'approved' | 'unavailable' | 'unreviewed';
  permitted: boolean;
}
export interface Channel {
  id: string;
  number: number | null;
  name: string;
  languages: string[];
  country: string;
  region: string;
  category: string;
  provider: string;
  source: string;
  logo?: string;
  streams: Stream[];
  playlistId?: string;
  epgId?: string;
  description?: string;
}
export interface Catalogue {
  schemaVersion: 1;
  revision: number;
  developmentFixture?: boolean;
  channels: Channel[];
}
export interface Playlist {
  id: string;
  name: string;
  channels: Channel[];
}
export function safeUrl(raw: string): boolean {
  try {
    const u = new URL(raw);
    return u.protocol === 'https:' && !!u.hostname && !u.username && !u.password;
  } catch {
    return false;
  }
}
export function playable(c: Channel): Stream | undefined {
  return c.streams.find((s) => s.permitted && s.status === 'approved' && safeUrl(s.url));
}
export function languageCode(value: string): string {
  const v = value.trim().toLowerCase();
  return ['tamil', 'tam', 'தமிழ்', 'ta'].includes(v)
    ? 'ta'
    : ['english', 'eng', 'en'].includes(v)
      ? 'en'
      : ['sanskrit', 'san', 'sa'].includes(v)
        ? 'sa'
        : v || 'und';
}
export async function stableId(raw: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw));
  return [...new Uint8Array(digest)].map((n) => n.toString(16).padStart(2, '0')).join('');
}
export function channelLanguages(raw: string): string[] {
  return [...new Set(raw.split(/[,;]/).map(languageCode))];
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Invalid object');
  return value as Record<string, unknown>;
}
function text(row: Record<string, unknown>, key: string, allowEmpty = false): string {
  const value = row[key];
  if (typeof value !== 'string' || (!allowEmpty && !value.trim())) throw Error('Invalid field');
  return value;
}
export function decodeCatalogue(value: unknown, previous?: Catalogue): Catalogue {
  const root = object(value);
  if (root['developmentFixture'] !== undefined && typeof root['developmentFixture'] !== 'boolean')
    throw Error('Invalid fixture flag');
  if (
    root['schemaVersion'] !== 1 ||
    !Number.isInteger(root['revision']) ||
    (root['revision'] as number) > 2147483647 ||
    (root['revision'] as number) < (previous?.revision ?? 1) ||
    !Array.isArray(root['channels']) ||
    root['channels'].length > 10000
  )
    throw Error('Invalid catalogue version');
  const channels: Channel[] = root['channels'].map((value) => {
    const row = object(value);
    const id = text(row, 'id');
    if (
      !/^[a-zA-Z0-9._-]{1,160}$/.test(id) ||
      !Array.isArray(row['languages']) ||
      !row['languages'].length ||
      row['languages'].some((l) => typeof l !== 'string' || l.length < 2)
    )
      throw Error('Invalid channel');
    const number = row['number'];
    if (
      number !== null &&
      (!Number.isInteger(number) || (number as number) < 1 || (number as number) > 2147483647)
    )
      throw Error('Invalid number');
    const logo = row['logo'] === undefined ? '' : text(row, 'logo', true);
    if (logo && !safeUrl(logo)) throw Error('Invalid logo');
    if (!Array.isArray(row['streams'])) throw Error('Invalid streams');
    const streams: Stream[] = row['streams'].map((value) => {
      const s = object(value);
      const url = text(s, 'url');
      if (
        !safeUrl(url) ||
        !['hls', 'dash', 'progressive'].includes(String(s['format'])) ||
        !['approved', 'unavailable', 'unreviewed'].includes(String(s['status'])) ||
        typeof s['permitted'] !== 'boolean'
      )
        throw Error('Invalid stream');
      return {
        url,
        format: s['format'] as Stream['format'],
        status: s['status'] as Stream['status'],
        permitted: s['permitted'],
      };
    });
    const c: Channel = {
      id,
      number: number as number | null,
      name: text(row, 'name'),
      languages: row['languages'] as string[],
      country: text(row, 'country'),
      region: text(row, 'region', true),
      category: text(row, 'category'),
      provider: text(row, 'provider'),
      source: text(row, 'source'),
      logo,
      streams,
      ...(row['epgId'] === undefined ? {} : { epgId: text(row, 'epgId', true) }),
      ...(row['description'] === undefined ? {} : { description: text(row, 'description', true) }),
    };
    const old = previous?.channels.find((x) => x.id === id);
    if (old?.number != null && old.number !== c.number) throw Error('Published number changed');
    return c;
  });
  allocate(channels); // validate all identities, ranges and collisions; preserve published nulls
  return {
    schemaVersion: 1,
    revision: root['revision'] as number,
    developmentFixture: root['developmentFixture'] === true,
    channels,
  };
}
export function allocate(
  channels: Channel[],
  previous: Record<string, number> = {},
  reserved: number[] = [],
): Channel[] {
  if (new Set(channels.map((c) => c.id)).size !== channels.length) throw Error('Duplicate ID');
  const used = new Set(reserved);
  const assigned = new Map<string, number>();
  const sorted = [...channels].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const c of sorted) {
    const n = previous[c.id] ?? c.number;
    if (n !== null && n !== undefined) {
      if (
        !Number.isInteger(n) ||
        n < 1 ||
        (c.languages.includes('ta') ? n > 250 : n < 251) ||
        used.has(n)
      )
        throw Error('Invalid number or collision');
      used.add(n);
      assigned.set(c.id, n);
    }
  }
  for (const c of sorted.filter((c) => !assigned.has(c.id))) {
    let n = c.languages.includes('ta') ? 1 : 251;
    while (used.has(n)) n++;
    if (!c.languages.includes('ta') || n <= 250) {
      used.add(n);
      assigned.set(c.id, n);
    }
  }
  return channels.map((c) => ({ ...c, number: assigned.get(c.id) ?? null }));
}
export async function parseM3u(raw: string, playlistId: string): Promise<Channel[]> {
  if (raw.length > 5_000_000) throw Error('Playlist too large');
  const lines = raw
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .map((s) => s.trim());
  if (lines[0] !== '#EXTM3U') throw Error('Expected #EXTM3U');
  let info: string | undefined;
  const records = new Map<string, Channel>();
  const urls = new Set<string>();
  for (const line of lines.slice(1)) {
    if (line.startsWith('#EXTINF:')) {
      info = line;
      continue;
    }
    if (!line || line.startsWith('#')) continue;
    const meta = info;
    info = undefined;
    if (!meta || !safeUrl(line)) continue;
    const attrs = Object.fromEntries(
      [...meta.matchAll(/([\w-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]),
    );
    let quoted = false;
    const comma = [...meta].findIndex((ch) => {
      if (ch === '"') quoted = !quoted;
      return ch === ',' && !quoted;
    });
    const name = (comma >= 0 ? meta.slice(comma + 1) : '') || attrs['tvg-name'];
    if (!name) continue;
    const id = `import.${playlistId}.${await stableId(attrs['tvg-id'] || line)}`;
    if (records.has(id) || urls.has(line)) continue;
    urls.add(line);
    const path = new URL(line).pathname.toLowerCase();
    const mime = (attrs['mime-type'] || attrs['type'] || '').toLowerCase();
    records.set(id, {
      id,
      number: null,
      name,
      languages: channelLanguages(attrs['tvg-language'] || attrs['language'] || ''),
      country: 'Unknown',
      region: '',
      category: attrs['group-title'] || 'Imported TV',
      provider: 'Private user import',
      source: 'Private user import',
      logo: safeUrl(attrs['tvg-logo']) ? attrs['tvg-logo'] : '',
      streams: [
        {
          url: line,
          format:
            mime.includes('dash') || path.endsWith('.mpd')
              ? 'dash'
              : mime.includes('mpegurl') || path.endsWith('.m3u8')
                ? 'hls'
                : 'progressive',
          status: 'approved',
          permitted: true,
        },
      ],
      playlistId,
      epgId: attrs['tvg-id'] || '',
    });
  }
  if (!records.size || records.size > 10000)
    throw Error('No supported channels or too many channels');
  return [...records.values()];
}
export interface PrivateBackupTransport {
  upload(versionedJson: string): Promise<void>;
  download(): Promise<string | null>;
}

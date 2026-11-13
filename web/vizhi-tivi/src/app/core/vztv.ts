import { Channel, languageCode, safeUrl, stableId, Stream } from './domain';

/** Import format is separate from the signed, published catalogue contract.
 * A successful private import means the user has chosen to try a URL, not that
 * the source has been authorized for republication.
 */
export async function parseVztv(raw: string, playlistId: string): Promise<Channel[]> {
  if (new TextEncoder().encode(raw).length > 12_000_000) throw Error('VZTV file too large');
  let root: unknown;
  try {
    root = JSON.parse(raw.replace(/^\uFEFF/, ''));
  } catch {
    throw Error('Invalid VZTV JSON');
  }
  if (!isObject(root) ||
      root['format'] !== 'app.vizhi-tivi.private-channel-collection' ||
      root['schemaVersion'] !== 1 ||
      !isObject(root['collection']) ||
      root['collection']['visibility'] !== 'private' ||
      !Array.isArray(root['channels']) ||
      !root['channels'].length ||
      root['channels'].length > 10_000)
    throw Error('Unsupported VZTV collection');

  const seenIds = new Set<string>();
  const records: Channel[] = [];
  for (const value of root['channels']) {
    if (!isObject(value)) throw Error('Invalid VZTV channel');
    const originalId = value['id'];
    const name = value['name'];
    if (typeof originalId !== 'string' ||
        !/^[a-zA-Z0-9._-]{1,160}$/.test(originalId) ||
        seenIds.has(originalId) ||
        typeof name !== 'string' || !name.trim() || name.length > 500)
      throw Error('Invalid or duplicate VZTV channel');
    seenIds.add(originalId);

    const category = typeof value['category'] === 'string' ? value['category'] : 'Imported TV';
    // The app's public and private TV views intentionally exclude TV news.
    if (/\bnews\b/i.test(category) || /\bnews\b/i.test(name)) continue;
    if (!Array.isArray(value['streams']) || value['streams'].length > 8)
      throw Error('Invalid VZTV streams');

    const languages = Array.isArray(value['languages'])
      ? [...new Set(value['languages']
          .filter((x): x is string => typeof x === 'string' && x.length < 80)
          .map(languageCode))]
      : [];
    if (!languages.length) languages.push('und');
    const streams: Stream[] = [];
    const streamUrls = new Set<string>();
    for (const entry of value['streams']) {
      if (!isObject(entry) || typeof entry['url'] !== 'string' || !safeUrl(entry['url']))
        continue;
      const url = entry['url'];
      if (streamUrls.has(url)) continue;
      streamUrls.add(url);
      const path = new URL(url).pathname.toLowerCase();
      const format = entry['format'] === 'hls' || entry['format'] === 'dash' ||
        entry['format'] === 'progressive' ? entry['format'] :
        path.endsWith('.mpd') ? 'dash' : path.endsWith('.m3u8') ? 'hls' : 'progressive';
      streams.push({ url, format, status: 'approved', permitted: true });
    }
    const logo = typeof value['logo'] === 'string' && safeUrl(value['logo'])
      ? value['logo'] : '';
    // Scope stable channel IDs to the collection; do not consume official IDs.
    const hash = await stableId(originalId);
    records.push({
      id: `import.${playlistId.slice(0, 24)}.${hash.slice(0, 24)}`,
      number: null,
      name: name.trim(),
      languages,
      sourceLanguages: [...languages],
      country: typeof value['country'] === 'string' && value['country'].trim()
        ? value['country'] : 'Unknown',
      region: '',
      category,
      provider: typeof value['origin'] === 'string' && value['origin'].trim()
        ? value['origin'] : 'Private user import',
      source: 'Private user import',
      logo,
      streams,
      playlistId,
      epgId: originalId,
    });
  }
  if (!records.length) throw Error('No non-news TV channels in VZTV collection');
  return records;
}

export function isVztv(raw: string): boolean {
  return raw.replace(/^\uFEFF/, '').trimStart().startsWith('{');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

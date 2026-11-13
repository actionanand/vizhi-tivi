import { parseVztv, isVztv } from './vztv';

const sample = (channels: unknown[]) => JSON.stringify({
  format: 'app.vizhi-tivi.private-channel-collection',
  schemaVersion: 1,
  collection: { visibility: 'private' },
  channels,
});
const channel = (overrides: Record<string, unknown> = {}) => ({
  id: 'dd-tamil',
  name: 'DD Tamil',
  languages: ['ta', 'tam'],
  country: 'IN',
  category: 'General',
  origin: 'research',
  streams: [{ url: 'https://example.invalid/one.m3u8', format: 'hls' }],
  ...overrides,
});

describe('Private VZTV collections', () => {
  it('preserves metadata and alternatives for private playback', async () => {
    const rows = await parseVztv(sample([channel({
      streams: [
        { url: 'https://example.invalid/one.m3u8', format: 'hls' },
        { url: 'https://example.invalid/two.mpd', format: 'dash' },
      ],
    })]), 'playlist');
    expect(rows).toHaveLength(1);
    expect(rows[0].name).toBe('DD Tamil');
    expect(rows[0].sourceLanguages).toEqual(['ta']);
    expect(rows[0].country).toBe('IN');
    expect(rows[0].streams.map((s) => s.format)).toEqual(['hls', 'dash']);
    expect(rows[0].streams.every((s) => s.permitted && s.status === 'approved')).toBe(true);
    expect(rows[0].number).toBeNull();
  });

  it('supports metadata-only records and rejects TV news', async () => {
    const rows = await parseVztv(sample([
      channel({ streams: [] }),
      channel({ id: 'news-1', name: 'Regional News TV', category: 'News' }),
    ]), 'playlist');
    expect(rows).toHaveLength(1);
    expect(rows[0].streams).toEqual([]);
  });

  it('rejects duplicate IDs, unsupported schema and non-private collections', async () => {
    await expect(parseVztv(sample([channel(), channel()]), 'p')).rejects.toThrow();
    await expect(parseVztv(sample([]).replace('"schemaVersion":1', '"schemaVersion":2'), 'p')).rejects.toThrow();
    await expect(parseVztv(sample([channel()]).replace('"visibility":"private"', '"visibility":"public"'), 'p')).rejects.toThrow();
  });

  it('ignores unsafe URLs, duplicate streams and invalid logos', async () => {
    const rows = await parseVztv(sample([channel({
      logo: 'javascript:alert(1)',
      streams: [
        { url: 'http://example.invalid/bad' },
        { url: 'https://user:pass@example.invalid/bad' },
        { url: 'https://example.invalid/one.m3u8', format: 'hls' },
        { url: 'https://example.invalid/one.m3u8', format: 'hls' },
      ],
    })]), 'p');
    expect(rows[0].logo).toBe('');
    expect(rows[0].streams).toHaveLength(1);
    expect(isVztv(sample([channel()]))).toBe(true);
    expect(isVztv('#EXTM3U')).toBe(false);
  });

  it('preserves stable identity when the channel name or URL changes', async () => {
    const a = (await parseVztv(sample([channel()]), 'p'))[0];
    const b = (await parseVztv(sample([channel({
      name: 'DD Pothigai',
      streams: [{ url: 'https://example.invalid/new.m3u8' }],
    })]), 'p'))[0];
    expect(a.id).toBe(b.id);
  });
});

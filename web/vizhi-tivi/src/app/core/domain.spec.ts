import {
  allocate,
  Channel,
  decodeCatalogue,
  languageCode,
  parseM3u,
  playable,
  safeUrl,
} from './domain';
import fixture from '../../../../../contracts/fixtures/catalogue-v1.json';
import vectors from '../../../../../contracts/fixtures/playlist-vectors.json';

const c = (id: string, ta = true, number: number | null = null): Channel => ({
  id,
  number,
  name: id,
  languages: [ta ? 'ta' : 'en'],
  country: 'IN',
  region: '',
  category: 'News',
  provider: 'Test',
  source: 'Test',
  streams: [],
});
describe('Shared catalogue and playlist contract', () => {
  it('decodes metadata-only development fixture', () => {
    const next = decodeCatalogue(fixture);
    expect(next.channels.length).toBe(2);
    expect(playable(next.channels[0])).toBeUndefined();
  });
  it('rejects future versions', () =>
    expect(() => decodeCatalogue({ ...fixture, schemaVersion: 2 })).toThrow());
  it('rejects incomplete metadata', () =>
    expect(() =>
      decodeCatalogue({ ...fixture, channels: [{ ...fixture.channels[0], name: '' }] }),
    ).toThrow());
  it('rejects duplicate IDs', () => expect(() => allocate([c('a'), c('a')])).toThrow());
  it('rejects number collisions', () =>
    expect(() => allocate([c('a', true, 1), c('b', true, 1)])).toThrow());
  it('rejects wrong language range', () => expect(() => allocate([c('a', false, 1)])).toThrow());
  it('preserves surviving numbers regardless of order or spelling', () => {
    const next = allocate([c('b'), { ...c('a'), name: 'Renamed' }, c('new')], { a: 1, b: 2 });
    expect(next.map((c) => c.number)).toEqual([2, 1, 3]);
  });
  it('reuses genuinely vacated gaps', () =>
    expect(allocate([c('new'), c('b')], { removed: 1, b: 2 })[0].number).toBe(1));
  it('starts other languages at 251', () => expect(allocate([c('a', false)])[0].number).toBe(251));
  it('never overwrites exhausted Tamil slots', () => {
    const next = allocate(
      Array.from({ length: 251 }, (_, i) => c(`id${String(i).padStart(3, '0')}`)),
    );
    expect(next[250].number).toBeNull();
    expect(new Set(next.slice(0, 250).map((c) => c.number)).size).toBe(250);
  });
  it('reserves official numbers for local imports', () =>
    expect(allocate([c('a')], {}, [1, 2])[0].number).toBe(3));
  it('rejects published number changes', () =>
    expect(() =>
      decodeCatalogue(
        { ...fixture, channels: [{ ...fixture.channels[0], number: 2 }, fixture.channels[1]] },
        decodeCatalogue(fixture),
      ),
    ).toThrow());
  it('rejects catalogue rollback', () =>
    expect(() => decodeCatalogue(fixture, { ...decodeCatalogue(fixture), revision: 2 })).toThrow());
  it('matches common M3U fixture including identity hashes', async () => {
    const rows = await parseM3u(vectors.input, vectors.playlistId);
    expect(
      rows.map((c) => ({
        id: c.id,
        name: c.name,
        language: c.languages[0],
        format: c.streams[0].format,
      })),
    ).toEqual(vectors.expected);
  });
  it('preserves reimport identity when metadata and URLs change', async () => {
    const first = await parseM3u(vectors.input, 'p');
    const next = await parseM3u(
      vectors.input.replace('Sample, Tamil', 'Renamed').replaceAll('/live.m3u8', '/other.m3u8'),
      'p',
    );
    expect(first[0].id).toBe(next[0].id);
  });
  it('requires an M3U header', async () => {
    await expect(parseM3u('invalid', 'p')).rejects.toThrow();
  });
  it('detects Tamil only from reliable metadata', () => {
    expect(languageCode('Tamil')).toBe('ta');
    expect(languageCode('tam')).toBe('ta');
    expect(languageCode('')).toBe('und');
  });
  it('rejects unsafe schemes and userinfo', () => {
    [
      'http://example.invalid/a',
      'file:///private',
      'javascript:alert(1)',
      'https://user:secret@example.invalid/',
    ].forEach((s) => expect(safeUrl(s)).toBe(false));
  });
  it('does not expose unreviewed streams for playback', () =>
    expect(
      playable({
        ...c('a'),
        streams: [
          {
            url: 'https://example.invalid/a',
            format: 'hls',
            status: 'unreviewed',
            permitted: true,
          },
        ],
      }),
    ).toBeUndefined());
  it('reads multiple language metadata and extensionless stream MIME', async () => {
    const rows = await parseM3u(
      '#EXTM3U\n#EXTINF:-1 tvg-language="Tamil;English" type="application/vnd.apple.mpegurl",Test\nhttps://example.invalid/watch',
      'test',
    );
    expect(rows[0].languages).toEqual(['ta', 'en']);
    expect(rows[0].streams[0].format).toBe('hls');
  });
});

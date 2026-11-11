import { LiveStore, DEVICE_KEY, CACHE_KEY } from './store';
import fixture from '../../../../../contracts/fixtures/catalogue-v1.json';
import vectors from '../../../../../contracts/fixtures/playlist-vectors.json';
import { vi } from 'vitest';
import publications from '../../../../../contracts/fixtures/official-v1/vectors.json';
import {
  OFFICIAL_MANIFEST_URL,
  PUBLICATION_KEY,
  publicationCache,
  publicationDigest,
} from './published-catalogue';

describe('Private state and catalogue persistence', () => {
  beforeEach(() => {
    localStorage.clear();
  });
  it('persists favourites and locale across service instances', () => {
    const store = new LiveStore();
    store.toggleFavourite('demo.tamil');
    store.save({ locale: 'ta' });
    const restored = new LiveStore();
    expect(restored.data().favourites).toEqual(['demo.tamil']);
    expect(restored.t('home')).toBe('முகப்பு');
  });
  it('falls back to English on invalid stored locale without overwriting records', () => {
    localStorage.setItem(
      DEVICE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        locale: 'xx',
        favourites: ['a'],
        recent: [],
        playlists: [],
      }),
    );
    const store = new LiveStore();
    expect(store.t('home')).toBe('Home');
    expect(store.error()).toBe(true);
    expect(localStorage.getItem(DEVICE_KEY)).toContain('"a"');
  });
  it('restores cached catalogue for offline browsing', () => {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ ...fixture, developmentFixture: false }));
    expect(new LiveStore().catalogue().channels.length).toBe(2);
  });
  it('preserves last-known-good cache after invalid refresh', async () => {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ ...fixture, developmentFixture: false }));
    const store = new LiveStore();
    vi.spyOn(store, 'fetchText').mockResolvedValue('{"schemaVersion":2}');
    await store.refresh();
    expect(store.error()).toBe(true);
    expect(store.catalogue().channels.length).toBe(2);
    expect(JSON.parse(localStorage.getItem(CACHE_KEY) ?? '{}')).toEqual({
      ...fixture,
      developmentFixture: false,
    });
  });
  it('reimports a playlist without duplicating it and retains favourites', async () => {
    const store = new LiveStore();
    await store.importText(vectors.input, 'file:test', 'Test');
    const id = store.data().playlists[0].channels[0].id;
    store.toggleFavourite(id);
    await store.importText(vectors.input.replace('Sample, Tamil', 'Renamed'), 'file:test', 'Test');
    expect(store.data().playlists.length).toBe(1);
    expect(store.data().playlists[0].channels.length).toBe(2);
    expect(store.data().favourites).toContain(id);
    expect(new LiveStore().data().playlists[0].channels[0].name).toBe('Renamed');
  });
  it('leaves old playlists intact after invalid reimport', async () => {
    const store = new LiveStore();
    await store.importText(vectors.input, 'file:test', 'Test');
    await store.importText('invalid', 'file:test', 'Test');
    expect(store.data().playlists[0].channels.length).toBe(2);
    expect(store.error()).toBe(true);
  });
  it('removes only the selected playlist', async () => {
    const store = new LiveStore();
    await store.importText(vectors.input, 'file:a', 'A');
    await store.importText(vectors.input, 'file:b', 'B');
    store.remove(store.data().playlists[0].id);
    expect(store.data().playlists.length).toBe(1);
    expect(store.data().playlists[0].name).toBe('B');
  });
  it('records recents in last-watched order', () => {
    const store = new LiveStore();
    store.watched('a');
    store.watched('b');
    store.watched('a');
    expect(new LiveStore().data().recent).toEqual(['a', 'b']);
  });
  it('gives newly published official numbers priority without changing private identity', async () => {
    const store = new LiveStore();
    store.catalogue.set({
      ...fixture,
      schemaVersion: 1,
      channels: fixture.channels.map((c) => ({ ...c, streams: [] })),
    });
    store.save({ localNumbers: true });
    await store.importText(vectors.input, 'file:test', 'Test');
    const imported = store.data().playlists[0].channels[0];
    expect(imported.number).toBe(2);
    store.toggleFavourite(imported.id);
    const files = { ...publications.synthetic.files };
    const channels = JSON.parse(files['catalog/v1/catalogue.json']);
    channels.channels[0].number = 2;
    files['catalog/v1/catalogue.json'] = JSON.stringify(channels);
    const manifest = JSON.parse(files['manifest.json']);
    manifest.contentSha256 = await publicationDigest(files);
    files['manifest.json'] = JSON.stringify(manifest);
    vi.spyOn(store, 'fetchText').mockImplementation(
      async (url) =>
        files[new URL(url).pathname.replace('/vizhi-tivi-data/', '') as keyof typeof files],
    );
    await store.refresh();
    expect(store.error()).toBe(false);
    expect(store.data().playlists[0].channels[0].number).toBeNull();
    const restored = new LiveStore();
    vi.spyOn(restored, 'fetchText').mockRejectedValue(Error('offline'));
    await restored.initial();
    expect(restored.data().playlists[0].channels[0].id).toBe(imported.id);
    expect(restored.data().favourites).toContain(imported.id);
  });
  it('reports storage quota errors without claiming persistence', () => {
    const store = new LiveStore();
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw Error('quota');
    });
    store.save({ locale: 'ta' });
    expect(store.data().locale).toBe('en');
    expect(store.error()).toBe(true);
    spy.mockRestore();
  });
  it('defaults to the official manifest URL while preserving a test override', () => {
    const store = new LiveStore();
    expect(store.data().endpoint).toBe(OFFICIAL_MANIFEST_URL);
    store.save({ endpoint: 'https://mirror.example/manifest.json' });
    expect(new LiveStore().data().endpoint).toBe('https://mirror.example/manifest.json');
  });
  it('does not display legacy development fixtures as real channels', () => {
    localStorage.setItem(CACHE_KEY, JSON.stringify(fixture));
    expect(new LiveStore().catalogue().channels).toEqual([]);
  });
  it('accepts the empty official snapshot, caches it and never loads a development fixture', async () => {
    const store = new LiveStore();
    const fetch = vi
      .spyOn(store, 'fetchText')
      .mockImplementation(
        async (url) =>
          publications.empty.files[
            new URL(url).pathname.replace(
              '/vizhi-tivi-data/',
              '',
            ) as keyof typeof publications.empty.files
          ],
      );
    await store.initial();
    expect(store.error()).toBe(false);
    expect(store.catalogue().channels).toEqual([]);
    expect(store.catalogue().contentSha256).toBeTruthy();
    expect(fetch.mock.calls.length).toBe(9);
    expect(localStorage.getItem(PUBLICATION_KEY)).toBeTruthy();
  });
  it('retains a verified cache and private imports after offline and corrupt publication failures', async () => {
    localStorage.setItem(PUBLICATION_KEY, publicationCache(publications.synthetic.files));
    const store = new LiveStore();
    await store.importText(vectors.input, 'file:test', 'Private');
    store.save({ locale: 'ta' });
    const privateBefore = localStorage.getItem(DEVICE_KEY);
    const cachedBefore = localStorage.getItem(PUBLICATION_KEY);
    const fetch = vi.spyOn(store, 'fetchText').mockRejectedValue(Error('offline'));
    await store.initial();
    expect(store.catalogue().channels.length).toBe(4);
    expect(store.error()).toBe(true);
    fetch.mockResolvedValue('{"schemaVersion":2}');
    await store.refresh();
    expect(store.catalogue().channels.length).toBe(4);
    expect(localStorage.getItem(PUBLICATION_KEY)).toBe(cachedBefore);
    expect(localStorage.getItem(DEVICE_KEY)).toBe(privateBefore);
    expect(store.data().playlists[0].channels.length).toBe(2);
    expect(store.t('home')).toBe('முகப்பு');
  });
  it('allows a valid empty publication to replace an older nonempty cache', async () => {
    localStorage.setItem(PUBLICATION_KEY, publicationCache(publications.synthetic.files));
    const store = new LiveStore();
    vi.spyOn(store, 'fetchText').mockImplementation(
      async (url) =>
        publications.empty.files[
          new URL(url).pathname.replace(
            '/vizhi-tivi-data/',
            '',
          ) as keyof typeof publications.empty.files
        ],
    );
    await store.initial();
    expect(store.catalogue().channels).toEqual([]);
    expect(store.error()).toBe(false);
    expect(store.catalogue().contentSha256).toBe(
      JSON.parse(publications.empty.files['manifest.json']).contentSha256,
    );
  });
  it('does not replace the last-known-good state if writing the new snapshot exceeds quota', async () => {
    localStorage.setItem(PUBLICATION_KEY, publicationCache(publications.synthetic.files));
    const store = new LiveStore();
    const fetch = vi.spyOn(store, 'fetchText').mockRejectedValue(Error('offline'));
    await store.initial();
    fetch.mockImplementation(
      async (url) =>
        publications.empty.files[
          new URL(url).pathname.replace(
            '/vizhi-tivi-data/',
            '',
          ) as keyof typeof publications.empty.files
        ],
    );
    const before = localStorage.getItem(PUBLICATION_KEY);
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw Error('quota');
    });
    await store.refresh();
    expect(store.error()).toBe(true);
    expect(store.catalogue().channels.length).toBe(4);
    expect(localStorage.getItem(PUBLICATION_KEY)).toBe(before);
    write.mockRestore();
  });
  it.each(['integrity', 'mixed'])(
    'retains the cache and private bytes after a %s publication failure',
    async (failure) => {
      localStorage.setItem(PUBLICATION_KEY, publicationCache(publications.synthetic.files));
      const store = new LiveStore();
      await store.importText(vectors.input, 'file:private', 'Private');
      const fetch = vi
        .spyOn(store, 'fetchText')
        .mockImplementation(
          async (url) =>
            publications.synthetic.files[
              new URL(url).pathname.replace(
                '/vizhi-tivi-data/',
                '',
              ) as keyof typeof publications.synthetic.files
            ],
        );
      await store.initial();
      const before = localStorage.getItem(PUBLICATION_KEY);
      const privateBefore = localStorage.getItem(DEVICE_KEY);
      const files = { ...publications.empty.files };
      const locale = JSON.parse(files['catalog/v1/locales/ta.json']);
      locale.appName = 'Tampered';
      if (failure === 'integrity') files['catalog/v1/locales/ta.json'] = JSON.stringify(locale);
      let manifests = 0;
      fetch.mockImplementation(async (url) => {
        const path = new URL(url).pathname.replace('/vizhi-tivi-data/', '') as keyof typeof files;
        if (path === 'manifest.json' && ++manifests === 2 && failure === 'mixed') {
          const m = JSON.parse(files[path]);
          m.contentSha256 = '0'.repeat(64);
          return JSON.stringify(m);
        }
        return files[path];
      });
      await store.refresh();
      expect(store.error()).toBe(true);
      expect(store.catalogue().channels.length).toBe(4);
      expect(localStorage.getItem(PUBLICATION_KEY)).toBe(before);
      expect(localStorage.getItem(DEVICE_KEY)).toBe(privateBefore);
      expect(
        fetch.mock.calls.every(([url]) =>
          url.startsWith('https://actionanand.github.io/vizhi-tivi-data/'),
        ),
      ).toBe(true);
    },
  );
});

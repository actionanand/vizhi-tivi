import { LiveStore, DEVICE_KEY, CACHE_KEY, RECOVERY_KEY } from './store';
import { RecoverySnapshots } from './recovery-snapshots';
import fixture from '../../../../../contracts/fixtures/catalogue-v1.json';
import vectors from '../../../../../contracts/fixtures/playlist-vectors.json';
import { vi } from 'vitest';
import publications from '../../../../../contracts/fixtures/official-v1/vectors.json';
import {
  OFFICIAL_MANIFEST_URL,
  PUBLICATION_KEY,
  decodePublication,
  publicationCache,
  publicationDigest,
} from './published-catalogue';

describe('Private state and catalogue persistence', () => {
  beforeEach(() => {
    localStorage.clear();
  });
  it('rejects a stale tab edit and reloads the newer private data', async () => {
    const first = new LiveStore();
    const second = new LiveStore();
    await first.save({ locale: 'ta' });
    await second.save({ theme: 'dark' });
    expect(second.storageConflict()).toBe(true);
    expect(second.data().locale).toBe('ta');
    expect(JSON.parse(localStorage.getItem(DEVICE_KEY)!).theme).toBe('system');
    await second.save({ theme: 'dark' });
    expect(new LiveStore().data().locale).toBe('ta');
    expect(new LiveStore().data().theme).toBe('dark');
  });
  it('does not modify private data if cross-tab locking is unavailable', async () => {
    const previous = navigator.locks;
    Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined });
    try {
      const store = new LiveStore();
      await store.save({ locale: 'ta' });
      expect(store.storageConflict()).toBe(true);
      expect(localStorage.getItem(DEVICE_KEY)).toBeNull();
    } finally { Object.defineProperty(navigator, 'locks', { configurable: true, value: previous }); }
  });
  it('does not crash or overwrite data when browser storage is unavailable', async () => {
    const getter = vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => { throw new DOMException('Denied', 'SecurityError'); });
    try {
      const store = new LiveStore();
      await store.save({ locale: 'ta' });
      expect(store.storageConflict()).toBe(true);
      expect(store.data().locale).toBe('en');
      store.ngOnDestroy();
    } finally { getter.mockRestore(); }
  });
  it('serializes competing tabs and rejects restoration based on stale active data', async () => {
    const one = new LiveStore();
    await one.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8', 'file:multi', 'Test');
    const manager = new RecoverySnapshots(localStorage, () => ({ count: 1, compatibility: 'complete' }));
    const snapshot = await manager.create(localStorage.getItem(DEVICE_KEY)!);
    const two = new LiveStore();
    await two.reloadSnapshots();
    await one.save({ locale: 'ta' });
    await two.restoreSnapshot(snapshot);
    expect(two.storageConflict()).toBe(true);
    expect(new LiveStore().data().locale).toBe('ta');
    expect(new LiveStore().data().playlists).toHaveLength(1);
  });
  it('refreshes visible state when another context changes private storage', async () => {
    const one = new LiveStore(); const two = new LiveStore();
    await one.save({ locale: 'sa' });
    window.dispatchEvent(new StorageEvent('storage', { key: DEVICE_KEY, storageArea: localStorage, newValue: localStorage.getItem(DEVICE_KEY) }));
    expect(two.data().locale).toBe('sa');
    two.ngOnDestroy(); one.ngOnDestroy();
  });
  it('rejects a stale selected snapshot after another tab deletes it', async () => {
    const store = new LiveStore();
    await store.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8', 'file:delete', 'Test');
    const manager = new RecoverySnapshots(localStorage, () => ({ count: 1, compatibility: 'complete' }));
    const first = await manager.create(localStorage.getItem(DEVICE_KEY)!);
    await manager.create(localStorage.getItem(DEVICE_KEY)!);
    await store.reloadSnapshots();
    const before = localStorage.getItem(DEVICE_KEY);
    await manager.remove(first);
    await store.restoreSnapshot(first);
    expect(store.storageConflict()).toBe(true);
    expect(localStorage.getItem(DEVICE_KEY)).toBe(before);
  });
  it('does not replace a newer manual correction when restoring an older snapshot', async () => {
    const store = new LiveStore();
    await store.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable" tvg-language="eng",News\nhttps://example.invalid/live.m3u8', 'file:older', 'Test');
    const raw = localStorage.getItem(DEVICE_KEY)!;
    const snapshots = new RecoverySnapshots(localStorage, () => ({ count: 1, compatibility: 'complete' }));
    const id = await snapshots.create(raw);
    await store.reloadSnapshots();
    await store.correctLanguage(store.data().playlists[0].channels[0], 'sa');
    const current = store.data().playlists[0].channels[0];
    await store.restoreSnapshot(id);
    expect(store.snapshotError()).toBe(false);
    expect(store.data().playlists[0].channels[0]).toEqual(current);
    expect(new LiveStore().data().playlists[0].channels[0].languageOverride).toBe('sa');
  });
  it('retains a language override, detected source, favourite and identity through recovery', async () => {
    const raw = '#EXTM3U\n#EXTINF:-1 tvg-id="stable" tvg-language="eng",News\nhttps://example.invalid/live.m3u8';
    const initial = new LiveStore();
    await initial.importText(raw, 'file:recover-override', 'Test');
    const original = initial.data().playlists[0].channels[0];
    await initial.correctLanguage(original, 'ta');
    await initial.toggleFavourite(original.id);
    const damaged = JSON.parse(localStorage.getItem(DEVICE_KEY) ?? '{}');
    damaged.playlists.push({ id: 'broken', name: 'Broken', channels: [{}] });
    localStorage.setItem(DEVICE_KEY, JSON.stringify(damaged));
    const recovering = new LiveStore();
    expect(recovering.recoveryRequired).toBe(true);
    await recovering.recoverPlaylists();
    const restored = new LiveStore();
    const channel = restored.data().playlists[0].channels[0];
    expect(channel.id).toBe(original.id);
    expect(channel.languageOverride).toBe('ta');
    expect(channel.sourceLanguages).toEqual(['en']);
    expect(restored.data().favourites).toContain(original.id);
    await restored.resetLanguage(channel);
    expect(restored.data().playlists[0].channels[0].languages).toEqual(['en']);
  });
  it('preserves a manual Tamil classification through restart and reimport', async () => {
    const raw = '#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8';
    const store = new LiveStore();
    await store.save({ localNumbers: true });
    await store.importText(raw, 'file:override', 'Test');
    await store.correctLanguage(store.data().playlists[0].channels[0], 'ta');
    const restored = new LiveStore();
    await restored.importText(raw, 'file:override', 'Test');
    const channel = restored.data().playlists[0].channels[0];
    expect(channel.languages).toEqual(['ta']);
    expect(channel.languageOverride).toBe('ta');
    expect(channel.number).toBe(1);
    await restored.resetLanguage(channel);
    expect(restored.data().playlists[0].channels[0].languages).toEqual(['und']);
    expect(restored.data().playlists[0].channels[0].number).toBeNull();
    await restored.importText(raw, 'file:override', 'Test');
    expect(restored.data().playlists[0].channels[0].languages).toEqual(['und']);
    expect(restored.data().playlists[0].channels[0].number).toBe(251);
  });
  it('isolates a corrupt private record while preserving the original stored bytes', async () => {
    const store = new LiveStore();
    await store.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8', 'file:good', 'Good');
    const data = JSON.parse(localStorage.getItem(DEVICE_KEY) ?? '{}');
    data.playlists.push({ id: 'bad', name: 'Bad', channels: [{ id: 'broken' }] });
    const raw = JSON.stringify(data);
    localStorage.setItem(DEVICE_KEY, raw);
    localStorage.setItem(PUBLICATION_KEY, publicationCache(publications.synthetic.files));
    const restored = new LiveStore();
    vi.spyOn(restored, 'fetchText').mockRejectedValue(new Error('offline'));
    await restored.initial();
    expect(restored.catalogue().channels.length).toBe(4);
    expect(restored.data().playlists.map((p) => p.name)).toEqual(['Good']);
    expect(restored.error()).toBe(true);
    expect(restored.recoveryRequired).toBe(true);
    expect(restored.originalPrivateStore()).toBe(raw);
    await restored.remove(restored.data().playlists[0].id);
    expect(localStorage.getItem(DEVICE_KEY)).toBe(raw);
    await restored.recoverPlaylists();
    expect(restored.recoveryRequired).toBe(false);
    expect(restored.snapshots()).toHaveLength(1);
    expect(restored.snapshotRaw(restored.snapshots()[0].id)).toBe(raw);
    expect(restored.originalPrivateStore()).toBe(raw);
    expect(new LiveStore().data().playlists.map((p) => p.name)).toEqual(['Good']);
  });
  it('does not change the original when the recovery backup cannot be written', async () => {
    const raw = '{broken';
    localStorage.setItem(DEVICE_KEY, raw);
    const restored = new LiveStore();
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw Error('quota'); });
    try {
      await restored.recoverPlaylists();
      expect(restored.recoveryRequired).toBe(true);
      expect(localStorage.getItem(DEVICE_KEY)).toBe(raw);
      expect(localStorage.getItem(RECOVERY_KEY)).toBeNull();
    } finally { write.mockRestore(); }
  });
  it('treats an empty stored record as damaged and preserves it', async () => {
    localStorage.setItem(DEVICE_KEY, '');
    const restored = new LiveStore();
    expect(restored.recoveryRequired).toBe(true);
    await restored.save({ locale: 'ta' });
    expect(localStorage.getItem(DEVICE_KEY)).toBe('');
  });
  it('preserves a different existing legacy recovery backup', async () => {
    localStorage.setItem(DEVICE_KEY, '{broken');
    localStorage.setItem(RECOVERY_KEY, '{earlier backup');
    const restored = new LiveStore();
    await restored.recoverPlaylists();
    expect(restored.recoveryRequired).toBe(false);
    expect(localStorage.getItem(RECOVERY_KEY)).toBe('{earlier backup');
    expect(restored.snapshots()).toHaveLength(2);
  });
  it('isolates a second playlist with colliding private channel identities', async () => {
    const store = new LiveStore();
    await store.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8', 'file:good', 'Good');
    const data = JSON.parse(localStorage.getItem(DEVICE_KEY) ?? '{}');
    data.playlists.push({ ...data.playlists[0], id: 'another', name: 'Collision' });
    const raw = JSON.stringify(data);
    localStorage.setItem(DEVICE_KEY, raw);
    const restored = new LiveStore();
    expect(restored.data().playlists.map((p) => p.name)).toEqual(['Good']);
    expect(restored.recoveryRequired).toBe(true);
    expect(restored.originalPrivateStore()).toBe(raw);
  });
  it('does not overwrite an oversized private store', async () => {
    const original = 'x'.repeat(20_000_001);
    const reader = vi.spyOn(Storage.prototype, 'getItem').mockImplementation((key) => key === DEVICE_KEY ? original : null);
    const writer = vi.spyOn(Storage.prototype, 'setItem');
    try {
      const restored = new LiveStore();
      expect(restored.recoveryRequired).toBe(true);
      await restored.save({ locale: 'ta' });
      expect(writer).not.toHaveBeenCalled();
      expect(restored.originalPrivateStore()).toBe(original);
    } finally {
      reader.mockRestore();
      writer.mockRestore();
    }
  });
  it('resets to source Tamil, English, Sanskrit and unknown metadata after restart', async () => {
    const expected: Record<string, string> = { tam: 'ta', eng: 'en', san: 'sa', '': 'und' };
    for (const source of ['tam', 'eng', 'san', '']) {
      localStorage.clear();
      const raw = `#EXTM3U\n#EXTINF:-1 tvg-id="stable" tvg-language="${source}",News\nhttps://example.invalid/live.m3u8`;
      const store = new LiveStore();
      await store.importText(raw, 'file:source', 'Test');
      const first = store.data().playlists[0].channels[0];
      await store.correctLanguage(first, source === 'tam' ? 'en' : 'ta');
      await store.importText(raw, 'file:source', 'Test');
      const restored = new LiveStore();
      await restored.resetLanguage(restored.data().playlists[0].channels[0]);
      await restored.importText(raw, 'file:source', 'Test');
      const result = restored.data().playlists[0].channels[0];
      expect(result.id).toBe(first.id);
      expect(result.languages).toEqual([expected[source]]);
      expect(result.languageOverride).toBeUndefined();
    }
  });
  it('retains an older override and enables reset after its source is reimported', async () => {
    const raw = '#EXTM3U\n#EXTINF:-1 tvg-id="stable" tvg-language="eng",News\nhttps://example.invalid/live.m3u8';
    const store = new LiveStore();
    await store.importText(raw, 'file:legacy', 'Test');
    await store.correctLanguage(store.data().playlists[0].channels[0], 'ta');
    const legacy = JSON.parse(localStorage.getItem(DEVICE_KEY) ?? '{}');
    delete legacy.playlists[0].channels[0].sourceLanguages;
    localStorage.setItem(DEVICE_KEY, JSON.stringify(legacy));
    const restored = new LiveStore();
    expect(restored.data().playlists[0].channels[0].languages).toEqual(['ta']);
    await restored.importText(raw, 'file:legacy', 'Test');
    await restored.resetLanguage(restored.data().playlists[0].channels[0]);
    expect(restored.data().playlists[0].channels[0].languages).toEqual(['en']);
  });
  it('reserves official numbers after resetting an imported language override', async () => {
    const raw = '#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8';
    const store = new LiveStore();
    store.catalogue.set(await decodePublication(publications.synthetic.files));
    await store.save({ localNumbers: true });
    await store.importText(raw, 'file:reserved', 'Test');
    const initial = store.data().playlists[0].channels[0];
    expect(initial.number).toBe(254);
    await store.correctLanguage(initial, 'ta');
    await store.importText(raw, 'file:reserved', 'Test');
    const corrected = store.data().playlists[0].channels[0];
    expect(corrected.number).toBe(1);
    await store.resetLanguage(corrected);
    await store.importText(raw, 'file:reserved', 'Test');
    const reset = store.data().playlists[0].channels[0];
    expect(reset.id).toBe(initial.id);
    expect(reset.number).toBe(254);
    expect(store.catalogue().channels.some((channel) => channel.number === reset.number)).toBe(false);
  });
  it('persists favourites and locale across service instances', async () => {
    const store = new LiveStore();
    await store.toggleFavourite('demo.tamil');
    await store.save({ locale: 'ta' });
    const restored = new LiveStore();
    expect(restored.data().favourites).toEqual(['demo.tamil']);
    expect(restored.t('home')).toBe('முகப்பு');
  });
  it('falls back to English on invalid stored locale without overwriting records', async () => {
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
  it('restores cached catalogue for offline browsing', async () => {
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
    await store.toggleFavourite(id);
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
    await store.remove(store.data().playlists[0].id);
    expect(store.data().playlists.length).toBe(1);
    expect(store.data().playlists[0].name).toBe('B');
  });
  it('records recents in last-watched order', async () => {
    const store = new LiveStore();
    await store.watched('a');
    await store.watched('b');
    await store.watched('a');
    expect(new LiveStore().data().recent).toEqual(['a', 'b']);
  });
  it('gives newly published official numbers priority without changing private identity', async () => {
    const store = new LiveStore();
    store.catalogue.set({
      ...fixture,
      schemaVersion: 1,
      channels: fixture.channels.map((c) => ({ ...c, streams: [] })),
    });
    await store.save({ localNumbers: true });
    await store.importText(vectors.input, 'file:test', 'Test');
    const imported = store.data().playlists[0].channels[0];
    expect(imported.number).toBe(2);
    await store.toggleFavourite(imported.id);
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
  it('reports storage quota errors without claiming persistence', async () => {
    const store = new LiveStore();
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw Error('quota');
    });
    await store.save({ locale: 'ta' });
    expect(store.data().locale).toBe('en');
    expect(store.error()).toBe(true);
    spy.mockRestore();
  });
  it('defaults to the official manifest URL while preserving a test override', async () => {
    const store = new LiveStore();
    expect(store.data().endpoint).toBe(OFFICIAL_MANIFEST_URL);
    await store.save({ endpoint: 'https://mirror.example/manifest.json' });
    expect(new LiveStore().data().endpoint).toBe('https://mirror.example/manifest.json');
  });
  it('does not display legacy development fixtures as real channels', async () => {
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
    await store.save({ locale: 'ta' });
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

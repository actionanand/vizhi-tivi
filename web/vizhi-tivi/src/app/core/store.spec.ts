import { LiveStore, DEVICE_KEY, CACHE_KEY } from './store';
import fixture from '../../../../../contracts/fixtures/catalogue-v1.json';
import vectors from '../../../../../contracts/fixtures/playlist-vectors.json';
import { vi } from 'vitest';

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
    localStorage.setItem(CACHE_KEY, JSON.stringify(fixture));
    expect(new LiveStore().catalogue().channels.length).toBe(2);
  });
  it('preserves last-known-good cache after invalid refresh', async () => {
    localStorage.setItem(CACHE_KEY, JSON.stringify(fixture));
    const store = new LiveStore();
    vi.spyOn(store, 'fetchText').mockResolvedValue('{"schemaVersion":2}');
    await store.refresh();
    expect(store.error()).toBe(true);
    expect(store.catalogue().channels.length).toBe(2);
    expect(JSON.parse(localStorage.getItem(CACHE_KEY) ?? '{}')).toEqual(fixture);
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
    const published = {
      ...fixture,
      revision: 2,
      developmentFixture: false,
      channels: [...fixture.channels, { ...fixture.channels[0], id: 'official.new', number: 2 }],
    };
    vi.spyOn(store, 'fetchText').mockResolvedValue(JSON.stringify(published));
    await store.refresh();
    expect(store.error()).toBe(false);
    expect(store.data().playlists[0].channels[0].number).toBeNull();
    const restored = new LiveStore();
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
});

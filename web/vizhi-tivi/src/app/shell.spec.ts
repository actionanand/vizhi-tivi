import { TestBed } from '@angular/core/testing';
import { Shell } from './shell';
import { LiveStore } from './core/store';
import { decodeCatalogue } from './core/domain';
import fixtureData from '../../../../contracts/fixtures/catalogue-v1.json';
import { vi } from 'vitest';
import publications from '../../../../contracts/fixtures/official-v1/vectors.json';
import { decodePublication } from './core/published-catalogue';

describe('Live TV navigation', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.spyOn(LiveStore.prototype, 'initial').mockResolvedValue();
  });
  afterEach(() => vi.restoreAllMocks());
  it('reports an export failure without changing the private store', async () => {
    const store = new LiveStore();
    await store.importText('#EXTM3U\n#EXTINF:-1 tvg-id="stable",News\nhttps://example.invalid/live.m3u8', 'file:export', 'Test');
    const before = JSON.stringify(store.data());
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => { throw Error('unavailable'); });
    fixture.componentInstance.exportPlaylists();
    expect(store.snapshotError()).toBe(true);
    expect(JSON.stringify(store.data())).toBe(before);
    fixture.destroy();
  });
  it('shows an honest empty official catalogue without development channels', async () => {
    const store = new LiveStore();
    store.catalogue.set(await decodePublication(publications.empty.files));
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain(store.t('official_empty'));
    fixture.destroy();
  });
  it('labels experimental playback as a trial while preferring verified candidates', async () => {
    const store = new LiveStore();
    store.catalogue.set(await decodePublication(publications.synthetic.files));
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const articles = Array.from(fixture.nativeElement.querySelectorAll('article')) as HTMLElement[];
    expect(
      articles.find((a) => a.textContent?.includes('Synthetic test-english'))?.textContent,
    ).toContain(store.t('trial'));
    expect(
      articles.find((a) => a.textContent?.includes('Synthetic test-tamil'))?.textContent,
    ).not.toContain(store.t('trial'));
    fixture.destroy();
  });
  it('returns keyboard focus to the recreated channel button after closing playback', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
    const store = new LiveStore();
    const catalogue = decodeCatalogue(fixtureData);
    catalogue.channels[0].streams = [
      {
        url: 'https://example.invalid/direct',
        format: 'progressive',
        status: 'approved',
        permitted: true,
      },
    ];
    store.catalogue.set(catalogue);
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.play') as HTMLButtonElement).click();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.activeElement?.id).toBe('player-back');
    fixture.componentInstance.close();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.activeElement?.id).toBe('play-demo.tamil');
    fixture.destroy();
  });
  it('browses metadata without exposing a Play button', async () => {
    const store = new LiveStore();
    store.catalogue.set(decodeCatalogue(fixtureData));
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.play')).toBeNull();
    fixture.destroy();
  });
  it('rebinds a selected channel and closes playback when it is removed', async () => {
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
    const store = new LiveStore();
    const catalogue = await decodePublication(publications.synthetic.files);
    store.catalogue.set(catalogue);
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const channel = catalogue.channels[0];
    fixture.componentInstance.play(channel, { currentTarget: { id: 'test' } } as unknown as Event);
    await fixture.whenStable();
    const updated = { ...channel, streams: [{ ...channel.streams[0], url: 'https://example.invalid/replaced', status: 'experimental' as const }] };
    store.catalogue.set({ ...catalogue, channels: [updated] });
    await fixture.whenStable();
    expect(fixture.componentInstance.selected()).toEqual(updated);
    store.catalogue.set({ ...catalogue, channels: [] });
    await fixture.whenStable();
    expect(fixture.componentInstance.selected()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-player')).toBeNull();
    fixture.destroy();
  });
  it('filters by search and navigates to favourites', async () => {
    const store = new LiveStore();
    store.catalogue.set(decodeCatalogue(fixtureData));
    await store.toggleFavourite('demo.tamil');
    TestBed.configureTestingModule({ providers: [{ provide: LiveStore, useValue: store }] });
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const shell = fixture.componentInstance;
    shell.search.set('English');
    expect(shell.shown().map((c) => c.id)).toEqual(['demo.english']);
    shell.search.set('');
    shell.navigate('favourites');
    expect(shell.shown().map((c) => c.id)).toEqual(['demo.tamil']);
    shell.navigate('imported');
    expect(shell.shown()).toEqual([]);
    fixture.destroy();
  });
});

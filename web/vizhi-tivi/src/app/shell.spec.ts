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
  it('filters by search and navigates to favourites', async () => {
    const store = new LiveStore();
    store.catalogue.set(decodeCatalogue(fixtureData));
    store.toggleFavourite('demo.tamil');
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

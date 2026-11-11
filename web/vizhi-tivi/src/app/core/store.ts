import { Service, computed, signal } from '@angular/core';
import {
  allocate,
  Catalogue,
  Channel,
  decodeCatalogue,
  parseM3u,
  Playlist,
  safeUrl,
  stableId,
} from './domain';
import en from '../locales/en.json';
import ta from '../locales/ta.json';
import sa from '../locales/sa.json';
import {
  OFFICIAL_MANIFEST_URL,
  PUBLICATION_KEY,
  decodePublication,
  loadPublication,
  parsePublicationCache,
  publicationCache,
} from './published-catalogue';

export type Locale = 'en' | 'ta' | 'sa';
export type TextKey = keyof typeof en;
interface DeviceState {
  schemaVersion: 1;
  locale: Locale;
  theme: string;
  favourites: string[];
  recent: string[];
  endpoint: string;
  localNumbers: boolean;
  playlists: Playlist[];
}
const defaults: DeviceState = {
  schemaVersion: 1,
  locale: 'en',
  theme: 'system',
  favourites: [],
  recent: [],
  endpoint: OFFICIAL_MANIFEST_URL,
  localNumbers: false,
  playlists: [],
};
export const DEVICE_KEY = 'vizhi-tivi.device.v1';
export const CACHE_KEY = 'vizhi-tivi.catalogue.v1';

@Service()
export class LiveStore {
  readonly data = signal<DeviceState>(defaults);
  readonly catalogue = signal<Catalogue>({ schemaVersion: 1, revision: null, channels: [] });
  readonly loading = signal(false);
  readonly error = signal(false);
  private writable = true;
  private initialized = false;
  readonly all = computed(() => [
    ...this.catalogue().channels,
    ...this.data().playlists.flatMap((p) => p.channels),
  ]);
  constructor() {
    try {
      const raw = localStorage.getItem(DEVICE_KEY);
      if (raw) {
        const d: DeviceState = JSON.parse(raw);
        if (
          d.schemaVersion !== 1 ||
          !['en', 'ta', 'sa'].includes(d.locale) ||
          !Array.isArray(d.favourites) ||
          !Array.isArray(d.recent) ||
          !Array.isArray(d.playlists)
        )
          throw Error('Invalid private store');
        const playlists = d.playlists.map((p) => ({
          ...p,
          channels: decodeCatalogue({
            schemaVersion: 1,
            revision: 1,
            channels: p.channels,
          }).channels.map((c) => ({ ...c, playlistId: p.id })),
        }));
        this.data.set({
          ...defaults,
          ...d,
          playlists,
          endpoint: d.endpoint?.trim() || OFFICIAL_MANIFEST_URL,
        });
      }
    } catch {
      this.error.set(true);
      this.writable = false;
    }
    try {
      const cache = localStorage.getItem(CACHE_KEY);
      if (cache) {
        const legacy = decodeCatalogue(JSON.parse(cache));
        if (!legacy.developmentFixture) this.catalogue.set(legacy);
      }
      const official = new Set(
        this.catalogue()
          .channels.map((c) => c.number)
          .filter((n) => n !== null),
      );
      this.data.update((d) => ({
        ...d,
        playlists: d.playlists.map((p) => ({
          ...p,
          channels: p.channels.map((c) => ({
            ...c,
            number: c.number !== null && official.has(c.number) ? null : c.number,
          })),
        })),
      }));
    } catch {
      this.error.set(true);
    }
  }
  t(key: TextKey): string {
    return { en, ta, sa }[this.data().locale]?.[key] || en[key];
  }
  save(patch: Partial<DeviceState>): void {
    if (!this.writable) {
      this.error.set(true);
      return;
    }
    try {
      const next = { ...this.data(), ...patch };
      localStorage.setItem(DEVICE_KEY, JSON.stringify(next));
      this.data.set(next);
    } catch {
      this.error.set(true);
    }
  }
  toggleFavourite(id: string): void {
    const ids = this.data().favourites;
    this.save({ favourites: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id] });
  }
  watched(id: string): void {
    this.save({ recent: [id, ...this.data().recent.filter((x) => x !== id)].slice(0, 30) });
  }
  async initial(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;
    await this.operation(async () => {
      try {
        const cached = localStorage.getItem(PUBLICATION_KEY);
        if (cached) this.reconcile(await decodePublication(parsePublicationCache(cached)));
      } catch {
        this.error.set(true);
      }
      await this.refreshPublication();
    });
  }
  async refresh(): Promise<void> {
    await this.operation(() => this.refreshPublication());
  }
  private async refreshPublication(): Promise<void> {
    const result = await loadPublication(
      this.data().endpoint || OFFICIAL_MANIFEST_URL,
      this.catalogue(),
      (url) => this.fetchText(url),
    );
    const next = result?.catalogue ?? this.catalogue();
    if (result) localStorage.setItem(PUBLICATION_KEY, publicationCache(result.files));
    this.reconcile(next);
    this.error.set(false);
  }
  private reconcile(next: Catalogue): void {
    this.catalogue.set(next);
    // Official numbers win. A new official assignment clears a conflicting private assignment.
    const official = new Set(next.channels.map((c) => c.number));
    this.data.update((d) => ({
      ...d,
      playlists: this.data().playlists.map((p) => ({
        ...p,
        channels: p.channels.map((c) => ({
          ...c,
          number: official.has(c.number) ? null : c.number,
        })),
      })),
    }));
  }
  async importText(raw: string, identity: string, name: string): Promise<void> {
    await this.operation(async () => {
      const id = await stableId(identity);
      let channels = await parseM3u(raw, id);
      const others = this.data().playlists.filter((p) => p.id !== id);
      if (this.data().localNumbers) {
        const reserved = [
          ...this.catalogue().channels,
          ...others.flatMap((p) => p.channels),
        ].flatMap((c) => (c.number === null ? [] : [c.number]));
        const old = Object.fromEntries(
          (this.data().playlists.find((p) => p.id === id)?.channels ?? [])
            .filter((c) => c.number !== null && !reserved.includes(c.number))
            .map((c) => [c.id, c.number as number]),
        );
        channels = allocate(channels, old, reserved);
      }
      this.save({ playlists: [...others, { id, name, channels }] });
    });
  }
  async importUrl(url: string): Promise<void> {
    await this.operation(async () => {
      const raw = await this.fetchText(url);
      await this.importText(raw, `url:${url}`, `M3U • ${new URL(url).hostname}`);
    });
  }
  remove(id: string): void {
    this.save({ playlists: this.data().playlists.filter((p) => p.id !== id) });
  }
  correctLanguage(c: Channel, language: string): void {
    this.save({
      playlists: this.data().playlists.map((p) => ({
        ...p,
        channels: p.channels.map((x) =>
          x.id === c.id ? { ...x, languages: [language], number: null } : x,
        ),
      })),
    });
  }
  async operation(fn: () => Promise<void>): Promise<void> {
    this.loading.set(true);
    this.error.set(false);
    try {
      await fn();
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
  async fetchText(raw: string): Promise<string> {
    if (!safeUrl(raw)) throw Error('HTTPS required');
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const result = await fetch(raw, {
          signal: AbortSignal.timeout(8000),
          credentials: 'omit',
          referrerPolicy: 'no-referrer',
        });
        if (!result.ok || !safeUrl(result.url)) throw Error('Network unavailable');
        const reader = result.body?.getReader();
        if (!reader) throw Error('Empty response');
        const chunks: Uint8Array[] = [];
        let size = 0;
        try {
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            size += value.length;
            if (size > 5_000_000) throw Error('Response too large');
            chunks.push(value);
          }
        } finally {
          await reader.cancel();
        }
        const bytes = new Uint8Array(size);
        let offset = 0;
        chunks.forEach((c) => {
          bytes.set(c, offset);
          offset += c.length;
        });
        return new TextDecoder().decode(bytes);
      } catch (e) {
        if (attempt) throw e;
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
    }
    throw Error('Network unavailable');
  }
}

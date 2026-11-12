import { Service, computed, signal, OnDestroy } from '@angular/core';
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
import { RecoverySnapshots, SnapshotRecord } from './recovery-snapshots';
import { withPrivateLock } from './private-lock';
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
export const RECOVERY_KEY = 'vizhi-tivi.device.recovery.v1';
const MAX_PRIVATE_BYTES = 20_000_000;

function inspectPrivate(raw: string): { state: DeviceState; complete: boolean } {
  if (new TextEncoder().encode(raw).length > MAX_PRIVATE_BYTES) throw Error('Private store too large');
  const d: DeviceState = JSON.parse(raw);
  if (!d || d.schemaVersion !== 1 || !['en', 'ta', 'sa'].includes(d.locale) || !Array.isArray(d.favourites) || !Array.isArray(d.recent) || !Array.isArray(d.playlists)) throw Error('Invalid private store');
  const ids = new Set<string>(); const channels = new Set<string>(); const numbers = new Set<number>();
  let complete = true;
  const playlists = d.playlists.flatMap((p) => {
    try {
      if (!p || typeof p.id !== 'string' || !p.id.trim() || typeof p.name !== 'string' || ids.has(p.id)) throw Error('Invalid playlist');
      const decoded = decodeCatalogue({ schemaVersion: 1, revision: 1, channels: p.channels }).channels.map((c) => ({ ...c, playlistId: p.id }));
      if (decoded.some((c) => channels.has(c.id) || (c.number !== null && numbers.has(c.number)))) throw Error('Private record collision');
      ids.add(p.id); decoded.forEach((c) => { channels.add(c.id); if (c.number !== null) numbers.add(c.number); });
      return [{ ...p, channels: decoded }];
    } catch { complete = false; return []; }
  });
  return { state: { ...defaults, ...d, playlists, endpoint: d.endpoint?.trim() || OFFICIAL_MANIFEST_URL }, complete };
}

@Service()
export class LiveStore implements OnDestroy {
  readonly data = signal<DeviceState>(defaults);
  readonly catalogue = signal<Catalogue>({ schemaVersion: 1, revision: null, channels: [] });
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly snapshots = signal<SnapshotRecord[]>([]);
  readonly snapshotError = signal(false);
  readonly storageConflict = signal(false);
  private readonly storage = this.openStorage();
  private readonly recovery = new RecoverySnapshots(this.storage, (raw) => {
    try { const result = inspectPrivate(raw); return { count: result.state.playlists.length, compatibility: result.complete ? 'complete' : 'partial' }; }
    catch { return { count: 0, compatibility: 'incompatible' }; }
  }, true);
  private openStorage(): Storage {
    try { return window.localStorage; }
    catch {
      this.storageConflict.set(true);
      const unavailable = (): never => { throw Error('Private storage unavailable'); };
      return { get length(): number { return unavailable(); }, clear: unavailable, getItem: unavailable, key: unavailable, removeItem: unavailable, setItem: unavailable };
    }
  }
  private writable = true;
  private lastRaw: string | null = null;
  private initialized = false;
  private readonly onStorage = (event: StorageEvent): void => {
    if (event.storageArea !== this.storage) return;
    if (event.key === DEVICE_KEY || event.key === null) this.reloadPrivate();
    if (event.key === null || event.key?.startsWith('vizhi-tivi.recovery.') || event.key === RECOVERY_KEY) void this.reloadSnapshots();
  };
  private reloadPrivate(): void {
    try {
      const raw = this.storage.getItem(DEVICE_KEY);
      if (raw === this.lastRaw) return;
      if (raw === null) { this.data.set(defaults); this.writable = true; }
      else { const parsed = inspectPrivate(raw); this.data.set(parsed.state); this.writable = parsed.complete; }
      this.lastRaw = raw;
      if (!this.writable) this.error.set(true);
    } catch { this.writable = false; this.error.set(true); }
  }
  ngOnDestroy(): void { window.removeEventListener('storage', this.onStorage); }
  private requireCurrent(expected: string | null): void {
    if (this.storage.getItem(DEVICE_KEY) !== expected) {
      this.reloadPrivate();
      this.storageConflict.set(true);
      throw Error('Private data changed in another tab');
    }
  }
  get recoveryRequired(): boolean { return !this.writable; }
  get recoveryBackupAvailable(): boolean { return this.snapshots().length > 0; }
  originalPrivateStore(): string | null {
    try {
      if (!this.writable) return this.storage.getItem(DEVICE_KEY);
      const last = this.snapshots().at(-1);
      return last ? this.recovery.raw(last.id) : this.storage.getItem(RECOVERY_KEY);
    } catch { this.snapshotError.set(true); return null; }
  }
  async reloadSnapshots(): Promise<void> {
    try { this.snapshots.set(await this.recovery.list()); }
    catch { if (!navigator.locks?.request) this.storageConflict.set(true); this.snapshotError.set(true); }
  }
  snapshotRaw(id: string): string | null { try { return this.recovery.raw(id); } catch { this.snapshotError.set(true); return null; } }
  async deleteSnapshot(id: string): Promise<void> {
    const selected = this.snapshots().find((record) => record.id === id);
    try { await withPrivateLock(async () => {
      const current = (await this.recovery.list()).find((record) => record.id === id);
      if (!selected || JSON.stringify(selected) !== JSON.stringify(current)) { this.storageConflict.set(true); throw Error('Snapshot changed'); }
      await this.recovery.remove(id);
    }); this.snapshotError.set(false); await this.reloadSnapshots(); }
    catch { if (!navigator.locks?.request) this.storageConflict.set(true); this.snapshotError.set(true); }
  }
  async restoreSnapshot(id: string): Promise<void> {
    const expected = this.lastRaw;
    const selected = this.snapshots().find((record) => record.id === id);
    try {
      await withPrivateLock(async () => {
      this.requireCurrent(expected);
      const previous = expected;
      const record = (await this.recovery.list()).find((s) => s.id === id);
      if (!selected || JSON.stringify(selected) !== JSON.stringify(record)) { this.storageConflict.set(true); throw Error('Snapshot changed'); }
      if (!record || record.integrity === 'invalid' || !record.recoverableCount) throw Error('Snapshot unavailable');
      const raw = this.recovery.raw(id);
      if (raw === null) throw Error('Snapshot unavailable');
      if (record.integrity === 'valid' && await stableId(raw) !== record.sha256) throw Error('Snapshot integrity changed');
      const candidate = inspectPrivate(raw).state;
      const current = this.data();
      const merged = [...current.playlists];
      for (const p of candidate.playlists) {
        const index = merged.findIndex((x) => x.id === p.id);
        if (index < 0) { merged.push(p); continue; }
        const old = merged[index];
        // Existing records contain the user's latest override and assignment. A
        // recovery snapshot may add missing channels but must not replace them.
        const updated = p.channels.map((c) => old.channels.find((x) => x.id === c.id) ?? c);
        const restoredIds = new Set(updated.map((c) => c.id));
        merged[index] = { ...p, channels: [...updated, ...old.channels.filter((c) => !restoredIds.has(c.id))] };
      }
      const ids = new Set<string>(); const numbers = new Set(this.catalogue().channels.flatMap((c) => c.number === null ? [] : [c.number]));
      for (const p of merged) for (const c of p.channels) {
        if (ids.has(c.id) || (c.number !== null && numbers.has(c.number))) throw Error('Channel conflict');
        ids.add(c.id); if (c.number !== null) numbers.add(c.number);
      }
      const recovered: DeviceState = { ...current, playlists: merged, favourites: [...new Set([...current.favourites, ...candidate.favourites])] };
      const encoded = JSON.stringify(recovered);
      if (new TextEncoder().encode(encoded).length > MAX_PRIVATE_BYTES) throw Error('Private store too large');
      if (previous !== null) await this.recovery.create(previous);
      this.storage.setItem(DEVICE_KEY, encoded);
      if (this.storage.getItem(DEVICE_KEY) !== encoded) throw Error('Private store write failed');
      this.lastRaw = encoded;
      this.data.set(recovered); this.writable = true; this.error.set(false); this.snapshotError.set(false);
      await this.recovery.markRecovered(id);
      });
      await this.reloadSnapshots();
    } catch { if (!navigator.locks?.request) this.storageConflict.set(true); this.snapshotError.set(true); this.error.set(true); await this.reloadSnapshots(); }
  }
  async recoverPlaylists(): Promise<void> {
    if (this.writable) return;
    const expected = this.lastRaw;
    try {
      await withPrivateLock(async () => {
      this.requireCurrent(expected);
      const original = this.storage.getItem(DEVICE_KEY);
      if (original === null) throw Error('Private store missing');
      const recovered = JSON.stringify(this.data());
      if (new TextEncoder().encode(recovered).length > MAX_PRIVATE_BYTES) throw Error('Private store too large');
      await this.recovery.create(original);
      this.storage.setItem(DEVICE_KEY, recovered);
      if (this.storage.getItem(DEVICE_KEY) !== recovered) throw Error('Private store write failed');
      this.lastRaw = recovered;
      this.writable = true;
      this.error.set(false);
      });
      await this.reloadSnapshots();
    } catch { if (!navigator.locks?.request) this.storageConflict.set(true); this.error.set(true); this.snapshotError.set(true); await this.reloadSnapshots(); }
  }
  readonly all = computed(() => [
    ...this.catalogue().channels,
    ...this.data().playlists.flatMap((p) => p.channels),
  ]);
  constructor() {
    try { this.lastRaw = this.storage.getItem(DEVICE_KEY); } catch { this.writable = false; }
    window.addEventListener('storage', this.onStorage);
    void this.reloadSnapshots();
    try {
      const raw = this.storage.getItem(DEVICE_KEY);
      if (raw !== null) {
        if (new TextEncoder().encode(raw).length > MAX_PRIVATE_BYTES) throw Error('Private store too large');
        const d: DeviceState = JSON.parse(raw);
        if (
          d.schemaVersion !== 1 ||
          !['en', 'ta', 'sa'].includes(d.locale) ||
          !Array.isArray(d.favourites) ||
          !Array.isArray(d.recent) ||
          !Array.isArray(d.playlists)
        )
          throw Error('Invalid private store');
        const ids = new Set<string>();
        const channelIds = new Set<string>();
        const numbers = new Set<number>();
        const playlists = d.playlists.flatMap((p) => {
          try {
          if (typeof p.id !== 'string' || !p.id.trim() || typeof p.name !== 'string' || ids.has(p.id)) throw Error('Invalid playlist');
          const decoded = {
          ...p,
          channels: decodeCatalogue({
            schemaVersion: 1,
            revision: 1,
            channels: p.channels,
          }).channels.map((c) => ({ ...c, playlistId: p.id })),
          };
          if (decoded.channels.some((c) => channelIds.has(c.id) || (c.number !== null && numbers.has(c.number)))) throw Error('Private record collision');
          ids.add(p.id);
          decoded.channels.forEach((c) => { channelIds.add(c.id); if (c.number !== null) numbers.add(c.number); });
          return [decoded]; } catch { this.error.set(true); this.writable = false; return []; }
        });
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
      const cache = this.storage.getItem(CACHE_KEY);
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
    } catch { this.error.set(true); }
  }
  t(key: TextKey): string {
    return { en, ta, sa }[this.data().locale]?.[key] || en[key];
  }
  async save(patch: Partial<DeviceState>): Promise<void> {
    if (!this.writable) {
      this.error.set(true);
      return;
    }
    const expected = this.lastRaw;
    try {
      await withPrivateLock(() => {
      this.requireCurrent(expected);
      const next = { ...this.data(), ...patch };
      if (patch.playlists) {
        const ids = new Set<string>();
        const channelIds = new Set<string>();
        const numbers = new Set<number>();
        for (const playlist of patch.playlists) {
          if (!playlist.id.trim() || ids.has(playlist.id) || typeof playlist.name !== 'string') throw Error('Invalid playlist');
          ids.add(playlist.id);
          const decoded = decodeCatalogue({ schemaVersion: 1, revision: 1, channels: playlist.channels });
          for (const channel of decoded.channels) {
            if (channelIds.has(channel.id) || (channel.number !== null && numbers.has(channel.number))) throw Error('Private record collision');
            channelIds.add(channel.id);
            if (channel.number !== null) numbers.add(channel.number);
          }
        }
      }
      const serialized = JSON.stringify(next);
      if (new TextEncoder().encode(serialized).length > MAX_PRIVATE_BYTES) throw Error('Private store too large');
      this.storage.setItem(DEVICE_KEY, serialized);
      this.lastRaw = serialized;
      this.data.set(next);
      this.storageConflict.set(false);
      });
    } catch {
      if (!navigator.locks?.request) this.storageConflict.set(true);
      this.error.set(true);
    }
  }
  toggleFavourite(id: string): Promise<void> {
    const ids = this.data().favourites;
    return this.save({ favourites: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id] });
  }
  watched(id: string): Promise<void> {
    return this.save({ recent: [id, ...this.data().recent.filter((x) => x !== id)].slice(0, 30) });
  }
  async initial(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;
    await this.operation(async () => {
      try {
        const cached = this.storage.getItem(PUBLICATION_KEY);
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
    if (result) this.storage.setItem(PUBLICATION_KEY, publicationCache(result.files));
    this.reconcile(next);
    this.error.set(!this.writable);
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
      const previous = this.data().playlists.find((p) => p.id === id)?.channels ?? [];
      channels = channels.map((c) => {
        const override = previous.find((old) => old.id === c.id)?.languageOverride;
        return override ? { ...c, languages: [override], languageOverride: override } : c;
      });
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
      await this.save({ playlists: [...others, { id, name, channels }] });
    });
  }
  async importUrl(url: string): Promise<void> {
    await this.operation(async () => {
      const raw = await this.fetchText(url);
      await this.importText(raw, `url:${url}`, `M3U • ${new URL(url).hostname}`);
    });
  }
  remove(id: string): Promise<void> {
    return this.save({ playlists: this.data().playlists.filter((p) => p.id !== id) });
  }
  correctLanguage(c: Channel, language: string): Promise<void> {
    return this.save({
      playlists: this.data().playlists.map((p) => ({
        ...p,
        channels: p.channels.map((x) =>
          x.id === c.id ? { ...x, languages: [language], number: x.languages.includes('ta') === (language === 'ta') ? x.number : null, languageOverride: language } : x,
        ),
      })),
    });
  }
  resetLanguage(c: Channel): Promise<void> {
    if (!c.sourceLanguages) return Promise.resolve();
    return this.save({ playlists: this.data().playlists.map((p) => ({ ...p, channels: p.channels.map((x) => {
      if (x.id !== c.id || !x.sourceLanguages) return x;
      return { ...x, languageOverride: undefined, languages: x.sourceLanguages, number: x.languages.includes('ta') === x.sourceLanguages.includes('ta') ? x.number : null };
    }) })) });
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
      if (!this.writable) this.error.set(true);
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

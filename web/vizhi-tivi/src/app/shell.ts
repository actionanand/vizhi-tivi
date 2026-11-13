import { Component, computed, effect, inject, signal } from '@angular/core';
import { LiveStore, Locale, TextKey } from './core/store';
import { Channel, playable } from './core/domain';
import { displayLanguage } from './core/language-display';
import { LivePlayer } from './player';
import { DOCUMENT, NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'app-shell',
  imports: [LivePlayer, NgOptimizedImage],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  readonly store = inject(LiveStore);
  readonly page = signal<TextKey>('home');
  readonly search = signal('');
  readonly language = signal('');
  readonly category = signal('');
  readonly country = signal('');
  readonly selectedId = signal<string | null>(null);
  readonly selected = computed(() => {
    const channel = this.store.all().find((c) => c.id === this.selectedId());
    return channel && playable(channel) ? channel : null;
  });
  readonly removeId = signal<string | null>(null);
  readonly deleteSnapshotId = signal<string | null>(null);
  readonly recoverDialog = signal(false);
  readonly nav: TextKey[] = ['home', 'channels', 'favourites', 'imported', 'settings'];
  readonly url = signal('');
  readonly name = signal('My IPTV');
  readonly endpoint = signal(this.store.data().endpoint);
  readonly playable = playable;
  readonly emptyMessage = computed<TextKey>(() =>
    ['home', 'channels'].includes(this.page()) &&
    this.store.catalogue().contentSha256 &&
    !this.store.catalogue().channels.length
      ? 'official_empty'
      : 'empty',
  );
  private readonly document = inject(DOCUMENT);
  private openerId = '';
  readonly base = computed(() =>
    this.page() === 'imported'
      ? this.store.all().filter((c) => c.playlistId)
      : this.page() === 'favourites'
        ? this.store.all().filter((c) => this.store.data().favourites.includes(c.id))
        : this.store.catalogue().channels,
  );
  readonly languages = computed(() => [...new Set(this.base().flatMap((c) => c.languages))].sort());
  readonly languageChoices = computed(() => [...new Set([...this.languages(), 'ta', 'en', 'sa', 'hi', 'ml', 'te', 'kn', 'si', 'und'])].sort());
  readonly categories = computed(() => [...new Set(this.base().map((c) => c.category))].sort());
  readonly countries = computed(() =>
    [...new Set(this.base().map((c) => c.country))].filter(Boolean).sort(),
  );
  readonly pageIndex = signal(0);
  readonly pageSize = 80;
  readonly shown = computed(() =>
    this.base()
      .filter(
        (c) =>
          c.name.toLowerCase().includes(this.search().toLowerCase()) &&
          (!this.language() || c.languages.includes(this.language())) &&
          (!this.category() || c.category === this.category()) &&
          (!this.country() || c.country === this.country()),
      )
      .sort(
        (a, b) => (a.number ?? Infinity) - (b.number ?? Infinity) || a.name.localeCompare(b.name),
      ),
  );
  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.shown().length / this.pageSize)));
  readonly shownPage = computed(() => {
    const index = Math.min(this.pageIndex(), this.pageCount() - 1);
    return this.shown().slice(index * this.pageSize, (index + 1) * this.pageSize);
  });
  readonly recent = computed(() =>
    this.store
      .data()
      .recent.map((id) => this.store.all().find((c) => c.id === id))
      .filter((c): c is Channel => !!c),
  );
  constructor() {
    effect(() => {
      if (this.selectedId() && !this.selected()) this.selectedId.set(null);
    });
    void this.store.initial();
  }
  previousPage(): void {
    this.pageIndex.set(Math.max(0, this.pageIndex() - 1));
  }
  nextPage(): void {
    this.pageIndex.set(Math.min(this.pageCount() - 1, this.pageIndex() + 1));
  }
  languageLabel(code: string): string {
    return displayLanguage(code, this.store.data().locale);
  }
  languageLabels(channels: string[]): string {
    return channels.map((lang) => this.languageLabel(lang)).join(', ');
  }
  value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
  logoFallback(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/app-icon.png')) image.src = 'app-icon.png';
  }
  navigate(page: TextKey): void {
    this.page.set(page);
    this.language.set('');
    this.category.set('');
    this.country.set('');
    this.pageIndex.set(0);
  }
  setLocale(event: Event): void {
    const locale = this.value(event);
    if (locale === 'en' || locale === 'ta' || locale === 'sa')
      this.store.save({ locale: locale as Locale });
  }
  play(c: Channel, event: Event): void {
    if (playable(c)) {
      this.openerId = (event.currentTarget as HTMLElement).id;
      this.selectedId.set(c.id);
      this.store.watched(c.id);
      setTimeout(() => this.document.getElementById('player-back')?.focus());
    }
  }
  close(): void {
    this.selectedId.set(null);
    setTimeout(() => this.document.getElementById(this.openerId)?.focus());
  }
  exportPlaylists(): void {
    const data = JSON.stringify({ schemaVersion: 1, playlists: this.store.data().playlists });
    this.downloadPrivate(data, 'vizhi-tivi-playlists.json');
  }
  exportOriginal(): void {
    const data = this.store.originalPrivateStore();
    if (data !== null) this.downloadPrivate(data, 'vizhi-tivi-original.json');
  }
  exportSnapshot(id: string): void {
    const raw = this.store.snapshotRaw(id);
    if (raw !== null) this.downloadPrivate(raw, 'vizhi-tivi-recovery.json');
  }
  private downloadPrivate(data: string, name: string): void {
    let url: string | undefined;
    try {
      url = URL.createObjectURL(new Blob([data], { type: 'application/json' }));
      const link = this.document.createElement('a');
      link.href = url;
      link.download = name;
      link.click();
    } catch {
      this.store.snapshotError.set(true);
    } finally {
      if (url) URL.revokeObjectURL(url);
    }
  }
  async file(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      await this.store.operation(async () => {
        if (file.size > (file.name.toLowerCase().endsWith('.vztv') ? 12_000_000 : 5_000_000)) throw Error('Too large');
        await this.store.importText(await file.text(), `file:${this.name()}`, this.name());
      });
      input.value = '';
    }
  }
}

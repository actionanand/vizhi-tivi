import { Component, computed, inject, signal } from '@angular/core';
import { LiveStore, Locale, TextKey } from './core/store';
import { Channel, playable } from './core/domain';
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
  readonly selected = signal<Channel | null>(null);
  readonly removeId = signal<string | null>(null);
  readonly nav: TextKey[] = ['home', 'channels', 'favourites', 'imported', 'settings'];
  readonly url = signal('');
  readonly name = signal('My IPTV');
  readonly endpoint = signal(this.store.data().endpoint);
  readonly playable = playable;
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
  readonly categories = computed(() => [...new Set(this.base().map((c) => c.category))].sort());
  readonly countries = computed(() =>
    [...new Set(this.base().map((c) => c.country))].filter(Boolean).sort(),
  );
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
  readonly recent = computed(() =>
    this.store
      .data()
      .recent.map((id) => this.store.all().find((c) => c.id === id))
      .filter((c): c is Channel => !!c),
  );
  constructor() {
    void this.store.initial();
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
  }
  setLocale(event: Event): void {
    const locale = this.value(event);
    if (locale === 'en' || locale === 'ta' || locale === 'sa')
      this.store.save({ locale: locale as Locale });
  }
  play(c: Channel, event: Event): void {
    if (playable(c)) {
      this.openerId = (event.currentTarget as HTMLElement).id;
      this.selected.set(c);
      this.store.watched(c.id);
      setTimeout(() => this.document.getElementById('player-back')?.focus());
    }
  }
  close(): void {
    this.selected.set(null);
    setTimeout(() => this.document.getElementById(this.openerId)?.focus());
  }
  async file(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      await this.store.operation(async () => {
        if (file.size > 5_000_000) throw Error('Too large');
        await this.store.importText(await file.text(), `file:${this.name()}`, this.name());
      });
      input.value = '';
    }
  }
}

import {
  Component,
  computed,
  effect,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Channel, playable } from './core/domain';
import { LiveStore } from './core/store';
import { inject } from '@angular/core';
import type Hls from 'hls.js';

@Component({
  selector: 'app-player',
  host: { '(keydown.escape)': 'closed.emit()' },
  template: ` <section class="player" [attr.aria-label]="store.t('live')">
    <header>
      <button id="player-back" (click)="closed.emit()">← {{ store.t('back') }}</button
      ><img
        [src]="channel().logo || 'app-icon.png'"
        alt=""
        width="48"
        height="48"
        referrerpolicy="no-referrer"
        (error)="logoFallback($event)"
      />
      <h2>{{ channel().name }}</h2>
      <span>{{ store.t('live') }}</span>
      @if (isTrial()) {
        <span>{{ store.t('trial') }}</span>
      }
    </header>
    <video
      #video
      controls
      playsinline
      (waiting)="buffering.set(true)"
      (playing)="buffering.set(false)"
      (error)="failed.set(true)"
      [attr.aria-label]="channel().name"
    ></video>
    @if (buffering() && !failed()) {
      <p role="status">{{ store.t('loading') }}</p>
    }
    @if (failed()) {
      <p role="alert">{{ store.t('playback_error') }} {{ store.t('browser_notice') }}</p>
      <button (click)="retry.update(increment)">{{ store.t('retry') }}</button>
    }
    <button (click)="fullscreen()">{{ store.t('fullscreen') }}</button>
    @if (audio().length) {
      <label
        >{{ store.t('audio_track')
        }}<select (change)="setAudio($event)">
          @for (a of audio(); track $index) {
            <option [value]="$index">{{ a }}</option>
          }
        </select></label
      >
    }
    @if (subtitles().length) {
      <label
        >{{ store.t('subtitle_track') }}<select (change)="setSubtitle($event)">
          <option value="-1">{{ store.t('off') }}</option>
          @for (s of subtitles(); track $index) {
            <option [value]="$index">{{ s }}</option>
          }
        </select></label
      >
    }
  </section>`,
  styles: [
    `
      :host {
        display: block;
      }
      header {
        display: flex;
        gap: 1rem;
        align-items: center;
        flex-wrap: wrap;
      }
      h2 {
        flex: 1;
      }
      video {
        width: 100%;
        max-height: 70vh;
        background: #000;
      }
      button,
      select {
        min-height: 48px;
        padding: 0.6rem 1rem;
      }
      label {
        margin: 1rem;
      }
      .player {
        padding: 1rem;
        border: 1px solid #526777;
        border-radius: 16px;
      }
    `,
  ],
})
export class LivePlayer {
  readonly channel = input.required<Channel>();
  readonly closed = output<void>();
  readonly store = inject(LiveStore);
  readonly isTrial = computed(() => playable(this.channel())?.status === 'experimental');
  readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  readonly failed = signal(false);
  readonly buffering = signal(true);
  readonly retry = signal(0);
  readonly audio = signal<string[]>([]);
  readonly subtitles = signal<string[]>([]);
  readonly increment = (n: number) => n + 1;
  private hls?: Hls;
  constructor() {
    effect((onCleanup) => {
      const element = this.video()?.nativeElement;
      const stream = playable(this.channel());
      this.retry();
      if (!element || !stream) return;
      this.failed.set(false);
      this.buffering.set(true);
      this.audio.set([]);
      this.subtitles.set([]);
      let disposed = false;
      const play = () => {
        if (!disposed)
          void element.play().catch(() => {
            this.buffering.set(false);
          });
      };
      if (stream.format === 'dash') {
        this.failed.set(true);
        this.buffering.set(false);
      } else if (stream.format === 'hls' && !element.canPlayType('application/vnd.apple.mpegurl')) {
        void import('hls.js')
          .then(({ default: Hls }) => {
            if (disposed) return;
            if (!Hls.isSupported()) {
              this.failed.set(true);
              return;
            }
            const hls = new Hls({
              manifestLoadPolicy: {
                default: {
                  maxTimeToFirstByteMs: 8000,
                  maxLoadTimeMs: 10000,
                  timeoutRetry: { maxNumRetry: 2, retryDelayMs: 500, maxRetryDelayMs: 2000 },
                  errorRetry: { maxNumRetry: 2, retryDelayMs: 500, maxRetryDelayMs: 2000 },
                },
              },
            });
            this.hls = hls;
            hls.loadSource(stream.url);
            hls.attachMedia(element);
            hls.on(Hls.Events.MANIFEST_PARSED, () => {
              this.audio.set(hls.audioTracks.map((t) => t.name || t.lang || this.store.t('audio_track')));
              this.subtitles.set(hls.subtitleTracks.map((t) => t.name || t.lang || this.store.t('subtitle_track')));
              play();
            });
            hls.on(Hls.Events.ERROR, (_, data) => {
              if (data.fatal) {
                this.failed.set(true);
                this.buffering.set(false);
                hls.destroy();
                this.hls = undefined;
              }
            });
          })
          .catch(() => this.failed.set(true));
      } else {
        element.src = stream.url;
        play();
      }
      const hidden = () => {
        if (document.hidden) element.pause();
      };
      document.addEventListener('visibilitychange', hidden);
      onCleanup(() => {
        disposed = true;
        document.removeEventListener('visibilitychange', hidden);
        this.hls?.destroy();
        this.hls = undefined;
        element.pause();
        element.removeAttribute('src');
        element.load();
      });
    });
  }
  setAudio(event: Event): void {
    if (this.hls) this.hls.audioTrack = Number((event.target as HTMLSelectElement).value);
  }
  logoFallback(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!image.src.endsWith('/app-icon.png')) image.src = 'app-icon.png';
  }
  setSubtitle(event: Event): void {
    if (this.hls) this.hls.subtitleTrack = Number((event.target as HTMLSelectElement).value);
  }
  fullscreen(): void {
    void this.video()
      ?.nativeElement.requestFullscreen?.()
      .catch(() => this.failed.set(true));
  }
}

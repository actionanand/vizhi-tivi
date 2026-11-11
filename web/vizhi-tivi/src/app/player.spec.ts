import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { LivePlayer } from './player';
import { Channel } from './core/domain';

describe('Foreground player lifecycle', () => {
  const c: Channel = {
    id: 'test',
    number: 251,
    name: 'Test',
    languages: ['en'],
    country: 'IN',
    region: '',
    category: 'Test',
    provider: 'Test',
    source: 'Test',
    streams: [
      {
        url: 'https://example.invalid/direct',
        format: 'progressive',
        status: 'approved',
        permitted: true,
      },
    ],
  };
  beforeEach(() => {
    localStorage.clear();
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
  });
  afterEach(() => vi.restoreAllMocks());
  it('loads only after selecting a playable channel and releases on close', async () => {
    const fixture = TestBed.createComponent(LivePlayer);
    fixture.componentRef.setInput('channel', c);
    await fixture.whenStable();
    const video = fixture.nativeElement.querySelector('video') as HTMLVideoElement;
    expect(video.src).toBe(c.streams[0].url);
    fixture.destroy();
    expect(video.getAttribute('src')).toBeNull();
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled();
  });
  it('switches channels using the same video element', async () => {
    const fixture = TestBed.createComponent(LivePlayer);
    fixture.componentRef.setInput('channel', c);
    await fixture.whenStable();
    const video = fixture.nativeElement.querySelector('video');
    fixture.componentRef.setInput('channel', {
      ...c,
      id: 'other',
      streams: [{ ...c.streams[0], url: 'https://example.invalid/other' }],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('video').length).toBe(1);
    expect(video.src).toBe('https://example.invalid/other');
    fixture.destroy();
  });
  it('shows an honest error for unsupported DASH playback', async () => {
    const fixture = TestBed.createComponent(LivePlayer);
    fixture.componentRef.setInput('channel', {
      ...c,
      streams: [{ ...c.streams[0], format: 'dash' }],
    });
    await fixture.whenStable();
    expect(fixture.componentInstance.failed()).toBe(true);
    expect(HTMLMediaElement.prototype.play).not.toHaveBeenCalled();
    fixture.destroy();
  });
  it('handles autoplay rejection without reconnect loops', async () => {
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValue(Error('Autoplay denied'));
    const fixture = TestBed.createComponent(LivePlayer);
    fixture.componentRef.setInput('channel', c);
    await fixture.whenStable();
    expect(fixture.componentInstance.buffering()).toBe(false);
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });
});

import { LEGACY_RECOVERY_KEY, RecoverySnapshots, SNAPSHOT_LIMIT, SNAPSHOT_PREFIX } from './recovery-snapshots';
import { vi } from 'vitest';

describe('Private recovery snapshots', () => {
  beforeEach(() => localStorage.clear());
  const snapshots = () => new RecoverySnapshots(localStorage, (raw) => ({ count: raw.startsWith('good') ? 1 : 0, compatibility: raw.startsWith('good') ? 'complete' : 'incompatible' }));

  it('preserves three consecutive snapshots and exports the selected original', async () => {
    const manager = snapshots();
    const ids = [];
    for (const raw of ['good-one', 'good-two', 'good-three']) ids.push(await manager.create(raw));
    const records = await manager.list();
    expect(records).toHaveLength(3);
    expect(new Set(ids).size).toBe(3);
    expect(manager.raw(ids[1])).toBe('good-two');
    expect(records.every((r) => r.integrity === 'valid' && r.recoverableCount === 1)).toBe(true);
  });
  it('never silently removes a snapshot at the retention limit', async () => {
    const manager = snapshots();
    for (let i = 0; i < SNAPSHOT_LIMIT; i++) await manager.create(`good-${i}`);
    await expect(manager.create('good-overflow')).rejects.toThrow();
    expect(await manager.list()).toHaveLength(SNAPSHOT_LIMIT);
  });
  it('refuses to delete the only recoverable snapshot', async () => {
    const manager = snapshots();
    const only = await manager.create('good-only');
    await manager.create('broken');
    await expect(manager.remove(only)).rejects.toThrow();
    const second = await manager.create('good-second');
    await manager.remove(only);
    expect(manager.raw(only)).toBeNull();
    expect(manager.raw(second)).toBe('good-second');
  });
  it('detects corrupted envelopes and modified content without leaking them', async () => {
    const manager = snapshots();
    const id = await manager.create('good-original');
    const key = SNAPSHOT_PREFIX + id;
    const envelope = JSON.parse(localStorage.getItem(key) ?? '{}');
    envelope.raw = 'good-modified';
    envelope.byteSize = new TextEncoder().encode(envelope.raw).length;
    localStorage.setItem(key, JSON.stringify(envelope));
    expect((await manager.list())[0].integrity).toBe('invalid');
    localStorage.setItem(key, '{broken');
    expect((await manager.list())[0].integrity).toBe('invalid');
    expect(manager.raw(id)).toBe('{broken');
  });
  it('retains the legacy backup and counts it toward capacity', async () => {
    localStorage.setItem(LEGACY_RECOVERY_KEY, 'good-legacy');
    const manager = snapshots();
    const records = await manager.list();
    expect(records[0].integrity).toBe('unverified');
    expect(manager.raw('legacy')).toBe('good-legacy');
    for (let i = 1; i < SNAPSHOT_LIMIT; i++) await manager.create(`good-${i}`);
    await expect(manager.create('good-overflow')).rejects.toThrow();
    expect(localStorage.getItem(LEGACY_RECOVERY_KEY)).toBe('good-legacy');
  });
  it('propagates storage quota failure without removing existing snapshots', async () => {
    const manager = snapshots();
    const first = await manager.create('good-first');
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw Error('quota'); });
    try {
      await expect(manager.create('good-second')).rejects.toThrow('quota');
      expect(manager.raw(first)).toBe('good-first');
    } finally { write.mockRestore(); }
  });
  it('serializes competing creators at the inventory limit', async () => {
    const first = snapshots(); const second = snapshots();
    for (let i = 0; i < SNAPSHOT_LIMIT - 1; i++) await first.create(`good-${i}`);
    const results = await Promise.allSettled([first.create('good-a'), second.create('good-b')]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect(results.filter((result) => result.status === 'rejected')).toHaveLength(1);
    expect(await first.list()).toHaveLength(SNAPSHOT_LIMIT);
  });
  it('preserves existing snapshots when one competing creator exceeds quota', async () => {
    const first = snapshots(); const second = snapshots();
    const existing = await first.create('good-existing');
    const original = Storage.prototype.setItem;
    const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (value.includes('good-quota')) throw Error('quota');
      original.call(this, key, value);
    });
    try {
      const results = await Promise.allSettled([first.create('good-quota'), second.create('good-success')]);
      expect(results.map((result) => result.status)).toEqual(['rejected', 'fulfilled']);
      expect(first.raw(existing)).toBe('good-existing');
      expect(await first.list()).toHaveLength(2);
    } finally { write.mockRestore(); }
  });
});

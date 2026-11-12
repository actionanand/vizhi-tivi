import { stableId } from './domain';
import { withPrivateLock } from './private-lock';

export const SNAPSHOT_PREFIX = 'vizhi-tivi.recovery.snapshot.v1.';
export const LEGACY_RECOVERY_KEY = 'vizhi-tivi.device.recovery.v1';
export const SNAPSHOT_LIMIT = 5;
const MAX_SNAPSHOT_BYTES = 20_000_000;

export interface SnapshotEnvelope {
  formatVersion: 1;
  id: string;
  createdAt: string;
  byteSize: number;
  sha256: string;
  recoveryStatus: 'saved' | 'recovered';
  raw: string;
}

export interface SnapshotRecord {
  id: string;
  createdAt: string;
  byteSize: number;
  formatVersion: number | null;
  integrity: 'valid' | 'invalid' | 'unverified';
  recoveryStatus: 'saved' | 'recovered' | 'unknown';
  recoverableCount: number;
  compatibility: 'complete' | 'partial' | 'incompatible';
  sha256?: string;
}

export type InspectPayload = (raw: string) => { count: number; compatibility: SnapshotRecord['compatibility'] };

/** Each envelope is stored in one atomic localStorage entry; no automatic deletion is performed. */
export class RecoverySnapshots {
  private writing = false;
  constructor(private readonly storage: Storage, private readonly inspectPayload: InspectPayload, private readonly externallyLocked = false) {}

  private locked<T>(operation: () => Promise<T>): Promise<T> {
    return this.externallyLocked ? operation() : withPrivateLock(operation);
  }

  private keys(): string[] {
    const keys: string[] = [];
    for (let i = 0; i < this.storage.length; i++) {
      const key = this.storage.key(i);
      if (key?.startsWith(SNAPSHOT_PREFIX)) keys.push(key);
    }
    if (this.storage.getItem(LEGACY_RECOVERY_KEY) !== null) keys.push(LEGACY_RECOVERY_KEY);
    return keys.sort();
  }

  async list(): Promise<SnapshotRecord[]> {
    return Promise.all(this.keys().map(async (key) => {
      const stored = this.storage.getItem(key);
      if (stored === null) throw Error('Snapshot disappeared');
      const id = key === LEGACY_RECOVERY_KEY ? 'legacy' : key.slice(SNAPSHOT_PREFIX.length);
      const invalid: SnapshotRecord = { id, createdAt: '', byteSize: new TextEncoder().encode(stored).length, formatVersion: null, integrity: 'invalid', recoveryStatus: 'unknown', recoverableCount: 0, compatibility: 'incompatible' };
      if (invalid.byteSize > MAX_SNAPSHOT_BYTES * 2) return invalid;
      if (id === 'legacy') {
        const inspected = this.inspectPayload(stored);
        return { ...invalid, byteSize: new TextEncoder().encode(stored).length, integrity: 'unverified' as const, ...{ recoverableCount: inspected.count, compatibility: inspected.compatibility } };
      }
      try {
        const envelope: SnapshotEnvelope = JSON.parse(stored);
        if (envelope.formatVersion !== 1 || envelope.id !== id || !/^[a-f0-9-]{36}$/.test(id) || typeof envelope.raw !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(envelope.createdAt) || !['saved', 'recovered'].includes(envelope.recoveryStatus) || !/^[a-f0-9]{64}$/.test(envelope.sha256) || envelope.byteSize !== new TextEncoder().encode(envelope.raw).length) return invalid;
        const inspected = this.inspectPayload(envelope.raw);
        return { id, createdAt: envelope.createdAt, byteSize: envelope.byteSize, formatVersion: 1, integrity: (await stableId(envelope.raw)) === envelope.sha256 ? 'valid' : 'invalid', recoveryStatus: envelope.recoveryStatus, recoverableCount: inspected.count, compatibility: inspected.compatibility, sha256: envelope.sha256 };
      } catch { return invalid; }
    }));
  }

  async create(raw: string): Promise<string> {
    return this.locked(() => this.createLocked(raw));
  }
  private async createLocked(raw: string): Promise<string> {
    if (this.writing) throw Error('Recovery operation in progress');
    this.writing = true;
    try {
    if (this.keys().length >= SNAPSHOT_LIMIT) throw Error('Recovery storage is full');
    if (new TextEncoder().encode(raw).length > MAX_SNAPSHOT_BYTES) throw Error('Snapshot too large');
    const id = crypto.randomUUID();
    const key = SNAPSHOT_PREFIX + id;
    if (this.storage.getItem(key) !== null) throw Error('Snapshot identity collision');
    const envelope: SnapshotEnvelope = { formatVersion: 1, id, createdAt: new Date().toISOString(), byteSize: new TextEncoder().encode(raw).length, sha256: await stableId(raw), recoveryStatus: 'saved', raw };
    const encoded = JSON.stringify(envelope);
    this.storage.setItem(key, encoded);
    if (this.storage.getItem(key) !== encoded) throw Error('Snapshot write failed');
    return id;
    } finally { this.writing = false; }
  }

  raw(id: string): string | null {
    if (id === 'legacy') return this.storage.getItem(LEGACY_RECOVERY_KEY);
    if (!/^[a-f0-9-]{36}$/.test(id)) return null;
    const stored = this.storage.getItem(SNAPSHOT_PREFIX + id);
    if (stored === null) return null;
    try { const parsed: unknown = JSON.parse(stored); return typeof (parsed as SnapshotEnvelope)?.raw === 'string' ? (parsed as SnapshotEnvelope).raw : stored; } catch { return stored; }
  }

  async markRecovered(id: string): Promise<void> {
    return this.locked(() => this.markRecoveredLocked(id));
  }
  private async markRecoveredLocked(id: string): Promise<void> {
    if (id === 'legacy') return;
    const key = SNAPSHOT_PREFIX + id;
    const stored = this.storage.getItem(key);
    if (stored === null) return;
    try {
      const envelope: SnapshotEnvelope = JSON.parse(stored);
      if (envelope.id === id) this.storage.setItem(key, JSON.stringify({ ...envelope, recoveryStatus: 'recovered' }));
    } catch { /* Snapshot remains exportable if marking fails. */ }
  }

  async remove(id: string): Promise<void> {
    return this.locked(() => this.removeLocked(id));
  }
  private async removeLocked(id: string): Promise<void> {
    const records = await this.list();
    if (!records.some((r) => r.id === id)) throw Error('Snapshot missing');
    if (!records.some((r) => r.id !== id && r.integrity === 'valid' && r.recoverableCount > 0)) throw Error('Keep a recoverable snapshot');
    this.storage.removeItem(id === 'legacy' ? LEGACY_RECOVERY_KEY : SNAPSHOT_PREFIX + id);
  }
}

/** Serializes private-store read/modify/write operations across same-origin tabs.
 * Web Locks releases the lock if a tab terminates. Without it, writes fail closed. */
export function withPrivateLock<T>(operation: () => Promise<T> | T): Promise<T> {
  if (typeof navigator === 'undefined' || !navigator.locks?.request) {
    return Promise.reject(new Error('Private storage coordination unavailable'));
  }
  return navigator.locks.request('vizhi-tivi.private-storage.v1', { mode: 'exclusive' }, operation);
}

/** Test-only FIFO lock shared by all simulated same-origin contexts in one worker. */
let tail = Promise.resolve();
Object.defineProperty(navigator, 'locks', {
  configurable: true,
  value: {
    request: <T>(_name: string, _options: unknown, callback: () => Promise<T> | T): Promise<T> => {
      const result = tail.then(callback);
      tail = result.then(() => undefined, () => undefined);
      return result;
    },
  },
});

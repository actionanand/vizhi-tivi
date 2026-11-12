import { defineConfig } from 'vitest/config';

export default defineConfig({ test: { maxWorkers: 2, setupFiles: ['./src/app/core/test-locks.ts'] } });

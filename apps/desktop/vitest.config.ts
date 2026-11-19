import { defineConfig } from 'vitest/config'

// Electron 없이 도는 순수 모듈만 테스트한다.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})

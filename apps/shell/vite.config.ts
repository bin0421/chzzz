/// <reference types="vitest/config" />
import { defineConfig } from 'vite'

// 앱이 file://(데스크톱)이나 앱 내부 출처(Android)에서 띄우므로 상대 경로로 빌드한다.
export default defineConfig({
  base: './',
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})

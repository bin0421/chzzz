/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
      // 라이브 방송 WHIP/WHEP: /media 접두어를 떼서 MediaMTX WebRTC HTTP 포트로 넘긴다
      '/media': {
        target: process.env.VITE_MEDIA_PROXY_TARGET ?? 'http://localhost:8889',
        rewrite: (path) => path.replace(/^\/media/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})

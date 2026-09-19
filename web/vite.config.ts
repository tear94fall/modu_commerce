/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// 앱(WebView)과 브라우저 모두 같은 출처의 /api, /auth-service 를 부른다. 개발은 Vite 가, 배포는 nginx 가 프록시한다.
// 그래서 commerce-service 에 CORS 설정이 없어도 된다.
export default defineConfig({
  plugins: [react()],
  server: {
    // --host 로 LAN 에 열어 폰의 WebView 가 Mac 의 dev 서버를 연다(HMR 포함).
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.COMMERCE_URL ?? 'http://localhost:8200', changeOrigin: true },
      '/auth-service': { target: process.env.GATEWAY_URL ?? 'http://localhost:8000', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
    globals: true,
  },
})

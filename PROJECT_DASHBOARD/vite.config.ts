/**
 * ============================================================================
 * FILE README: vite.config.ts
 * ============================================================================
 * Purpose:
 *   Vite bundler and development server configuration for PROJECT_DASHBOARD.
 *
 * Key Integration Details:
 *   - Proxies REST API requests (`/api/*`) to the Spring Boot orchestration
 *     server (`PROJECT_BACKEND`) running at `http://localhost:8080`.
 *   - Proxies WebSocket connections (`/ws/*`) to `ws://localhost:8080` so
 *     live edge pose telemetry (`/ws/telemetry`) streams seamlessly in dev mode.
 * ============================================================================
 */
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})


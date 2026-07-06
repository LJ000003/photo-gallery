import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['pwa-icon.svg'],
      manifest: {
        name: '照片管理器',
        short_name: '照片管理器',
        description: '私人照片管理应用 — 朋友间的私人图库',
        theme_color: '#0a0a0f',
        background_color: '#0a0a0f',
        display: 'standalone',
        orientation: 'any',
        start_url: '/',
        icons: [
          {
            src: 'pwa-icon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,svg,woff,woff2,ttf}'],
        runtimeCaching: [
          {
            urlPattern: /\/api\/photos\/\d+\/thumbnail/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'thumbnails',
              expiration: { maxEntries: 500, maxAgeSeconds: 7 * 24 * 60 * 60 },
            },
          },
          {
            urlPattern: /\/api\/photos\/\d+\/(file|webp)/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'photos',
              expiration: { maxEntries: 100, maxAgeSeconds: 24 * 60 * 60 },
            },
          },
          {
            urlPattern: /\/api\/(photos|tags|categories|albums|timeline|map)/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api',
              expiration: { maxEntries: 50, maxAgeSeconds: 5 * 60 },
            },
          },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/share': 'http://localhost:8080',
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          gsap: ['gsap'],
          lottie: ['lottie-web'],
          leaflet: ['leaflet'],
        },
      },
    },
  },
});

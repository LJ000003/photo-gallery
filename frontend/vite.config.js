import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/share': 'http://localhost:8080'
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'gsap': ['gsap'],
          'lottie': ['lottie-web'],
          'leaflet': ['leaflet']
        }
      }
    }
  }
});

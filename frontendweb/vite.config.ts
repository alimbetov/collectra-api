import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiBaseUrl = process.env.API_BASE_URL ?? process.env.VITE_API_BASE_URL ?? '';
const devProxyTarget = apiBaseUrl || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  define: {
    __API_BASE_URL__: JSON.stringify(apiBaseUrl),
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true,
      },
    },
  },
});

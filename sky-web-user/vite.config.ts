import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/ai-api': {
        target: process.env.VITE_AI_API_TARGET || 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ai-api/, '/api'),
      },
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})

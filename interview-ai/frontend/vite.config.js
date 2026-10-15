import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: true,          // 同时监听 IPv4/IPv6，避免 127.0.0.1 无法访问
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/oauth2': {
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/login/oauth2': {
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  }
})

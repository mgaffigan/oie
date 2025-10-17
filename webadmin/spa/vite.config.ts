import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: '/webadmin/home/',
  server: {
    port: 8081,
    proxy: {
      '^/(?!webadmin/).*': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  },
  plugins: [react()],
})

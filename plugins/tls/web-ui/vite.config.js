import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'


// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/tls-manager/',
  server: {
    allowedHosts: ['localhost', '127.0.0.1', '0.0.0.0', '778ded44be8d.ngrok-free.app'],
    proxy: {
      "/api": {
        target: "https://oie-test.quantis.health",
        changeOrigin: true,
        secure: true,
      },
    },

  },
  build: {
    outDir: 'tls-manager',
    emptyOutDir: true,
  },
})

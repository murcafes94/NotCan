import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  base: './',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['notcan.svg'],
      manifest: {
        name: 'NotCan',
        short_name: 'NotCan',
        description: 'Espacio académico sincronizado de NotCan',
        theme_color: '#10141f',
        background_color: '#0b0f17',
        display: 'standalone',
        start_url: './',
        icons: [
          {
            src: 'notcan.svg',
            sizes: 'any',
            type: 'image/svg+xml',
            purpose: 'any maskable'
          }
        ]
      },
      workbox: {
        navigateFallback: 'index.html',
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}']
      }
    })
  ]
})

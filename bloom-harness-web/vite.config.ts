import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendTarget = env.VITE_BACKEND_URL || 'http://127.0.0.1:8787';

  const proxyConfig = {
    '/api': {
      target: backendTarget,
      changeOrigin: true,
      configure: (proxy: { on: (event: string, handler: (...args: any[]) => void) => void }) => {
        proxy.on('error', (err: Error, req: { method?: string; url?: string }) => {
          console.error(`[Vite Proxy Error] ${req.method || 'REQ'} ${req.url || ''} -> ${backendTarget}:`, err.message);
        });
      },
    },
    '/ws': {
      target: backendTarget.replace(/^http/, 'ws'),
      ws: true,
      changeOrigin: true,
      configure: (proxy: { on: (event: string, handler: (...args: any[]) => void) => void }) => {
        proxy.on('error', (err: Error) => {
          console.error('[Vite WS Proxy Error]:', err.message);
        });
      },
    },
  };

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 5173,
      proxy: proxyConfig,
    },
    preview: {
      port: 5173,
      proxy: proxyConfig,
    },
    build: {
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks(id: string) {
            if (id.includes('node_modules')) {
              if (id.includes('vue') || id.includes('pinia')) {
                return 'vendor-vue';
              }
              if (id.includes('lucide-vue-next')) {
                return 'vendor-icons';
              }
              if (id.includes('marked') || id.includes('dompurify')) {
                return 'vendor-markdown';
              }
              // Keep @shikijs/langs dynamic chunks independent; bundle only shiki core engine
              if (id.includes('node_modules/shiki/') || id.includes('node_modules/@shikijs/core')) {
                return 'vendor-shiki-core';
              }
            }
          },
        },
      },
    },
  };
});

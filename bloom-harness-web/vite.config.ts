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
      configure: (proxy: any) => {
        proxy.on('error', (err: any, req: any) => {
          console.error(`[Vite Proxy Error] ${req.method} ${req.url} -> ${backendTarget}:`, err.message);
        });
        proxy.on('proxyReq', (proxyReq: any, req: any) => {
          console.log(`[Vite Proxy] Forwarding: ${req.method} ${req.url} -> ${backendTarget}${req.url}`);
        });
      },
    },
    '/ws': {
      target: backendTarget.replace(/^http/, 'ws'),
      ws: true,
      changeOrigin: true,
      configure: (proxy: any) => {
        proxy.on('error', (err: any) => {
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
  };
});

import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', ['POST_API_PROXY_TARGET', 'MEDIA_API_PROXY_TARGET']);

  return {
    plugins: [react()],
    server: {
      proxy: {
        '^/api/(posts|feed)(/|\\?|$)': {
          target: env.POST_API_PROXY_TARGET || 'http://127.0.0.1:8081',
        },
        '^/api/media(/|\\?|$)': {
          target: env.MEDIA_API_PROXY_TARGET || 'http://127.0.0.1:8082',
        },
      },
    },
  };
});

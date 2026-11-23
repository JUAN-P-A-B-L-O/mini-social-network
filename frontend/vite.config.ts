import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv, type ProxyOptions } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxy: Record<string, ProxyOptions> = {};

  if (env.POST_API_PROXY_TARGET) {
    proxy['^/api/(posts|feed)(/|\\?|$)'] = { target: env.POST_API_PROXY_TARGET };
  }
  if (env.MEDIA_API_PROXY_TARGET) {
    proxy['^/api/media(/|\\?|$)'] = { target: env.MEDIA_API_PROXY_TARGET };
  }

  return { plugins: [react()], server: { proxy } };
});

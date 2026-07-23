// Vite 6 配置：Svelte 5 + Tailwind 4 插件，极简构建。
import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath, URL } from 'node:url';
import pkg from './package.json' assert { type: 'json' };

export default defineConfig({
  plugins: [svelte(), tailwindcss()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  resolve: {
    // 路径别名（与 tsconfig.json paths 对齐）
    alias: {
      $lib: fileURLToPath(new URL('./src/lib', import.meta.url)),
      $components: fileURLToPath(new URL('./src/components', import.meta.url)),
      $routes: fileURLToPath(new URL('./src/routes', import.meta.url)),
    },
    // 允许省略 .svelte.ts / .svelte.js 扩展名（Svelte 5 runes 模块）
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.svelte', '.svelte.ts', '.svelte.js'],
  },
  build: {
    target: 'es2022',
    cssCodeSplit: false,
    assetsInlineLimit: 2048,
    rollupOptions: {
      output: {
        manualChunks: undefined,
        // 单一入口，最大化 tree-shaking 以压低包体积
      },
    },
  },
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
});

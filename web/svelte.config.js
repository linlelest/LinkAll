// Svelte 5 配置：启用 Runes 模式（$state/$derived/$effect/$props）。
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

export default {
  preprocess: vitePreprocess(),
  compilerOptions: {
    // Svelte 5 默认 Runes 模式，显式声明便于阅读
    runes: true,
  },
};

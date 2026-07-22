// Tailwind CSS v4 配置：v4 主要由 CSS 内 @theme 指令定义主题，
// 此处仅保留极简 JS 配置用于潜在扩展（v4 默认即可零配置使用）。
import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{svelte,ts,js}'],
  // v4 主题变量见 src/app.css 的 @theme 块
  theme: {
    extend: {},
  },
  plugins: [],
} satisfies Config;

<script lang="ts">
  // 主布局外壳：桌面侧边导航 + 主工作区；移动底部 TabBar + 堆叠面板。
  // 使用 CSS Grid + Flex 自适应，无媒体查询硬编码：通过容器宽度切换显示。
  import Sidebar from './Sidebar.svelte';
  import TabBar from './TabBar.svelte';
  import { routerStore } from '$lib/stores/router';
  import { authStore } from '$lib/stores/auth';
  import { t } from '$lib/i18n';
  import type { Snippet } from 'svelte';

  let { children }: { children: Snippet } = $props();

  let current = $derived(routerStore.current);
  let isLoggedIn = $derived(authStore.isLoggedIn);

  let title = $derived(t(`nav.${current}`));
</script>

<div class="shell" class:mobile={!isLoggedIn || false}>
  {#if isLoggedIn}
    <div class="desktop-only">
      <Sidebar />
    </div>
  {/if}

  <main class="main">
    {#if isLoggedIn}
      <header class="topbar desktop-only">
        <h1 class="page-title">{title}</h1>
      </header>
    {/if}
    <div class="content">
      {@render children?.()}
    </div>
    {#if isLoggedIn}
      <div class="mobile-only">
        <TabBar />
      </div>
    {/if}
  </main>
</div>

<style>
  .shell {
    display: grid;
    grid-template-columns: auto 1fr;
    height: 100%;
    width: 100%;
    container-type: inline-size;
  }
  /* 通过容器查询切换布局：窄屏隐藏侧边栏，显示底部 TabBar */
  .desktop-only {
    display: block;
  }
  .mobile-only {
    display: none;
  }
  .main {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-width: 0;
    overflow: hidden;
  }
  .topbar {
    height: 48px;
    min-height: 48px;
    display: flex;
    align-items: center;
    padding: 0 16px;
    border-bottom: 1px solid var(--color-border-soft);
    background: var(--color-bg-soft);
  }
  .page-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
  }
  .content {
    flex: 1 1 auto;
    overflow: auto;
    padding: 16px;
    min-height: 0;
  }

  /* 容器查询：宽度 <= 760px 切换为移动布局 */
  @container (max-width: 760px) {
    .desktop-only {
      display: none;
    }
    .mobile-only {
      display: block;
    }
    .shell {
      grid-template-columns: 1fr;
    }
    .content {
      padding: 10px;
    }
  }
</style>

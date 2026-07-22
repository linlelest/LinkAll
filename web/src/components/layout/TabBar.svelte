<script lang="ts">
  // 移动底部 TabBar：固定底部，CSS Flex 自适应。
  import { t } from '$lib/i18n';
  import { routerStore, type RouteName } from '$lib/stores/router';
  import { connectionStore } from '$lib/stores/connection';

  let current = $derived(routerStore.current);

  const tabs: { route: RouteName; key: string; icon: string }[] = [
    { route: 'dashboard', key: 'nav.dashboard', icon: '▦' },
    { route: 'devices', key: 'nav.devices', icon: '▥' },
    { route: 'control', key: 'nav.control', icon: '◈' },
    { route: 'announcements', key: 'nav.announcements', icon: '☰' },
    { route: 'settings', key: 'nav.settings', icon: '⚙' },
  ];

  let connStatus = $derived(connectionStore.phase === 'connected');
</script>

<nav class="tabbar">
  {#each tabs as tab}
    <button
      class="tab"
      class:active={current === tab.route}
      class:conn={tab.route === 'control' && connStatus}
      onclick={() => routerStore.go(tab.route)}
      aria-label={t(tab.key)}
    >
      <span class="tab-icon">{tab.icon}</span>
      <span class="tab-label">{t(tab.key)}</span>
    </button>
  {/each}
</nav>

<style>
  .tabbar {
    display: flex;
    align-items: stretch;
    justify-content: space-around;
    height: 56px;
    background: var(--color-bg-soft);
    border-top: 1px solid var(--color-border-soft);
    padding: 0 4px;
    /* 适配 iOS 安全区 */
    padding-bottom: env(safe-area-inset-bottom, 0);
  }
  .tab {
    flex: 1 1 0;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    border: none;
    background: transparent;
    color: var(--color-fg-dim);
    cursor: pointer;
    padding: 6px 2px;
  }
  .tab.active {
    color: var(--color-accent);
  }
  .tab.conn {
    color: var(--color-online);
  }
  .tab-icon {
    font-size: 18px;
    line-height: 1;
  }
  .tab-label {
    font-size: 10px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }
</style>

<script lang="ts">
  // 桌面侧边导航：固定宽度，CSS Grid 自适应。
  import { t } from '$lib/i18n';
  import { routerStore, type RouteName } from '$lib/stores/router';
  import { authStore } from '$lib/stores/auth';
  import { connectionStore } from '$lib/stores/connection';

  let current = $derived(routerStore.current);

  const navItems: { route: RouteName; key: string; icon: string }[] = [
    { route: 'dashboard', key: 'nav.dashboard', icon: '▦' },
    { route: 'devices', key: 'nav.devices', icon: '▥' },
    { route: 'control', key: 'nav.control', icon: '◈' },
    { route: 'announcements', key: 'nav.announcements', icon: '☰' },
    { route: 'settings', key: 'nav.settings', icon: '⚙' },
    { route: 'ota', key: 'nav.ota', icon: '↑' },
  ];

  function logout() {
    authStore.logout();
    routerStore.go('login');
  }

  let connStatus = $derived(connectionStore.phase === 'connected' ? '●' : '');
</script>

<aside class="sidebar">
  <div class="brand">
    <span class="brand-mark">L</span>
    <span class="brand-name">LinkALL</span>
  </div>

  <nav class="nav">
    {#each navItems as item}
      <button
        class="nav-item"
        class:active={current === item.route}
        onclick={() => routerStore.go(item.route)}
      >
        <span class="nav-icon">{item.icon}</span>
        <span class="nav-label">{t(item.key)}</span>
        {#if item.route === 'control' && connStatus}
          <span class="nav-badge">{connStatus}</span>
        {/if}
      </button>
    {/each}
  </nav>

  <div class="sidebar-footer">
    {#if authStore.user}
      <div class="user-info">
        <div class="user-name">{authStore.user.username}</div>
        <div class="user-role mono">{authStore.user.role}</div>
      </div>
    {/if}
    <button class="btn btn-ghost logout" onclick={logout}>{t('nav.logout')}</button>
  </div>
</aside>

<style>
  .sidebar {
    display: flex;
    flex-direction: column;
    width: 200px;
    min-width: 200px;
    height: 100%;
    background: var(--color-bg-soft);
    border-right: 1px solid var(--color-border-soft);
    padding: 12px 0;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px 16px;
    font-weight: 700;
    font-size: 16px;
  }
  .brand-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: 6px;
    background: var(--color-accent);
    color: #0b0b1a;
    font-weight: 800;
  }
  .nav {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 0 8px;
    flex: 1 1 auto;
  }
  .nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 6px;
    border: none;
    background: transparent;
    color: var(--color-fg-muted);
    cursor: pointer;
    text-align: left;
    font-size: 13px;
  }
  .nav-item:hover {
    background: var(--color-bg-elev);
    color: var(--color-fg);
  }
  .nav-item.active {
    background: var(--color-bg-elev2);
    color: var(--color-accent);
  }
  .nav-icon {
    width: 18px;
    text-align: center;
    font-size: 15px;
  }
  .nav-label {
    flex: 1 1 auto;
  }
  .nav-badge {
    color: var(--color-online);
    font-size: 14px;
  }
  .sidebar-footer {
    padding: 8px 12px;
    border-top: 1px solid var(--color-border-soft);
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .user-info {
    padding: 4px 6px;
  }
  .user-name {
    font-size: 13px;
    font-weight: 600;
  }
  .user-role {
    font-size: 11px;
    color: var(--color-fg-dim);
    text-transform: uppercase;
  }
  .logout {
    width: 100%;
    justify-content: flex-start;
    color: var(--color-fg-muted);
  }
</style>

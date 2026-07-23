<script lang="ts">
  // 根组件：状态化路由分发 + 全局 Toast。
  // 路由守卫优先级：首次初始化检查 > 登录态检查 > 正常路由。
  import { routerStore } from '$lib/stores/router';
  import { authStore } from '$lib/stores/auth';
  import { setupStore } from '$lib/stores/setup';
  import AppShell from '$components/layout/AppShell.svelte';
  import Toast from '$components/ui/Toast.svelte';
  import AnnouncementPopup from '$components/AnnouncementPopup.svelte';
  import Setup from '$routes/Setup.svelte';
  import Login from '$routes/Login.svelte';
  import Dashboard from '$routes/Dashboard.svelte';
  import Devices from '$routes/Devices.svelte';
  import Control from '$routes/Control.svelte';
  import Announcements from '$routes/Announcements.svelte';
  import Settings from '$routes/Settings.svelte';
  import OTA from '$routes/OTA.svelte';

  let current = $derived(routerStore.current);
  let isLoggedIn = $derived(authStore.isLoggedIn);
  let setupState = $derived(setupStore.state);

  // 应用启动时检查首次初始化状态
  $effect(() => {
    if (setupState === 'unknown') {
      void setupStore.check();
    }
  });

  // 路由守卫：首次初始化优先级最高，强制拦截所有路由到 setup
  $effect(() => {
    if (setupState !== 'needed') return;
    if (current !== 'setup') {
      routerStore.go('setup');
    }
  });

  // 未登录强制跳到 login；已登录但停在 login 则跳到 dashboard
  // 注意：仅在 setup 已完成时才执行登录态守卫
  $effect(() => {
    if (setupState === 'needed' || setupState === 'unknown') return;
    if (!isLoggedIn && current !== 'login') {
      routerStore.go('login');
    }
    if (isLoggedIn && current === 'login') {
      routerStore.go('dashboard');
    }
  });

  // 控制页全屏沉浸，不套 AppShell
  let isControl = $derived(current === 'control' && isLoggedIn);
</script>

{#if setupState === 'unknown'}
  <!-- 初始化检查中：显示空白避免闪烁 -->
  <div class="app-boot"></div>
{:else if setupState === 'needed'}
  <Setup />
{:else if isControl}
  <Control />
{:else if current === 'login' || !isLoggedIn}
  <Login />
{:else}
  <AppShell>
    {#if current === 'dashboard'}
      <Dashboard />
    {:else if current === 'devices'}
      <Devices />
    {:else if current === 'announcements'}
      <Announcements />
    {:else if current === 'settings'}
      <Settings />
    {:else if current === 'ota'}
      <OTA />
    {:else}
      <Dashboard />
    {/if}
  </AppShell>
{/if}

<Toast />
<AnnouncementPopup />

<style>
  .app-boot {
    min-height: 100vh;
    background: var(--color-bg);
  }
</style>

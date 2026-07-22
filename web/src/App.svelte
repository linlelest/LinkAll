<script lang="ts">
  // 根组件：状态化路由分发 + 全局 Toast。
  import { routerStore } from '$lib/stores/router';
  import { authStore } from '$lib/stores/auth';
  import AppShell from '$components/layout/AppShell.svelte';
  import Toast from '$components/ui/Toast.svelte';
  import Login from '$routes/Login.svelte';
  import Dashboard from '$routes/Dashboard.svelte';
  import Devices from '$routes/Devices.svelte';
  import Control from '$routes/Control.svelte';
  import Announcements from '$routes/Announcements.svelte';
  import Settings from '$routes/Settings.svelte';
  import OTA from '$routes/OTA.svelte';

  let current = $derived(routerStore.current);
  let isLoggedIn = $derived(authStore.isLoggedIn);

  // 未登录强制跳到 login；已登录但停在 login 则跳到 dashboard
  $effect(() => {
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

{#if isControl}
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

<script lang="ts">
  // 设置页：账号设置 + 高级设置 双标签。
  import { t } from '$lib/i18n';
  import AccountSettings from '$components/admin/AccountSettings.svelte';
  import AdvancedSettings from '$components/admin/AdvancedSettings.svelte';

  let tab = $state<'account' | 'advanced'>('account');
</script>

<div class="settings-page">
  <div class="tabs">
    <button class="tab" class:active={tab === 'account'} onclick={() => (tab = 'account')}>
      {t('settings.account')}
    </button>
    <button class="tab" class:active={tab === 'advanced'} onclick={() => (tab = 'advanced')}>
      {t('settings.advanced')}
    </button>
  </div>
  {#if tab === 'account'}
    <AccountSettings />
  {:else}
    <AdvancedSettings />
  {/if}
</div>

<style>
  .settings-page {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .tabs {
    display: flex;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .tab {
    padding: 8px 16px;
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 13px;
    border-bottom: 2px solid transparent;
  }
  .tab.active {
    color: var(--color-accent);
    border-bottom-color: var(--color-accent);
  }
</style>

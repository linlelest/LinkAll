<script lang="ts">
  // 连接输入面板：12 位设备编号 + 设备码 + 连接模式切换（匿名/同账号/设备码）。
  // 匿名模式弹出确认框：[仅本次 / 永久允许 / 拒绝]。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { startConnection } from '$lib/webrtc/connection-manager';
  import type { ConnectionMode } from '$lib/webrtc/control';
  import Modal from '$components/ui/Modal.svelte';
  import { toast } from '$lib/stores/toast';
  import { routerStore } from '$lib/stores/router';

  let deviceId = $state('');
  let deviceCode = $state('');
  let mode = $state<ConnectionMode>('device_code');
  let confirmOpen = $state(false);
  let connecting = $derived(connectionStore.isConnecting);

  const modes: { v: ConnectionMode; key: string }[] = [
    { v: 'device_code', key: 'control.mode_device_code' },
    { v: 'anonymous', key: 'control.mode_anonymous' },
    { v: 'same_account', key: 'control.mode_same_account' },
  ];

  function submit() {
    const id = deviceId.trim();
    if (id.length !== 12) {
      toast.error(t('control.id_invalid'));
      return;
    }
    if (mode === 'device_code' && !deviceCode.trim()) {
      toast.error(t('control.code_placeholder'));
      return;
    }
    if (mode === 'anonymous') {
      // 匿名模式需用户确认授权语义
      confirmOpen = true;
      return;
    }
    void doConnect();
  }

  async function doConnect() {
    confirmOpen = false;
    await startConnection(deviceId.trim(), deviceCode.trim(), mode);
  }

  function formatDeviceId() {
    // 自动格式化为 4-4-4 分组便于阅读
    const raw = deviceId.replace(/[^a-zA-Z0-9]/g, '').toUpperCase().slice(0, 12);
    const parts = [raw.slice(0, 4), raw.slice(4, 8), raw.slice(8, 12)].filter(Boolean);
    deviceId = parts.join('-');
  }
</script>

<div class="connect-page">
  <div class="connect-card card">
    <button class="back-btn" onclick={() => routerStore.go('dashboard')}>
      <span class="back-arrow">‹</span>
      <span>{t('nav.dashboard')}</span>
    </button>
    <h2 class="title">{t('control.title')}</h2>

    <div class="field">
      <label class="label">{t('control.device_id')}</label>
      <input
        class="input mono"
        type="text"
        bind:value={deviceId}
        oninput={formatDeviceId}
        placeholder={t('control.id_placeholder')}
        maxlength={14}
        autocomplete="off"
      />
    </div>

    <div class="field">
      <label class="label">{t('control.device_code')}</label>
      <input
        class="input mono"
        type="password"
        bind:value={deviceCode}
        placeholder={t('control.code_placeholder')}
        autocomplete="off"
        disabled={mode === 'same_account'}
      />
    </div>

    <div class="field">
      <label class="label">{t('control.mode')}</label>
      <div class="mode-row">
        {#each modes as m}
          <button
            class="btn mode-btn"
            class:active={mode === m.v}
            onclick={() => (mode = m.v)}
          >
            {t(m.key)}
          </button>
        {/each}
      </div>
    </div>

    <button class="btn btn-primary connect-btn" onclick={submit} disabled={connecting}>
      {connecting ? t('control.connecting') : t('control.connect')}
    </button>

    {#if connectionStore.phase === 'waiting_confirm'}
      <div class="status-info">{t('control.confirm_pending')}</div>
    {:else if connectionStore.phase === 'failed'}
      <div class="status-error">{connectionStore.errorMsg || t('control.connection_failed', { reason: '' })}</div>
    {/if}
  </div>
</div>

<Modal bind:open={confirmOpen} title={t('control.confirm_title')}>
  <p class="confirm-body">{t('control.confirm_body', { ip: '-', id: deviceId })}</p>
  <p class="confirm-hint muted">{t('control.confirm_pending')}</p>
  {#snippet footer()}
    <button class="btn btn-danger" onclick={() => (confirmOpen = false)}>{t('control.confirm_deny')}</button>
    <button class="btn" onclick={() => { mode = 'anonymous'; void doConnect(); }}>
      {t('control.confirm_once')}
    </button>
    <button class="btn btn-primary" onclick={() => { mode = 'anonymous'; void doConnect(); }}>
      {t('control.confirm_always')}
    </button>
  {/snippet}
</Modal>

<style>
  .connect-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100%;
    padding: 16px;
  }
  .connect-card {
    width: 100%;
    max-width: 420px;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 600;
    text-align: center;
  }
  .back-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    padding: 4px 0;
    font-size: 13px;
    align-self: flex-start;
  }
  .back-btn:hover {
    color: var(--color-accent);
  }
  .back-arrow {
    font-size: 18px;
    line-height: 1;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .mode-row {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
  .mode-btn {
    flex: 1 1 0;
    min-width: 90px;
    font-size: 12px;
  }
  .mode-btn.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
  .connect-btn {
    width: 100%;
    padding: 10px;
    font-size: 14px;
  }
  .status-info {
    padding: 8px 10px;
    background: var(--color-bg-soft);
    border-radius: 4px;
    color: var(--color-warn);
    font-size: 12px;
    text-align: center;
  }
  .status-error {
    padding: 8px 10px;
    background: rgba(248, 113, 113, 0.1);
    border-radius: 4px;
    color: var(--color-busy);
    font-size: 12px;
    text-align: center;
  }
  .confirm-body {
    margin: 0 0 8px;
    font-size: 13px;
  }
  .confirm-hint {
    font-size: 12px;
  }
</style>

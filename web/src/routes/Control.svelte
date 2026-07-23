<script lang="ts">
  // 远程控制页：连接面板 -> 控制画布 + 设置 + 触屏工具 + 状态栏。
  // 桌面端：画布全屏 + 浮层；移动端：画布 + 触屏辅助层。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { disconnect, resetConnection } from '$lib/webrtc/connection-manager';
  import ConnectPanel from '$components/control/ConnectPanel.svelte';
  import ControlCanvas from '$components/control/ControlCanvas.svelte';
  import SettingsPanel from '$components/control/SettingsPanel.svelte';
  import VirtualKeyboard from '$components/control/VirtualKeyboard.svelte';
  import VirtualMouse from '$components/control/VirtualMouse.svelte';
  import MouseButton from '$components/control/MouseButton.svelte';
  import WheelSlider from '$components/control/WheelSlider.svelte';
  import StatusBar from '$components/control/StatusBar.svelte';
  import FileTransfer from '$components/control/FileTransfer.svelte';
  import Modal from '$components/ui/Modal.svelte';

  let phase = $derived(connectionStore.phase);
  let showFiles = $state(false);
  let isTouch = $derived(
    typeof window !== 'undefined' && ('ontouchstart' in window || navigator.maxTouchPoints > 0),
  );

  function onDisconnect() {
    disconnect();
    resetConnection();
  }

  function toggleKeyboard() {
    connectionStore.keyboardVisible = !connectionStore.keyboardVisible;
  }
</script>

<div class="control-page">
  {#if phase === 'idle' || phase === 'failed' || phase === 'disconnected'}
    <ConnectPanel />
  {:else}
    <div class="control-stage">
      <ControlCanvas>
        {#if isTouch}
          <VirtualMouse />
        {/if}
      </ControlCanvas>

      <!-- 设置浮层 -->
      <SettingsPanel />

      <!-- 触屏辅助工具 -->
      {#if isTouch && connectionStore.isConnected}
        <WheelSlider />
        <MouseButton />
      {/if}

      <!-- 顶部工具栏 -->
      <div class="topbar">
        <div class="topbar-info">
          <span class="mono">{connectionStore.deviceId}</span>
          <span class="phase-badge" class:{phase}>{t(`control.${phase === 'connected' ? 'connected' : phase === 'connecting' ? 'connecting' : phase === 'waiting_confirm' ? 'confirm_pending' : 'disconnected'}`)}</span>
        </div>
        <div class="topbar-actions">
          <button class="btn btn-sm" onclick={toggleKeyboard}>{t('control.keyboard')}</button>
          <button class="btn btn-sm" onclick={() => (showFiles = true)}>{t('control.files')}</button>
          <button class="btn btn-sm btn-danger" onclick={onDisconnect}>{t('control.disconnect')}</button>
        </div>
      </div>

      <!-- 虚拟键盘 -->
      {#if isTouch}
        <VirtualKeyboard />
      {/if}

      <!-- 状态栏 -->
      <StatusBar />
    </div>
  {/if}
</div>

<!-- 文件传输模态 -->
<Modal bind:open={showFiles} title={t('control.files')}>
  <div class="files-modal-body">
    <FileTransfer />
  </div>
</Modal>

<style>
  .control-page {
    height: 100%;
    width: 100%;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .control-stage {
    position: relative;
    flex: 1 1 auto;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: #000;
  }
  .topbar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 15;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    padding-top: calc(6px + env(safe-area-inset-top, 0));
    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.6), transparent);
    pointer-events: none;
  }
  .topbar-info,
  .topbar-actions {
    pointer-events: auto;
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }
  .topbar-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  .topbar-info .mono {
    font-size: 11px;
    color: var(--color-fg);
    background: rgba(0, 0, 0, 0.5);
    padding: 2px 6px;
    border-radius: 3px;
  }
  .phase-badge {
    font-size: 10px;
    padding: 2px 6px;
    border-radius: 3px;
    background: var(--color-bg-elev2);
    color: var(--color-fg-muted);
  }
  .phase-badge.connected {
    color: var(--color-online);
  }
  .phase-badge.connecting,
  .phase-badge.waiting_confirm {
    color: var(--color-warn);
  }
  .btn-sm {
    padding: 4px 8px;
    font-size: 11px;
  }
  .files-modal-body {
    height: 60vh;
    min-height: 320px;
  }
  :global(.modal) {
    max-width: 720px;
  }
</style>

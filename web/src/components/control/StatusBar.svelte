<script lang="ts">
  // 状态栏：实时 RTT、丢包率、码率/帧率、编解码格式、连接时长。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { formatBitrate, formatDuration, formatPercent } from '$lib/utils/format';

  let s = $derived(connectionStore.stats);
  let connected = $derived(connectionStore.isConnected);
</script>

<div class="status-bar" class:connected>
  {#if !connected}
    <span class="dim">{t('control.disconnected')}</span>
  {:else}
    <div class="stat">
      <span class="stat-label">{t('status.rtt')}</span>
      <span class="stat-value mono" class:warn={s.rtt > 150} class:bad={s.rtt > 300}>{s.rtt}ms</span>
    </div>
    <div class="stat">
      <span class="stat-label">{t('status.packet_loss')}</span>
      <span class="stat-value mono" class:warn={s.packetLoss > 0.05} class:bad={s.packetLoss > 0.15}>
        {formatPercent(s.packetLoss)}
      </span>
    </div>
    <div class="stat">
      <span class="stat-label">{t('status.bitrate')}</span>
      <span class="stat-value mono">{formatBitrate(s.bitrate)}</span>
    </div>
    <div class="stat">
      <span class="stat-label">{t('status.fps')}</span>
      <span class="stat-value mono">{s.fps}</span>
    </div>
    <div class="stat">
      <span class="stat-label">{t('status.codec')}</span>
      <span class="stat-value mono">{s.codec || connectionStore.codec}</span>
    </div>
    <div class="stat grow">
      <span class="stat-label">{t('status.duration')}</span>
      <span class="stat-value mono">{formatDuration(s.duration)}</span>
    </div>
  {/if}
</div>

<style>
  .status-bar {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 6px 12px;
    background: var(--color-bg-soft);
    border-top: 1px solid var(--color-border-soft);
    font-size: 11px;
    flex-wrap: wrap;
    overflow-x: auto;
  }
  .status-bar.connected {
    color: var(--color-fg);
  }
  .stat {
    display: flex;
    align-items: center;
    gap: 4px;
    white-space: nowrap;
  }
  .stat-label {
    color: var(--color-fg-muted);
  }
  .stat-value {
    color: var(--color-online);
  }
  .stat-value.warn {
    color: var(--color-warn);
  }
  .stat-value.bad {
    color: var(--color-busy);
  }
  .grow {
    flex: 1 1 auto;
    justify-content: flex-end;
  }
  .dim {
    color: var(--color-fg-dim);
  }
</style>

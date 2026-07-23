<script lang="ts">
  // 我的设备列表：卡片/列表视图切换、在线呼吸灯、复制编号/名称、连接。
  // 所有用户（含超管）均通过 /api/devices/discover 获取自己的设备，按 owner 隔离。
  import { t } from '$lib/i18n';
  import { devicesStore, type DeviceItem } from '$lib/stores/devices';
  import { discoverDevices } from '$lib/api/devices';
  import { ApiError } from '$lib/api/client';
  import { toast } from '$lib/stores/toast';
  import { copyText } from '$lib/utils/clipboard';
  import { formatRelative } from '$lib/utils/format';
  import { routerStore } from '$lib/stores/router';
  import BreathingLight from '$components/ui/BreathingLight.svelte';

  let loading = $state(false);

  let store = $derived(devicesStore);

  async function refresh() {
    loading = true;
    devicesStore.setLoading(true);
    try {
      const { data, total } = await discoverDevices(devicesStore.onlineOnly);
      devicesStore.set(data, total);
    } catch (e) {
      const err = e as ApiError;
      toast.error(err.message || t('common.failed'));
    } finally {
      loading = false;
      devicesStore.setLoading(false);
    }
  }

  async function doCopy(text: string) {
    const ok = await copyText(text);
    if (ok) toast.success(t('common.copied'));
    else toast.error(t('common.failed'));
  }

  // 连接设备：复制编号并跳转到远程控制页
  function connectDevice(d: DeviceItem) {
    void copyText(d.deviceId);
    routerStore.go('control');
    toast.success(t('devices.copied_goto_control'));
  }

  function statusKey(s: string): string {
    if (s === 'online') return 'common.online';
    if (s === 'busy') return 'common.busy';
    if (s === 'sleeping') return 'common.sleeping';
    return 'common.offline';
  }

  $effect(() => {
    // 首次挂载拉取
    void refresh();
  });
</script>

<div class="device-page">
  <div class="toolbar">
    <div class="row">
      <button class="btn" class:active={store.viewMode === 'card'} onclick={() => (devicesStore.viewMode = 'card')}>
        {t('devices.view_card')}
      </button>
      <button class="btn" class:active={store.viewMode === 'list'} onclick={() => (devicesStore.viewMode = 'list')}>
        {t('devices.view_list')}
      </button>
    </div>
    <div class="row grow">
      <button class="btn" class:active={store.onlineOnly} onclick={() => { devicesStore.toggleOnlineOnly(); void refresh(); }}>
        {t('devices.filter_online')}
      </button>
    </div>
    <button class="btn" onclick={refresh} disabled={loading}>{t('common.refresh')}</button>
  </div>

  {#if loading && store.list.length === 0}
    <div class="empty">{t('common.loading')}</div>
  {:else if store.list.length === 0}
    <div class="empty">{t('devices.empty')}</div>
  {:else if store.viewMode === 'card'}
    <div class="card-grid">
      {#each store.list as d (d.deviceId)}
        <div class="card device-card">
          <div class="card-header">
            <BreathingLight status={d.onlineStatus as any} size={10} />
            <span class="card-title mono">{d.deviceName || d.deviceId}</span>
            <span class="badge">{t(statusKey(d.onlineStatus))}</span>
          </div>
          <div class="card-body">
            <div class="field">
              <span class="field-label">{t('devices.device_id')}</span>
              <button class="field-value copy-btn mono" onclick={() => doCopy(d.deviceId)}>
                {d.deviceId}
                <span class="copy-icon">⧉</span>
              </button>
            </div>
            <div class="field">
              <span class="field-label">{t('devices.platform')}</span>
              <span class="field-value mono">{d.platform || '-'}</span>
            </div>
            <div class="field">
              <span class="field-label">{t('devices.version')}</span>
              <span class="field-value mono">{d.version || '-'}</span>
            </div>
            <div class="field">
              <span class="field-label">{t('devices.last_seen')}</span>
              <span class="field-value">{formatRelative(d.lastSeen)}</span>
            </div>
          </div>
          <div class="card-actions">
            <button class="btn" onclick={() => doCopy(d.deviceName || d.deviceId)}>{t('devices.copy_name')}</button>
            <button class="btn btn-primary" disabled={d.onlineStatus !== 'online'} onclick={() => connectDevice(d)}>
              {t('devices.connect')}
            </button>
          </div>
        </div>
      {/each}
    </div>
  {:else}
    <div class="list-wrap">
      <table class="list-table">
        <thead>
          <tr>
            <th>{t('devices.status')}</th>
            <th>{t('devices.device_id')}</th>
            <th>{t('devices.device_name')}</th>
            <th>{t('devices.platform')}</th>
            <th>{t('devices.version')}</th>
            <th>{t('devices.last_seen')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {#each store.list as d (d.deviceId)}
            <tr>
              <td><BreathingLight status={d.onlineStatus as any} size={8} label={t(statusKey(d.onlineStatus))} /></td>
              <td class="mono">
                <button class="copy-cell" onclick={() => doCopy(d.deviceId)}>{d.deviceId} ⧉</button>
              </td>
              <td>{d.deviceName || '-'}</td>
              <td class="mono">{d.platform || '-'}</td>
              <td class="mono">{d.version || '-'}</td>
              <td>{formatRelative(d.lastSeen)}</td>
              <td>
                <button class="btn btn-primary btn-sm" disabled={d.onlineStatus !== 'online'} onclick={() => connectDevice(d)}>
                  {t('devices.connect')}
                </button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
</div>

<style>
  .device-page {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .btn.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
  .btn-sm {
    padding: 3px 8px;
    font-size: 12px;
  }
  .empty {
    padding: 40px;
    text-align: center;
    color: var(--color-fg-dim);
  }
  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 10px;
  }
  .device-card {
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .card-title {
    flex: 1 1 auto;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .badge {
    font-size: 11px;
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--color-bg-soft);
    color: var(--color-fg-muted);
  }
  .card-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .field {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    font-size: 12px;
  }
  .field-label {
    color: var(--color-fg-muted);
  }
  .field-value {
    color: var(--color-fg);
  }
  .copy-btn {
    background: none;
    border: none;
    color: var(--color-accent);
    cursor: pointer;
    padding: 0;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .copy-icon {
    font-size: 11px;
    opacity: 0.7;
  }
  .card-actions {
    display: flex;
    gap: 6px;
    justify-content: flex-end;
    border-top: 1px solid var(--color-border-soft);
    padding-top: 8px;
  }
  .list-wrap {
    overflow: auto;
    /* 移动端横向滚动表格 */
    -webkit-overflow-scrolling: touch;
  }
  .list-table {
    width: 100%;
    min-width: 600px;
    border-collapse: collapse;
    font-size: 12px;
  }
  .list-table th,
  .list-table td {
    text-align: left;
    padding: 8px 10px;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .list-table th {
    color: var(--color-fg-muted);
    font-weight: 600;
    position: sticky;
    top: 0;
    background: var(--color-bg-elev);
  }
  .copy-cell {
    background: none;
    border: none;
    color: var(--color-accent);
    cursor: pointer;
    padding: 0;
  }
  .confirm-body {
    margin: 0;
  }
</style>

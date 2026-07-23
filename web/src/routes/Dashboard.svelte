<script lang="ts">
  // 仪表盘：服务器信息 + 全局安全设置 + .env 预览。
  import { t } from '$lib/i18n';
  import { getServerInfo, type ServerInfo } from '$lib/api/server-info';
  import { getSecurity, type SecuritySettings } from '$lib/api/security';
  import { envPreview, type EnvPreview } from '$lib/api/security';
  import { toast } from '$lib/stores/toast';
  import { formatBytes, formatDuration } from '$lib/utils/format';
  import { ApiError } from '$lib/api/client';
  import { authStore } from '$lib/stores/auth';

  let info = $state<ServerInfo | null>(null);
  let security = $state<SecuritySettings | null>(null);
  let env = $state<EnvPreview | null>(null);
  let loading = $state(false);

  async function refresh() {
    loading = true;
    const tasks: Promise<void>[] = [];
    if (authStore.isAdmin) {
      tasks.push(
        getServerInfo()
          .then((d) => (info = d))
          .catch((e) => toast.error((e as ApiError).message)),
        getSecurity()
          .then((d) => (security = d))
          .catch((e) => toast.error((e as ApiError).message)),
        envPreview()
          .then((d) => (env = d))
          .catch((e) => toast.error((e as ApiError).message)),
      );
    }
    await Promise.allSettled(tasks);
    loading = false;
  }

  $effect(() => {
    void refresh();
  });
</script>

<div class="dashboard">
  {#if authStore.isAdmin}
    <section class="card section">
      <div class="section-head">
        <h3 class="section-title">{t('dashboard.server_info')}</h3>
        <button class="btn btn-sm" onclick={refresh} disabled={loading}>{t('common.refresh')}</button>
      </div>
      {#if info}
        <div class="stat-grid">
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.online_devices')}</div>
            <div class="stat-value mono online">{info.onlineDevices}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.active_sessions')}</div>
            <div class="stat-value mono">{info.activeSessions}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.signaling_latency')}</div>
            <div class="stat-value mono">{info.signalingLatencyMs}ms</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.uptime')}</div>
            <div class="stat-value mono">{formatDuration(info.uptime)}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.cpu')}</div>
            <div class="stat-value mono" class:warn={info.cpu.percent > 80}>{info.cpu.percent.toFixed(1)}%</div>
            <div class="stat-sub muted">{info.cpu.cores} cores</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.memory')}</div>
            <div class="stat-value mono">{formatBytes(info.memory.heapInuse)}</div>
            <div class="stat-sub muted">sys {formatBytes(info.memory.sys)}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.goroutines')}</div>
            <div class="stat-value mono">{info.numGoroutines}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.go_version')}</div>
            <div class="stat-value mono">{info.goVersion}</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">{t('dashboard.hostname')}</div>
            <div class="stat-value mono">{info.hostname}</div>
          </div>
        </div>
      {:else if loading}
        <div class="empty">{t('common.loading')}</div>
      {/if}
    </section>

    <section class="card section">
      <h3 class="section-title">{t('dashboard.security_settings')}</h3>
      {#if security}
        <div class="sec-grid">
          <div class="sec-row"><span>{t('dashboard.max_sessions')}</span><span class="mono">{security.maxConcurrentSessions}</span></div>
          <div class="sec-row"><span>{t('dashboard.retention_days')}</span><span class="mono">{security.dataRetentionDays}</span></div>
          <div class="sec-row"><span>{t('dashboard.connection_password')}</span>
            <span class:online={security.connectionPasswordSet} class:dim={!security.connectionPasswordSet}>
              {security.connectionPasswordSet ? t('dashboard.connection_password_set') : t('dashboard.connection_password_unset')}
            </span>
          </div>
        </div>
      {/if}
    </section>

    <section class="card section">
      <h3 class="section-title">{t('dashboard.env_preview')}</h3>
      {#if env}
        <div class="env-grid">
          <div class="env-row"><span class="muted">SERVER_PORT</span><span class="mono">{env.serverPort}</span></div>
          <div class="env-row"><span class="muted">ENV</span><span class="mono">{env.env}</span></div>
          <div class="env-row"><span class="muted">OFFICIAL_SERVER</span><span class="mono">{env.officialServer || '-'}</span></div>
          <div class="env-row"><span class="muted">DB_PATH</span><span class="mono">{env.dbPath}</span></div>
          <div class="env-row"><span class="muted">JWT_SCHEME</span><span class="mono">{env.jwtScheme}</span></div>
          <div class="env-row"><span class="muted">JWT_SECRET</span><span class="mono">{env.jwtSecret}</span></div>
          <div class="env-row"><span class="muted">JWT_EXPIRY</span><span class="mono">{env.jwtExpiry}</span></div>
          <div class="env-row"><span class="muted">STUN_SERVERS</span><span class="mono">{env.stunServers.join(', ') || '-'}</span></div>
          <div class="env-row"><span class="muted">FORCE_HTTPS</span><span class="mono">{env.forceHttps ? 'true' : 'false'}</span></div>
        </div>
      {/if}
    </section>
  {:else}
    <section class="card section">
      <h3 class="section-title">{t('app.name')}</h3>
      <p class="muted">{t('app.tagline')}</p>
      <p class="muted">当前账号：{authStore.user?.username}（{authStore.user?.role}）</p>
      <p class="muted">前往「远程控制」开始连接设备。</p>
    </section>
  {/if}
</div>

<style>
  .dashboard {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 960px;
  }
  .section {
    padding: 14px;
  }
  .section-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
  .section-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
  .btn-sm {
    padding: 3px 8px;
    font-size: 12px;
  }
  .stat-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 8px;
  }
  .stat-cell {
    background: var(--color-bg-soft);
    border-radius: 6px;
    padding: 10px;
  }
  .stat-label {
    font-size: 11px;
    color: var(--color-fg-muted);
    margin-bottom: 4px;
  }
  .stat-value {
    font-size: 16px;
    font-weight: 600;
  }
  .stat-value.online {
    color: var(--color-online);
  }
  .stat-value.warn {
    color: var(--color-warn);
  }
  .stat-sub {
    font-size: 10px;
    margin-top: 2px;
  }
  .empty {
    padding: 20px;
    text-align: center;
    color: var(--color-fg-dim);
  }
  .sec-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 8px;
  }
  .sec-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 8px;
    background: var(--color-bg-soft);
    border-radius: 4px;
    font-size: 12px;
  }
  .sec-row.col {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  .online {
    color: var(--color-online);
  }
  .dim {
    color: var(--color-fg-dim);
  }
  .whitelist {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
  .tag {
    background: var(--color-bg-elev2);
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 11px;
  }
  .env-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 4px;
  }
  .env-row {
    display: flex;
    justify-content: space-between;
    padding: 4px 8px;
    font-size: 12px;
    border-bottom: 1px solid var(--color-border-soft);
  }
</style>

<script lang="ts">
  // 检查更新：请求 /api/ota/check，弹窗显示更新详情与下载进度，静默下载 + 手动安装提示。
  import { t } from '$lib/i18n';
  import { toast } from '$lib/stores/toast';
  import { checkOta, downloadOtaPackage, type OtaCheckResult } from '$lib/api/ota';
  import { ApiError } from '$lib/api/client';
  import { formatBytes } from '$lib/utils/format';
  import Modal from '$components/ui/Modal.svelte';
  import Toggle from '$components/ui/Toggle.svelte';

  let checking = $state(false);
  let result = $state<OtaCheckResult | null>(null);
  let modalOpen = $state(false);
  let downloading = $state(false);
  let downloadPercent = $state(0);
  let downloaded = $state(false);
  let forceOnly = $state(false);
  let silentDownload = $state(true);
  let abortCtrl: AbortController | null = null;

  const CURRENT_VERSION = '0.1.0';
  const PLATFORM = 'web';

  async function check() {
    checking = true;
    try {
      const r = await checkOta({ platform: PLATFORM, version: CURRENT_VERSION });
      result = r;
      if (r.hasUpdate) {
        modalOpen = true;
        if (r.forceUpdate) {
          forceOnly = true;
        }
        if (silentDownload && r.downloadUrl) {
          void startDownload(true);
        }
      } else {
        toast.success(t('ota.up_to_date'));
      }
    } catch (e) {
      // Phase 1 服务端尚未实现 OTA 路由，容错提示
      toast.warn((e as ApiError).message || t('ota.no_release'));
    } finally {
      checking = false;
    }
  }

  async function startDownload(silent = false) {
    if (!result?.downloadUrl) {
      toast.error(t('ota.no_release'));
      return;
    }
    downloading = true;
    downloadPercent = 0;
    downloaded = false;
    abortCtrl = new AbortController();
    try {
      await downloadOtaPackage(
        result.downloadUrl,
        (p) => {
          downloadPercent = p;
          if (silent && p >= 100) {
            downloaded = true;
            toast.success(t('ota.download_complete'));
          }
        },
        abortCtrl.signal,
      );
      downloaded = true;
    } catch (e) {
      toast.error((e as Error).message || t('common.failed'));
    } finally {
      downloading = false;
    }
  }

  function cancelDownload() {
    abortCtrl?.abort();
    downloading = false;
  }

  function install() {
    // Web 端 OTA 为前端资源，提示用户刷新即安装
    toast.info(t('ota.install_now'));
    modalOpen = false;
  }

  $effect(() => {
    // 启动时静默检查一次
    void check();
  });
</script>

<div class="ota-page">
  <section class="card section">
    <div class="row between">
      <div>
        <h3 class="section-title" style="margin:0">{t('ota.title')}</h3>
        <p class="hint">{t('ota.current_version')}: <span class="mono">{CURRENT_VERSION}</span></p>
      </div>
      <button class="btn btn-primary" onclick={check} disabled={checking}>
        {checking ? t('ota.checking') : t('ota.check')}
      </button>
    </div>
  </section>

  <section class="card section">
    <div class="row between">
      <div>
        <h3 class="section-title" style="margin:0">静默下载</h3>
        <p class="hint">检测到更新时后台静默下载</p>
      </div>
      <Toggle checked={silentDownload} onChange={(v) => (silentDownload = v)} />
    </div>
  </section>

  {#if result?.hasUpdate}
    <section class="card section update-card">
      <div class="row between">
        <h3 class="section-title" style="margin:0">{t('ota.update_available', { version: result.latestVersion })}</h3>
        {#if result.forceUpdate}
          <span class="badge force">{t('ota.force_update')}</span>
        {/if}
      </div>
      <div class="meta-row">
        <span class="muted">{t('ota.latest_version')}: <span class="mono">{result.latestVersion}</span></span>
        <span class="muted">{t('ota.platform')}: <span class="mono">{PLATFORM}</span></span>
        {#if result.fileSize > 0}
          <span class="muted">{formatBytes(result.fileSize)}</span>
        {/if}
        <span class="muted">{t('ota.signed')}: {result.signature ? '✓' : '-'}</span>
      </div>
      {#if result.releaseNotes}
        <div class="release-notes">
          <div class="notes-title">{t('ota.release_notes')}</div>
          <pre class="notes-body">{result.releaseNotes}</pre>
        </div>
      {/if}
      {#if downloading}
        <div class="progress-wrap">
          <div class="progress-bar"><div class="progress-fill" style="width: {downloadPercent}%"></div></div>
          <span class="mono">{t('ota.downloading', { percent: downloadPercent })}</span>
          <button class="btn btn-sm" onclick={cancelDownload}>{t('common.cancel')}</button>
        </div>
      {:else if downloaded}
        <div class="row">
          <span class="muted">{t('ota.download_complete')}</span>
          <button class="btn btn-primary" onclick={install}>{t('ota.install_now')}</button>
        </div>
      {:else}
        <button class="btn btn-primary" onclick={() => startDownload(false)}>{t('ota.download')}</button>
      {/if}
    </section>
  {/if}
</div>

<Modal bind:open={modalOpen} title={t('ota.update_available', { version: result?.latestVersion ?? '' })}>
  {#if result?.forceUpdate}
    <div class="force-banner">{t('ota.force_update')}</div>
  {/if}
  {#if result}
    <div class="meta-row">
      <span class="muted">{t('ota.latest_version')}: <span class="mono">{result.latestVersion}</span></span>
      <span class="muted">{t('ota.platform')}: <span class="mono">{PLATFORM}</span></span>
    </div>
  {/if}
  {#if downloading}
    <div class="progress-wrap">
      <div class="progress-bar"><div class="progress-fill" style="width: {downloadPercent}%"></div></div>
      <span class="mono">{downloadPercent}%</span>
    </div>
  {:else if downloaded}
    <p>{t('ota.download_complete')}</p>
  {/if}
  {#snippet footer()}
    {#if forceOnly && !downloaded}
      <button class="btn btn-primary" onclick={() => startDownload(false)} disabled={downloading}>
        {downloading ? t('ota.downloading', { percent: downloadPercent }) : t('ota.download')}
      </button>
    {:else if downloaded}
      <button class="btn btn-primary" onclick={install}>{t('ota.install_now')}</button>
    {:else}
      <button class="btn" onclick={() => (modalOpen = false)}>{t('common.cancel')}</button>
      <button class="btn btn-primary" onclick={() => startDownload(false)}>{t('ota.download')}</button>
    {/if}
  {/snippet}
</Modal>

<style>
  .ota-page {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 640px;
  }
  .section {
    padding: 14px;
  }
  .section-title {
    margin: 0 0 8px;
    font-size: 14px;
    font-weight: 600;
  }
  .hint {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--color-fg-muted);
  }
  .between {
    justify-content: space-between;
    align-items: flex-start;
  }
  .update-card {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .badge.force {
    background: var(--color-busy);
    color: #1a0808;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 600;
  }
  .meta-row {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    font-size: 12px;
  }
  .release-notes {
    border-top: 1px solid var(--color-border-soft);
    padding-top: 8px;
  }
  .notes-title {
    font-size: 12px;
    color: var(--color-fg-muted);
    margin-bottom: 4px;
  }
  .notes-body {
    margin: 0;
    font-size: 12px;
    white-space: pre-wrap;
    font-family: var(--font-mono);
    color: var(--color-fg);
    max-height: 200px;
    overflow: auto;
  }
  .progress-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .progress-bar {
    flex: 1 1 auto;
    height: 6px;
    background: var(--color-border);
    border-radius: 3px;
    overflow: hidden;
  }
  .progress-fill {
    height: 100%;
    background: var(--color-accent);
  }
  .force-banner {
    background: var(--color-busy);
    color: #1a0808;
    padding: 8px 10px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 600;
    margin-bottom: 10px;
  }
</style>

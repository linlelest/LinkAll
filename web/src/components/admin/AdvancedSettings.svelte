<script lang="ts">
  // 高级设置：自定义服务器地址（覆盖 OFFICIAL_SERVER）、连接超时阈值、日志级别开关。
  import { t } from '$lib/i18n';
  import { toast } from '$lib/stores/toast';
  import { browserStorage } from '$lib/utils/storage';
  import Toggle from '$components/ui/Toggle.svelte';

  let serverAddr = $state(browserStorage.get('linkall.serverAddress', ''));
  let timeoutSec = $state(browserStorage.getJSON<number>('linkall.timeoutSec', 30));
  let logLevel = $state<'debug' | 'info' | 'warn' | 'error'>(
    browserStorage.get('linkall.logLevel', 'info') as any,
  );
  let perfMonitor = $state(browserStorage.getJSON<boolean>('linkall.perfMonitor', false));

  function saveServerAddr() {
    browserStorage.set('linkall.serverAddress', serverAddr.trim());
    toast.success(t('settings.saved'));
  }

  function saveTimeout(v: number) {
    timeoutSec = v;
    browserStorage.setJSON('linkall.timeoutSec', v);
  }

  function saveLogLevel(v: 'debug' | 'info' | 'warn' | 'error') {
    logLevel = v;
    browserStorage.set('linkall.logLevel', v);
  }

  function savePerfMonitor(v: boolean) {
    perfMonitor = v;
    browserStorage.setJSON('linkall.perfMonitor', v);
  }

  const levels: ('debug' | 'info' | 'warn' | 'error')[] = ['debug', 'info', 'warn', 'error'];
</script>

<div class="advanced-page">
  <section class="card section">
    <h3 class="section-title">{t('settings.server_address')}</h3>
    <p class="hint">{t('settings.server_address_hint')}</p>
    <div class="row">
      <input class="input grow" type="text" bind:value={serverAddr} placeholder="https://example.com:8080" />
      <button class="btn btn-primary" onclick={saveServerAddr}>{t('common.save')}</button>
    </div>
  </section>

  <section class="card section">
    <h3 class="section-title">{t('settings.connection_timeout')}</h3>
    <div class="row">
      <input
        class="input"
        type="number"
        min="5"
        max="600"
        bind:value={timeoutSec}
        oninput={(e) => saveTimeout(Number((e.target as HTMLInputElement).value))}
        style="max-width: 120px;"
      />
      <span class="muted">{t('common.units_seconds')}</span>
    </div>
  </section>

  <section class="card section">
    <h3 class="section-title">{t('settings.log_level')}</h3>
    <div class="level-row">
      {#each levels as lv}
        <button class="btn" class:active={logLevel === lv} onclick={() => saveLogLevel(lv)}>
          {t(`settings.log_${lv}`)}
        </button>
      {/each}
    </div>
  </section>

  <section class="card section">
    <div class="row between">
      <div>
        <h3 class="section-title" style="margin:0">{t('settings.advanced_section')}</h3>
        <p class="hint">性能监控 Toggle</p>
      </div>
      <Toggle checked={perfMonitor} onChange={savePerfMonitor} label={t('dashboard.cpu')} />
    </div>
  </section>
</div>

<style>
  .advanced-page {
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
    margin: 0 0 10px;
    font-size: 12px;
    color: var(--color-fg-muted);
  }
  .level-row {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
  .btn.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
  .between {
    justify-content: space-between;
    align-items: flex-start;
  }
</style>

<script lang="ts">
  // 设置面板：缩放滑块(10-300% 步5)、码率对数滑块(512K-200M)、帧率离散步进(15-144)、防窥屏开关。
  // 实时比例水印预览、码率预估延迟显示。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { syncScale, syncBitrate, syncFps, syncPrivacyScreen, syncCodec } from '$lib/webrtc/connection-manager';
  import Slider from '$components/ui/Slider.svelte';
  import Toggle from '$components/ui/Toggle.svelte';

  // 缩放 10~300 步5
  function onScale(v: number) {
    connectionStore.scale = v;
    syncScale();
  }

  // 码率对数 512K~200M
  const BITRATE_MIN = 512_000;
  const BITRATE_MAX = 200_000_000;
  function formatBitrate(v: number): string {
    if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)} Mbps`;
    return `${Math.round(v / 1000)} Kbps`;
  }
  // 预估延迟：码率越低延迟越高（粗略估算）
  let bitrateLatency = $derived(
    Math.max(8, Math.round(80 - Math.log2(connectionStore.maxBitrate / 512_000) * 4)),
  );
  function onBitrate(v: number) {
    connectionStore.maxBitrate = v;
    syncBitrate();
  }

  // 帧率离散步进
  const FPS_STEPS = [15, 30, 45, 60, 75, 90, 105, 120, 135, 144];
  function onFps(v: number) {
    connectionStore.fps = v;
    syncFps();
  }

  // 防窥屏
  function onPrivacy(v: boolean) {
    connectionStore.privacyScreen = v;
    syncPrivacyScreen();
  }

  // 编解码
  const CODECS = ['H264', 'VP8', 'VP9', 'AV1'] as const;
  function onCodec(v: 'H264' | 'VP8' | 'VP9' | 'AV1') {
    connectionStore.codec = v;
    syncCodec();
  }

  let open = $state(false);
</script>

<div class="settings-panel" class:open>
  <button class="toggle-handle btn" onclick={() => (open = !open)}>
    {open ? '▸' : '◂'} {t('control.settings')}
  </button>

  {#if open}
    <div class="panel-body card">
      <Slider
        label={t('control.scale')}
        value={connectionStore.scale}
        min={10}
        max={300}
        step={5}
        onInput={onScale}
        format={(v) => t('control.scale_watermark', { value: v })}
      />

      <Slider
        label={t('control.bitrate')}
        value={connectionStore.maxBitrate}
        min={BITRATE_MIN}
        max={BITRATE_MAX}
        logScale
        onInput={onBitrate}
        format={formatBitrate}
      />
      <div class="hint muted">{t('control.bitrate_estimated', { ms: bitrateLatency })}</div>

      <div class="field">
        <label class="label">{t('control.fps')}</label>
        <div class="fps-row">
          {#each FPS_STEPS as f}
            <button
              class="btn fps-btn"
              class:active={connectionStore.fps === f}
              onclick={() => onFps(f)}
            >
              {f}
            </button>
          {/each}
        </div>
      </div>

      <div class="field">
        <label class="label">{t('control.codec')}</label>
        <div class="codec-row">
          {#each CODECS as c}
            <button
              class="btn codec-btn"
              class:active={connectionStore.codec === c}
              onclick={() => onCodec(c)}
            >
              {c}
            </button>
          {/each}
        </div>
      </div>

      <div class="field row between">
        <span>{t('control.privacy_screen')}</span>
        <Toggle
          checked={connectionStore.privacyScreen}
          onChange={onPrivacy}
          label={connectionStore.privacyScreen ? t('control.privacy_screen_on') : t('control.privacy_screen_off')}
        />
      </div>
    </div>
  {/if}
</div>

<style>
  .settings-panel {
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 20;
    max-width: 280px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .toggle-handle {
    align-self: flex-end;
    font-size: 12px;
  }
  .panel-body {
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 14px;
    max-height: 70vh;
    overflow: auto;
  }
  .hint {
    font-size: 11px;
    margin-top: -8px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .fps-row,
  .codec-row {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
  .fps-btn,
  .codec-btn {
    padding: 4px 8px;
    font-size: 11px;
    font-family: var(--font-mono);
  }
  .fps-btn.active,
  .codec-btn.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
    background: var(--color-bg-elev2);
  }
  .between {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
</style>

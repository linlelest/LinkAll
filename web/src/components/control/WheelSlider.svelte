<script lang="ts">
  // 滚轮垂直滑块：右侧，上滑=滚轮上 / 下滑=滚轮下，灵敏度可调。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { sendWheel } from '$lib/webrtc/control';
  import { browserStorage } from '$lib/utils/storage';

  let sensitivity = $state(browserStorage.getJSON<number>('linkall.wheelSensitivity', 1));
  let lastY = 0;
  let active = $state(false);

  $effect(() => {
    connectionStore.wheelSensitivity = sensitivity;
  });

  function onPointerDown(e: PointerEvent) {
    active = true;
    lastY = e.clientY;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    e.preventDefault();
  }

  function onPointerMove(e: PointerEvent) {
    if (!active) return;
    const dy = e.clientY - lastY;
    if (Math.abs(dy) >= 4 / sensitivity) {
      // 向下滑动 = 滚轮向下（正值）
      const delta = Math.round(dy * sensitivity * 2);
      sendWheel(0, delta);
      lastY = e.clientY;
    }
  }

  function onPointerUp() {
    active = false;
  }

  function saveSens(v: number) {
    sensitivity = v;
    browserStorage.setJSON('linkall.wheelSensitivity', v);
  }
</script>

<div class="wheel-wrap">
  <div class="wheel-track" class:active={connectionStore.isConnected}>
    <div class="wheel-thumb"></div>
  </div>
  <div
    class="wheel-touch"
    class:active={connectionStore.isConnected}
    onpointerdown={onPointerDown}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
    onpointercancel={onPointerUp}
    oncontextmenu={(e) => e.preventDefault()}
  ></div>
  <div class="wheel-sens">
    <label class="label">{t('control.wheel_sensitivity')}</label>
    <input
      type="range"
      min="0.5"
      max="3"
      step="0.1"
      value={sensitivity}
      oninput={(e) => saveSens(Number((e.target as HTMLInputElement).value))}
      style="width: 60px; writing-mode: bt-lr;"
    />
  </div>
  <div class="wheel-label">
    <span>{t('control.wheel_up')}</span>
    <span>{t('control.wheel_down')}</span>
  </div>
</div>

<style>
  .wheel-wrap {
    position: absolute;
    right: 4px;
    top: 50%;
    transform: translateY(-50%);
    z-index: 22;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    user-select: none;
  }
  .wheel-track {
    width: 8px;
    height: 160px;
    background: var(--color-bg-elev);
    border: 1px solid var(--color-border-soft);
    border-radius: 4px;
    position: relative;
    display: none;
  }
  .wheel-track.active {
    display: block;
  }
  .wheel-thumb {
    position: absolute;
    top: 50%;
    left: 0;
    right: 0;
    height: 20px;
    background: var(--color-accent);
    border-radius: 4px;
    transform: translateY(-50%);
    opacity: 0.6;
  }
  .wheel-touch {
    position: absolute;
    top: 0;
    bottom: 0;
    left: -12px;
    right: -12px;
    touch-action: none;
    display: none;
    cursor: ns-resize;
  }
  .wheel-touch.active {
    display: block;
  }
  .wheel-sens {
    display: none;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .wheel-sens {
    display: flex;
  }
  .wheel-label {
    display: flex;
    flex-direction: column;
    align-items: center;
    font-size: 9px;
    color: var(--color-fg-muted);
    gap: 60px;
  }
  .label {
    font-size: 9px;
  }
</style>

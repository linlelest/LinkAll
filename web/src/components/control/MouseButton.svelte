<script lang="ts">
  // 左右键悬浮按钮：右下角固定 L/R，可拖动位置、透明度可调。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { sendMouseDown, sendMouseUp } from '$lib/webrtc/control';
  import { browserStorage } from '$lib/utils/storage';

  // 位置持久化
  let pos = $state<{ x: number; y: number }>(
    browserStorage.getJSON('linkall.mouseBtnPos', { x: -1, y: -1 }),
  );
  let opacity = $state(browserStorage.getJSON<number>('linkall.mouseOpacity', 0.6));
  let adjusting = $state(false);
  let dragging = $state(false);
  let dragOffset = { x: 0, y: 0 };

  // 同步透明度到 store
  $effect(() => {
    connectionStore.mouseOpacity = opacity;
  });

  function press(button: 'left' | 'right') {
    sendMouseDown(button);
  }
  function release(button: 'left' | 'right') {
    sendMouseUp(button);
  }

  function onPointerDown(e: PointerEvent, btn: 'left' | 'right') {
    if (adjusting) {
      // 拖动整个控件
      dragging = true;
      const target = e.currentTarget as HTMLElement;
      const rect = target.parentElement!.getBoundingClientRect();
      dragOffset.x = e.clientX - rect.left;
      dragOffset.y = e.clientY - rect.top;
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
      e.preventDefault();
      return;
    }
    press(btn);
  }

  function onPointerUp(e: PointerEvent, btn: 'left' | 'right') {
    if (dragging) {
      dragging = false;
      const parent = (e.currentTarget as HTMLElement).parentElement!;
      const rect = parent.getBoundingClientRect();
      pos = { x: rect.left, y: rect.top };
      browserStorage.setJSON('linkall.mouseBtnPos', pos);
      return;
    }
    release(btn);
  }

  function onPointerMove(e: PointerEvent) {
    if (!dragging) return;
    const wrap = document.querySelector('.mouse-btn-wrap') as HTMLElement;
    if (!wrap) return;
    const x = e.clientX - dragOffset.x;
    const y = e.clientY - dragOffset.y;
    wrap.style.left = `${x}px`;
    wrap.style.top = `${y}px`;
    wrap.style.right = 'auto';
    wrap.style.bottom = 'auto';
  }

  // 初始位置：右下角默认
  let initStyle = $derived(
    pos.x >= 0
      ? `left: ${pos.x}px; top: ${pos.y}px; right: auto; bottom: auto;`
      : 'right: 12px; bottom: 80px;',
  );
</script>

<div class="mouse-btn-wrap" style="{initStyle} opacity: {opacity};">
  {#if adjusting}
    <div class="adjust-panel">
      <label class="label">{t('control.mouse_opacity')}</label>
      <input
        type="range"
        min="0.2"
        max="1"
        step="0.1"
        bind:value={opacity}
        style="width: 100px;"
      />
      <button class="btn btn-sm" onclick={() => (adjusting = false)}>✓</button>
    </div>
  {/if}
  <button
    class="mouse-btn l"
    disabled={!connectionStore.isConnected}
    onpointerdown={(e) => onPointerDown(e, 'left')}
    onpointerup={(e) => onPointerUp(e, 'left')}
    onpointermove={onPointerMove}
    oncontextmenu={(e) => e.preventDefault()}
  >
    {t('control.mouse_left')}
  </button>
  <button
    class="mouse-btn r"
    disabled={!connectionStore.isConnected}
    onpointerdown={(e) => onPointerDown(e, 'right')}
    onpointerup={(e) => onPointerUp(e, 'right')}
    onpointermove={onPointerMove}
    oncontextmenu={(e) => e.preventDefault()}
  >
    {t('control.mouse_right')}
  </button>
  <button class="adjust-btn" onclick={() => (adjusting = !adjusting)}>⚙</button>
</div>

<style>
  .mouse-btn-wrap {
    position: absolute;
    z-index: 25;
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: flex-end;
  }
  .mouse-btn {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: 1px solid var(--color-border);
    background: var(--color-bg-elev2);
    color: var(--color-fg);
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    user-select: none;
    touch-action: none;
  }
  .mouse-btn:active:not(:disabled) {
    background: var(--color-accent);
    color: #0b0b1a;
  }
  .mouse-btn:disabled {
    opacity: 0.4;
  }
  .adjust-btn {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 1px solid var(--color-border);
    background: var(--color-bg-elev);
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 12px;
    line-height: 1;
  }
  .adjust-panel {
    position: absolute;
    bottom: 130px;
    right: 0;
    background: var(--color-bg-elev);
    border: 1px solid var(--color-border);
    border-radius: 6px;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: 160px;
  }
</style>

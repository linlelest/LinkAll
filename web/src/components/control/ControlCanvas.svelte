<script lang="ts">
  // 视频画布：渲染 WebRTC MediaStream（VP8/H264 接收 + 显示）。
  // 使用 <video> 元素直接渲染 MediaStream，触屏辅助工具层叠加其上。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import {
    sendMouseMove,
    sendMouseDown,
    sendMouseUp,
    sendMouseClick,
    sendMouseDoubleClick,
    sendWheel,
  } from '$lib/webrtc/control';
  import { peerConnection } from '$lib/webrtc/connection-manager';

  let { children }: { children?: import('svelte').Snippet } = $props();
  let videoEl = $state<HTMLVideoElement | null>(null);
  let containerEl = $state<HTMLDivElement | null>(null);
  let stream = $state<MediaStream | null>(null);
  let watermark = $derived(`${connectionStore.scale}%`);

  // 接收远端流
  $effect(() => {
    const handler = (e: Event) => {
      const s = (e as CustomEvent<MediaStream>).detail;
      stream = s;
      if (videoEl) {
        videoEl.srcObject = s;
        void videoEl.play().catch(() => {});
      }
    };
    window.addEventListener('linkall:remotestream', handler);
    return () => window.removeEventListener('linkall:remotestream', handler);
  });

  // 鼠标事件（桌面端）：坐标映射到被控端屏幕分辨率
  function screenCoords(e: MouseEvent): { x: number; y: number } {
    if (!videoEl || !videoEl.videoWidth) return { x: 0, y: 0 };
    const rect = videoEl.getBoundingClientRect();
    const x = Math.round((e.clientX - rect.left) / rect.width * videoEl.videoWidth);
    const y = Math.round((e.clientY - rect.top) / rect.height * videoEl.videoHeight);
    return { x, y };
  }

  function onMouseMove(e: MouseEvent) {
    if (!connectionStore.isConnected) return;
    const { x, y } = screenCoords(e);
    sendMouseMove(x, y);
  }

  function onMouseDown(e: MouseEvent) {
    if (!connectionStore.isConnected) return;
    const button = e.button === 2 ? 'right' : e.button === 1 ? 'middle' : 'left';
    sendMouseDown(button);
  }

  function onMouseUp(e: MouseEvent) {
    if (!connectionStore.isConnected) return;
    const button = e.button === 2 ? 'right' : e.button === 1 ? 'middle' : 'left';
    sendMouseUp(button);
  }

  function onDblClick(e: MouseEvent) {
    if (!connectionStore.isConnected) return;
    const button = e.button === 2 ? 'right' : 'left';
    sendMouseDoubleClick(button);
  }

  function onWheel(e: WheelEvent) {
    if (!connectionStore.isConnected) return;
    e.preventDefault();
    sendWheel(0, e.deltaY);
  }

  function onContextmenu(e: Event) {
    e.preventDefault();
  }

  let isTouchDevice = $derived(
    typeof window !== 'undefined' && ('ontouchstart' in window || navigator.maxTouchPoints > 0),
  );
</script>

<div class="canvas-wrap" bind:this={containerEl}>
  {#if !connectionStore.isConnected}
    <div class="placeholder">
      <div class="placeholder-icon">◈</div>
      <div class="placeholder-text">{t('control.disconnected')}</div>
    </div>
  {/if}

  <video
    bind:this={videoEl}
    class:visible={connectionStore.isConnected}
    autoplay
    playsinline
    muted
    onmousemove={onMouseMove}
    onmousedown={onMouseDown}
    onmouseup={onMouseUp}
    ondblclick={onDblClick}
    onwheel={onWheel}
    oncontextmenu={onContextmenu}
  ></video>

  {#if connectionStore.isConnected}
    <div class="watermark mono">{watermark}</div>
  {/if}

  {#if connectionStore.isConnected && isTouchDevice}
    {@render children?.()}
  {/if}
</div>

<style>
  .canvas-wrap {
    position: relative;
    width: 100%;
    height: 100%;
    background: #000;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .placeholder {
    color: var(--color-fg-dim);
    text-align: center;
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
  }
  .placeholder-icon {
    font-size: 48px;
    opacity: 0.4;
  }
  .placeholder-text {
    font-size: 13px;
  }
  video {
    max-width: 100%;
    max-height: 100%;
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: none;
    cursor: crosshair;
  }
  video.visible {
    display: block;
  }
  .watermark {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 6px;
    background: rgba(0, 0, 0, 0.6);
    color: var(--color-accent);
    font-size: 11px;
    border-radius: 3px;
    pointer-events: none;
  }
</style>

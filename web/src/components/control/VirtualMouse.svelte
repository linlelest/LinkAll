<script lang="ts">
  // 虚拟鼠标：透明触控层，滑动=移动，单击=左键，长按=右键，双击=双击模拟。
  import { sendMouseMove, sendMouseDown, sendMouseUp, sendMouseClick, sendMouseDoubleClick } from '$lib/webrtc/control';
  import { connectionStore } from '$lib/stores/connection';

  let touching = $state(false);
  let startX = 0;
  let startY = 0;
  let lastX = 0;
  let lastY = 0;
  let startTime = 0;
  let lastTapTime = 0;
  let longPressTimer: ReturnType<typeof setTimeout> | null = null;
  let moved = false;

  const TAP_THRESHOLD = 10; // 像素
  const LONG_PRESS_MS = 500;
  const DOUBLE_TAP_MS = 300;

  function onTouchStart(e: TouchEvent) {
    if (e.touches.length !== 1) return;
    const t = e.touches[0];
    startX = lastX = t.clientX;
    startY = lastY = t.clientY;
    startTime = Date.now();
    moved = false;
    touching = true;

    // 长按检测
    longPressTimer = setTimeout(() => {
      if (!moved && touching) {
        // 长按 = 右键
        sendMouseClick('right');
        touching = false;
      }
    }, LONG_PRESS_MS);
  }

  function onTouchMove(e: TouchEvent) {
    if (!touching || e.touches.length !== 1) return;
    const t = e.touches[0];
    const dx = Math.round(t.clientX - lastX);
    const dy = Math.round(t.clientY - lastY);
    const totalDx = Math.abs(t.clientX - startX);
    const totalDy = Math.abs(t.clientY - startY);
    if (totalDx > TAP_THRESHOLD || totalDy > TAP_THRESHOLD) {
      moved = true;
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
    }
    if (dx !== 0 || dy !== 0) {
      sendMouseMove(dx, dy);
    }
    lastX = t.clientX;
    lastY = t.clientY;
  }

  function onTouchEnd(e: TouchEvent) {
    if (longPressTimer) {
      clearTimeout(longPressTimer);
      longPressTimer = null;
    }
    if (!touching) return;
    touching = false;
    const dt = Date.now() - startTime;
    if (!moved && dt < LONG_PRESS_MS) {
      // 视为单击
      const now = Date.now();
      if (now - lastTapTime < DOUBLE_TAP_MS) {
        // 双击
        sendMouseDoubleClick('left');
        lastTapTime = 0;
      } else {
        sendMouseClick('left');
        lastTapTime = now;
      }
    }
  }
</script>

<div
  class="vmouse-layer"
  class:active={connectionStore.isConnected}
  ontouchstart={onTouchStart}
  ontouchmove={onTouchMove}
  ontouchend={onTouchEnd}
></div>

<style>
  .vmouse-layer {
    position: absolute;
    inset: 0;
    z-index: 10;
    touch-action: none;
    background: transparent;
    display: none;
  }
  .vmouse-layer.active {
    display: block;
  }
</style>

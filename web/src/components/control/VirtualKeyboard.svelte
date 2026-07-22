<script lang="ts">
  // 虚拟键盘：半屏布局，Ctrl/Alt/Win/Meta 组合键，回车/ESC 快捷栏。
  import { t } from '$lib/i18n';
  import { connectionStore } from '$lib/stores/connection';
  import { sendKeyboard, type KeyCode } from '$lib/webrtc/control';

  let modifiers = $state<Set<KeyCode>>(new Set());

  const rows: KeyCode[][] = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['KeyQ', 'KeyW', 'KeyE', 'KeyR', 'KeyT', 'KeyY', 'KeyU', 'KeyI', 'KeyO', 'KeyP'],
    ['KeyA', 'KeyS', 'KeyD', 'KeyF', 'KeyG', 'KeyH', 'KeyJ', 'KeyK', 'KeyL'],
    ['KeyZ', 'KeyX', 'KeyC', 'KeyV', 'KeyB', 'KeyN', 'KeyM'],
  ];

  // 显示用：KeyA -> A
  function display(key: KeyCode): string {
    if (key.startsWith('Key')) return key.slice(3);
    return key;
  }

  function tap(key: KeyCode) {
    const mods = Array.from(modifiers);
    if (mods.length > 0) {
      // 组合键：按下修饰键 + 按下目标键 + 释放
      sendKeyboard(key, 'press', mods);
      modifiers = new Set(); // 触发后清空修饰键
    } else {
      sendKeyboard(key, 'press');
    }
  }

  function toggleMod(mod: KeyCode) {
    const next = new Set(modifiers);
    if (next.has(mod)) next.delete(mod);
    else next.add(mod);
    modifiers = next;
  }

  function quickKey(key: KeyCode) {
    sendKeyboard(key, 'press');
  }

  let visible = $derived(connectionStore.keyboardVisible);
</script>

{#if visible}
  <div class="keyboard">
    <div class="kb-quickbar">
      <button class="kb-key quick" onclick={() => quickKey('Enter')}>{t('control.keyboard_enter')}</button>
      <button class="kb-key quick" onclick={() => quickKey('Escape')}>{t('control.keyboard_esc')}</button>
      <button class="kb-key quick" onclick={() => quickKey('Backspace')}>{t('control.keyboard_backspace')}</button>
      <button class="kb-key quick" onclick={() => quickKey('Tab')}>{t('control.keyboard_tab')}</button>
      <button class="kb-key quick wide" onclick={() => quickKey('Space')}>{t('control.keyboard_space')}</button>
    </div>

    <div class="kb-modbar">
      <button class="kb-key mod" class:active={modifiers.has('ControlLeft')} onclick={() => toggleMod('ControlLeft')}>
        {t('control.keyboard_ctrl')}
      </button>
      <button class="kb-key mod" class:active={modifiers.has('AltLeft')} onclick={() => toggleMod('AltLeft')}>
        {t('control.keyboard_alt')}
      </button>
      <button class="kb-key mod" class:active={modifiers.has('MetaLeft')} onclick={() => toggleMod('MetaLeft')}>
        {t('control.keyboard_win')}
      </button>
      <button class="kb-key mod" class:active={modifiers.has('ShiftLeft')} onclick={() => toggleMod('ShiftLeft')}>
        {t('control.keyboard_shift')}
      </button>
    </div>

    {#each rows as row}
      <div class="kb-row">
        {#each row as key}
          <button class="kb-key" onclick={() => tap(key)}>{display(key)}</button>
        {/each}
      </div>
    {/each}

    <button class="kb-hide" onclick={() => (connectionStore.keyboardVisible = false)}>
      {t('control.keyboard_hide')} ▾
    </button>
  </div>
{/if}

<style>
  .keyboard {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    max-height: 50vh;
    background: var(--color-bg-soft);
    border-top: 1px solid var(--color-border);
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    z-index: 30;
    user-select: none;
  }
  .kb-quickbar,
  .kb-modbar,
  .kb-row {
    display: flex;
    gap: 4px;
    justify-content: center;
    flex-wrap: nowrap;
  }
  .kb-key {
    flex: 1 1 0;
    min-width: 0;
    height: 36px;
    border: 1px solid var(--color-border);
    background: var(--color-bg-elev);
    color: var(--color-fg);
    border-radius: 4px;
    font-size: 13px;
    cursor: pointer;
    padding: 0 4px;
  }
  .kb-key:active {
    background: var(--color-accent-soft);
  }
  .kb-key.quick.wide {
    flex: 3 1 0;
  }
  .kb-key.mod.active {
    background: var(--color-accent);
    color: #0b0b1a;
    border-color: var(--color-accent);
  }
  .kb-hide {
    align-self: center;
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 12px;
    padding: 4px 12px;
  }
</style>

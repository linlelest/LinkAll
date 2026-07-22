<script lang="ts">
  // 开关切换组件，极简 CSS。
  let {
    checked = $bindable(false),
    label = '',
    disabled = false,
    onChange = undefined as ((v: boolean) => void) | undefined,
  }: {
    checked: boolean;
    label?: string;
    disabled?: boolean;
    onChange?: (v: boolean) => void;
  } = $props();

  function toggle() {
    if (disabled) return;
    checked = !checked;
    onChange?.(checked);
  }
</script>

<label class="toggle-wrap" class:disabled>
  {#if label}
    <span class="toggle-label">{label}</span>
  {/if}
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    class="toggle"
    class:on={checked}
    {disabled}
    onclick={toggle}
  >
    <span class="knob" class:on={checked}></span>
  </button>
</label>

<style>
  .toggle-wrap {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    user-select: none;
  }
  .toggle-wrap.disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .toggle-label {
    font-size: 13px;
    color: var(--color-fg);
  }
  .toggle {
    position: relative;
    width: 38px;
    height: 22px;
    border-radius: 11px;
    border: 1px solid var(--color-border);
    background: var(--color-bg-soft);
    padding: 0;
    cursor: pointer;
  }
  .toggle.on {
    background: var(--color-accent);
    border-color: var(--color-accent);
  }
  .toggle:disabled {
    cursor: not-allowed;
  }
  .knob {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--color-fg);
  }
  .knob.on {
    left: 18px;
    background: #0b0b1a;
  }
</style>

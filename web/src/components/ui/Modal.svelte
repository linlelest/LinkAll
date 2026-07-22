<script lang="ts">
  // 模态对话框，遮罩 + 居中卡片，无动画。
  let {
    open = $bindable(false),
    title = '',
    onClose = undefined as (() => void) | undefined,
    children = $props(),
    footer = $props(),
  }: {
    open: boolean;
    title?: string;
    onClose?: () => void;
    children?: import('svelte').Snippet;
    footer?: import('svelte').Snippet;
  } = $props();

  function close() {
    open = false;
    onClose?.();
  }

  function onBackdrop(e: MouseEvent) {
    if (e.target === e.currentTarget) close();
  }
</script>

{#if open}
  <div class="modal-backdrop" onclick={onBackdrop} role="presentation">
    <div class="modal" role="dialog" aria-modal="true" aria-label={title}>
      {#if title}
        <div class="modal-header">
          <h3 class="modal-title">{title}</h3>
          <button class="modal-close btn-ghost btn" onclick={close} aria-label="close">×</button>
        </div>
      {/if}
      <div class="modal-body">
        {@render children?.()}
      </div>
      {#if footer}
        <div class="modal-footer">
          {@render footer()}
        </div>
      {/if}
    </div>
  </div>
{/if}

<style>
  .modal-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    padding: 16px;
  }
  .modal {
    background: var(--color-bg-elev);
    border: 1px solid var(--color-border);
    border-radius: 10px;
    width: 100%;
    max-width: 480px;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
  }
  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .modal-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
  }
  .modal-close {
    font-size: 20px;
    line-height: 1;
    padding: 2px 8px;
  }
  .modal-body {
    padding: 16px;
    overflow: auto;
    flex: 1 1 auto;
  }
  .modal-footer {
    padding: 12px 16px;
    border-top: 1px solid var(--color-border-soft);
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>

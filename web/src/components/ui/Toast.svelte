<script lang="ts">
  // Toast 通知：单例响应式列表，提供 add/remove API。
  // 通过 toastStore 管理全局队列。
  import { toastStore, type ToastItem } from '$lib/stores/toast';

  let items = $derived(toastStore.items);

  function dismiss(id: number) {
    toastStore.remove(id);
  }
</script>

{#if items.length > 0}
  <div class="toast-container" role="region" aria-live="polite">
    {#each items as item (item.id)}
      <div class="toast {item.type}">
        <span class="toast-msg">{item.message}</span>
        <button class="toast-close" onclick={() => dismiss(item.id)} aria-label="close">×</button>
      </div>
    {/each}
  </div>
{/if}

<style>
  .toast-container {
    position: fixed;
    top: 12px;
    right: 12px;
    z-index: 2000;
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-width: 340px;
  }
  .toast {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    background: var(--color-bg-elev2);
    border: 1px solid var(--color-border);
    border-left: 3px solid var(--color-accent);
    border-radius: 6px;
    color: var(--color-fg);
    font-size: 13px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  }
  .toast.success {
    border-left-color: var(--color-online);
  }
  .toast.error {
    border-left-color: var(--color-busy);
  }
  .toast.warn {
    border-left-color: var(--color-warn);
  }
  .toast-msg {
    flex: 1 1 auto;
  }
  .toast-close {
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 16px;
    line-height: 1;
    padding: 0 4px;
  }
  .toast-close:hover {
    color: var(--color-fg);
  }
</style>

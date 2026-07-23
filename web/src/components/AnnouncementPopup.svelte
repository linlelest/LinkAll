<script lang="ts">
  // 公告浮窗：普通用户登录后自动弹出未读公告（按平台/版本过滤）。
  // 仅对非管理员用户显示；管理员在公告管理页查看。
  import { t } from '$lib/i18n';
  import { authStore } from '$lib/stores/auth';
  import { listAnnouncements, markRead, type Announcement } from '$lib/api/announcements';
  import { renderMarkdown } from '$lib/utils/markdown';
  import { formatDateTime } from '$lib/utils/format';
  import { browserStorage } from '$lib/utils/storage';

  let visible = $state(false);
  let queue = $state<Announcement[]>([]);
  let index = $state(0);

  let current = $derived(queue[index]);
  let isLoggedIn = $derived(authStore.isLoggedIn);
  let isAdmin = $derived(authStore.isAdmin);

  const READ_KEY = 'linkall.readAnnouncements';

  // 当前应用版本（由 vite define 注入，见 vite.config.ts）
  const APP_VERSION: string = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.0.0';

  function getReadIds(): Set<number> {
    const raw = browserStorage.get(READ_KEY, '[]');
    try {
      return new Set(JSON.parse(raw) as number[]);
    } catch {
      return new Set();
    }
  }

  function markReadLocal(id: number) {
    const ids = getReadIds();
    ids.add(id);
    browserStorage.set(READ_KEY, JSON.stringify([...ids]));
  }

  // 版本匹配：空/`*`/`all` 表示匹配所有；否则支持精确、前缀（`0.1` 匹配 `0.1.x`）、
  // 逗号分隔列表（`0.1.0,0.2.0`）。
  function versionMatches(filter: string): boolean {
    if (!filter) return true;
    const f = filter.trim().toLowerCase();
    if (!f || f === '*' || f === 'all') return true;
    const parts = f.split(',').map((s) => s.trim()).filter(Boolean);
    for (const p of parts) {
      if (p === APP_VERSION) return true;
      // 前缀匹配：`0.1` 匹配 `0.1.0`、`0.1.5`
      if (p.endsWith('.') && APP_VERSION.startsWith(p)) return true;
      if (!p.endsWith('.') && APP_VERSION.startsWith(p + '.')) return true;
    }
    return false;
  }

  // 过滤：平台匹配（all/web）+ 版本匹配 + 未读
  function filterUnread(list: Announcement[]): Announcement[] {
    const readIds = getReadIds();
    return list.filter((a) => {
      if (readIds.has(a.id)) return false;
      if (a.platform !== 'all' && a.platform !== 'web') return false;
      if (!versionMatches(a.versionFilter)) return false;
      return true;
    });
  }

  async function load() {
    if (!isLoggedIn || isAdmin) return;
    try {
      const { data } = await listAnnouncements(20, 0);
      const pending = filterUnread(data);
      // 置顶优先
      pending.sort((a, b) => Number(b.pinned) - Number(a.pinned));
      if (pending.length > 0) {
        queue = pending;
        index = 0;
        visible = true;
      }
    } catch {
      // 静默失败
    }
  }

  function close() {
    if (current) {
      markReadLocal(current.id);
      void markRead(current.id).catch(() => {});
    }
    if (index < queue.length - 1) {
      index += 1;
    } else {
      visible = false;
      queue = [];
      index = 0;
    }
  }

  // 登录态变化时加载
  $effect(() => {
    if (isLoggedIn && !isAdmin) {
      void load();
    }
  });
</script>

{#if visible && current}
  <div class="ann-popup-overlay" role="dialog" aria-modal="true">
    <div class="ann-popup card">
      <div class="popup-head">
        {#if current.pinned}<span class="pin">★</span>{/if}
        <span class="popup-title">{current.title}</span>
        <button class="popup-close" onclick={close} aria-label="close">✕</button>
      </div>
      <div class="popup-body markdown-body">
        {@html renderMarkdown(current.contentMd)}
      </div>
      <div class="popup-foot">
        <span class="muted">{formatDateTime(current.createdAt)}</span>
        <span class="muted">{index + 1}/{queue.length}</span>
        <button class="btn btn-sm btn-primary" onclick={close}>
          {index < queue.length - 1 ? t('common.next') : t('common.confirm')}
        </button>
      </div>
    </div>
  </div>
{/if}

<style>
  .ann-popup-overlay {
    position: fixed;
    inset: 0;
    z-index: 200;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
    background: rgba(0, 0, 0, 0.55);
  }
  .ann-popup {
    width: 100%;
    max-width: 520px;
    max-height: 80vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .popup-head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 14px;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .pin {
    color: var(--color-warn);
  }
  .popup-title {
    flex: 1 1 auto;
    font-weight: 600;
    font-size: 14px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .popup-close {
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 16px;
    padding: 2px 6px;
  }
  .popup-close:hover {
    color: var(--color-fg);
  }
  .popup-body {
    padding: 12px 14px;
    overflow: auto;
    font-size: 13px;
    line-height: 1.6;
  }
  .popup-body :global(h1),
  .popup-body :global(h2),
  .popup-body :global(h3) {
    margin: 10px 0 6px;
    font-weight: 600;
  }
  .popup-body :global(p) {
    margin: 6px 0;
  }
  .popup-body :global(a) {
    color: var(--color-accent);
    text-decoration: underline;
  }
  .popup-body :global(code) {
    background: var(--color-bg-soft);
    padding: 1px 4px;
    border-radius: 3px;
    font-family: var(--font-mono);
    font-size: 12px;
  }
  .popup-body :global(pre) {
    background: var(--color-bg-soft);
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;
  }
  .popup-body :global(ul),
  .popup-body :global(ol) {
    padding-left: 20px;
    margin: 6px 0;
  }
  .popup-body :global(blockquote) {
    border-left: 3px solid var(--color-border);
    padding-left: 10px;
    margin: 8px 0;
    color: var(--color-fg-dim);
  }
  .popup-foot {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border-top: 1px solid var(--color-border-soft);
    justify-content: space-between;
  }
  .btn-sm {
    padding: 4px 12px;
    font-size: 12px;
  }
</style>

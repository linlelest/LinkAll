<script lang="ts">
  // 公告列表页：可折叠面板，置顶在前。
  import { t } from '$lib/i18n';
  import { listAnnouncements, markRead, type Announcement } from '$lib/api/announcements';
  import { toast } from '$lib/stores/toast';
  import { ApiError } from '$lib/api/client';
  import { formatDateTime, formatRelative } from '$lib/utils/format';

  let list = $state<Announcement[]>([]);
  let expanded = $state<Record<number, boolean>>({});
  let loading = $state(false);

  async function refresh() {
    loading = true;
    try {
      const { data } = await listAnnouncements(50, 0);
      list = data;
    } catch (e) {
      toast.error((e as ApiError).message);
    } finally {
      loading = false;
    }
  }

  async function toggle(a: Announcement) {
    expanded = { ...expanded, [a.id]: !expanded[a.id] };
    if (expanded[a.id]) {
      try {
        await markRead(a.id);
      } catch {
        // ignore
      }
    }
  }

  $effect(() => {
    void refresh();
  });
</script>

<div class="ann-page">
  <div class="toolbar">
    <h2 class="page-title">{t('announcements.title')}</h2>
    <button class="btn btn-sm" onclick={refresh} disabled={loading}>{t('common.refresh')}</button>
  </div>

  {#if loading && list.length === 0}
    <div class="empty">{t('common.loading')}</div>
  {:else if list.length === 0}
    <div class="empty">{t('announcements.empty')}</div>
  {:else}
    <div class="ann-list">
      {#each list as a (a.id)}
        <div class="ann-item card">
          <button class="ann-head" onclick={() => toggle(a)}>
            {#if a.pinned}
              <span class="pin">★</span>
            {/if}
            <span class="ann-title">{a.title}</span>
            <span class="ann-time muted">{formatRelative(a.createdAt)}</span>
            <span class="toggle">{expanded[a.id] ? '▾' : '▸'}</span>
          </button>
          {#if expanded[a.id]}
            <div class="ann-body">
              <pre class="ann-content">{a.contentMd || '-'}</pre>
              <div class="ann-meta muted">{t('announcements.published_at', { time: formatDateTime(a.createdAt) })}</div>
            </div>
          {/if}
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .ann-page {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 820px;
  }
  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .page-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }
  .btn-sm {
    padding: 3px 8px;
    font-size: 12px;
  }
  .empty {
    padding: 40px;
    text-align: center;
    color: var(--color-fg-dim);
  }
  .ann-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .ann-item {
    overflow: hidden;
  }
  .ann-head {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 10px 12px;
    background: none;
    border: none;
    color: var(--color-fg);
    cursor: pointer;
    text-align: left;
    font-size: 13px;
  }
  .pin {
    color: var(--color-warn);
  }
  .ann-title {
    flex: 1 1 auto;
    font-weight: 600;
  }
  .ann-time {
    font-size: 11px;
  }
  .toggle {
    color: var(--color-fg-muted);
  }
  .ann-body {
    padding: 0 12px 12px;
    border-top: 1px solid var(--color-border-soft);
  }
  .ann-content {
    margin: 8px 0;
    font-family: var(--font-mono);
    font-size: 12px;
    white-space: pre-wrap;
    color: var(--color-fg);
  }
  .ann-meta {
    font-size: 11px;
  }
</style>

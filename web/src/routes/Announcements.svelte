<script lang="ts">
  // 公告列表页 + 管理员 Markdown 编辑器
  // 普通用户：查看公告（Markdown 渲染）、标记已读
  // 管理员：额外可新建/编辑/删除公告，含实时 Markdown 预览与 Ed25519 签名验证
  import { t } from '$lib/i18n';
  import { authStore } from '$lib/stores/auth';
  import {
    listAnnouncements,
    markRead,
    createAnnouncement,
    updateAnnouncement,
    deleteAnnouncement,
    getAnnouncementPublicKey,
    type Announcement,
  } from '$lib/api/announcements';
  import { toast } from '$lib/stores/toast';
  import { ApiError } from '$lib/api/client';
  import { formatDateTime, formatRelative } from '$lib/utils/format';
  import { renderMarkdown, verifyAnnouncementSignature } from '$lib/utils/markdown';

  let list = $state<Announcement[]>([]);
  let expanded = $state<Record<number, boolean>>({});
  let loading = $state(false);

  // Ed25519 公钥与签名验证状态
  let pubKeyHex = $state('');
  let verifiedSet = $state<Record<number, boolean>>({});

  // 管理员编辑器状态
  let editing = $state(false);
  let editingId = $state<number | null>(null); // null=新建，数字=编辑
  let form = $state({
    title: '',
    contentMd: '',
    pinned: false,
    platform: 'all',
    versionFilter: '',
  });
  let showPreview = $state(false);
  let saving = $state(false);

  // Markdown 编辑器 textarea 引用（用于工具栏操作选区）
  let ta = $state<HTMLTextAreaElement | null>(null);

  const platforms = ['all', 'windows', 'linux', 'android', 'web'];

  // 包裹选中文本（前后缀），无选中时插入占位
  function wrap(before: string, after: string, placeholder = '') {
    if (!ta) return;
    const s = ta.selectionStart;
    const e = ta.selectionEnd;
    const sel = form.contentMd.slice(s, e) || placeholder;
    const text = before + sel + after;
    form.contentMd = form.contentMd.slice(0, s) + text + form.contentMd.slice(e);
    requestAnimationFrame(() => {
      ta!.focus();
      ta!.selectionStart = s + before.length;
      ta!.selectionEnd = s + before.length + sel.length;
    });
  }

  // 行首插入前缀（对选中范围内每一行）
  function linePrefix(prefix: string) {
    if (!ta) return;
    const s = ta.selectionStart;
    const e = ta.selectionEnd;
    const block = form.contentMd.slice(s, e) || '';
    const lines = block.split('\n').map((l) => prefix + l);
    const text = lines.join('\n');
    form.contentMd = form.contentMd.slice(0, s) + text + form.contentMd.slice(e);
    requestAnimationFrame(() => ta!.focus());
  }

  // 在光标处插入文本
  function insert(text: string) {
    if (!ta) return;
    const s = ta.selectionStart;
    form.contentMd = form.contentMd.slice(0, s) + text + form.contentMd.slice(ta.selectionEnd);
    requestAnimationFrame(() => {
      ta!.focus();
      ta!.selectionStart = ta!.selectionEnd = s + text.length;
    });
  }

  async function refresh() {
    loading = true;
    try {
      const { data } = await listAnnouncements(50, 0);
      list = data;
      // 拉取公钥并异步验证签名
      if (!pubKeyHex) {
        try {
          const { publicKey } = await getAnnouncementPublicKey();
          pubKeyHex = publicKey;
        } catch {
          // 公钥不可用，跳过验签
        }
      }
      // 异步验证每条公告的签名
      for (const a of data) {
        if (a.signature && pubKeyHex) {
          verifyAnnouncementSignature(a.title, a.contentMd, a.createdAt, a.signature, pubKeyHex)
            .then((ok) => {
              verifiedSet = { ...verifiedSet, [a.id]: ok };
            })
            .catch(() => {
              verifiedSet = { ...verifiedSet, [a.id]: false };
            });
        }
      }
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

  // === 管理员编辑器 ===
  function openNew() {
    editingId = null;
    form = { title: '', contentMd: '', pinned: false, platform: 'all', versionFilter: '' };
    showPreview = false;
    editing = true;
  }

  function openEdit(a: Announcement) {
    editingId = a.id;
    form = {
      title: a.title,
      contentMd: a.contentMd,
      pinned: a.pinned,
      platform: a.platform,
      versionFilter: a.versionFilter,
    };
    showPreview = false;
    editing = true;
  }

  function cancelEdit() {
    editing = false;
    editingId = null;
  }

  async function save() {
    if (!form.title.trim()) {
      toast.error(t('announcements.admin_title'));
      return;
    }
    saving = true;
    try {
      if (editingId === null) {
        await createAnnouncement({
          title: form.title,
          contentMd: form.contentMd,
          pinned: form.pinned,
          platform: form.platform,
          versionFilter: form.versionFilter,
        });
        toast.success(t('announcements.admin_created'));
      } else {
        await updateAnnouncement(editingId, {
          title: form.title,
          contentMd: form.contentMd,
          pinned: form.pinned,
          platform: form.platform,
          versionFilter: form.versionFilter,
        });
        toast.success(t('announcements.admin_updated'));
      }
      editing = false;
      editingId = null;
      await refresh();
    } catch (e) {
      toast.error((e as ApiError).message);
    } finally {
      saving = false;
    }
  }

  async function remove(a: Announcement) {
    if (!confirm(t('announcements.admin_delete_confirm'))) return;
    try {
      await deleteAnnouncement(a.id);
      toast.success(t('announcements.admin_deleted'));
      await refresh();
    } catch (e) {
      toast.error((e as ApiError).message);
    }
  }

  // 渲染后的 HTML（预览用）
  let previewHtml = $derived(renderMarkdown(form.contentMd));

  $effect(() => {
    void refresh();
  });
</script>

<div class="ann-page">
  <div class="toolbar">
    <h2 class="page-title">{t('announcements.title')}</h2>
    <div class="toolbar-actions">
      {#if authStore.isAdmin && !editing}
        <button class="btn btn-sm btn-primary" onclick={openNew}>{t('announcements.admin_new')}</button>
      {/if}
      <button class="btn btn-sm" onclick={refresh} disabled={loading}>{t('common.refresh')}</button>
    </div>
  </div>

  {#if editing}
    <!-- 管理员编辑器 -->
    <div class="editor card">
      <h3 class="editor-title">
        {editingId === null ? t('announcements.admin_new') : t('announcements.admin_edit')}
      </h3>
      <div class="editor-tabs">
        <button class="tab" class:active={!showPreview} onclick={() => (showPreview = false)}>
          {t('announcements.admin_edit_tab')}
        </button>
        <button class="tab" class:active={showPreview} onclick={() => (showPreview = true)}>
          {t('announcements.admin_preview')}
        </button>
      </div>
      <div class="editor-body">
        {#if !showPreview}
          <label class="field">
            <span class="label">{t('announcements.admin_title')}</span>
            <input type="text" bind:value={form.title} placeholder={t('announcements.admin_title')} />
          </label>
          <div class="field">
            <span class="label">{t('announcements.admin_content')}</span>
            <div class="md-toolbar">
              <button class="md-tool" title="H1" onclick={() => linePrefix('# ')}>H1</button>
              <button class="md-tool" title="H2" onclick={() => linePrefix('## ')}>H2</button>
              <button class="md-tool" title="H3" onclick={() => linePrefix('### ')}>H3</button>
              <span class="md-sep"></span>
              <button class="md-tool" title="粗体" onclick={() => wrap('**', '**', '粗体')}><b>B</b></button>
              <button class="md-tool" title="斜体" onclick={() => wrap('*', '*', '斜体')}><i>I</i></button>
              <button class="md-tool" title="删除线" onclick={() => wrap('~~', '~~', '删除线')}><s>S</s></button>
              <button class="md-tool" title="行内代码" onclick={() => wrap('`', '`', 'code')}>code</button>
              <span class="md-sep"></span>
              <button class="md-tool" title="链接" onclick={() => wrap('[', '](https://)', '链接文字')}>link</button>
              <button class="md-tool" title="图片" onclick={() => insert('![图片描述](图片地址)')}>img</button>
              <span class="md-sep"></span>
              <button class="md-tool" title="无序列表" onclick={() => linePrefix('- ')}>• 列表</button>
              <button class="md-tool" title="有序列表" onclick={() => linePrefix('1. ')}>1. 列表</button>
              <button class="md-tool" title="任务列表" onclick={() => linePrefix('- [ ] ')}>☐ 任务</button>
              <button class="md-tool" title="引用" onclick={() => linePrefix('> ')}>“ 引用</button>
              <span class="md-sep"></span>
              <button class="md-tool" title="代码块" onclick={() => wrap('\n```\n', '\n```\n', '代码')}>{'{ }'} 代码块</button>
              <button class="md-tool" title="分隔线" onclick={() => insert('\n---\n')}>— 分隔</button>
              <button class="md-tool" title="表格" onclick={() => insert('\n| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| A | B | C |\n')}>▦ 表格</button>
            </div>
            <textarea bind:this={ta} bind:value={form.contentMd} rows="12" placeholder="Markdown..."></textarea>
          </div>
          <div class="field-row">
            <label class="field">
              <span class="label">{t('announcements.admin_platform')}</span>
              <select bind:value={form.platform}>
                {#each platforms as p}
                  <option value={p}>{p === 'all' ? t('announcements.platform_all') : p}</option>
                {/each}
              </select>
            </label>
            <label class="field">
              <span class="label">{t('announcements.admin_version')}</span>
              <input
                type="text"
                bind:value={form.versionFilter}
                placeholder={t('announcements.admin_version_hint')}
              />
            </label>
            <label class="field checkbox-field">
              <input type="checkbox" bind:checked={form.pinned} />
              <span>{t('announcements.admin_pinned')}</span>
            </label>
          </div>
        {:else}
          <div class="preview">
            {#if form.title}
              <h3>{form.title}</h3>
            {/if}
            {@html previewHtml}
          </div>
        {/if}
      </div>
      <div class="editor-actions">
        <button class="btn" onclick={cancelEdit} disabled={saving}>{t('announcements.admin_cancel')}</button>
        <button class="btn btn-primary" onclick={save} disabled={saving}>
          {editingId === null ? t('announcements.admin_publish') : t('announcements.admin_update')}
        </button>
      </div>
    </div>
  {:else if loading && list.length === 0}
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
            {#if a.signature && verifiedSet[a.id] !== undefined}
              <span class="sig-badge" class:verified={verifiedSet[a.id]} class:unverified={!verifiedSet[a.id]}>
                {verifiedSet[a.id] ? '✓' : '?'}
                {verifiedSet[a.id] ? t('announcements.signature_verified') : t('announcements.signature_unverified')}
              </span>
            {/if}
            <span class="ann-time muted">{formatRelative(a.createdAt)}</span>
            <span class="toggle">{expanded[a.id] ? '▾' : '▸'}</span>
          </button>
          {#if expanded[a.id]}
            <div class="ann-body">
              <div class="ann-content markdown-body">{@html renderMarkdown(a.contentMd)}</div>
              <div class="ann-meta muted">
                <span>{t('announcements.published_at', { time: formatDateTime(a.createdAt) })}</span>
                <span class="platform-tag">{a.platform === 'all' ? t('announcements.platform_all') : a.platform}</span>
              </div>
              {#if authStore.isAdmin}
                <div class="ann-admin-actions">
                  <button class="btn btn-sm" onclick={() => openEdit(a)}>{t('common.edit')}</button>
                  <button class="btn btn-sm btn-danger" onclick={() => remove(a)}>{t('announcements.admin_delete')}</button>
                </div>
              {/if}
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
  .toolbar-actions {
    display: flex;
    gap: 8px;
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
  .btn-primary {
    background: var(--color-accent, #3b82f6);
    color: #fff;
    border: none;
  }
  .btn-danger {
    color: var(--color-danger, #ef4444);
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
  .sig-badge {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 8px;
    white-space: nowrap;
  }
  .sig-badge.verified {
    background: rgba(34, 197, 94, 0.15);
    color: #16a34a;
  }
  .sig-badge.unverified {
    background: rgba(234, 179, 8, 0.15);
    color: #ca8a04;
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
    font-size: 13px;
    line-height: 1.6;
    color: var(--color-fg);
  }
  .ann-content :global(h1),
  .ann-content :global(h2),
  .ann-content :global(h3),
  .ann-content :global(h4),
  .ann-content :global(h5),
  .ann-content :global(h6) {
    margin: 12px 0 6px;
    font-weight: 600;
  }
  .ann-content :global(p) {
    margin: 6px 0;
  }
  .ann-content :global(a) {
    color: var(--color-accent, #3b82f6);
    text-decoration: underline;
  }
  .ann-content :global(code) {
    background: var(--color-bg-soft, #f1f5f9);
    padding: 1px 4px;
    border-radius: 3px;
    font-family: var(--font-mono, monospace);
    font-size: 12px;
  }
  .ann-content :global(pre) {
    background: var(--color-bg-soft, #1e293b);
    color: var(--color-fg);
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 8px 0;
  }
  .ann-content :global(pre code) {
    background: none;
    padding: 0;
  }
  .ann-content :global(ul),
  .ann-content :global(ol) {
    margin: 6px 0;
    padding-left: 20px;
  }
  .ann-content :global(blockquote) {
    border-left: 3px solid var(--color-border);
    padding-left: 10px;
    margin: 8px 0;
    color: var(--color-fg-dim);
  }
  .ann-content :global(hr) {
    border: none;
    border-top: 1px solid var(--color-border-soft);
    margin: 10px 0;
  }
  .ann-content :global(table),
  .preview :global(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
    font-size: 12px;
  }
  .ann-content :global(th),
  .ann-content :global(td),
  .preview :global(th),
  .preview :global(td) {
    border: 1px solid var(--color-border-soft);
    padding: 4px 8px;
    text-align: left;
  }
  .ann-content :global(th),
  .preview :global(th) {
    background: var(--color-bg-soft);
    font-weight: 600;
  }
  .ann-content :global(.task-list),
  .preview :global(.task-list) {
    list-style: none;
    padding-left: 4px;
  }
  .ann-meta {
    font-size: 11px;
    display: flex;
    gap: 12px;
    align-items: center;
  }
  .platform-tag {
    background: var(--color-bg-soft, #f1f5f9);
    padding: 1px 6px;
    border-radius: 8px;
    font-size: 10px;
  }
  .ann-admin-actions {
    display: flex;
    gap: 6px;
    margin-top: 8px;
  }

  /* === 编辑器样式 === */
  .editor {
    padding: 16px;
  }
  .editor-title {
    margin: 0 0 12px;
    font-size: 15px;
    font-weight: 600;
  }
  .editor-tabs {
    display: flex;
    gap: 4px;
    border-bottom: 1px solid var(--color-border-soft);
    margin-bottom: 12px;
  }
  .editor-tabs .tab {
    padding: 6px 14px;
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 12px;
  }
  .editor-tabs .tab.active {
    color: var(--color-fg);
    border-bottom-color: var(--color-accent, #3b82f6);
  }
  .editor-body {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
  }
  .field .label {
    font-size: 11px;
    color: var(--color-fg-muted);
  }
  .field input,
  .field textarea,
  .field select {
    padding: 6px 8px;
    border: 1px solid var(--color-border, #e2e8f0);
    border-radius: 4px;
    background: var(--color-bg, #fff);
    color: var(--color-fg);
    font-size: 13px;
    font-family: inherit;
  }
  .field textarea {
    font-family: var(--font-mono, monospace);
    resize: vertical;
    min-height: 200px;
  }
  /* Markdown 工具栏 */
  .md-toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
    padding: 4px 6px;
    background: var(--color-bg-soft);
    border: 1px solid var(--color-border-soft);
    border-radius: 4px 4px 0 0;
    border-bottom: none;
  }
  .md-tool {
    padding: 3px 7px;
    border: 1px solid transparent;
    border-radius: 3px;
    background: transparent;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 12px;
    white-space: nowrap;
  }
  .md-tool:hover {
    background: var(--color-bg-elev2);
    color: var(--color-fg);
    border-color: var(--color-border-soft);
  }
  .md-sep {
    width: 1px;
    background: var(--color-border-soft);
    margin: 2px 3px;
  }
  .field-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }
  .checkbox-field {
    flex-direction: row;
    align-items: center;
    gap: 6px;
    padding-top: 18px;
  }
  .preview {
    min-height: 200px;
    padding: 8px;
    line-height: 1.6;
    font-size: 13px;
  }
  .preview :global(h1),
  .preview :global(h2),
  .preview :global(h3) {
    margin: 12px 0 6px;
  }
  .preview :global(p) {
    margin: 6px 0;
  }
  .preview :global(code) {
    background: var(--color-bg-soft, #f1f5f9);
    padding: 1px 4px;
    border-radius: 3px;
    font-family: var(--font-mono, monospace);
  }
  .preview :global(pre) {
    background: var(--color-bg-soft, #1e293b);
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
  }
  .preview :global(ul),
  .preview :global(ol) {
    padding-left: 20px;
  }
  .editor-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
</style>

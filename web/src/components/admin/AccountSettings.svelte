<script lang="ts">
  // 账号设置：修改密码、邀请码管理、查看公告（折叠）、语言切换。
  import { t, setLocale, getLocale, type Locale } from '$lib/i18n';
  import { authStore } from '$lib/stores/auth';
  import { changePassword } from '$lib/api/auth';
  import { generateInvites, listInvites, revokeInvite, exportInvites, type InviteCode } from '$lib/api/invites';
  import { listAnnouncements, markRead, type Announcement } from '$lib/api/announcements';
  import { ApiError } from '$lib/api/client';
  import { toast } from '$lib/stores/toast';
  import { formatDateTime, formatRelative } from '$lib/utils/format';
  import { copyText } from '$lib/utils/clipboard';
  import Toggle from '$components/ui/Toggle.svelte';

  // 修改密码
  let oldPw = $state('');
  let newPw = $state('');
  let confirmPw = $state('');
  let changing = $state(false);

  async function submitChangePassword() {
    if (newPw.length < 8) {
      toast.error(t('settings.password_short'));
      return;
    }
    if (newPw !== confirmPw) {
      toast.error(t('settings.password_mismatch'));
      return;
    }
    changing = true;
    try {
      await changePassword(oldPw, newPw);
      toast.success(t('settings.password_changed'));
      oldPw = newPw = confirmPw = '';
    } catch (e) {
      toast.error((e as ApiError).message || t('settings.old_password_wrong'));
    } finally {
      changing = false;
    }
  }

  // 邀请码
  let inviteCount = $state(1);
  let inviteTtl = $state(168);
  let inviteNote = $state('');
  let invites = $state<InviteCode[]>([]);
  let genLoading = $state(false);

  async function loadInvites() {
    try {
      const { data } = await listInvites(100, 0);
      invites = data;
    } catch (e) {
      toast.error((e as ApiError).message);
    }
  }

  async function generate() {
    genLoading = true;
    try {
      const res = await generateInvites(inviteCount, inviteTtl, inviteNote);
      toast.success(t('invite.generated') + ` (${res.count})`);
      await loadInvites();
    } catch (e) {
      toast.error((e as ApiError).message);
    } finally {
      genLoading = false;
    }
  }

  async function revoke(code: string) {
    if (!confirm(t('settings.invite_revoke_confirm'))) return;
    try {
      await revokeInvite(code);
      toast.success(t('settings.invite_revoked_ok'));
      await loadInvites();
    } catch (e) {
      toast.error((e as ApiError).message);
    }
  }

  async function exportCsv() {
    try {
      const csv = await exportInvites();
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'invite-codes.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast.error((e as ApiError).message);
    }
  }

  async function copyCode(code: string) {
    if (await copyText(code)) toast.success(t('common.copied'));
  }

  function inviteStatus(c: InviteCode): string {
    if (c.revoked) return t('settings.invite_revoked');
    if (c.used) return t('settings.invite_used');
    if (c.expiresAt * 1000 < Date.now()) return t('settings.invite_expired');
    return t('settings.invite_unused');
  }

  // 公告
  let announcements = $state<Announcement[]>([]);
  let expanded = $state<Record<number, boolean>>({});

  async function loadAnnouncements() {
    try {
      const { data } = await listAnnouncements(20, 0);
      announcements = data;
    } catch (e) {
      toast.error((e as ApiError).message);
    }
  }

  async function toggleAnnouncement(a: Announcement) {
    expanded = { ...expanded, [a.id]: !expanded[a.id] };
    if (!expanded[a.id]) return;
    try {
      await markRead(a.id);
    } catch {
      // 忽略
    }
  }

  // 语言
  let locale = $state<Locale>(getLocale());
  function switchLocale(l: Locale) {
    locale = l;
    setLocale(l);
  }

  $effect(() => {
    void loadInvites();
    void loadAnnouncements();
  });
</script>

<div class="account-page">
  {/* 修改密码 */}
  <section class="card section">
    <h3 class="section-title">{t('settings.change_password')}</h3>
    <div class="form-grid">
      <div>
        <label class="label">{t('settings.old_password')}</label>
        <input class="input" type="password" bind:value={oldPw} autocomplete="current-password" />
      </div>
      <div>
        <label class="label">{t('settings.new_password')}</label>
        <input class="input" type="password" bind:value={newPw} autocomplete="new-password" />
      </div>
      <div>
        <label class="label">{t('settings.confirm_password')}</label>
        <input class="input" type="password" bind:value={confirmPw} autocomplete="new-password" />
      </div>
      <div class="form-actions">
        <button class="btn btn-primary" onclick={submitChangePassword} disabled={changing}>
          {t('common.save')}
        </button>
      </div>
    </div>
  </section>

  {/* 邀请码 */}
  {#if authStore.isAdmin}
    <section class="card section">
      <div class="section-header">
        <h3 class="section-title">{t('settings.invite_codes')}</h3>
        <button class="btn btn-sm" onclick={exportCsv}>{t('settings.invite_export')}</button>
      </div>
      <div class="form-grid">
        <div>
          <label class="label">{t('settings.invite_count')}</label>
          <input class="input" type="number" min="1" max="1000" bind:value={inviteCount} />
        </div>
        <div>
          <label class="label">{t('settings.invite_ttl')}</label>
          <input class="input" type="number" min="1" bind:value={inviteTtl} />
        </div>
        <div>
          <label class="label">{t('settings.invite_note')}</label>
          <input class="input" type="text" bind:value={inviteNote} />
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" onclick={generate} disabled={genLoading}>
            {t('common.generate')}
          </button>
        </div>
      </div>

      <div class="invite-list">
        {#each invites.slice(0, 50) as c (c.id)}
          <div class="invite-row">
            <button class="invite-code mono" onclick={() => copyCode(c.code)}>{c.code} ⧉</button>
            <span class="invite-status" class:used={c.used || c.revoked}>{inviteStatus(c)}</span>
            <span class="muted mono">{formatRelative(c.createdAt)}</span>
            <button class="btn btn-sm btn-danger" disabled={c.revoked || c.used} onclick={() => revoke(c.code)}>
              {t('common.revoke')}
            </button>
          </div>
        {/each}
      </div>
    </section>
  {/if}

  {/* 公告折叠面板 */}
  <section class="card section">
    <h3 class="section-title">{t('announcements.title')}</h3>
    {#if announcements.length === 0}
      <div class="empty">{t('announcements.empty')}</div>
    {:else}
      <div class="ann-list">
        {#each announcements as a (a.id)}
          <div class="ann-item">
            <button class="ann-head" onclick={() => toggleAnnouncement(a)}>
              <span class="ann-pin" class:pinned={a.pinned}>{a.pinned ? '★' : '○'}</span>
              <span class="ann-title">{a.title}</span>
              <span class="ann-time muted">{formatDateTime(a.createdAt)}</span>
              <span class="ann-toggle">{expanded[a.id] ? '▾' : '▸'}</span>
            </button>
            {#if expanded[a.id]}
              <div class="ann-body">{a.contentMd || '-'}</div>
            {/if}
          </div>
        {/each}
      </div>
    {/if}
  </section>

  {/* 语言切换 */}
  <section class="card section">
    <h3 class="section-title">{t('settings.language')}</h3>
    <div class="lang-row">
      <button class="btn" class:active={locale === 'zh-CN'} onclick={() => switchLocale('zh-CN')}>
        {t('settings.language_zh')}
      </button>
      <button class="btn" class:active={locale === 'en-US'} onclick={() => switchLocale('en-US')}>
        {t('settings.language_en')}
      </button>
    </div>
  </section>
</div>

<style>
  .account-page {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-width: 760px;
  }
  .section {
    padding: 14px;
  }
  .section-title {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 600;
  }
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
  .section-header .section-title {
    margin: 0;
  }
  .form-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 10px;
  }
  .form-actions {
    grid-column: 1 / -1;
    display: flex;
    justify-content: flex-end;
  }
  .btn-sm {
    padding: 3px 8px;
    font-size: 12px;
  }
  .invite-list {
    margin-top: 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 280px;
    overflow: auto;
  }
  .invite-row {
    display: grid;
    grid-template-columns: 1fr auto auto auto;
    gap: 8px;
    align-items: center;
    padding: 6px 8px;
    background: var(--color-bg-soft);
    border-radius: 4px;
    font-size: 12px;
  }
  .invite-code {
    background: none;
    border: none;
    color: var(--color-accent);
    cursor: pointer;
    text-align: left;
    font-size: 12px;
  }
  .invite-status.used {
    color: var(--color-fg-dim);
  }
  .empty {
    padding: 20px;
    text-align: center;
    color: var(--color-fg-dim);
  }
  .ann-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .ann-item {
    border-bottom: 1px solid var(--color-border-soft);
  }
  .ann-head {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 8px 4px;
    background: none;
    border: none;
    color: var(--color-fg);
    cursor: pointer;
    text-align: left;
    font-size: 13px;
  }
  .ann-pin.pinned {
    color: var(--color-warn);
  }
  .ann-title {
    flex: 1 1 auto;
  }
  .ann-time {
    font-size: 11px;
  }
  .ann-toggle {
    color: var(--color-fg-muted);
  }
  .ann-body {
    padding: 8px 12px 12px 24px;
    color: var(--color-fg-muted);
    font-size: 12px;
    white-space: pre-wrap;
  }
  .lang-row {
    display: flex;
    gap: 8px;
  }
  .btn.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
</style>

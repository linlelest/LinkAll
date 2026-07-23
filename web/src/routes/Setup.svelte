<script lang="ts">
  // 首次启动初始化页：创建首个超级管理员账户。
  // 仅在系统无 superadmin 时可用，创建成功后自动登录并跳转仪表盘。
  import { t } from '$lib/i18n';
  import { initSetup } from '$lib/api/setup';
  import { authStore } from '$lib/stores/auth';
  import type { UserInfo } from '$lib/stores/auth';
  import { routerStore } from '$lib/stores/router';
  import { setupStore } from '$lib/stores/setup';
  import { toast } from '$lib/stores/toast';
  import { ApiError } from '$lib/api/client';
  import { setLocale, getLocale } from '$lib/i18n';
  import type { Locale } from '$lib/i18n';

  let username = $state('');
  let password = $state('');
  let confirmPassword = $state('');
  let loading = $state(false);

  async function submit() {
    const u = username.trim();
    if (u.length < 3 || u.length > 32) {
      toast.error(t('setup.error_username_short'));
      return;
    }
    if (password.length < 8 || password.length > 128) {
      toast.error(t('setup.error_password_short'));
      return;
    }
    if (password !== confirmPassword) {
      toast.error(t('setup.error_password_mismatch'));
      return;
    }
    loading = true;
    try {
      const res = await initSetup(u, password);
      if (res.autoLogin && res.token) {
        const user: UserInfo = {
          id: res.userId,
          username: res.username,
          role: 'superadmin',
          status: 'active',
          banned: false,
          deviceCount: 0,
          createdAt: Math.floor(Date.now() / 1000),
          lastLoginIp: '',
        };
        authStore.setSession(res.token, res.expiresIn ?? 0, user);
        setupStore.markDone();
        toast.success(t('setup.success'));
        routerStore.goAfterLogin();
      } else {
        // 自动登录失败，回退到登录页
        setupStore.markDone();
        toast.info(res.message || t('setup.success'));
        routerStore.go('login');
      }
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'ERR_SETUP_ALREADY_DONE') {
        toast.error(t('setup.error_done'));
        setupStore.markDone();
        routerStore.go('login');
      } else {
        toast.error(err.message || t('setup.error_failed'));
      }
    } finally {
      loading = false;
    }
  }

  function switchLocale(l: Locale) {
    setLocale(l);
  }
  let locale = $derived(getLocale());
</script>

<div class="setup-page">
  <div class="setup-card card">
    <div class="brand">
      <span class="brand-mark">L</span>
      <div class="brand-text">
        <div class="brand-name">LinkALL</div>
        <div class="brand-sub muted">{t('setup.subtitle')}</div>
      </div>
    </div>

    <div class="notice">{t('setup.notice')}</div>

    <form class="form" onsubmit={(e) => { e.preventDefault(); void submit(); }}>
      <div class="field">
        <label class="label">{t('setup.username')}</label>
        <input class="input" bind:value={username} autocomplete="username" />
      </div>
      <div class="field">
        <label class="label">{t('setup.password')}</label>
        <input class="input" type="password" bind:value={password} autocomplete="new-password" />
      </div>
      <div class="field">
        <label class="label">{t('setup.confirm_password')}</label>
        <input class="input" type="password" bind:value={confirmPassword} autocomplete="new-password" />
      </div>
      <button class="btn btn-primary submit-btn" type="submit" disabled={loading}>
        {loading ? t('common.loading') : t('setup.submit')}
      </button>
    </form>

    <div class="lang-switch">
      <button class="btn btn-sm" class:active={locale === 'zh-CN'} onclick={() => switchLocale('zh-CN')}>中文</button>
      <button class="btn btn-sm" class:active={locale === 'en-US'} onclick={() => switchLocale('en-US')}>EN</button>
    </div>
  </div>
</div>

<style>
  .setup-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100%;
    padding: 16px;
    background: var(--color-bg);
  }
  .setup-card {
    width: 100%;
    max-width: 380px;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .brand-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 8px;
    background: var(--color-accent);
    color: #0b0b1a;
    font-weight: 800;
    font-size: 22px;
  }
  .brand-name {
    font-size: 18px;
    font-weight: 700;
  }
  .brand-sub {
    font-size: 11px;
  }
  .notice {
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--color-accent-soft, rgba(99, 102, 241, 0.12));
    border: 1px solid var(--color-accent);
    font-size: 12px;
    line-height: 1.5;
    color: var(--color-fg-muted);
  }
  .form {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .submit-btn {
    width: 100%;
    padding: 10px;
    font-size: 14px;
  }
  .lang-switch {
    display: flex;
    gap: 6px;
    justify-content: center;
  }
  .btn-sm {
    padding: 3px 10px;
    font-size: 11px;
  }
  .btn-sm.active {
    border-color: var(--color-accent);
    color: var(--color-accent);
  }
</style>

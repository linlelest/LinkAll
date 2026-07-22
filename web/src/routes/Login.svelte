<script lang="ts">
  // 登录 / 注册页：双 tab 切换，邀请码注册。
  import { t } from '$lib/i18n';
  import { login, register } from '$lib/api/auth';
  import { authStore } from '$lib/stores/auth';
  import { routerStore } from '$lib/stores/router';
  import { toast } from '$lib/stores/toast';
  import { ApiError } from '$lib/api/client';
  import { setLocale, getLocale } from '$lib/i18n';
  import type { Locale } from '$lib/i18n';

  let tab = $state<'login' | 'register'>('login');
  let username = $state('');
  let password = $state('');
  let confirmPassword = $state('');
  let inviteCode = $state('');
  let loading = $state(false);

  async function submitLogin() {
    if (username.trim().length < 3) {
      toast.error(t('login.error_username_short'));
      return;
    }
    if (password.length < 8) {
      toast.error(t('login.error_password_short'));
      return;
    }
    loading = true;
    try {
      const res = await login(username.trim(), password);
      authStore.setSession(res.token, res.expiresIn, res.user);
      toast.success(t('login.welcome', { name: res.user.username }));
      routerStore.goAfterLogin();
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'ERR_USER_BANNED') toast.error(t('login.error_banned'));
      else toast.error(t('login.error_auth'));
    } finally {
      loading = false;
    }
  }

  async function submitRegister() {
    if (username.trim().length < 3) {
      toast.error(t('login.error_username_short'));
      return;
    }
    if (password.length < 8) {
      toast.error(t('login.error_password_short'));
      return;
    }
    if (password !== confirmPassword) {
      toast.error(t('login.error_password_mismatch'));
      return;
    }
    if (!inviteCode.trim()) {
      toast.error(t('login.error_invite'));
      return;
    }
    loading = true;
    try {
      const res = await register(username.trim(), password, inviteCode.trim());
      if (res.token) {
        authStore.setSession(res.token, res.expiresIn, res.user);
        toast.success(t('login.welcome', { name: res.user.username }));
        routerStore.goAfterLogin();
      } else {
        toast.success(t('auth.register_success'));
        tab = 'login';
      }
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 'ERR_INVITE_CODE_INVALID') toast.error(t('login.error_invite'));
      else if (err.code === 'ERR_INVITE_CODE_USED') toast.error(t('login.error_invite'));
      else if (err.code === 'ERR_INVITE_CODE_EXPIRED') toast.error(t('login.error_invite'));
      else toast.error(err.message || t('login.error_username_exists'));
    } finally {
      loading = false;
    }
  }

  function switchLocale(l: Locale) {
    setLocale(l);
  }
  let locale = $derived(getLocale());
</script>

<div class="login-page">
  <div class="login-card card">
    <div class="brand">
      <span class="brand-mark">L</span>
      <div class="brand-text">
        <div class="brand-name">LinkALL</div>
        <div class="brand-sub muted">{t('app.tagline')}</div>
      </div>
    </div>

    <div class="tabs">
      <button class="tab" class:active={tab === 'login'} onclick={() => (tab = 'login')}>
        {t('login.title')}
      </button>
      <button class="tab" class:active={tab === 'register'} onclick={() => (tab = 'register')}>
        {t('login.register_tab')}
      </button>
    </div>

    {#if tab === 'login'}
      <form class="form" onsubmit={(e) => { e.preventDefault(); void submitLogin(); }}>
        <div class="field">
          <label class="label">{t('login.username')}</label>
          <input class="input" bind:value={username} autocomplete="username" />
        </div>
        <div class="field">
          <label class="label">{t('login.password')}</label>
          <input class="input" type="password" bind:value={password} autocomplete="current-password" />
        </div>
        <button class="btn btn-primary submit-btn" type="submit" disabled={loading}>
          {loading ? t('common.loading') : t('login.submit')}
        </button>
        <button type="button" class="switch-link" onclick={() => (tab = 'register')}>
          {t('login.to_register')}
        </button>
      </form>
    {:else}
      <form class="form" onsubmit={(e) => { e.preventDefault(); void submitRegister(); }}>
        <div class="field">
          <label class="label">{t('login.username')}</label>
          <input class="input" bind:value={username} autocomplete="username" />
        </div>
        <div class="field">
          <label class="label">{t('login.password')}</label>
          <input class="input" type="password" bind:value={password} autocomplete="new-password" />
        </div>
        <div class="field">
          <label class="label">{t('login.confirm_password')}</label>
          <input class="input" type="password" bind:value={confirmPassword} autocomplete="new-password" />
        </div>
        <div class="field">
          <label class="label">{t('login.invite_code')}</label>
          <input class="input mono" bind:value={inviteCode} placeholder="XXXX-XXXX-XXXX" />
        </div>
        <button class="btn btn-primary submit-btn" type="submit" disabled={loading}>
          {loading ? t('common.loading') : t('login.register_submit')}
        </button>
        <button type="button" class="switch-link" onclick={() => (tab = 'login')}>
          {t('login.to_login')}
        </button>
      </form>
    {/if}

    <div class="lang-switch">
      <button class="btn btn-sm" class:active={locale === 'zh-CN'} onclick={() => switchLocale('zh-CN')}>中文</button>
      <button class="btn btn-sm" class:active={locale === 'en-US'} onclick={() => switchLocale('en-US')}>EN</button>
    </div>
  </div>
</div>

<style>
  .login-page {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 100%;
    padding: 16px;
    background: var(--color-bg);
  }
  .login-card {
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
  .tabs {
    display: flex;
    border-bottom: 1px solid var(--color-border-soft);
  }
  .tab {
    flex: 1 1 0;
    padding: 8px;
    background: none;
    border: none;
    color: var(--color-fg-muted);
    cursor: pointer;
    font-size: 13px;
    border-bottom: 2px solid transparent;
  }
  .tab.active {
    color: var(--color-accent);
    border-bottom-color: var(--color-accent);
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
  .switch-link {
    background: none;
    border: none;
    color: var(--color-accent);
    cursor: pointer;
    font-size: 12px;
    text-align: center;
    padding: 4px;
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

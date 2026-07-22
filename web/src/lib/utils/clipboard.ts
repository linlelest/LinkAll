// 剪贴板辅助：优先使用 Clipboard API，降级到 execCommand。
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 降级到旧 API
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    ta.style.top = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

export async function readText(): Promise<string> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      return await navigator.clipboard.readText();
    }
  } catch {
    // ignore
  }
  return '';
}

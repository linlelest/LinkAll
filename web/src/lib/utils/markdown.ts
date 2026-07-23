// 轻量级 Markdown 渲染器（无外部依赖）
// 支持：标题、粗体/斜体、行内代码、代码块、链接、列表、引用、分隔线、段落
// 安全：先转义 HTML 再渲染，防止 XSS

// 转义 HTML 特殊字符
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// 渲染行内 Markdown（粗体、斜体、行内代码、链接）
function renderInline(text: string): string {
  let result = text;
  // 行内代码 `code`
  result = result.replace(/`([^`]+)`/g, (_, code) => `<code>${escapeHtml(code)}</code>`);
  // 链接 [text](url) — 仅允许 http/https 协议
  result = result.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_, label, url) => {
    return `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`;
  });
  // 粗体 **text**
  result = result.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  // 斜体 *text*
  result = result.replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');
  // 删除线 ~~text~~
  result = result.replace(/~~([^~]+)~~/g, '<del>$1</del>');
  return result;
}

// 将 Markdown 文本渲染为 HTML 字符串
export function renderMarkdown(md: string): string {
  if (!md || !md.trim()) return '';

  const lines = md.split('\n');
  const html: string[] = [];
  let inCodeBlock = false;
  let codeBuffer: string[] = [];
  let inList = false;
  let listType: 'ul' | 'ol' = 'ul';
  let inQuote = false;

  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    const line = raw.trimEnd();

    // 代码块围栏 ```lang
    if (line.match(/^```/)) {
      if (inCodeBlock) {
        // 结束代码块
        const lang = codeBuffer[0]?.match(/^```(\w*)/)?.[1] || '';
        const code = codeBuffer.slice(lang ? 1 : 0).join('\n');
        html.push(`<pre><code class="language-${lang}">${escapeHtml(code)}</code></pre>`);
        codeBuffer = [];
        inCodeBlock = false;
      } else {
        // 开始代码块
        inCodeBlock = true;
        codeBuffer = [line];
      }
      continue;
    }

    if (inCodeBlock) {
      codeBuffer.push(line);
      continue;
    }

    // 空行
    if (line.trim() === '') {
      if (inList) {
        html.push(`</${listType}>`);
        inList = false;
      }
      if (inQuote) {
        html.push('</blockquote>');
        inQuote = false;
      }
      continue;
    }

    // 标题 # ~ ######
    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      if (inList) {
        html.push(`</${listType}>`);
        inList = false;
      }
      if (inQuote) {
        html.push('</blockquote>');
        inQuote = false;
      }
      const level = headingMatch[1].length;
      html.push(`<h${level}>${renderInline(escapeHtml(headingMatch[2]))}</h${level}>`);
      continue;
    }

    // 水平分隔线
    if (line.match(/^(-{3,}|\*{3,}|_{3,})$/)) {
      if (inList) {
        html.push(`</${listType}>`);
        inList = false;
      }
      html.push('<hr/>');
      continue;
    }

    // 引用块 >
    const quoteMatch = line.match(/^>\s*(.*)$/);
    if (quoteMatch) {
      if (inList) {
        html.push(`</${listType}>`);
        inList = false;
      }
      if (!inQuote) {
        html.push('<blockquote>');
        inQuote = true;
      }
      html.push(`<p>${renderInline(escapeHtml(quoteMatch[1]))}</p>`);
      continue;
    }
    if (inQuote) {
      html.push('</blockquote>');
      inQuote = false;
    }

    // 无序列表 - * +
    const ulMatch = line.match(/^[-*+]\s+(.+)$/);
    if (ulMatch) {
      if (!inList || listType !== 'ul') {
        if (inList) html.push(`</${listType}>`);
        html.push('<ul>');
        inList = true;
        listType = 'ul';
      }
      html.push(`<li>${renderInline(escapeHtml(ulMatch[1]))}</li>`);
      continue;
    }

    // 有序列表 1.
    const olMatch = line.match(/^\d+\.\s+(.+)$/);
    if (olMatch) {
      if (!inList || listType !== 'ol') {
        if (inList) html.push(`</${listType}>`);
        html.push('<ol>');
        inList = true;
        listType = 'ol';
      }
      html.push(`<li>${renderInline(escapeHtml(olMatch[1]))}</li>`);
      continue;
    }

    // 普通段落
    if (inList) {
      html.push(`</${listType}>`);
      inList = false;
    }
    html.push(`<p>${renderInline(escapeHtml(line))}</p>`);
  }

  // 清理未闭合的块
  if (inCodeBlock && codeBuffer.length > 0) {
    const code = codeBuffer.slice(1).join('\n');
    html.push(`<pre><code>${escapeHtml(code)}</code></pre>`);
  }
  if (inList) html.push(`</${listType}>`);
  if (inQuote) html.push('</blockquote>');

  return html.join('\n');
}

// Ed25519 签名验证（使用 Web Crypto API，浏览器支持时可用）
// 签名内容 = title + "\n" + contentMd + "\n" + createdAt（十进制字符串）
export async function verifyAnnouncementSignature(
  title: string,
  contentMd: string,
  createdAt: number,
  signatureB64: string,
  publicKeyHex: string,
): Promise<boolean> {
  if (!signatureB64 || !publicKeyHex) return false;
  try {
    // hex -> raw bytes
    const pubKeyRaw = hexToBytes(publicKeyHex);
    // base64 -> raw bytes
    const sigBytes = base64ToBytes(signatureB64);
    // 消息
    const msg = new TextEncoder().encode(`${title}\n${contentMd}\n${createdAt}`);
    // 导入 Ed25519 公钥（Node 18+ / Chrome 113+ 支持）
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      pubKeyRaw,
      { name: 'Ed25519' },
      false,
      ['verify'],
    );
    return crypto.subtle.verify('Ed25519', cryptoKey, sigBytes, msg);
  } catch {
    // 浏览器不支持 Ed25519 时标记为未验证
    return false;
  }
}

function hexToBytes(hex: string): Uint8Array {
  const arr = new Uint8Array(hex.length / 2);
  for (let i = 0; i < arr.length; i++) {
    arr[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  return arr;
}

function base64ToBytes(b64: string): Uint8Array {
  const binary = atob(b64);
  const arr = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    arr[i] = binary.charCodeAt(i);
  }
  return arr;
}

// OTA 更新检查 API。
// 注：服务端 /api/ota/check 在 Phase 6 完整实现，此处先按预期结构调用。
import { request } from './client';

export interface OtaCheckResult {
  hasUpdate: boolean;
  forceUpdate: boolean;
  currentVersion: string;
  latestVersion: string;
  releaseNotes: string;
  downloadUrl: string;
  signature: string;
  fileSize: number;
  publishedAt: number;
}

export interface OtaPlatformInfo {
  platform: string;
  version: string;
}

export function checkOta(current: OtaPlatformInfo): Promise<OtaCheckResult> {
  // 服务端 Phase 1 尚未实现该路由，前端容错：失败时返回"已是最新"
  return request<OtaCheckResult>('/api/ota/check', {
    method: 'POST',
    body: current,
  });
}

// 模拟下载进度（OTA 实际下载在 Phase 6 接入，此处提供回调骨架）
export function downloadOtaPackage(
  url: string,
  onProgress: (percent: number) => void,
  signal?: AbortSignal,
): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.responseType = 'arraybuffer';
    xhr.onprogress = (e) => {
      if (e.lengthComputable) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response as ArrayBuffer);
      } else {
        reject(new Error(`download failed: ${xhr.status}`));
      }
    };
    xhr.onerror = () => reject(new Error('download network error'));
    xhr.onabort = () => reject(new Error('download aborted'));
    if (signal) {
      signal.addEventListener('abort', () => xhr.abort());
    }
    xhr.send();
  });
}

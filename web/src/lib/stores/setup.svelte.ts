// 首次初始化状态：应用启动时检查系统是否需要初始化。
// 若 needsSetup=true，强制跳转到 /#/setup 页面，拦截所有其他路由。
import { getSetupStatus } from '$lib/api/setup';

type SetupState = 'unknown' | 'needed' | 'done';

class SetupStore {
  state = $state<SetupState>('unknown');

  // 应用启动时检查初始化状态
  async check(): Promise<void> {
    try {
      const res = await getSetupStatus();
      this.state = res.needsSetup ? 'needed' : 'done';
    } catch {
      // 检查失败时保守处理：视为已完成，避免误锁用户
      this.state = 'done';
    }
  }

  get needsSetup(): boolean {
    return this.state === 'needed';
  }

  get ready(): boolean {
    return this.state !== 'unknown';
  }

  // 初始化完成后标记为已完成
  markDone() {
    this.state = 'done';
  }
}

export const setupStore = new SetupStore();

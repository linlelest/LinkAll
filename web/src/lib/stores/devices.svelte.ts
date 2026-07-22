// 设备列表状态：Svelte 5 runes 模式。
export interface DeviceItem {
  deviceId: string;
  ownerUserId: number;
  onlineStatus: 'offline' | 'online' | 'busy' | 'sleeping';
  lastSeen: number;
  platform: string;
  version: string;
  deviceName: string;
  createdAt: number;
}

class DevicesStore {
  list = $state<DeviceItem[]>([]);
  total = $state<number>(0);
  loading = $state<boolean>(false);
  onlineOnly = $state<boolean>(false);
  viewMode = $state<'card' | 'list'>('card');

  set(list: DeviceItem[], total: number) {
    this.list = list;
    this.total = total;
  }

  setLoading(v: boolean) {
    this.loading = v;
  }

  toggleOnlineOnly() {
    this.onlineOnly = !this.onlineOnly;
  }

  toggleViewMode() {
    this.viewMode = this.viewMode === 'card' ? 'list' : 'card';
  }

  // 本地移除被踢出的设备
  removeByKick(deviceId: string) {
    const d = this.list.find((x) => x.deviceId === deviceId);
    if (d) {
      d.onlineStatus = 'offline';
    }
  }

  clear() {
    this.list = [];
    this.total = 0;
  }
}

export const devicesStore = new DevicesStore();

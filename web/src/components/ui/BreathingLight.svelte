<script lang="ts">
  // 在线状态呼吸灯：绿=在线 / 灰=离线 / 红=忙 / 黄=休眠。
  // 遵循"去除动画过渡"，使用静态发光效果替代呼吸动画。
  export type LightStatus = 'online' | 'offline' | 'busy' | 'sleeping';

  let {
    status = 'offline',
    size = 10,
    label = '',
  }: {
    status: LightStatus;
    size?: number;
    label?: string;
  } = $props();
</script>

<span class="light-wrap">
  <span
    class="light"
    class:online={status === 'online'}
    class:offline={status === 'offline'}
    class:busy={status === 'busy'}
    class:sleeping={status === 'sleeping'}
    style="width: {size}px; height: {size}px;"
  ></span>
  {#if label}
    <span class="light-label">{label}</span>
  {/if}
</span>

<style>
  .light-wrap {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .light {
    display: inline-block;
    border-radius: 50%;
    flex-shrink: 0;
    position: relative;
  }
  .light.online {
    background: var(--color-online);
    box-shadow: 0 0 6px var(--color-online);
  }
  .light.offline {
    background: var(--color-offline);
    box-shadow: none;
  }
  .light.busy {
    background: var(--color-busy);
    box-shadow: 0 0 6px var(--color-busy);
  }
  .light.sleeping {
    background: var(--color-warn);
    box-shadow: 0 0 4px var(--color-warn);
  }
  .light-label {
    font-size: 12px;
    color: var(--color-fg-muted);
  }
</style>

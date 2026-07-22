<script lang="ts">
  // 可复用滑块：支持线性 / 对数刻度，支持离散步进。
  // 遵循"去除动画过渡"，原生 range 控件 + 极简 CSS。
  let {
    value = $bindable(0),
    min = 0,
    max = 100,
    step = 1,
    label = '',
    unit = '',
    logScale = false,
    discrete = false,
    discreteSteps = undefined as number[] | undefined,
    format = undefined as ((v: number) => string) | undefined,
    onInput = undefined as ((v: number) => void) | undefined,
  }: {
    value: number;
    min: number;
    max: number;
    step?: number;
    label?: string;
    unit?: string;
    logScale?: boolean;
    discrete?: boolean;
    discreteSteps?: number[];
    format?: (v: number) => string;
    onInput?: (v: number) => void;
  } = $props();

  // 进度（0~1）用于背景填充
  let progress = $derived.by(() => {
    if (logScale) {
      const p = Math.log(value / min) / Math.log(max / min);
      return Math.max(0, Math.min(1, p));
    }
    return Math.max(0, Math.min(1, (value - min) / (max - min)));
  });

  let displayValue = $derived(format ? format(value) : `${value}${unit}`);

  function handleInput(e: Event) {
    const input = e.target as HTMLInputElement;
    const raw = Number(input.value);
    let v: number;
    if (logScale) {
      // 对数刻度：raw 是 0~1000 的进度，映射回实际值
      const p = raw / 1000;
      v = Math.round(min * Math.pow(max / min, p));
    } else if (discrete && discreteSteps) {
      // 离散步进：raw 是 discreteSteps 的索引
      const idx = Math.round(raw);
      v = discreteSteps[idx] ?? min;
    } else {
      v = raw;
    }
    value = v;
    onInput?.(v);
  }

  // range 控件内部值
  let rangeValue = $derived.by(() => {
    if (logScale) {
      return Math.round(progress * 1000);
    }
    if (discrete && discreteSteps) {
      return discreteSteps.indexOf(value);
    }
    return value;
  });

  let rangeMin = $derived(discrete && discreteSteps ? 0 : logScale ? 0 : min);
  let rangeMax = $derived(discrete && discreteSteps ? discreteSteps.length - 1 : logScale ? 1000 : max);
  let rangeStep = $derived(discrete && discreteSteps ? 1 : step);
</script>

<div class="slider-wrap">
  {#if label}
    <div class="slider-label">
      <span>{label}</span>
      <span class="slider-value mono">{displayValue}</span>
    </div>
  {/if}
  <input
    type="range"
    min={rangeMin}
    max={rangeMax}
    step={rangeStep}
    value={rangeValue}
    oninput={handleInput}
    style="background: linear-gradient(to right, var(--color-accent) {progress * 100}%, var(--color-border) {progress * 100}%);"
  />
  {#if discrete && discreteSteps}
    <div class="slider-ticks">
      {#each discreteSteps as s}
        <span class="mono" class:active={s === value}>{s}</span>
      {/each}
    </div>
  {/if}
</div>

<style>
  .slider-wrap {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
  }
  .slider-label {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    color: var(--color-fg-muted);
  }
  .slider-value {
    color: var(--color-accent);
    font-weight: 600;
  }
  input[type='range'] {
    width: 100%;
    height: 4px;
    border-radius: 2px;
  }
  .slider-ticks {
    display: flex;
    justify-content: space-between;
    font-size: 10px;
    color: var(--color-fg-dim);
  }
  .slider-ticks span.active {
    color: var(--color-accent);
    font-weight: 600;
  }
</style>

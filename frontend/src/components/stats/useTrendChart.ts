import { onUnmounted, watch, type Ref } from 'vue'
import uPlot from 'uplot'
// uPlot 的布局依赖其 CSS（.u-wrap relative + canvas absolute），不引入会导致 canvas 溢出容器
import 'uplot/dist/uPlot.min.css'
import i18n from '../../i18n'

export interface TrendData {
  months: string[]
  counts: number[]
}

/** 从 CSS 变量读主题色（canvas 无法用 CSS 变量，与 ImageEditor 同法） */
function themeColors(): { accent: string; accentHover: string; textDim: string; grid: string } {
  const cs = getComputedStyle(document.documentElement)
  // 防御：未注入/注入成 "undefined" 字符串（theme.ts 历史 bug）时回退默认值
  const v = (name: string): string => {
    const val = cs.getPropertyValue(name).trim()
    return val && val !== 'undefined' ? val : ''
  }
  return {
    accent: v('--c-accent') || '#2563eb',
    accentHover: v('--c-accent-hover') || '#1d4ed8',
    textDim: v('--c-text-dim') || '#6e6e73',
    grid: 'rgba(128, 128, 128, 0.15)',
  }
}

/**
 * uPlot 封装：按月上传趋势柱状图。
 * - ordinal x 轴（distr: 2，月份字符串作为类别）+ 半格边距（单数据点可绘制）
 * - y 用 range 函数（`min: 0` 会被 uPlot 视为「已显式设置」，max 永不自动计算）
 * - 柱体：accent→accent-hover 垂直渐变 + 顶部圆角 + 柱顶数据标签 + hover 图例
 * - 自建 ResizeObserver 跟随容器宽度（uPlot 1.x 无内置 responsive），卸载时 disconnect + destroy
 */
export function useTrendChart(
  target: Ref<HTMLElement | null>,
  data: Ref<TrendData>,
  height = 240,
): void {
  let chart: uPlot | null = null
  let resizeObs: ResizeObserver | null = null
  /** 类别标签缓存：x 轴按索引绘制（AlignedData 要求 x 为 number[]），刻度回调映射回月份 */
  let monthsCache: string[] = []

  function createPlot(el: HTMLElement, d: TrendData): uPlot {
    monthsCache = d.months
    const colors = themeColors()
    return new uPlot(
      {
        width: Math.max(200, el.clientWidth),
        height,
        legend: { show: true },
        scales: {
          // ordinal x：显式 range 留出半格边距——单数据点（min==max==0）时
          // 若 range 为 [0,0]，valToPosX 除零，柱形无法绘制
          x: { time: false, distr: 2, range: (_u, min, max) => [min - 0.5, max + 0.5] },
          // y 不能用 `min: 0`（uPlot 1.6 视为「已显式设置」，max 永不自动计算 → null → 柱形不画），
          // 必须用 range 函数：min 固定 0，max 取数据最大值
          y: { range: (_u, _min, max) => [0, max > 0 ? max : 1] },
        },
        axes: [
          {
            stroke: colors.textDim,
            ticks: { stroke: colors.grid },
            grid: { stroke: colors.grid },
            size: 30,
            values: (_self, ticks) => ticks.map((i) => monthsCache[i] ?? String(i)),
          },
          {
            stroke: colors.textDim,
            ticks: { stroke: colors.grid },
            grid: { stroke: colors.grid },
            size: 34,
          },
        ],
        hooks: {
          // 柱顶数据标签（bars 绘制完成后叠加文字）
          drawSeries: [
            (self: uPlot, seriesIdx: number) => {
              if (seriesIdx !== 1) return
              const counts = self.data[1]
              if (!counts) return
              const ctx = self.ctx
              ctx.save()
              ctx.fillStyle = colors.textDim
              ctx.font = '11px system-ui, sans-serif'
              ctx.textAlign = 'center'
              ctx.textBaseline = 'bottom'
              for (let i = 0; i < counts.length; i++) {
                const v = counts[i]
                if (v == null) continue
                const x = self.valToPos(i, 'x', true)
                const y = self.valToPos(v, 'y', true)
                ctx.fillText(String(v), x, y - 5)
              }
              ctx.restore()
            },
          ],
        },
        series: [
          {
            label: i18n.global.t('stats.month'),
            // legend 的 x 列显示月份而非索引
            value: (_self, raw) => monthsCache[Number(raw)] ?? String(raw),
          },
          {
            label: i18n.global.t('stats.barLabel'),
            // size [factor, max]：max 120px 兜底——单数据点时 findColWidth 返回 inf，
            // 无上限会得到无穷宽矩形导致柱形不可见；radius [顶, 底] 因子
            paths: uPlot.paths.bars?.({ size: [0.55, 120], radius: [0.2, 0] }),
            points: { show: false },
            stroke: colors.accent,
            // 垂直渐变：顶部 accent 亮 → 底部 accent-hover 深。
            // 注意：uPlot 构造早期（legend marker 取色）会调用 fill，此时 bbox 尚未计算
            // （top/height 非有限值），createLinearGradient 会抛错——必须回退纯色
            fill: (self: uPlot, _seriesIdx: number) => {
              const { top, height } = self.bbox
              if (!Number.isFinite(top) || !Number.isFinite(height) || height <= 0) {
                return colors.accent
              }
              const ctx = self.ctx
              const grad = ctx.createLinearGradient(0, top, 0, top + height)
              grad.addColorStop(0, colors.accent)
              grad.addColorStop(1, colors.accentHover)
              return grad
            },
          },
        ],
      },
      [d.counts.map((_, i) => i), d.counts],
      el,
    )
  }

  function mount(el: HTMLElement, d: TrendData): void {
    chart = createPlot(el, d)
    resizeObs = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width
      if (chart && w) chart.setSize({ width: Math.max(200, Math.round(w)), height })
    })
    resizeObs.observe(el)
  }

  function unmount(): void {
    resizeObs?.disconnect()
    resizeObs = null
    chart?.destroy()
    chart = null
  }

  watch(target, (el) => {
    unmount()
    if (el) mount(el, data.value)
  })

  watch(data, (d) => {
    monthsCache = d.months
    if (chart) chart.setData([d.counts.map((_, i) => i), d.counts])
  })

  onUnmounted(unmount)
}

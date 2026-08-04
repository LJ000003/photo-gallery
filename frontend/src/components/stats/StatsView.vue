<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Skeleton, Tag as AntTag } from 'ant-design-vue'
import { api } from '../../api'
import { formatSize } from '../../utils/format'
import { useTrendChart } from './useTrendChart'
import EmptyState from '../common/EmptyState.vue'
import type { ApiResponse } from '../../types/api'

interface MonthlyTrend {
  month: string
  count: number
}
interface TopTag {
  name: string
  color: string | null
  count: number
}
interface StatsData {
  totalPhotos: number
  totalSize: number
  monthlyTrend: MonthlyTrend[]
  topTags: TopTag[]
}

/**
 * 统计面板：照片总数 / 存储用量 / 每月上传趋势（uPlot 柱状图）/ 热门标签 TOP10
 * 数据来自 GET /api/v1/stats（后端 30s 缓存 + 写操作主动失效）
 */
const { t } = useI18n()

const loading = ref(true)
const error = ref(false)
const stats = ref<StatsData | null>(null)
const trendEl = ref<HTMLElement | null>(null)

const trendData = computed(() => ({
  months: (stats.value?.monthlyTrend ?? []).map((m) => m.month),
  counts: (stats.value?.monthlyTrend ?? []).map((m) => m.count),
}))

useTrendChart(trendEl, trendData)

onMounted(async () => {
  try {
    const res = await api('/api/stats')
    if (!res.ok) throw new Error(String(res.status))
    const json = (await res.json()) as ApiResponse<StatsData>
    stats.value = json.data
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="stats-view">
    <h1 class="page-title">{{ t('stats.title') }}</h1>

    <div v-if="loading" class="stats-loading">
      <Skeleton active :paragraph="{ rows: 6 }" />
    </div>

    <EmptyState
      v-else-if="error"
      :title="t('gallery.loadFailed')"
      :hint="t('stats.empty')"
    />

    <template v-else-if="stats">
      <div class="stats-cards">
        <div class="stat-card">
          <span class="stat-label">{{ t('stats.totalPhotos') }}</span>
          <span class="stat-value">{{ stats.totalPhotos }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('stats.storageUsed') }}</span>
          <span class="stat-value">{{ formatSize(stats.totalSize) }}</span>
        </div>
      </div>

      <section class="stats-section">
        <h2 class="stats-section-title">{{ t('stats.uploadTrend') }}</h2>
        <div ref="trendEl" class="trend-chart" />
      </section>

      <section class="stats-section">
        <h2 class="stats-section-title">{{ t('stats.topTags') }}</h2>
        <div v-if="stats.topTags.length" class="top-tags">
          <div v-for="tag in stats.topTags" :key="tag.name" class="tag-row">
            <AntTag :color="tag.color || undefined">{{ tag.name }}</AntTag>
            <span class="tag-count">{{ t('stats.photoCount', { n: tag.count }) }}</span>
          </div>
        </div>
        <p v-else class="tag-empty">{{ t('filter.emptyTags') }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.stats-view {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 20px 64px;
}
.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 700;
  color: var(--c-text);
}
.stats-loading {
  padding-top: 8px;
}
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  border-radius: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
}
.stat-label {
  font-size: 13px;
  color: var(--c-text-dim);
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--c-text);
  line-height: 1.1;
}
.stats-section {
  margin-bottom: 32px;
}
.stats-section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 650;
  color: var(--c-text);
}
.trend-chart {
  position: relative;
  width: 100%;
  border-radius: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  padding: 12px 8px 4px;
}
/* uPlot 图例：右上角小卡片（hover 显示当月数值） */
.trend-chart :deep(.u-legend) {
  position: absolute;
  top: 10px;
  right: 14px;
  z-index: 2;
  margin: 0;
  padding: 3px 10px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  font-size: 12px;
  text-align: left;
  line-height: 1.6;
}
.trend-chart :deep(.u-legend th) {
  color: var(--c-text-dim);
  font-weight: 500;
  font-size: 11px;
}
.trend-chart :deep(.u-legend th:first-child) {
  display: none;
}
.trend-chart :deep(.u-legend .u-series) {
  display: block;
  color: var(--c-text);
  font-weight: 600;
}
.trend-chart :deep(.u-legend .u-marker) {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}
.top-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  padding: 16px;
  border-radius: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
}
.tag-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tag-count {
  font-size: 13px;
  color: var(--c-text-dim);
}
.tag-empty {
  color: var(--c-text-dim);
  font-size: 13px;
}
</style>

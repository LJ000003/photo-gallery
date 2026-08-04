<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  CaretDownOutlined,
  CaretUpOutlined,
  ColumnHeightOutlined,
  FieldTimeOutlined,
  FontSizeOutlined,
  GlobalOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { Button, Dropdown, Input, Menu, MenuItem } from 'ant-design-vue'

import ModeTabs from './ModeTabs.vue'
import CornerMenu from './CornerMenu.vue'
import FilterPanel from './FilterPanel.vue'
import { usePhotoStore } from '../../stores/photo'
import { useUiStore } from '../../stores/ui'
import type { SortField, SortOrder } from '../../types/view'

/**
 * 顶部栏（F 型阅读的起点）：
 * 左：品牌 | 中：模式分段导航 | 右：搜索 · 排序 · 筛选 · 上传 · 角落菜单
 * sticky + 毛玻璃；移动端搜索收起为图标，点开变为第二行
 */
const { t, locale } = useI18n()
const photo = usePhotoStore()
const ui = useUiStore()

/** 中英切换：写入 localStorage（与 i18n.ts detectLocale 同一 key），antd 文案由 App.vue 自动跟随 */
function toggleLang(): void {
  const next = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = next
  localStorage.setItem('locale', next)
}
const route = useRoute()

/** 排序仅对照片/相册两种模式有意义（时间线/地图/回收站无排序语义），其余模式留占位 */
const showSort = computed(() => route.name === 'gallery' || route.name === 'albums')
/** 筛选（标签/分类）仅照片模式有意义，其余模式留占位 */
const showFilter = computed(() => route.name === 'gallery')
/** 搜索仅照片/相册两种模式有意义（照片搜照片、相册搜相册），其余模式留占位 */
const showSearch = computed(() => route.name === 'gallery' || route.name === 'albums')
/** 搜索框文案随模式变化：照片搜照片、相册搜相册 */
const searchPlaceholder = computed(() =>
  route.name === 'albums' ? t('topbar.searchAlbumsPlaceholder') : t('topbar.searchPlaceholder'),
)

/* ---------- 品牌彩蛋：连点 30 次触发彩虹 ---------- */
const clicks = ref(0)
const rainbow = ref(false)
function onBrandClick(): void {
  clicks.value++
  if (clicks.value >= 30) rainbow.value = true
}

/* ---------- 搜索（实时；按模式分派：照片搜照片、相册搜相册） ---------- */
const searchValue = ref(photo.searchQuery)
const mobileSearchOpen = ref(false)

// 实时搜索：每字符立即生效（照片模式走服务端搜索，photo store 的 requestId
// 竞态保护已保证旧请求结果不会覆盖新输入）
watch(searchValue, (q) => {
  if (route.name === 'albums') {
    if (q !== ui.albumSearch) ui.albumSearch = q
  } else if (route.name === 'gallery') {
    if (q !== photo.searchQuery) photo.setSearch(q)
  }
})

// 切模式时搜索框显示当前模式的关键词（两种模式的搜索词各自独立）
watch(
  () => route.name,
  () => {
    searchValue.value = route.name === 'albums' ? ui.albumSearch : photo.searchQuery
    mobileSearchOpen.value = false
  },
)

function focusSearch(): void {
  if (!showSearch.value) return // 非搜索模式：快捷键不弹搜索框
  mobileSearchOpen.value = true
  requestAnimationFrame(() =>
    document.querySelector<HTMLInputElement>('.mobile-search-row input')?.focus(),
  )
}

onMounted(() => {
  document.addEventListener('kb:focusSearch', focusSearch)
})
onUnmounted(() => {
  document.removeEventListener('kb:focusSearch', focusSearch)
})

/* ---------- 排序：三项字段，点击切换正/倒序（菜单项右侧单边箭头指示当前方向） ---------- */
const sortOptions: {
  field: SortField
  icon: typeof FieldTimeOutlined
  label: string
}[] = [
  { field: 'time', icon: FieldTimeOutlined, label: 'sort.time' },
  { field: 'name', icon: FontSizeOutlined, label: 'sort.name' },
  { field: 'size', icon: ColumnHeightOutlined, label: 'sort.size' },
]

// 各字段最后使用的方向（首次切到该字段时采用；与旧版六项菜单各字段的默认项一致）
const lastOrder: Record<SortField, SortOrder> = { time: 'desc', name: 'asc', size: 'desc' }

/**
 * store 语义 ↔ 用户可见方向互转：time 字段在请求层反转（store asc = 列表 desc = 最新优先↓）。
 * 反转自逆，同一函数双向可用。
 */
function effectiveOrder(field: SortField, order: SortOrder): SortOrder {
  return field === 'time' ? (order === 'asc' ? 'desc' : 'asc') : order
}

function applySort(field: SortField, order: SortOrder): void {
  if (photo.sortBy === field && photo.sortOrder === order) return
  photo.sortBy = field
  photo.sortOrder = order
  photo.resetAndReload()
}

function onSortMenuClick({ key }: { key: string | number }): void {
  const field = String(key) as SortField
  const current = effectiveOrder(photo.sortBy, photo.sortOrder)
  const next = field === photo.sortBy ? (current === 'asc' ? 'desc' : 'asc') : lastOrder[field]
  lastOrder[field] = next
  applySort(field, effectiveOrder(field, next))
}

const activeSortLabel = computed(() => {
  const o = sortOptions.find((s) => s.field === photo.sortBy)
  return o ? t(o.label) : t('sort.time')
})

/** 当前排序方向（用户可见语义，按钮图标用） */
const currentOrder = computed(() => effectiveOrder(photo.sortBy, photo.sortOrder))
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <button class="brand" :class="{ rainbow }" :aria-label="t('app.name')" @click="onBrandClick">
        {{ t('app.name') }}
      </button>

      <ModeTabs class="topbar-tabs" />

      <div class="topbar-actions">
        <template v-if="showSearch">
          <Input
            v-model:value="searchValue"
            class="search-input"
            :placeholder="searchPlaceholder"
            :aria-label="searchPlaceholder"
            allow-clear
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </Input>
        </template>
        <!-- 其余模式：等宽占位，保持顶栏布局不跳动 -->
        <span v-else class="search-placeholder" aria-hidden="true"></span>

        <template v-if="showSort">
          <Dropdown placement="bottomRight" trigger="click">
            <Button class="tool-btn" type="text" :aria-label="t('topbar.sort')">
              <span class="sort-label">{{ activeSortLabel }}</span>
              <CaretUpOutlined v-if="currentOrder === 'asc'" class="caret" />
              <CaretDownOutlined v-else class="caret" />
            </Button>
            <template #overlay>
              <Menu selectable :selected-keys="[photo.sortBy]" @click="onSortMenuClick">
                <MenuItem v-for="o in sortOptions" :key="o.field">
                  <component :is="o.icon" />
                  {{ t(o.label) }}
                  <span class="dir-arrow" :aria-hidden="photo.sortBy !== o.field">
                    <CaretUpOutlined
                      v-if="
                        photo.sortBy === o.field && effectiveOrder(o.field, photo.sortOrder) === 'asc'
                      "
                    />
                    <CaretDownOutlined v-else-if="photo.sortBy === o.field" />
                  </span>
                </MenuItem>
              </Menu>
            </template>
          </Dropdown>
        </template>
        <!-- 其余模式：等宽占位，保持顶栏布局不跳动 -->
        <span v-else class="sort-placeholder" aria-hidden="true"></span>

        <template v-if="showFilter">
          <FilterPanel v-model:open="ui.filterOpen" />
        </template>
        <!-- 其余模式：等宽占位，保持顶栏布局不跳动 -->
        <span v-else class="filter-placeholder" aria-hidden="true"></span>

        <Button
          type="primary"
          class="upload-btn"
          :aria-label="t('topbar.upload')"
          @click="ui.uploadOpen = true"
        >
          <PlusOutlined />
          <span class="upload-label">{{ t('topbar.upload') }}</span>
        </Button>

        <Button
          class="tool-btn lang-btn"
          type="text"
          :aria-label="t('topbar.language')"
          @click="toggleLang"
        >
          <GlobalOutlined />
          <span class="lang-label">{{ locale === 'zh-CN' ? '中' : 'EN' }}</span>
        </Button>

        <CornerMenu />
      </div>
    </div>

    <!-- 移动端搜索行 -->
    <div v-show="mobileSearchOpen && showSearch" class="mobile-search-row">
      <Input
        v-model:value="searchValue"
        class="mobile-search-input"
        :placeholder="searchPlaceholder"
        :aria-label="searchPlaceholder"
        allow-clear
        autofocus
        @blur="mobileSearchOpen = false"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </Input>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  background: color-mix(in srgb, var(--c-bg) 82%, transparent);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border-bottom: 1px solid var(--c-border);
}
.topbar-inner {
  max-width: 1400px;
  margin: 0 auto;
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.brand {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--c-text);
  padding: 0;
  white-space: nowrap;
}
.brand:active {
  transform: scale(0.97);
}
.brand.rainbow {
  animation: hue-spin 3s linear infinite;
}
@keyframes hue-spin {
  from {
    filter: hue-rotate(0deg);
  }
  to {
    filter: hue-rotate(360deg);
  }
}
.topbar-tabs {
  flex: 1;
}
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.search-input {
  width: 220px;
  border-radius: 999px;
  transition: width 0.25s ease;
}
.search-placeholder {
  width: 220px;
  height: 36px;
}
.search-input:focus-within {
  width: 300px;
}
.tool-btn {
  height: 36px;
  border-radius: 999px;
  color: var(--c-text-dim);
  padding: 0 12px;
}
.tool-btn:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}
.lang-btn {
  gap: 4px;
}
.lang-label {
  font-size: 12px;
  line-height: 1;
}
.sort-label {
  font-size: 13px;
}
.sort-placeholder {
  width: 62px;
  height: 36px;
}
.filter-placeholder {
  width: 36px;
  height: 36px;
}
.caret {
  font-size: 10px;
  margin-left: 2px;
}
.dir-arrow {
  display: inline-flex;
  width: 14px;
  justify-content: center;
  font-size: 10px;
  margin-left: 10px;
}
.upload-btn {
  border-radius: 999px;
  margin-left: 8px;
}
.mobile-search-row {
  display: none;
}

@media (max-width: 768px) {
  .topbar-inner {
    height: 52px;
    padding: 0 12px;
    gap: 12px;
  }
  .topbar-tabs {
    display: none;
  }
  .search-input {
    display: none;
  }
  /* 移动端 actions 行本无搜索按钮，占位随之隐藏 */
  .search-placeholder {
    display: none;
  }
  .mobile-search-row {
    display: block;
    padding: 0 12px 10px;
  }
  .mobile-search-input {
    border-radius: 999px;
  }
  .sort-label {
    display: none;
  }
  .sort-placeholder {
    width: 34px;
  }
  .upload-label {
    display: none;
  }
  .upload-btn {
    width: 36px;
    padding: 0;
  }
}
</style>

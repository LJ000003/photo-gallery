<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CaretDownOutlined,
  CaretUpOutlined,
  ColumnHeightOutlined,
  FieldTimeOutlined,
  FilterOutlined,
  FontSizeOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { Badge, Button, Dropdown, Input, Menu, MenuItem } from 'ant-design-vue'

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
const { t } = useI18n()
const photo = usePhotoStore()
const ui = useUiStore()

/* ---------- 品牌彩蛋：连点 30 次触发彩虹 ---------- */
const clicks = ref(0)
const rainbow = ref(false)
function onBrandClick(): void {
  clicks.value++
  if (clicks.value >= 30) rainbow.value = true
}

/* ---------- 搜索（300ms 防抖；查询语义与旧版 setSearch 一致） ---------- */
const searchValue = ref(photo.searchQuery)
const mobileSearchOpen = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

watch(searchValue, (q) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    if (q !== photo.searchQuery) photo.setSearch(q)
  }, 300)
})

function focusSearch(): void {
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
  if (debounceTimer) clearTimeout(debounceTimer)
})

/* ---------- 排序：显式字段 + 方向（六项菜单，勾选当前项） ---------- */
const sortOptions: {
  field: SortField
  order: SortOrder
  icon: typeof FieldTimeOutlined
  label: string
}[] = [
  { field: 'time', order: 'desc', icon: FieldTimeOutlined, label: 'sort.time' },
  { field: 'time', order: 'asc', icon: FieldTimeOutlined, label: 'sort.time' },
  { field: 'name', order: 'asc', icon: FontSizeOutlined, label: 'sort.name' },
  { field: 'name', order: 'desc', icon: FontSizeOutlined, label: 'sort.name' },
  { field: 'size', order: 'desc', icon: ColumnHeightOutlined, label: 'sort.size' },
  { field: 'size', order: 'asc', icon: ColumnHeightOutlined, label: 'sort.size' },
]

function applySort(field: SortField, order: SortOrder): void {
  if (photo.sortBy === field && photo.sortOrder === order) return
  photo.sortBy = field
  photo.sortOrder = order
  photo.resetAndReload()
}

function onSortMenuClick({ key }: { key: string | number }): void {
  const [field, order] = String(key).split('-') as [SortField, SortOrder]
  applySort(field, order)
}

const activeSortLabel = computed(() => {
  const o = sortOptions.find((s) => s.field === photo.sortBy && s.order === photo.sortOrder)
  return o ? t(o.label) : t('sort.time')
})

/* ---------- 筛选 ---------- */
const filterCount = computed(() => photo.selectedTagIds.length + photo.selectedCategoryIds.length)
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <button class="brand" :class="{ rainbow }" :aria-label="t('app.name')" @click="onBrandClick">
        {{ t('app.name') }}
      </button>

      <ModeTabs class="topbar-tabs" />

      <div class="topbar-actions">
        <Input
          v-model:value="searchValue"
          class="search-input"
          :placeholder="t('topbar.searchPlaceholder')"
          :aria-label="t('topbar.searchPlaceholder')"
          allow-clear
        >
          <template #prefix>
            <SearchOutlined />
          </template>
        </Input>

        <Dropdown placement="bottomRight" trigger="click">
          <Button class="tool-btn" type="text" :aria-label="t('topbar.sort')">
            <span class="sort-label">{{ activeSortLabel }}</span>
            <CaretDownOutlined class="caret" />
          </Button>
          <template #overlay>
            <Menu selectable :selected-keys="[`${photo.sortBy}-${photo.sortOrder}`]" @click="onSortMenuClick">
              <MenuItem
                v-for="o in sortOptions"
                :key="`${o.field}-${o.order}`"
              >
                <component :is="o.icon" />
                {{ t(o.label) }}
                <CaretUpOutlined v-if="o.order === 'asc'" class="menu-caret" />
                <CaretDownOutlined v-else class="menu-caret" />
              </MenuItem>
            </Menu>
          </template>
        </Dropdown>

        <Badge :count="filterCount" :offset="[-2, 2]" size="small">
          <Button
            class="tool-btn"
            type="text"
            :aria-label="t('topbar.filter')"
            @click="ui.filterOpen = true"
          >
            <FilterOutlined />
          </Button>
        </Badge>

        <Button
          type="primary"
          class="upload-btn"
          :aria-label="t('topbar.upload')"
          @click="ui.uploadOpen = true"
        >
          <PlusOutlined />
          <span class="upload-label">{{ t('topbar.upload') }}</span>
        </Button>

        <CornerMenu />
      </div>
    </div>

    <!-- 移动端搜索行 -->
    <div v-show="mobileSearchOpen" class="mobile-search-row">
      <Input
        v-model:value="searchValue"
        class="mobile-search-input"
        :placeholder="t('topbar.searchPlaceholder')"
        :aria-label="t('topbar.searchPlaceholder')"
        allow-clear
        autofocus
        @blur="mobileSearchOpen = false"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </Input>
    </div>

    <FilterPanel v-model:open="ui.filterOpen" />
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
.sort-label {
  font-size: 13px;
}
.caret {
  font-size: 10px;
  margin-left: 2px;
}
.menu-caret {
  font-size: 10px;
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
  .upload-label {
    display: none;
  }
  .upload-btn {
    width: 36px;
    padding: 0;
  }
}
</style>

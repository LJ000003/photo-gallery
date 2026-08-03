<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { FilterOutlined } from '@ant-design/icons-vue'
import { Badge, Button, Drawer, Popover } from 'ant-design-vue'
import FilterPanelContent from './FilterPanelContent.vue'
import { usePhotoStore } from '../../stores/photo'
import { useUiStore } from '../../stores/ui'

/**
 * 筛选入口（按钮 + 面板）：
 * - 桌面：按钮即 Popover trigger（antd 原生切换，无受控 open 竞态）
 * - 移动：按钮唤起右侧 Drawer（受控 open，由 AppShell 的 ui.filterOpen 驱动）
 * 内容组件 FilterPanelContent 两处复用
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

const { t } = useI18n()
const photo = usePhotoStore()
const ui = useUiStore()

const filterCount = computed(() => photo.selectedTagIds.length + photo.selectedCategoryIds.length)

const isMobile = ref(false)
let mq: MediaQueryList | null = null
function updateMobile(): void {
  isMobile.value = window.matchMedia('(max-width: 768px)').matches
}
onMounted(() => {
  mq = window.matchMedia('(max-width: 768px)')
  updateMobile()
  mq.addEventListener('change', updateMobile)
})
onUnmounted(() => mq?.removeEventListener('change', updateMobile))
</script>

<template>
  <!-- 移动端：按钮 + 右侧抽屉 -->
  <template v-if="isMobile">
    <Badge :count="filterCount" :offset="[-2, 2]" size="small">
      <Button
        class="filter-trigger"
        type="text"
        :aria-label="t('topbar.filter')"
        @click="ui.filterOpen = true"
      >
        <FilterOutlined />
      </Button>
    </Badge>
    <Drawer
      :open="props.open"
      :title="t('filter.title')"
      placement="right"
      :width="'min(320px, 86vw)'"
      @update:open="emit('update:open', $event)"
    >
      <FilterPanelContent />
    </Drawer>
  </template>

  <!-- 桌面：按钮即 Popover trigger（不受控，antd 原生切换） -->
  <Popover
    v-else
    placement="bottomRight"
    trigger="click"
    :overlay-inner-style="{ padding: 0, borderRadius: '16px', overflow: 'hidden', width: '340px' }"
  >
    <Badge :count="filterCount" :offset="[-2, 2]" size="small">
      <Button class="filter-trigger" type="text" :aria-label="t('topbar.filter')">
        <FilterOutlined />
      </Button>
    </Badge>
    <template #content>
      <FilterPanelContent />
    </template>
  </Popover>
</template>

<style scoped>
.filter-trigger {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  color: var(--c-text-dim);
  padding: 0 12px;
}
.filter-trigger:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}
</style>

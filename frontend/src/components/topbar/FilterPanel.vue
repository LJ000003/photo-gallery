<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Drawer, Popover } from 'ant-design-vue'
import FilterPanelContent from './FilterPanelContent.vue'

/**
 * 筛选面板容器：桌面 Popover / 移动端右侧 Drawer
 * 内容组件 FilterPanelContent 两处复用
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

const { t } = useI18n()

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
  <!-- 移动端：右侧抽屉 -->
  <Drawer
    v-if="isMobile"
    :open="props.open"
    :title="t('filter.title')"
    placement="right"
    :width="'min(320px, 86vw)'"
    @update:open="emit('update:open', $event)"
  >
    <FilterPanelContent />
  </Drawer>

  <!-- 桌面：底部弹出面板 -->
  <Popover
    v-else
    :open="props.open"
    placement="bottomRight"
    trigger="click"
    :overlay-inner-style="{ padding: 0, borderRadius: '16px', overflow: 'hidden', width: '340px' }"
    @update:open="emit('update:open', $event)"
  >
    <template #content>
      <FilterPanelContent />
    </template>
    <span></span>
  </Popover>
</template>

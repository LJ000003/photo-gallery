<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { DeleteOutlined, EditOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { Button, Checkbox } from 'ant-design-vue'

/**
 * 选择模式工具条（出现于多选后，粘在顶栏下方）：
 * 全选 · 已选 N 张 · 批量分享/编辑/删除 · 退出
 */
const props = defineProps<{
  count: number
  totalCount: number
  allSelected: boolean
}>()

const emit = defineEmits<{
  'toggle-all': []
  share: []
  edit: []
  delete: []
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="selection-bar" role="toolbar" :aria-label="t('selection.selected', { n: count })">
    <Checkbox :checked="props.allSelected" @change="emit('toggle-all')">
      {{ t('actions.selectAll') }}
    </Checkbox>
    <span class="selection-count">{{ t('selection.selected', { n: count }) }}</span>

    <div class="selection-actions">
      <Button size="small" @click="emit('share')">
        <LinkOutlined />
        {{ t('selection.batchShare') }}
      </Button>
      <Button size="small" @click="emit('edit')">
        <EditOutlined />
        {{ t('selection.batchEdit') }}
      </Button>
      <Button size="small" danger @click="emit('delete')">
        <DeleteOutlined />
        {{ t('selection.batchDelete') }}
      </Button>
      <Button size="small" type="text" class="cancel-btn" @click="emit('cancel')">
        {{ t('actions.cancel') }}
      </Button>
    </div>
  </div>
</template>

<style scoped>
.selection-bar {
  position: sticky;
  top: 56px;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 20px;
  margin: 0 -20px 12px;
  background: color-mix(in srgb, var(--c-bg) 88%, transparent);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--c-border);
  font-size: 13px;
  color: var(--c-text);
  animation: slide-down 0.25s ease;
}
@keyframes slide-down {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.selection-count {
  font-weight: 600;
  color: var(--c-accent);
  flex: 1;
}
.selection-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cancel-btn {
  color: var(--c-text-dim);
}

@media (max-width: 768px) {
  .selection-bar {
    top: 52px;
    flex-wrap: wrap;
    gap: 8px;
    margin: 0 -12px 10px;
    padding: 8px 12px;
  }
  .selection-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>

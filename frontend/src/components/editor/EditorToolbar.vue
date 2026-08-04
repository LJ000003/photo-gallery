<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  RotateLeftOutlined,
  RotateRightOutlined,
  ScissorOutlined,
  SwapOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'

/**
 * 图片编辑器工具栏：6 个工具按钮（旋转/镜像/裁剪/重置）+ 确认/取消
 * active 态（mirror/crop）由父级传入，事件全部上抛
 */
defineProps<{
  mirrorH: boolean
  mirrorV: boolean
  cropMode: boolean
}>()
const emit = defineEmits<{
  rotateLeft: []
  rotateRight: []
  mirrorH: []
  mirrorV: []
  toggleCrop: []
  reset: []
  confirm: []
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="editor-toolbar">
    <button
      type="button"
      :title="t('editor.rotateLeft')"
      :aria-label="t('editor.rotateLeft')"
      @click="emit('rotateLeft')"
    >
      <RotateLeftOutlined />
    </button>
    <button
      type="button"
      :title="t('editor.rotateRight')"
      :aria-label="t('editor.rotateRight')"
      @click="emit('rotateRight')"
    >
      <RotateRightOutlined />
    </button>
    <button
      type="button"
      :class="{ active: mirrorH }"
      :title="t('editor.mirrorH')"
      :aria-label="t('editor.mirrorH')"
      @click="emit('mirrorH')"
    >
      <SwapOutlined />
    </button>
    <button
      type="button"
      :class="{ active: mirrorV }"
      :title="t('editor.mirrorV')"
      :aria-label="t('editor.mirrorV')"
      @click="emit('mirrorV')"
    >
      <SwapOutlined class="flip-v" />
    </button>
    <button
      type="button"
      :class="{ active: cropMode }"
      :title="t('editor.crop')"
      :aria-label="t('editor.crop')"
      @click="emit('toggleCrop')"
    >
      <ScissorOutlined />
    </button>
    <button
      type="button"
      :title="t('editor.reset')"
      :aria-label="t('editor.reset')"
      @click="emit('reset')"
    >
      <UndoOutlined />
    </button>
    <span class="toolbar-spacer"></span>
    <Button type="primary" @click="emit('confirm')">{{ t('actions.confirm') }}</Button>
    <Button @click="emit('cancel')">{{ t('actions.cancel') }}</Button>
  </div>
</template>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--c-border);
}
.editor-toolbar > button {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--c-text-dim);
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}
.editor-toolbar > button:hover {
  background: var(--c-surface-2);
  color: var(--c-text);
}
.editor-toolbar > button.active {
  background: var(--c-accent-soft);
  color: var(--c-accent);
}
.flip-v {
  transform: rotate(90deg);
}
.toolbar-spacer {
  flex: 1;
}

@media (max-width: 768px) {
  .editor-toolbar {
    flex-wrap: wrap;
    padding: 8px 10px;
  }
}
</style>

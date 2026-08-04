<script setup lang="ts">
import { CheckCircleFilled, CloseCircleFilled, InfoCircleFilled } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '../../stores/toast'

/**
 * 全局 toast 队列渲染（支持撤销类操作按钮）
 * 右上角堆叠，语义色图标 + 毛玻璃卡片
 */
const { t } = useI18n()
const toast = useToastStore()

const icons = {
  success: CheckCircleFilled,
  error: CloseCircleFilled,
  info: InfoCircleFilled,
} as const

function dismiss(id: number): void {
  toast.remove(id)
}

function runAction(id: number): void {
  const item = toast.toasts.find((t) => t.id === id)
  if (item?.action) void item.action.onClick()
  toast.remove(id)
}
</script>

<template>
  <div class="toast-stack" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="item in toast.toasts"
        :key="item.id"
        class="toast-item"
        :class="`toast-${item.type}`"
      >
        <component :is="icons[item.type]" class="toast-icon" aria-hidden="true" />
        <span class="toast-msg">{{ item.message }}</span>
        <button v-if="item.action" class="toast-action" @click="runAction(item.id)">
          {{ item.action.label }}
        </button>
        <button class="toast-close" :aria-label="t('actions.close')" @click="dismiss(item.id)">×</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 64px;
  right: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 360px;
  pointer-events: none;
}
.toast-item {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
}
.toast-icon {
  font-size: 16px;
  flex-shrink: 0;
}
.toast-success .toast-icon {
  color: var(--c-success);
}
.toast-error .toast-icon {
  color: var(--c-danger);
}
.toast-info .toast-icon {
  color: var(--c-accent);
}
.toast-msg {
  flex: 1;
  font-size: 13px;
  color: var(--c-text);
  line-height: 1.4;
}
.toast-action {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-accent);
  cursor: pointer;
  white-space: nowrap;
}
.toast-action:hover {
  color: var(--c-accent-hover);
}
.toast-close {
  border: none;
  background: none;
  padding: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 14px;
  color: var(--c-text-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toast-close:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(24px);
}

@media (max-width: 768px) {
  .toast-stack {
    top: 56px;
    right: 12px;
    left: 12px;
    max-width: none;
  }
}
</style>

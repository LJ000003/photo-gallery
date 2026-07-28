<script setup lang="ts">
import { useToastStore } from '../stores/toast'

const toastStore = useToastStore()
</script>

<template>
  <div class="toast-container">
    <div
      v-for="t in toastStore.toasts"
      :key="t.id"
      class="toast-item"
      :class="t.type"
    >
      <span class="toast-msg">{{ t.message }}</span>
      <button
        v-if="t.action"
        class="toast-action"
        @click="t.action.onClick(); toastStore.remove(t.id)"
      >
        {{ t.action.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 5000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast-item {
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 14px;
  color: #fff;
  pointer-events: auto;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  animation: toast-in 0.3s ease;
  max-width: 400px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.toast-msg {
  flex: 1;
}
.toast-action {
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s;
}
.toast-action:hover {
  background: rgba(255, 255, 255, 0.3);
}
.toast-item.success {
  background: rgba(0, 200, 100, 0.85);
  border: 1px solid rgba(0, 255, 128, 0.3);
}
.toast-item.error {
  background: rgba(220, 40, 80, 0.85);
  border: 1px solid rgba(255, 80, 120, 0.3);
}
.toast-item.info {
  background: rgba(0, 180, 240, 0.85);
  border: 1px solid rgba(0, 220, 255, 0.3);
}
@keyframes toast-in {
  0% {
    opacity: 0;
    transform: translateX(40px);
  }
  100% {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>

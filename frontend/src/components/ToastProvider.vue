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
      @click="toastStore.remove(t.id)"
    >
      {{ t.message }}
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
  cursor: pointer;
  pointer-events: auto;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  animation: toast-in 0.3s ease;
  max-width: 360px;
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

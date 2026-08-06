<script setup lang="ts">
defineProps<{
  title: string
  hint: string
}>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-art" aria-hidden="true">
      <!-- 手绘几何插画：相框 + 太阳 + 山 -->
      <svg viewBox="0 0 120 120" fill="none" stroke="currentColor">
        <rect x="22" y="26" width="76" height="64" rx="10" stroke-width="3" />
        <circle cx="48" cy="50" r="7" stroke-width="3" />
        <path
          d="M32 80l18-16 12 10 8-7 18 13"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path d="M60 22v-8M82 32l6-6M46 32l-6-6" stroke-width="2.5" stroke-linecap="round" />
        <circle cx="60" cy="17" r="2.5" fill="currentColor" stroke="none" />
      </svg>
      <div class="sun-spark" />
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p class="empty-hint">{{ hint }}</p>
    <slot name="action" />
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 96px 24px;
  text-align: center;
}
.empty-art {
  position: relative;
  width: 120px;
  height: 120px;
  color: var(--c-accent);
  opacity: 0.75;
  animation: art-float 3.2s ease-in-out infinite;
}
.sun-spark {
  position: absolute;
  top: 4px;
  right: 26px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-warning);
  opacity: 0.9;
  animation: spark-pulse 1.6s ease-in-out infinite;
}
@keyframes art-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}
@keyframes spark-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.9;
  }
  50% {
    transform: scale(1.25);
    opacity: 0.5;
  }
}
.empty-title {
  margin-top: 24px;
  font-size: 17px;
  font-weight: 650;
  color: var(--c-text);
}
.empty-hint {
  margin-top: 8px;
  font-size: 13px;
  color: var(--c-text-dim);
  line-height: 1.6;
  max-width: 320px;
}
</style>

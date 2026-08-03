<script setup lang="ts">
import { computed } from 'vue'

/**
 * 照片网格骨架屏：与真实网格同构的占位（shimmer）
 * count 默认 12；列数跟随容器宽度（与 PhotoGrid 同一套算法）
 */
withDefaults(defineProps<{ count?: number }>(), { count: 12 })

const width = computed(() => (typeof window !== 'undefined' ? window.innerWidth : 1280))
const columns = computed(() => {
  const w = width.value
  const min = w < 600 ? 150 : 240
  const gap = w < 600 ? 4 : 8
  if (w < min) return 1
  return Math.max(1, Math.floor((w + gap) / (min + gap)))
})
</script>

<template>
  <div
    class="grid-skeleton"
    :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)`, gap: `${width < 600 ? 4 : 8}px` }"
    aria-hidden="true"
  >
    <div v-for="i in count" :key="i" class="skeleton-tile">
      <div class="skeleton-img shimmer"></div>
    </div>
  </div>
</template>

<style scoped>
.grid-skeleton {
  display: grid;
  width: 100%;
}
.skeleton-tile {
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 8px;
}
.skeleton-img {
  width: 100%;
  height: 100%;
  background: var(--c-surface-2);
}
.shimmer {
  background: linear-gradient(
    100deg,
    var(--c-surface-2) 40%,
    color-mix(in srgb, var(--c-surface-2) 40%, var(--c-surface)) 50%,
    var(--c-surface-2) 60%
  );
  background-size: 200% 100%;
  animation: shimmer 1.6s ease-in-out infinite;
}
@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
</style>

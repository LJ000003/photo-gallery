<script setup lang="ts">
defineProps<{ currentPath: string }>()
const emit = defineEmits<{ 'click-active': [path: string] }>()
const views = [
  { path: '/', label: 'nav.grid' },
  { path: '/albums', label: 'nav.albums' },
  { path: '/timeline', label: 'nav.timeline' },
  { path: '/map', label: 'nav.map' },
]

function onLinkClick(path: string, e: MouseEvent): void {
  if (path === '/map') return // 地图不需要切换排序
  emit('click-active', path)
  e.preventDefault()
}
</script>

<template>
  <div class="view-switch">
    <span class="sort-label">{{ $t('nav.view') }}：</span>
    <div class="view-track">
      <router-link
        v-for="v in views"
        :key="v.path"
        :to="v.path"
        class="view-opt"
        :class="{ active: currentPath === v.path }"
        @click="(e: MouseEvent) => currentPath === v.path && onLinkClick(v.path, e)"
      >
        {{ v.path === '/map' ? '地图' : $t(v.label) }}
        <slot :name="'suffix-' + (v.path === '/' ? 'grid' : v.path.slice(1))"></slot>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.sort-label {
  font-size: 13px;
  color: var(--text-dim);
  white-space: nowrap;
}

.view-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}
.view-track {
  display: flex;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}
.view-opt {
  position: relative;
  padding: 8px 24px;
  font-size: 14px;
  color: var(--text-dim);
  background: transparent;
  border: none;
  border-right: 1px solid var(--border);
  cursor: pointer;
  transition:
    color 0.3s,
    background 0.3s;
}
.view-opt:last-child {
  border-right: none;
}
.view-opt.active {
  color: var(--accent);
  background: rgba(0, 212, 255, 0.12);
}
.view-opt:hover {
  color: var(--text);
}

@media (max-width: 768px) {
  .view-track {
    margin: 0 auto;
  }
  .view-opt {
    padding: 6px 10px;
    font-size: 12px;
  }
}
</style>

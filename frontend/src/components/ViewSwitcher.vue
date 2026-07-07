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

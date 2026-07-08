<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import TimelineView from '../components/TimelineView.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const route = useRoute()
const ui = useUiStore()
const timelineSortOrder = ref('desc')

function onViewClickActive(path: string): void {
  if (path === '/timeline') {
    timelineSortOrder.value = timelineSortOrder.value === 'desc' ? 'asc' : 'desc'
  }
}
</script>

<template>
  <section class="gallery-section">
    <h2>{{ $t('gallery.myPhotos') }}</h2>
    <div class="gallery-toolbar centered">
      <div class="toolbar-center">
        <ViewSwitcher :current-path="route.path" @click-active="onViewClickActive">
          <template #suffix-timeline>
            <span class="sort-arrows">
              <i
                class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down"
                :class="{ active: timelineSortOrder === 'asc' }"
              ></i>
              <i
                class="iconfont icon-jiantou_qiehuanxiangshang_o"
                :class="{ active: timelineSortOrder === 'desc' }"
              ></i>
            </span>
          </template>
        </ViewSwitcher>
      </div>
    </div>
    <TimelineView :sort-order="timelineSortOrder" @view="(p) => (ui.viewPhoto = p as Photo)" />
  </section>
</template>

<style scoped>
.sort-arrows {
  position: absolute;
  display: inline-flex;
  font-size: 14px;
  top: 50%;
  transform: translateY(-50%);
}
.sort-arrows i {
  display: inline-block;
  opacity: 0.25;
  transition: opacity 0.2s;
}
.sort-arrows i:last-child {
  margin-left: -8px;
}
.sort-arrows i.active {
  opacity: 1;
  color: var(--accent);
}
.sort-arrow-down {
  transform: rotate(180deg);
}
</style>

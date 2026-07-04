<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import TimelineView from '../components/TimelineView.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const route = useRoute()
const ui = useUiStore()
const timelineSortOrder = ref('desc')

function onTimelineClick(e: MouseEvent): void {
  if (route.path === '/timeline') {
    e.preventDefault()
    timelineSortOrder.value = timelineSortOrder.value === 'desc' ? 'asc' : 'desc'
  }
}
</script>

<template>
  <section class="gallery-section">
    <h2>我的照片</h2>
    <div class="gallery-toolbar">
      <div class="view-switch">
        <span class="sort-label">视图：</span>
        <div class="view-track">
          <router-link to="/" class="view-opt">网格</router-link>
          <router-link to="/albums" class="view-opt">相册</router-link>
          <router-link to="/timeline" class="view-opt" :class="{ active: true }" @click="onTimelineClick">
            时间线
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
          </router-link>
          <router-link to="/map" class="view-opt">地图</router-link>
        </div>
      </div>
    </div>
    <TimelineView :sort-order="timelineSortOrder" @view="(p) => (ui.viewPhoto = p as Photo)" />
  </section>
</template>

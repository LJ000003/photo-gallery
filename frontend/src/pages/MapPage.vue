<script setup lang="ts">
import MapView from '../components/MapView.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const ui = useUiStore()
</script>

<template>
  <section class="gallery-section">
    <h2>我的照片</h2>
    <div class="gallery-toolbar centered">
      <div class="toolbar-center">
        <div class="view-switch">
          <span class="sort-label">视图：</span>
          <div class="view-track">
            <router-link to="/" class="view-opt">网格</router-link>
            <router-link to="/albums" class="view-opt">相册</router-link>
            <router-link to="/timeline" class="view-opt">时间线</router-link>
            <router-link to="/map" class="view-opt" :class="{ active: true }">地图</router-link>
          </div>
        </div>
      </div>
    </div>
    <MapView @view="(p) => (ui.viewPhoto = p as Photo)" />
  </section>
</template>

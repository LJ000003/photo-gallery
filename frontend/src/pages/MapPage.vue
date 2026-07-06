<script setup lang="ts">
import { useRoute } from 'vue-router'
import MapView from '../components/MapView.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const route = useRoute()
const ui = useUiStore()
</script>

<template>
  <section class="gallery-section">
    <h2>我的照片</h2>
    <div class="gallery-toolbar centered">
      <div class="toolbar-center">
        <ViewSwitcher :current-path="route.path" />
      </div>
    </div>
    <MapView @view="(p) => (ui.viewPhoto = p as Photo)" />
  </section>
</template>

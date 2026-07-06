<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AlbumView from '../components/AlbumView.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import SortSwitch from '../components/SortSwitch.vue'
import type { SortOption } from '../components/SortSwitch.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const route = useRoute()
const ui = useUiStore()

const albumSortBy = ref('time')
const albumSortOrder = ref('desc')

const sortOptions: SortOption[] = [
  { key: 'time', label: '时间' },
  { key: 'name', label: '名称' },
]

function onSortToggle(key: string): void {
  if (albumSortBy.value === key) {
    albumSortOrder.value = albumSortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    albumSortBy.value = key
    albumSortOrder.value = 'asc'
  }
}
</script>

<template>
  <section class="gallery-section">
    <h2>{{ $t('gallery.myPhotos') }}</h2>
    <div class="gallery-toolbar centered">
      <div class="toolbar-center">
        <ViewSwitcher :current-path="route.path" />
        <SortSwitch
          :options="sortOptions"
          :model-value="albumSortBy"
          :order="albumSortOrder"
          @toggle="onSortToggle"
        />
      </div>
    </div>
    <AlbumView
      :sort-by="albumSortBy"
      :sort-order="albumSortOrder"
      @update:sort-by="(k: string) => (albumSortBy = k)"
      @update:sort-order="(o: string) => (albumSortOrder = o)"
      @view="(p: object) => (ui.viewPhoto = p as Photo)"
    />
  </section>
</template>

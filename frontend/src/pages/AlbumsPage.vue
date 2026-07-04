<script setup lang="ts">
import { ref } from 'vue'
import AlbumView from '../components/AlbumView.vue'
import { useUiStore } from '../stores/ui'
import type { Photo } from '../types/photo'

const ui = useUiStore()

const albumSortBy = ref('time')
const albumSortOrder = ref('desc')

function setAlbumSort(key: string): void {
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
    <h2>我的照片</h2>
    <div class="gallery-toolbar">
      <div class="toolbar-center">
        <div class="view-switch">
          <span class="sort-label">视图：</span>
          <div class="view-track">
            <router-link to="/" class="view-opt">网格</router-link>
            <router-link to="/albums" class="view-opt" :class="{ active: true }">相册</router-link>
            <router-link to="/timeline" class="view-opt">时间线</router-link>
            <router-link to="/map" class="view-opt">地图</router-link>
          </div>
        </div>
        <div class="sort-switch">
          <span class="sort-label">排序方式：</span>
          <div class="sort-track sort-2cols">
            <div
              class="sort-slider"
              :style="{ transform: `translateX(${albumSortBy === 'time' ? 0 : 100}%)` }"
            ></div>
            <button
              class="sort-opt"
              :class="{ active: albumSortBy === 'time' }"
              @click="setAlbumSort('time')"
            >
              时间
              <span v-if="albumSortBy === 'time'" class="sort-arrows">
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down"
                  :class="{ active: albumSortOrder === 'asc' }"
                ></i>
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o"
                  :class="{ active: albumSortOrder === 'desc' }"
                ></i>
              </span>
            </button>
            <button
              class="sort-opt"
              :class="{ active: albumSortBy === 'name' }"
              @click="setAlbumSort('name')"
            >
              名称
              <span v-if="albumSortBy === 'name'" class="sort-arrows">
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down"
                  :class="{ active: albumSortOrder === 'asc' }"
                ></i>
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o"
                  :class="{ active: albumSortOrder === 'desc' }"
                ></i>
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
    <AlbumView
      :sort-by="albumSortBy"
      :sort-order="albumSortOrder"
      @update:sort-by="setAlbumSort"
      @update:sort-order="(o) => (albumSortOrder = o)"
      @view="(p) => (ui.viewPhoto = p as Photo)"
    />
  </section>
</template>

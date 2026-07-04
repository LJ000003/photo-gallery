<script setup lang="ts">
import { ref, onMounted, defineAsyncComponent, nextTick } from 'vue'
import gsap from 'gsap'
import { webpUrl } from '../webp'
import type { Photo } from '../types/photo'
import type { PageResponse } from '../types/api'

const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'))

const token = window.location.pathname.replace('/share/', '')
const photos = ref<Photo[]>([])
const loading = ref(true)
const hasMore = ref(false)
const page = ref(0)
const viewPhoto = ref<Photo | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await fetch(`/api/share/view?page=${page.value}&size=20`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || '分享链接无效或已过期')
    }
    const json = await res.json()
    const data: PageResponse<Photo> = json.data
    photos.value = [...photos.value, ...data.content]
    hasMore.value = !data.last
    page.value++
  } catch {
    photos.value = []
    hasMore.value = false
  } finally {
    loading.value = false
    nextTick(() => {
      if (photos.value.length > 0) {
        gsap.fromTo(
          '.photo-card',
          { y: 30, opacity: 0 },
          { y: 0, opacity: 1, stagger: 0.05, duration: 0.5, ease: 'expo.out' },
        )
      }
    })
  }
}

function onScroll(): void {
  if (!hasMore.value || loading.value) return
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200) load()
}

function formatSize(bytes: number | undefined): string {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  load()
})
</script>

<template>
  <div class="share-page">
    <header class="header"><h1>分享的照片</h1></header>
    <p class="share-sub">此链接仅可查看，无法编辑或删除</p>

    <div class="gallery">
      <div
        v-for="i in 6"
        v-if="loading && photos.length === 0"
        :key="'s' + i"
        class="skeleton-card"
      >
        <div class="skeleton-img"></div>
        <div class="skeleton-body">
          <div class="skeleton-line"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>

      <div v-for="p in photos" :key="p.id" class="photo-card" @click="viewPhoto = p">
        <div class="photo-thumb">
          <img :src="`/api/photos/${p.id}/thumbnail?token=${token}`" :alt="p.name" loading="lazy" />
        </div>
        <div class="photo-overlay"><button class="btn-primary mini">查看</button></div>
        <div class="photo-info">
          <span class="photo-name">{{ p.name }}</span>
          <span class="photo-size">{{ formatSize(p.fileSize) }}</span>
        </div>
      </div>
    </div>

    <div v-if="loading && photos.length > 0" class="sentinel">
      <LottieLoader name="loading" :size="60" />
    </div>
    <div v-else-if="!hasMore && photos.length > 0" class="end-hint">没有更多了</div>
    <div v-if="!loading && photos.length === 0" class="empty-state">
      <LottieLoader name="empty" :size="160" />
      <p class="empty-hint">链接无效或已过期</p>
    </div>

    <div v-if="viewPhoto" class="modal" @click.self="viewPhoto = null">
      <div class="modal-content">
        <img
          :src="`${webpUrl(viewPhoto.id)}?token=${token}`"
          :alt="viewPhoto.name"
          loading="lazy"
          style="max-width: 90vw; max-height: 80vh; border-radius: 10px"
        />
        <h3>{{ viewPhoto.name }}</h3>
        <p v-if="viewPhoto.description" class="view-desc">{{ viewPhoto.description }}</p>
        <button class="modal-close" @click="viewPhoto = null">✕</button>
      </div>
    </div>
  </div>
</template>

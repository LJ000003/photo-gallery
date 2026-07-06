<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollToPlugin } from 'gsap/ScrollToPlugin'
import { useAppEffects } from './composables/useAppEffects'
gsap.registerPlugin(ScrollToPlugin)

import AppHeader from './components/AppHeader.vue'
import FilterSidebar from './components/FilterSidebar.vue'
import KonamiGate from './components/KonamiGate.vue'
import ToastProvider from './components/ToastProvider.vue'
import ViewModal from './components/ViewModal.vue'
import EditModal from './components/EditModal.vue'

import { usePhotoStore } from './stores/photo'
import { useUiStore } from './stores/ui'
import { useToastStore } from './stores/toast'
import { requestToken } from './api'
import type { Photo } from './types/photo'

const { t } = useI18n()
const router = useRouter()
const photo = usePhotoStore()
const ui = useUiStore()
const toast = useToastStore()

const isSharePath = window.location.pathname.startsWith('/share/')

function scrollToTop(): void {
  if ('ontouchstart' in window) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } else {
    gsap.to(window, { scrollTo: 0, duration: 0.6, ease: 'power3.out' })
  }
}

function onSaved(): void {
  ui.editPhoto = null
  photo.resetAndReload()
}

function onTagFilterChange(ids: number[]): void {
  photo.selectedTagIds = ids
  photo.resetAndReload()
  ui.sidebarOpen = false
}

function onCatFilterChange(ids: number[]): void {
  photo.selectedCategoryIds = ids
  photo.resetAndReload()
  ui.sidebarOpen = false
}

async function onUnlock(): Promise<void> {
  useAppEffects()
  try {
    const token = await requestToken()
    ui.setToken(token)
    ui.unlock()
    router.replace(window.location.pathname + window.location.search)
    photo.loadMore()
    toast.success(t('auth.success'))
  } catch {
    toast.error(t('auth.failed'))
  }
}

onMounted(() => {
  if (ui.unlocked && !ui.token) {
    ui.reLock()
  }
  if (ui.unlocked) useAppEffects()
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      ui.viewPhoto = null
      ui.editPhoto = null
    }
  })
  window.addEventListener('scroll', () => {
    ui.showBackTop = window.scrollY > 400
  })
  if (ui.unlocked) photo.loadMore()
})
</script>

<template>
  <!-- 分享页独立渲染，不走主布局 -->
  <RouterView v-if="isSharePath" />
  <template v-else>
    <KonamiGate v-if="!ui.unlocked" @unlocked="onUnlock" />
    <template v-if="ui.unlocked">
      <span class="relock-wrap">
        <button class="relock-btn" @click="ui.reLock">🔒</button>
        <span class="relock-hint">{{ $t('auth.relock') }}</span>
      </span>
      <AppHeader />
      <main class="page">
        <button class="sidebar-toggle" @click="ui.sidebarOpen = !ui.sidebarOpen">
          {{ ui.sidebarOpen ? '✕' : '☰' }}
        </button>
        <div v-if="ui.sidebarOpen" class="sidebar-backdrop" @click="ui.sidebarOpen = false"></div>
        <FilterSidebar
          :class="{ open: ui.sidebarOpen }"
          :selected-tag-ids="photo.selectedTagIds"
          :selected-category-ids="photo.selectedCategoryIds"
          @update:selected-tag-ids="onTagFilterChange($event as number[])"
          @update:selected-category-ids="onCatFilterChange($event as number[])"
        />
        <div class="main-content">
          <RouterView v-if="!isSharePath" />
        </div>
      </main>
      <button v-show="ui.showBackTop" class="back-top" @click="scrollToTop">↑</button>
      <ViewModal v-if="ui.viewPhoto" :photo="ui.viewPhoto" @close="ui.viewPhoto = null" />
      <EditModal
        v-if="ui.editPhoto"
        :photo="ui.editPhoto"
        @close="ui.editPhoto = null"
        @saved="onSaved"
      />
      <ToastProvider />
    </template>
  </template>
</template>

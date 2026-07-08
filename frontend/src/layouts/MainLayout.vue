<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollToPlugin } from 'gsap/ScrollToPlugin'
import { useAppEffects } from '../composables/useAppEffects'
gsap.registerPlugin(ScrollToPlugin)

import AppHeader from '../components/AppHeader.vue'
import FilterSidebar from '../components/FilterSidebar.vue'
import KonamiGate from '../components/KonamiGate.vue'
import ToastProvider from '../components/ToastProvider.vue'
import ViewModal from '../components/ViewModal.vue'
import EditModal from '../components/EditModal.vue'

import { usePhotoStore } from '../stores/photo'
import { useUiStore } from '../stores/ui'
import { useToastStore } from '../stores/toast'
import { requestToken } from '../api'
import type { Photo } from '../types/photo'

const { t } = useI18n()
const router = useRouter()
const photo = usePhotoStore()
const ui = useUiStore()
const toast = useToastStore()

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
        <RouterView />
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

<style scoped>
.page {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 32px 24px 64px;
  display: flex;
  gap: 28px;
  align-items: flex-start;
}
.main-content {
  flex: 1;
  min-width: 0;
}

.relock-wrap {
  position: fixed;
  top: 12px;
  right: 12px;
  z-index: 100;
}
.relock-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.4;
  transition: opacity 0.3s;
}
.relock-btn:hover {
  opacity: 1;
}
.relock-hint {
  position: absolute;
  top: 40px;
  right: 0;
  padding: 5px 12px;
  background: var(--accent2);
  color: #fff;
  font-size: 12px;
  border-radius: 8px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s;
}
.relock-wrap:hover .relock-hint {
  opacity: 1;
}

.back-top {
  position: fixed;
  right: 24px;
  bottom: 32px;
  z-index: 100;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  color: var(--text);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  padding: 0;
}
.back-top:hover {
  border-color: var(--accent);
  box-shadow: var(--glow-cyan);
  transform: translateY(-2px);
}

.sidebar-toggle {
  display: none;
}
.sidebar-backdrop {
  display: none;
}

@media (max-width: 768px) {
  .page {
    flex-direction: column;
    padding: 16px 12px 48px;
    gap: 16px;
    align-items: stretch;
  }
  .main-content {
    width: 100%;
  }
  .back-top {
    right: 12px;
    bottom: 20px;
    width: 38px;
    height: 38px;
    font-size: 16px;
  }
  .sidebar-toggle {
    display: flex;
    position: fixed;
    top: 12px;
    left: 12px;
    z-index: 50;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid var(--border);
    color: var(--text);
    font-size: 18px;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    padding: 0;
  }
  .sidebar-backdrop {
    position: fixed;
    inset: 0;
    z-index: 199;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(4px);
  }
}
</style>

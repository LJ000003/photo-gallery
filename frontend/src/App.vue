<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import gsap from 'gsap'
import { ScrollToPlugin } from 'gsap/ScrollToPlugin'
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

function onSaved(): void { ui.editPhoto = null; photo.resetAndReload() }

async function onUnlock(): Promise<void> {
  initEffects()
  try {
    const token = await requestToken()
    ui.setToken(token)
    ui.unlock()
    photo.loadMore()
    toast.success('认证成功')
  } catch {
    toast.error('认证失败，请重试')
  }
}

let firstLoad = true
function initEffects(): void {
  if (!firstLoad) return
  firstLoad = false

  const orbsHTML = `<div class="bg-orbs">
    <div class="orb orb-1"></div><div class="orb orb-2"></div><div class="orb orb-3"></div>
  </div>`
  document.body.insertAdjacentHTML('afterbegin', orbsHTML)

  gsap.to('.orb-1', { keyframes: [
    { xPercent: -10, yPercent: -5, scale: 1 }, { xPercent: 10, yPercent: 5, scale: 1.15 },
    { xPercent: -5, yPercent: 10, scale: 0.9 }, { xPercent: 5, yPercent: -5, scale: 1.05 },
    { xPercent: -10, yPercent: -5, scale: 1 }
  ], duration: 12, repeat: -1, ease: 'sine.inOut' })
  gsap.to('.orb-2', { keyframes: [
    { xPercent: 5, yPercent: 5, scale: 1 }, { xPercent: -8, yPercent: -8, scale: 0.85 },
    { xPercent: 3, yPercent: 3, scale: 1.1 }, { xPercent: -5, yPercent: 5, scale: 0.95 },
    { xPercent: 5, yPercent: 5, scale: 1 }
  ], duration: 15, repeat: -1, ease: 'sine.inOut' })
  gsap.to('.orb-3', { keyframes: [
    { xPercent: 3, yPercent: -5, scale: 1 }, { xPercent: -6, yPercent: 3, scale: 1.1 },
    { xPercent: 8, yPercent: -8, scale: 0.85 }, { xPercent: -3, yPercent: 5, scale: 1.05 },
    { xPercent: 3, yPercent: -5, scale: 1 }
  ], duration: 10, repeat: -1, ease: 'sine.inOut' })

  if (!('ontouchstart' in window)) {
    const trails: { el: HTMLDivElement; x: number; y: number }[] = []
    let mx = 0, my = 0
    for (let i = 0; i < 12; i++) {
      const dot = document.createElement('div')
      dot.className = 'cursor-trail'
      dot.style.opacity = String((1 - i / 12) * 0.5)
      dot.style.transform = `translate(-50%,-50%) scale(${1 - i / 12})`
      document.body.appendChild(dot)
      trails.push({ el: dot, x: 0, y: 0 })
    }
    document.addEventListener('mousemove', e => { mx = e.clientX; my = e.clientY })
    ;(function tick() {
      let tx = mx, ty = my
      for (let i = 0; i < trails.length; i++) {
        const t = trails[i]
        t.x += (tx - t.x) * (0.35 - i * 0.02)
        t.y += (ty - t.y) * (0.35 - i * 0.02)
        t.el.style.left = t.x + 'px'
        t.el.style.top = t.y + 'px'
        tx = t.x; ty = t.y
      }
      requestAnimationFrame(tick)
    })()
  }

  document.addEventListener('click', e => {
    const btn = (e.target as HTMLElement).closest('button')
    if (!btn) return
    const ripple = document.createElement('span')
    ripple.className = 'ripple'
    const rect = btn.getBoundingClientRect()
    const size = Math.max(rect.width, rect.height)
    ripple.style.width = ripple.style.height = size + 'px'
    ripple.style.left = (e.clientX - rect.left - size / 2) + 'px'
    ripple.style.top = (e.clientY - rect.top - size / 2) + 'px'
    btn.appendChild(ripple)
    ripple.addEventListener('animationend', () => ripple.remove())
  })

  gsap.fromTo('.header h1', { y: -60, opacity: 0 }, { y: 0, opacity: 1, duration: 1, ease: 'expo.out' })
  gsap.fromTo('.upload-card', { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, delay: 0.3, ease: 'expo.out' })
  gsap.fromTo('.gallery-section h2', { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, delay: 0.5, ease: 'power1.out' })
}

onMounted(() => {
  if (ui.unlocked && !ui.token) {
    ui.reLock()
  }
  if (ui.unlocked) initEffects()
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') { ui.viewPhoto = null; ui.editPhoto = null }
  })
  window.addEventListener('scroll', () => { ui.showBackTop = window.scrollY > 400 })
  if (ui.unlocked) photo.loadMore()
})
</script>

<template>
  <KonamiGate v-if="!ui.unlocked" @unlocked="onUnlock" />
  <template v-else>
    <span class="relock-wrap">
      <button class="relock-btn" @click="ui.reLock">🔒</button>
      <span class="relock-hint">再来一次！</span>
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
        @update:selected-tag-ids="photo.selectedTagIds = $event as number[]; photo.resetAndReload(); ui.sidebarOpen = false"
        @update:selected-category-ids="photo.selectedCategoryIds = $event as number[]; photo.resetAndReload(); ui.sidebarOpen = false"
      />
      <div class="main-content">
        <RouterView />
      </div>
    </main>
    <button v-show="ui.showBackTop" class="back-top" @click="scrollToTop">↑</button>
    <ViewModal v-if="ui.viewPhoto" :photo="ui.viewPhoto" @close="ui.viewPhoto = null" />
    <EditModal v-if="ui.editPhoto" :photo="ui.editPhoto" @close="ui.editPhoto = null" @saved="onSaved" />
    <ToastProvider />
  </template>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import gsap from 'gsap'
import { webpUrl } from '../webp'
import { tokenParam } from '../utils/token'
import type { Photo } from '../types/photo'

const props = defineProps<{ photo: Photo }>()
const emit = defineEmits<{ close: [] }>()

const isMobile = 'ontouchstart' in window
const fullLoaded = ref(false)

onMounted(() => {
  const content = document.querySelector('#viewModal .modal-content')
  const backdrop = document.querySelector('#viewModal .modal-backdrop')
  const dur = isMobile ? 0.2 : 0.35
  gsap.fromTo(
    content,
    { scale: 0.95, opacity: 0 },
    { scale: 1, opacity: 1, duration: dur, ease: 'expo.out' },
  )
  gsap.fromTo(backdrop, { opacity: 0 }, { opacity: 1, duration: dur, ease: 'none' })
})

function onClose(): void {
  const content = document.querySelector('#viewModal .modal-content')
  const backdrop = document.querySelector('#viewModal .modal-backdrop')
  const dur = isMobile ? 0.15 : 0.2
  gsap.to(content, {
    scale: 0.95,
    opacity: 0,
    duration: dur,
    ease: 'power1.in',
    onComplete: () => emit('close'),
  })
  gsap.to(backdrop, { opacity: 0, duration: dur, ease: 'none' })
}
</script>

<template>
  <div id="viewModal" class="modal">
    <div class="modal-backdrop" @click="onClose"></div>
    <div class="modal-content">
      <button class="modal-close" @click="onClose">&times;</button>
      <div class="img-wrap">
        <img
          :src="`/api/photos/${photo.id}/thumbnail${tokenParam(photo.fileSize)}`"
          :alt="photo.name"
          decoding="async"
          loading="lazy"
        />
        <img
          class="img-full"
          :class="{ show: fullLoaded }"
          :src="`${webpUrl(photo.id)}${tokenParam(photo.fileSize)}`"
          :alt="photo.name"
          decoding="async"
          loading="lazy"
          @load="fullLoaded = true"
        />
      </div>
      <div class="modal-info">
        <h3>{{ photo.name }}</h3>
        <p>{{ photo.description }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import gsap from 'gsap'
import { webpUrl } from '../webp'
import { tokenParam } from '../utils/token'
import { formatSize } from '../utils/format'
import type { Photo } from '../types/photo'

const { t } = useI18n()
const props = defineProps<{ photo: Photo }>()
const emit = defineEmits<{ close: [] }>()

const isMobile = 'ontouchstart' in window
const fullLoaded = ref(false)

const hasExif = computed(() => props.photo.exifData != null)

const exifGroups = computed(() => {
  const exif = props.photo.exifData
  if (!exif) return []

  const groups: { label: string; items: { label: string; value: string }[] }[] = []

  const cameraItems: { label: string; value: string }[] = []
  if (exif.cameraModel) cameraItems.push({ label: t('exif.cameraModel'), value: exif.cameraModel })
  if (exif.lensModel) cameraItems.push({ label: t('exif.lensModel'), value: exif.lensModel })
  if (cameraItems.length) groups.push({ label: t('exif.cameraGroup'), items: cameraItems })

  const shootItems: { label: string; value: string }[] = []
  if (exif.focalLength) shootItems.push({ label: t('exif.focalLength'), value: exif.focalLength })
  if (exif.aperture) shootItems.push({ label: t('exif.aperture'), value: exif.aperture })
  if (exif.shutterSpeed) shootItems.push({ label: t('exif.shutterSpeed'), value: exif.shutterSpeed })
  if (exif.iso != null) shootItems.push({ label: t('exif.iso'), value: `ISO ${exif.iso}` })
  if (shootItems.length) groups.push({ label: t('exif.shootGroup'), items: shootItems })

  const locItems: { label: string; value: string }[] = []
  if (exif.dateTaken) {
    locItems.push({ label: t('exif.dateTaken'), value: new Date(exif.dateTaken).toLocaleString() })
  }
  if (exif.latitude != null && exif.longitude != null) {
    locItems.push({ label: t('exif.gps'), value: `${exif.latitude.toFixed(6)}, ${exif.longitude.toFixed(6)}` })
  }
  if (locItems.length) groups.push({ label: t('exif.locationGroup'), items: locItems })

  return groups
})

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
    <div class="modal-content-wrap">
      <button class="modal-close" @click="onClose">&times;</button>
      <div class="modal-content">
        <div class="img-wrap">
          <img
            :src="`/api/v1/photos/${photo.id}/thumbnail${tokenParam(photo.fileSize)}`"
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
          <p v-if="photo.description">{{ photo.description }}</p>
          <p class="modal-filesize">{{ formatSize(photo.fileSize) }}</p>

          <details v-if="hasExif" class="exif-panel">
            <summary>{{ t('exif.title') }}</summary>
            <div v-for="group in exifGroups" :key="group.label" class="exif-group">
              <h4>{{ group.label }}</h4>
              <div class="exif-grid">
                <template v-for="item in group.items" :key="item.label">
                  <span class="exif-label">{{ item.label }}</span>
                  <span class="exif-value">{{ item.value }}</span>
                </template>
              </div>
            </div>
          </details>
          <p v-else class="exif-empty">{{ t('exif.empty') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-filesize {
  color: var(--text-dim);
  font-size: 13px;
  margin-top: 2px;
}

.exif-panel {
  margin-top: 16px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.exif-panel summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-dim);
  user-select: none;
}

.exif-panel summary:hover {
  color: #fff;
}

.exif-group {
  margin-top: 12px;
}

.exif-group h4 {
  font-size: 12px;
  font-weight: 600;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.exif-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 16px;
}

.exif-label {
  color: #888;
  font-size: 13px;
  white-space: nowrap;
}

.exif-value {
  color: #fff;
  font-size: 13px;
  word-break: break-all;
}

.exif-empty {
  color: #666;
  font-size: 13px;
  margin-top: 12px;
  font-style: italic;
}
</style>

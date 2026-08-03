<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Photo } from '../../types/photo'

/**
 * EXIF 滑出面板：相机 / 拍摄参数 / 位置 三组信息
 */
const props = defineProps<{
  photo: Photo
  open: boolean
}>()

const { t } = useI18n()

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
  if (exif.shutterSpeed)
    shootItems.push({ label: t('exif.shutterSpeed'), value: exif.shutterSpeed })
  if (exif.iso != null) shootItems.push({ label: t('exif.iso'), value: `ISO ${exif.iso}` })
  if (shootItems.length) groups.push({ label: t('exif.shootGroup'), items: shootItems })

  const locItems: { label: string; value: string }[] = []
  if (exif.dateTaken) {
    locItems.push({ label: t('exif.dateTaken'), value: new Date(exif.dateTaken).toLocaleString() })
  }
  if (exif.latitude != null && exif.longitude != null) {
    locItems.push({
      label: t('exif.gps'),
      value: `${exif.latitude.toFixed(6)}, ${exif.longitude.toFixed(6)}`,
    })
  }
  if (locItems.length) groups.push({ label: t('exif.locationGroup'), items: locItems })

  return groups
})
</script>

<template>
  <aside class="exif-panel" :class="{ open }" aria-label="拍摄信息">
    <h3 class="exif-title">{{ t('viewer.exif') }}</h3>
    <template v-if="exifGroups.length">
      <div v-for="group in exifGroups" :key="group.label" class="exif-group">
        <h4 class="exif-group-title">{{ group.label }}</h4>
        <dl class="exif-grid">
          <template v-for="item in group.items" :key="item.label">
            <dt class="exif-label">{{ item.label }}</dt>
            <dd class="exif-value">{{ item.value }}</dd>
          </template>
        </dl>
      </div>
    </template>
    <p v-else class="exif-empty">{{ t('viewer.noExif') }}</p>
  </aside>
</template>

<style scoped>
.exif-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(320px, 86vw);
  background: rgba(20, 20, 24, 0.96);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  padding: 20px;
  overflow-y: auto;
  transform: translateX(100%);
  transition: transform 0.3s ease;
  z-index: 5;
}
.exif-panel.open {
  transform: translateX(0);
}
.exif-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 18px;
}
.exif-group {
  margin-bottom: 18px;
}
.exif-group-title {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.45);
  margin-bottom: 8px;
}
.exif-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 14px;
  margin: 0;
}
.exif-label {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
  white-space: nowrap;
}
.exif-value {
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  word-break: break-all;
  margin: 0;
}
.exif-empty {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}
</style>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import gsap from 'gsap'
import { webpUrl, thumbUrl, thumbSrcset } from '../webp'
import { tokenParam, tokenQS } from '../utils/token'
import { formatSize } from '../utils/format'
import { api } from '../api'
import { useToastStore } from '../stores/toast'
import { usePhotoStore } from '../stores/photo'
import { useConfirm } from '../useConfirm'
import ImageEditor from './ImageEditor.vue'
import type { Photo } from '../types/photo'
import type { TransformParams } from '../types/transform'

const { t } = useI18n()
const confirmFn = useConfirm()
const toast = useToastStore()
const photoStore = usePhotoStore()
const props = defineProps<{
  photo: Photo
  selected?: boolean
  searchQuery?: string
}>()
const emit = defineEmits<{
  view: []
  edit: []
  delete: [id: number]
  toggleSelect: [id: number]
}>()

interface HighlightSegment {
  text: string
  hl: boolean
}

function highlightSegments(text: string | undefined): HighlightSegment[] {
  if (!text) return [{ text: '', hl: false }]
  const q = props.searchQuery
  if (!q || !q.trim()) return [{ text, hl: false }]
  const escaped = q.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'))
  return parts.map((p) => ({ text: p, hl: p.toLowerCase() === q.trim().toLowerCase() }))
}

const nameSegments = computed(() => highlightSegments(props.photo.name))
const descSegments = computed(() => highlightSegments(props.photo.description))

const cardRef = ref<HTMLElement | null>(null)
const editorVisible = ref(false)
const editorSrc = ref('')

function openImageEditor(): void {
  editorSrc.value = `${webpUrl(props.photo.id)}${tokenParam(props.photo.fileSize)}`
  editorVisible.value = true
}

async function onImageEditDone({ params }: { params: TransformParams; blob: Blob }): Promise<void> {
  editorVisible.value = false
  try {
    const res = await api(`/api/photos/${props.photo.id}/transform`, {
      method: 'POST',
      body: JSON.stringify(params),
    })
    if (res.ok) {
      toast.success(t('editor.done'))
      photoStore.resetAndReload()
    } else {
      toast.error(t('editor.failed'))
    }
  } catch {
    toast.error(t('editor.failed'))
  }
}

function tiltOn(e: MouseEvent): void {
  const card = cardRef.value
  if (!card) return
  const rect = card.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width - 0.5
  const y = (e.clientY - rect.top) / rect.height - 0.5
  gsap.to(card, {
    rotateY: x * 8,
    rotateX: -y * 8,
    duration: 0.2,
    overwrite: 'auto',
    ease: 'power1.out',
  })
}

function tiltOff(): void {
  gsap.to(cardRef.value, {
    rotateY: 0,
    rotateX: 0,
    duration: 0.4,
    overwrite: 'auto',
    ease: 'power1.out',
  })
}

const retrying = ref(false)

async function retryProcessing(): Promise<void> {
  retrying.value = true
  try {
    const res = await api(`/api/photos/${props.photo.id}/retry-processing`, { method: 'POST' })
    if (res.ok) {
      // 立即更新本地状态，避免等待整页刷新
      props.photo.processingStatus = 'PROCESSING'
      props.photo.errorMessage = undefined
      toast.success(t('processing.retry'))
    } else {
      toast.error(t('general.operationFailed'))
    }
  } catch {
    toast.error(t('general.networkError'))
  } finally {
    retrying.value = false
  }
}

async function onDelete(): Promise<void> {
  if (!(await confirmFn(t('photo.deleteConfirm'), t('photo.delete')))) return
  await gsap.to(cardRef.value, {
    scale: 0.7,
    opacity: 0,
    rotateX: 40,
    y: -30,
    filter: 'blur(8px)',
    duration: 0.4,
    ease: 'power1.in',
  })
  emit('delete', props.photo.id)
}
</script>

<template>
  <div
    ref="cardRef"
    class="photo-card"
    :class="{ selected: selected }"
    :data-insert="$attrs['data-insert']"
    @mousemove="tiltOn"
    @mouseleave="tiltOff"
  >
    <div class="card-check" @click.stop="emit('toggleSelect', photo.id)">
      <span v-if="selected" class="check-mark">✓</span>
    </div>
    <div class="photo-thumb" @click="$emit('view')">
      <img
        :src="thumbUrl(photo.id, 400) + tokenQS(photo.fileSize)"
        :srcset="thumbSrcset(photo.id)
          .split(', ')
          .map((s) => s + tokenQS(photo.fileSize))
          .join(', ')"
        sizes="(max-width: 768px) calc((100vw - 24px) / 2), (max-width: 1400px) calc((100vw - 280px) / 4), 280px"
        :alt="photo.name"
        loading="lazy"
      />
      <div class="photo-overlay">
        <button class="btn-view" @click.stop="$emit('view')">{{ $t('photo.view') }}</button>
      </div>
      <div v-if="photo.processingStatus === 'PROCESSING'" class="processing-overlay">
        <div class="processing-spinner"></div>
        <span>{{ $t('processing.processing') }}</span>
      </div>
      <div v-if="photo.processingStatus === 'FAILED'" class="failed-overlay">
        <span class="failed-icon">!</span>
        <span class="failed-text">{{ photo.errorMessage || $t('processing.failed') }}</span>
        <button
          class="btn-retry"
          @click.stop="retryProcessing"
          :disabled="retrying"
        >{{ retrying ? $t('processing.retrying') : $t('processing.retry') }}</button>
      </div>
    </div>
    <div class="photo-body">
      <h4 class="photo-name">
        <span v-for="(s, i) in nameSegments" :key="i" :class="{ 'search-hl': s.hl }">{{
          s.text
        }}</span>
      </h4>
      <p v-if="searchQuery && photo.description" class="photo-desc">
        <span v-for="(s, i) in descSegments" :key="i" :class="{ 'search-hl': s.hl }">{{
          s.text
        }}</span>
      </p>
      <p class="photo-meta">
        {{ formatSize(photo.fileSize)
        }}<span v-if="photo.category"> · {{ photo.category.name }}</span>
      </p>
      <div v-if="photo.tags && photo.tags.length" class="card-tags">
        <span
          v-for="t in photo.tags"
          :key="t.id"
          class="card-tag"
          :style="{ background: t.color }"
          >{{ t.name }}</span
        >
      </div>
      <div class="photo-actions">
        <button class="btn-edit" @click="$emit('edit')">{{ $t('photo.edit') }}</button>
        <button class="btn-img-edit" :title="$t('upload.editImage')" @click="openImageEditor">✂</button>
        <button class="btn-del" @click="onDelete">{{ $t('photo.delete') }}</button>
      </div>
    </div>
  </div>
  <ImageEditor
    :src="editorSrc"
    :visible="editorVisible"
    @close="editorVisible = false"
    @done="onImageEditDone"
  />
</template>

<style scoped>
.card-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.card-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  color: #fff;
}

.btn-img-edit {
  background: rgba(0, 212, 255, 0.1);
  color: var(--accent);
  font-size: 13px;
  padding: 7px 10px;
}
.btn-img-edit:hover {
  background: rgba(0, 212, 255, 0.25);
  box-shadow: var(--glow);
}

.search-hl {
  background: rgba(255, 200, 0, 0.35);
  color: #ffd700;
  border-radius: 2px;
}
.photo-desc {
  font-size: 12px;
  color: var(--text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin: 2px 0 4px;
}
.photo-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.card-check {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 5;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.3);
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  opacity: 0;
}
.photo-card:hover .card-check,
.photo-card.selected .card-check {
  opacity: 1;
}
.photo-card.selected .card-check {
  border-color: var(--accent);
  background: var(--accent);
}
.check-mark {
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}
.photo-card.selected {
  border-color: var(--accent) !important;
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.2);
}

/* 处理中遮罩 */
.processing-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 4;
  pointer-events: none;
  color: var(--text-dim);
  font-size: 13px;
}
.processing-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 处理失败遮罩 */
.failed-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 4;
  color: #f59e0b;
  font-size: 12px;
  text-align: center;
  padding: 12px;
}
.failed-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid #f59e0b;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #f59e0b;
}
.failed-text {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #fbbf24;
}
.btn-retry {
  pointer-events: auto;
  padding: 4px 14px;
  border-radius: 12px;
  border: 1px solid #f59e0b;
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 4px;
}
.btn-retry:hover:not(:disabled) {
  background: rgba(245, 158, 11, 0.3);
  box-shadow: 0 0 12px rgba(245, 158, 11, 0.3);
}
.btn-retry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .photo-actions {
    flex-wrap: wrap;
    gap: 4px;
  }
  .photo-actions button {
    font-size: 11px;
    padding: 5px 10px;
  }
  .photo-card:hover .card-check {
    opacity: 0;
  }
}
</style>

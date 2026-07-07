<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import gsap from 'gsap'
import { useStore } from '../store'
import { useToastStore } from '../stores/toast'
import { compressImages, uploadWithProgress } from '../upload'
import ImageEditor from './ImageEditor.vue'
import type { ImageEditResult } from '../types/transform'

const { t } = useI18n()
const toast = useToastStore()

const MAX_BATCH = 50

const emit = defineEmits<{ uploaded: [] }>()

const { tags: allTags, categories: allCats, loadAll } = useStore()

const fileInput = ref<HTMLInputElement | null>(null)
const preview = ref<HTMLImageElement | null>(null)
const fileLabel = ref<HTMLLabelElement | null>(null)
const uploadName = ref('')
const uploadDesc = ref('')
const watermark = ref('')
const previewSrc = ref('')
const showPreview = ref(false)
const _URL = URL
const submitting = ref(false)
const selectedCount = ref(0)
const previews = ref<{ name: string; url: string }[]>([])
const selectedTagIds = ref<number[]>([])
const selectedCatId = ref<number | null>(null)
const dragOver = ref(false)
const editorVisible = ref(false)
const editorSrc = ref('')
const editingIndex = ref(-1)
const editedBlobs = ref<Record<number, Blob>>({})
const previewEditedSrc = ref('')
const compressedFiles = ref<Record<number, File>>({})
const compressing = ref(false)
const uploadProgress = ref(-1)

function onFileChange(e: Event): void {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  if (files.length > MAX_BATCH) {
    toast.error(t('upload.maxBatch', { max: MAX_BATCH }))
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  revokePreviews()
  editedBlobs.value = {}
  if (previewEditedSrc.value) {
    URL.revokeObjectURL(previewEditedSrc.value)
    previewEditedSrc.value = ''
  }
  selectedCount.value = files.length
  if (files.length === 0) return

  if (files.length === 1) {
    const reader = new FileReader()
    reader.onload = (ev) => {
      previewSrc.value = (ev.target?.result as string) || ''
      showPreview.value = true
      nextTick(() => {
        gsap.fromTo(
          preview.value,
          { scale: 0.85, opacity: 0 },
          { scale: 1, opacity: 1, duration: 0.5, ease: 'expo.out' },
        )
      })
    }
    reader.readAsDataURL(files[0])
  } else {
    showPreview.value = false
    previewSrc.value = ''
    previews.value = Array.from(files).map((f) => ({
      name: f.name,
      url: URL.createObjectURL(f),
    }))
  }
  compressSelected()
}

function processFiles(files: FileList | File[]): void {
  if (!files || files.length === 0) return
  const dt = new DataTransfer()
  for (const f of files) dt.items.add(f)
  if (fileInput.value) fileInput.value.files = dt.files
  onFileChange({ target: { files: dt.files } } as unknown as Event)
}

function onDragOver(e: DragEvent): void {
  e.preventDefault()
  dragOver.value = true
}
function onDragLeave(): void {
  dragOver.value = false
}
function onDrop(e: DragEvent): void {
  e.preventDefault()
  dragOver.value = false
  if (e.dataTransfer?.files) processFiles(e.dataTransfer.files)
}
function onPaste(e: ClipboardEvent): void {
  if (e.clipboardData && e.clipboardData.files.length > 0) {
    e.preventDefault()
    processFiles(e.clipboardData.files)
  }
}

onMounted(() => {
  document.addEventListener('paste', onPaste)
})
onUnmounted(() => {
  document.removeEventListener('paste', onPaste)
})

function revokePreviews(): void {
  for (const p of previews.value) URL.revokeObjectURL(p.url)
  previews.value = []
}

function toggleTag(id: number): void {
  const idx = selectedTagIds.value.indexOf(id)
  if (idx > -1) selectedTagIds.value.splice(idx, 1)
  else selectedTagIds.value.push(id)
}

function clearSelection(): void {
  showPreview.value = false
  previewSrc.value = ''
  selectedCount.value = 0
  revokePreviews()
  editedBlobs.value = {}
  compressedFiles.value = {}
  if (previewEditedSrc.value) {
    URL.revokeObjectURL(previewEditedSrc.value)
    previewEditedSrc.value = ''
  }
  if (fileInput.value) fileInput.value.value = ''
}

async function compressSelected(): Promise<void> {
  const files = fileInput.value?.files
  if (!files || files.length === 0) return
  compressing.value = true
  try {
    const results = await compressImages(Array.from(files))
    const map: Record<number, File> = {}
    results.forEach((f, i) => {
      map[i] = f
    })
    compressedFiles.value = map
  } finally {
    compressing.value = false
  }
}

function openEditor(index: number): void {
  editingIndex.value = index
  editorSrc.value = editedBlobs.value[index]
    ? URL.createObjectURL(editedBlobs.value[index])
    : previews.value.length > 0
      ? previews.value[index].url
      : previewSrc.value
  editorVisible.value = true
}

function onEditorDone({ blob }: ImageEditResult): void {
  if (editingIndex.value >= 0) {
    if (previewEditedSrc.value) URL.revokeObjectURL(previewEditedSrc.value)
    previewEditedSrc.value = URL.createObjectURL(blob)
    editedBlobs.value = { ...editedBlobs.value, [editingIndex.value]: blob }
  }
  editorVisible.value = false
  editingIndex.value = -1
}

function buildFile(i: number, original: File): File {
  if (editedBlobs.value[i]) {
    return new File([editedBlobs.value[i]], original.name, { type: 'image/jpeg' })
  }
  return compressedFiles.value[i] ?? original
}

async function onSubmit(): Promise<void> {
  const files = fileInput.value?.files
  if (!files || files.length === 0) {
    toast.error(t('upload.failed'))
    return
  }

  const fileArray = Array.from(files)
  const fd = new FormData()
  const isBatch = files.length > 1

  for (let i = 0; i < fileArray.length; i++) {
    fd.append(isBatch ? 'files' : 'file', buildFile(i, fileArray[i]))
  }
  fd.append('name', uploadName.value.trim())
  fd.append('description', uploadDesc.value.trim())
  selectedTagIds.value.forEach((id) => fd.append('tagIds', String(id)))
  if (selectedCatId.value) fd.append('categoryId', String(selectedCatId.value))
  if (watermark.value.trim()) fd.append('watermark', watermark.value.trim())

  submitting.value = true
  uploadProgress.value = 0
  try {
    const url = isBatch ? '/api/photos/batch' : '/api/photos'
    const { ok, data } = await uploadWithProgress(url, fd, (pct) => {
      uploadProgress.value = pct
    })
    if (!ok) {
      const msg = (data as Record<string, unknown>)?.message || '请求失败'
      throw new Error(String(msg))
    }
    uploadName.value = ''
    uploadDesc.value = ''
    showPreview.value = false
    selectedCount.value = 0
    revokePreviews()
    compressedFiles.value = {}
    if (fileInput.value) fileInput.value.value = ''
    gsap.to('.upload-card', {
      keyframes: [
        { borderColor: 'rgba(255,255,255,0.08)', boxShadow: '0 8px 32px rgba(0,0,0,0.3)' },
        {
          borderColor: 'rgba(0,212,255,0.6)',
          boxShadow: '0 8px 32px rgba(0,0,0,0.3), 0 0 30px rgba(0,212,255,0.4)',
        },
        { borderColor: 'rgba(255,255,255,0.08)', boxShadow: '0 8px 32px rgba(0,0,0,0.3)' },
      ],
      duration: 1,
      ease: 'expo.out',
    })
    emit('uploaded')
  } catch (err) {
    toast.error(t('upload.failed') + ': ' + (err instanceof Error ? err.message : t('general.unknownError')))
    clearSelection()
  } finally {
    submitting.value = false
    uploadProgress.value = -1
  }
}
</script>

<template>
  <section class="upload-card">
    <h2>{{ $t('upload.title') }}</h2>
    <form @submit.prevent="onSubmit">
      <div class="file-area">
        <input
          id="fileInput"
          ref="fileInput"
          type="file"
          accept="image/*"
          multiple
          @change="onFileChange"
        />
        <label
          ref="fileLabel"
          for="fileInput"
          :class="{ 'preview-hidden': showPreview, 'drag-over': dragOver }"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <span class="file-icon">+</span>
          <span>{{
            compressing
              ? $t('upload.compressing')
              : selectedCount > 0
                ? $t('upload.selected', { count: selectedCount })
                : $t('upload.selectHint')
          }}</span>
        </label>
        <div v-if="showPreview" class="single-preview-wrap">
          <img ref="preview" :src="editedBlobs[0] ? previewEditedSrc : previewSrc" alt="预览" />
          <button type="button" class="upload-edit-btn" title="编辑图片" @click="openEditor(0)">
            ✎
          </button>
        </div>
      </div>
      <div v-if="previews.length > 0" class="preview-grid">
        <div v-for="(p, i) in previews" :key="p.name" class="preview-item">
          <img :src="editedBlobs[i] ? _URL.createObjectURL(editedBlobs[i]) : p.url" :alt="p.name" />
          <button type="button" class="upload-edit-btn" title="编辑图片" @click="openEditor(i)">
            ✎
          </button>
          <span>{{ p.name }}</span>
        </div>
      </div>
      <div class="meta-row">
        <select v-model="selectedCatId" class="mini-select">
          <option :value="null">{{ $t('upload.noCategory') }}</option>
          <option v-for="c in allCats" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <div class="tag-chips">
          <button
            v-for="t in allTags"
            :key="t.id"
            type="button"
            class="tag-chip"
            :class="{ on: selectedTagIds.includes(t.id) }"
            :style="
              selectedTagIds.includes(t.id) ? { background: t.color, borderColor: t.color } : {}
            "
            @click="toggleTag(t.id)"
          >
            {{ t.name }}
          </button>
        </div>
      </div>
      <div class="form-row">
        <input v-model="uploadName" type="text" :placeholder="$t('upload.namePlaceholder')" />
        <input v-model="uploadDesc" type="text" maxlength="500" :placeholder="$t('upload.descPlaceholder')" />
        <input v-model="watermark" type="text" maxlength="30" :placeholder="$t('upload.watermarkPlaceholder')" />
        <button type="submit" :disabled="submitting">
          {{ selectedCount > 1 ? $t('upload.uploadCount', { count: selectedCount }) : $t('upload.upload') }}
        </button>
        <button v-if="selectedCount > 0" type="button" class="btn-clear" @click="clearSelection">
          {{ $t('upload.cancelSelect') }}
        </button>
      </div>
      <div v-if="uploadProgress >= 0" class="upload-progress">
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
        </div>
        <span class="progress-text">{{ uploadProgress }}%</span>
      </div>
    </form>
  </section>
  <ImageEditor
    :src="editorSrc"
    :visible="editorVisible"
    @close="editorVisible = false"
    @done="onEditorDone"
  />
</template>

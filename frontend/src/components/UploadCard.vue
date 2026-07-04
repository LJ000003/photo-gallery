<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import gsap from 'gsap'
import { useStore } from '../store'
import { useToastStore } from '../stores/toast'
import { api } from '../api'
import ImageEditor from './ImageEditor.vue'
import type { ImageEditResult } from '../types/transform'

const toast = useToastStore()
const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'))

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

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const data = await res.json()
    return data.message || `请求失败（${res.status}）`
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`
  }
}

function onFileChange(e: Event): void {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
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
  if (previewEditedSrc.value) {
    URL.revokeObjectURL(previewEditedSrc.value)
    previewEditedSrc.value = ''
  }
  if (fileInput.value) fileInput.value.value = ''
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

async function onSubmit(): Promise<void> {
  const files = fileInput.value?.files
  if (!files || files.length === 0) {
    toast.error('请选择照片')
    return
  }

  const fd = new FormData()
  const fileArray = Array.from(files)
  for (let i = 0; i < fileArray.length; i++) {
    const f = editedBlobs.value[i]
      ? new File([editedBlobs.value[i]], fileArray[i].name, { type: 'image/jpeg' })
      : fileArray[i]
    fd.append('files', f)
  }
  fd.append('name', uploadName.value.trim())
  fd.append('description', uploadDesc.value.trim())
  selectedTagIds.value.forEach((id) => fd.append('tagIds', String(id)))
  if (selectedCatId.value) fd.append('categoryId', String(selectedCatId.value))
  if (watermark.value.trim()) fd.append('watermark', watermark.value.trim())

  submitting.value = true
  try {
    if (files.length > 1) {
      const res = await api('/api/photos/batch', { method: 'POST', body: fd })
      if (!res.ok) {
        const msg = await extractErrorMessage(res)
        throw new Error(msg)
      }
    } else {
      const singleFd = new FormData()
      const singleFile = editedBlobs.value[0]
        ? new File([editedBlobs.value[0]], files[0].name, { type: 'image/jpeg' })
        : files[0]
      singleFd.append('file', singleFile)
      singleFd.append('name', uploadName.value.trim())
      singleFd.append('description', uploadDesc.value.trim())
      selectedTagIds.value.forEach((id) => singleFd.append('tagIds', String(id)))
      if (selectedCatId.value) singleFd.append('categoryId', String(selectedCatId.value))
      if (watermark.value.trim()) singleFd.append('watermark', watermark.value.trim())
      const res = await api('/api/photos', { method: 'POST', body: singleFd })
      if (!res.ok) {
        const msg = await extractErrorMessage(res)
        throw new Error(msg)
      }
    }
    uploadName.value = ''
    uploadDesc.value = ''
    showPreview.value = false
    selectedCount.value = 0
    revokePreviews()
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
    toast.error('上传失败: ' + (err instanceof Error ? err.message : '未知错误'))
    clearSelection()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="upload-card">
    <h2>上传照片</h2>
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
            selectedCount > 0 ? `已选 ${selectedCount} 张` : '点击选择 / 拖拽 / 粘贴 (Ctrl+V)'
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
          <option :value="null">无分类</option>
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
        <input v-model="uploadName" type="text" placeholder="照片名称（可选）" />
        <input v-model="uploadDesc" type="text" maxlength="500" placeholder="照片描述（可选）" />
        <input v-model="watermark" type="text" maxlength="30" placeholder="水印文字（可选）" />
        <button type="submit" :disabled="submitting">
          <span v-if="submitting" class="btn-loading">
            <LottieLoader name="uploading" :size="20" />
          </span>
          <span v-else>{{ selectedCount > 1 ? `上传 (${selectedCount})` : '上传' }}</span>
        </button>
        <button v-if="selectedCount > 0" type="button" class="btn-clear" @click="clearSelection">
          取消选择
        </button>
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

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CloudUploadOutlined, InboxOutlined, ScissorOutlined } from '@ant-design/icons-vue'
import { Button, Drawer, Input, Progress, Select, Spin } from 'ant-design-vue'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { useUiStore } from '../../stores/ui'
import { AuthError } from '../../api'
import { compressImages, uploadWithProgress } from '../../upload'
import ImageEditor from '../editor/ImageEditor.vue'
import type { Photo } from '../../types/photo'
import type { ImageEditResult } from '../../types/transform'

/**
 * 上传抽屉：拖拽/点击/粘贴（Ctrl+V）→ 压缩 → 逐张编辑 → 元数据 → XHR 进度
 * 业务链路与旧版 UploadCard 一致（FormData 键名、409 查重引导、批量去重提示）
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  'update:open': [open: boolean]
  uploaded: []
}>()

const { t } = useI18n()
const toast = useToastStore()
const ui = useUiStore()
const data = useDataStore()

const MAX_BATCH = 50

const fileInput = ref<HTMLInputElement | null>(null)
/** 待上传文件的唯一状态来源：input change / 拖拽 / 粘贴统一写入。
 *  此前依赖 fileInput.files——抽屉未渲染时 ref 为 null，粘贴文件只进预览
 *  不落 input，提交时读到空 FileList 静默失败。 */
const pendingFiles = ref<File[]>([])
const previews = ref<{ name: string; url: string }[]>([])
const selectedCount = ref(0)
const dragOver = ref(false)
const compressing = ref(false)
const submitting = ref(false)
const uploadProgress = ref(-1)
const uploadName = ref('')
const uploadDesc = ref('')
const watermark = ref('')
const selectedTagIds = ref<number[]>([])
const selectedCatId = ref<number | undefined>(undefined)
const compressedFiles = ref<Record<number, File>>({})
const editedBlobs = ref<Record<number, Blob>>({})
const editorVisible = ref(false)
const editorSrc = ref('')
const editingIndex = ref(-1)

/* ---------- 文件选取 ---------- */
function setFiles(files: File[]): void {
  if (files.length > MAX_BATCH) {
    toast.error(t('upload.tooMany'))
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  pendingFiles.value = files
  revokePreviews()
  editedBlobs.value = {}
  selectedCount.value = files.length
  if (files.length === 0) return
  previews.value = files.map((f) => ({
    name: f.name,
    url: URL.createObjectURL(f),
  }))
  void compressSelected()
}

function onFileChange(e: Event): void {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  setFiles(Array.from(files))
}

function processFiles(files: FileList | File[]): void {
  if (!files || files.length === 0) return
  // 统一走组件状态（不写 fileInput.files）：抽屉未渲染（forceRender=false）
  // 时 ref 为 null，旧实现粘贴文件丢失、提交静默失败
  setFiles(Array.from(files))
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

/** 全局粘贴：任何时候粘贴图片 → 打开抽屉并加入 */
function onPaste(e: ClipboardEvent): void {
  if (e.clipboardData && e.clipboardData.files.length > 0) {
    const imgs = Array.from(e.clipboardData.files).filter((f) => f.type.startsWith('image/'))
    if (imgs.length > 0) {
      e.preventDefault()
      if (!props.open) emit('update:open', true)
      processFiles(imgs)
    }
  }
}

onMounted(() => document.addEventListener('paste', onPaste))
onUnmounted(() => document.removeEventListener('paste', onPaste))

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
  selectedCount.value = 0
  pendingFiles.value = []
  revokePreviews()
  editedBlobs.value = {}
  compressedFiles.value = {}
  if (fileInput.value) fileInput.value.value = ''
}

// 关闭抽屉即清空选择（旧实现残留：重开显示上次的预览与文件）
watch(
  () => props.open,
  (v) => {
    if (!v) clearSelection()
  },
)

async function compressSelected(): Promise<void> {
  const files = pendingFiles.value
  if (!files || files.length === 0) return
  compressing.value = true
  try {
    const results = await compressImages(files)
    const map: Record<number, File> = {}
    results.forEach((f, i) => {
      map[i] = f
    })
    compressedFiles.value = map
  } finally {
    compressing.value = false
  }
}

/* ---------- 逐张编辑 ---------- */
function openEditor(index: number): void {
  editingIndex.value = index
  editorSrc.value = editedBlobs.value[index]
    ? URL.createObjectURL(editedBlobs.value[index])
    : previews.value[index]?.url || ''
  editorVisible.value = true
}

function onEditorDone({ blob }: ImageEditResult): void {
  if (editingIndex.value >= 0) {
    editedBlobs.value = { ...editedBlobs.value, [editingIndex.value]: blob }
    // 刷新预览图
    const old = previews.value[editingIndex.value]
    if (old) {
      previews.value[editingIndex.value] = { name: old.name, url: URL.createObjectURL(blob) }
      URL.revokeObjectURL(old.url)
    }
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

/* ---------- 提交 ---------- */
async function onSubmit(): Promise<void> {
  const fileArray = pendingFiles.value
  if (!fileArray || fileArray.length === 0) return
  const fd = new FormData()
  const isBatch = fileArray.length > 1

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
    const {
      ok,
      status,
      data: resp,
    } = await uploadWithProgress(url, fd, (pct) => {
      uploadProgress.value = pct
    })
    if (!ok) {
      if (status === 409) {
        const dup = resp as { code: number; message: string; data: Photo }
        toast.add(dup.message, 'info', 5000, {
          label: t('upload.viewExisting'),
          onClick: () => ui.openViewer(dup.data, [dup.data]),
        })
        clearSelection()
        return
      }
      const msg = (resp as Record<string, unknown>)?.message || t('common.unknownError')
      throw new Error(String(msg))
    }
    if (isBatch) {
      const batchResp = resp as { code: number; data: Photo[] }
      const uploadedCount = batchResp.data?.length ?? 0
      if (uploadedCount < selectedCount.value) {
        toast.info(t('upload.skipped', { n: selectedCount.value - uploadedCount }))
      }
    }
    uploadName.value = ''
    uploadDesc.value = ''
    watermark.value = ''
    selectedTagIds.value = []
    selectedCatId.value = undefined
    clearSelection()
    toast.success(t('upload.success'))
    emit('update:open', false)
    emit('uploaded')
  } catch (err) {
    if (err instanceof AuthError) {
      toast.error(err.message)
      return
    }
    toast.error(t('upload.failed') + ': ' + (err instanceof Error ? err.message : ''))
    clearSelection()
  } finally {
    submitting.value = false
    uploadProgress.value = -1
  }
}

function previewUrl(i: number): string {
  return previews.value[i]?.url || ''
}
</script>

<template>
  <Drawer
    :open="props.open"
    :title="t('upload.title')"
    placement="right"
    :width="'min(460px, 100vw)'"
    :closable="true"
    @update:open="emit('update:open', $event)"
  >
    <div class="upload-body">
      <input
        id="uploadFileInput"
        ref="fileInput"
        type="file"
        accept="image/*"
        multiple
        class="file-input"
        @change="onFileChange"
      />

      <!-- 拖拽区 -->
      <label
        for="uploadFileInput"
        class="dropzone"
        :class="{ over: dragOver }"
        @dragover="onDragOver"
        @dragleave="onDragLeave"
        @drop="onDrop"
      >
        <template v-if="selectedCount === 0">
          <CloudUploadOutlined class="dropzone-icon" />
          <span class="dropzone-text">{{ t('upload.dropzone') }}</span>
          <span class="dropzone-sub">{{ t('upload.pasteHint') }}</span>
        </template>
        <template v-else>
          <Spin v-if="compressing" size="small" />
          <span class="dropzone-text">
            {{
              compressing ? t('upload.compressing') : t('selection.selected', { n: selectedCount })
            }}
          </span>
          <span class="dropzone-sub">{{ t('upload.dropzone') }}</span>
        </template>
      </label>

      <!-- 预览网格 -->
      <div v-if="previews.length > 0" class="preview-grid">
        <div v-for="(p, i) in previews" :key="p.name + i" class="preview-item">
          <img :src="previewUrl(i)" :alt="p.name" loading="lazy" />
          <button
            type="button"
            class="preview-edit"
            :aria-label="t('upload.editing')"
            :title="t('upload.editing')"
            @click="openEditor(i)"
          >
            <ScissorOutlined />
          </button>
        </div>
      </div>

      <!-- 元数据 -->
      <div class="meta-section">
        <Input v-model:value="uploadName" :placeholder="t('upload.name')" allow-clear />
        <Input
          v-model:value="uploadDesc"
          :placeholder="t('upload.description')"
          :maxlength="500"
          allow-clear
        />
        <Select
          v-model:value="selectedCatId"
          :placeholder="t('upload.category')"
          style="width: 100%"
          :options="data.categories.map((c) => ({ value: c.id, label: c.name }))"
          :allow-clear="true"
        />
        <div class="tag-row">
          <span class="tag-label">{{ t('upload.tags') }}</span>
          <div class="tag-chips">
            <button
              v-for="tag in data.tags"
              :key="tag.id"
              type="button"
              class="chip"
              :class="{ on: selectedTagIds.includes(tag.id) }"
              @click="toggleTag(tag.id)"
            >
              <span class="chip-dot" :style="{ background: tag.color }"></span>
              {{ tag.name }}
            </button>
          </div>
        </div>
        <Input
          v-model:value="watermark"
          :placeholder="t('upload.watermark')"
          :maxlength="30"
          allow-clear
        />
      </div>

      <!-- 进度 -->
      <div v-if="uploadProgress >= 0" class="progress-row">
        <Progress
          :percent="uploadProgress"
          :status="uploadProgress === 100 ? 'success' : 'active'"
          :show-info="false"
        />
        <span class="progress-text">{{ t('upload.uploading', { pct: uploadProgress }) }}</span>
      </div>

      <!-- 提交 -->
      <Button
        type="primary"
        block
        size="large"
        class="submit-btn"
        :disabled="selectedCount === 0 || submitting || compressing"
        :loading="submitting"
        @click="onSubmit"
      >
        <InboxOutlined v-if="!submitting" />
        {{ selectedCount > 0 ? t('selection.selected', { n: selectedCount }) : t('topbar.upload') }}
      </Button>
    </div>

    <ImageEditor
      :src="editorSrc"
      :visible="editorVisible"
      @close="editorVisible = false"
      @done="onEditorDone"
    />
  </Drawer>
</template>

<style scoped>
.upload-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.file-input {
  display: none;
}
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 150px;
  border: 2px dashed var(--c-border);
  border-radius: 16px;
  cursor: pointer;
  color: var(--c-text-dim);
  background: var(--c-surface-2);
  transition: all 0.2s ease;
  text-align: center;
  padding: 20px;
}
.dropzone:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
}
.dropzone.over {
  border-color: var(--c-accent);
  color: var(--c-accent);
  background: var(--c-accent-soft);
  transform: scale(1.01);
}
.dropzone-icon {
  font-size: 36px;
  color: var(--c-accent);
  opacity: 0.8;
}
.dropzone-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--c-text);
}
.dropzone-sub {
  font-size: 12px;
  color: var(--c-text-dim);
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
}
.preview-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  background: var(--c-surface-2);
}
.preview-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-edit {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: background 0.15s ease;
}
.preview-edit:hover {
  background: rgba(0, 0, 0, 0.8);
}

.meta-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tag-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.tag-label {
  font-size: 13px;
  color: var(--c-text-dim);
  line-height: 32px;
  flex-shrink: 0;
}
.tag-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.chip:hover {
  border-color: var(--c-accent);
}
.chip.on {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
  color: var(--c-accent);
}
.chip-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.progress-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.progress-text {
  font-size: 12px;
  color: var(--c-text-dim);
  text-align: right;
}
.submit-btn {
  border-radius: 999px;
  margin-top: 4px;
}
</style>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import PhotoGrid from './PhotoGrid.vue'
import GridSkeleton from './GridSkeleton.vue'
import SelectionBar from './SelectionBar.vue'
import EmptyState from '../common/EmptyState.vue'
import ImageEditor from '../editor/ImageEditor.vue'
import { Modal } from 'ant-design-vue'

import { usePhotoStore } from '../../stores/photo'
import { useUiStore } from '../../stores/ui'
import { usePhotoActions } from '../../composables/usePhotoActions'
import { webpUrl } from '../../webp'
import { appendMediaParams } from '../../utils/token'
import { api } from '../../api'
import { useToastStore } from '../../stores/toast'
import type { Photo } from '../../types/photo'
import type { TransformParams } from '../../types/transform'

/**
 * 照片流（默认首页）：虚拟化网格 + 选择模式 + 键盘操作
 * 业务链路与旧版一致：无限滚动 / 批量编辑 / 批量删除（可撤销）/ 分享 / 图片编辑
 */
const { t } = useI18n()
const photo = usePhotoStore()
const ui = useUiStore()
const toast = useToastStore()
const { deletePhoto, deletePhotos, generateShare } = usePhotoActions()

/** 批量编辑上限（后端语义一致；超过则提示并截取前 50 张） */
const BATCH_EDIT_LIMIT = 50

/* ---------- 选择状态（照片流局部） ---------- */
const selectedIds = ref(new Set<number>())

function toggleSelect(id: number): void {
  const s = selectedIds.value
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = new Set(s)
}

const allSelected = computed(
  () => photo.totalCount > 0 && selectedIds.value.size === photo.totalCount,
)

async function toggleAll(): Promise<void> {
  if (allSelected.value) {
    selectedIds.value = new Set()
    return
  }
  // 失败即终止循环：loadMore 返回 false 时服务器不可用，
  // 旧实现会 while(hasMore) 无限发请求
  let failed = false
  while (photo.hasMore && !photo.loading) {
    if (!(await photo.loadMore())) {
      failed = true
      break
    }
  }
  selectedIds.value = new Set(photo.photos.map((p) => p.id))
  if (failed) {
    toast.error(t('gallery.loadFailed'))
  }
}

function batchDelete(): void {
  if (selectedIds.value.size === 0) return
  Modal.confirm({
    title: t('actions.delete'),
    content: t('selection.batchDeleteConfirm', { n: selectedIds.value.size }),
    okText: t('actions.delete'),
    okButtonProps: { danger: true },
    cancelText: t('actions.cancel'),
    onOk: () => {
      deletePhotos([...selectedIds.value])
      selectedIds.value = new Set()
    },
  })
}

function batchEdit(): void {
  if (selectedIds.value.size === 0) return
  const selected = photo.photos.filter((p) => selectedIds.value.has(p.id))
  if (selected.length === 0) return
  // 超过 50 张：提示并截取前 50 张（限制提前到进入时，避免提交时才报错）
  if (selected.length > BATCH_EDIT_LIMIT) {
    toast.info(t('batchEdit.tooMany'))
    ui.batchEditPhotos = selected.slice(0, BATCH_EDIT_LIMIT)
    return
  }
  // 保留选择：取消弹窗不丢多选状态
  ui.batchEditPhotos = selected
}

function onGenerateShare(): void {
  if (selectedIds.value.size === 0) return
  generateShare([...selectedIds.value])
}

function viewSelected(): void {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  const p = photo.photos.find((ph) => ph.id === ids[0])
  if (p) ui.openViewer(p, photo.photos)
}

// 列表变化时剪掉已不存在的选中项（与旧版一致）
watch(
  () => photo.photos,
  () => {
    const currentIds = new Set(photo.photos.map((p) => p.id))
    let changed = false
    for (const id of selectedIds.value) {
      if (!currentIds.has(id)) {
        changed = true
        break
      }
    }
    if (changed) {
      selectedIds.value = new Set([...selectedIds.value].filter((id) => currentIds.has(id)))
    }
  },
)

/* ---------- 键盘：Ctrl+A 全选 / Delete 删除 / Enter 查看 / Esc 退出 ---------- */
function onKeydown(e: KeyboardEvent): void {
  if (!ui.unlocked || ui.viewPhoto) return
  const target = e.target as HTMLElement | null
  if (
    target &&
    (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)
  ) {
    return
  }
  if (e.ctrlKey || e.metaKey) {
    if (e.key.toLowerCase() === 'a') {
      e.preventDefault()
      void toggleAll()
    }
    return
  }
  if (e.key === 'Delete' && selectedIds.value.size > 0) {
    e.preventDefault()
    batchDelete()
  } else if (e.key === 'Enter' && selectedIds.value.size > 0) {
    e.preventDefault()
    viewSelected()
  } else if (e.key === 'Escape' && selectedIds.value.size > 0) {
    selectedIds.value = new Set()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

/* ---------- 图片编辑器（裁剪/旋转，POST transform） ---------- */
const editorPhoto = ref<Photo | null>(null)
const editorSrc = computed(() =>
  editorPhoto.value ? appendMediaParams(webpUrl(editorPhoto.value.id), editorPhoto.value) : '',
)

function openImageEditor(p: Photo): void {
  editorPhoto.value = p
}

async function onImageEditDone({ params }: { params: TransformParams; blob: Blob }): Promise<void> {
  const p = editorPhoto.value
  editorPhoto.value = null
  if (!p) return
  try {
    const res = await api(`/api/photos/${p.id}/transform`, {
      method: 'POST',
      body: JSON.stringify(params),
    })
    if (res.ok) {
      toast.success(t('edit.saved'))
      photo.resetAndReload()
    } else {
      toast.error(t('common.unknownError'))
    }
  } catch {
    toast.error(t('common.networkError'))
  }
}

/* ---------- 空态区分：有筛选条件 vs 纯空 ---------- */
const hasFilters = computed(
  () =>
    photo.selectedTagIds.length > 0 || photo.selectedCategoryIds.length > 0 || !!photo.searchQuery,
)

const showSkeleton = computed(() => photo.loading && photo.photos.length === 0)
const showEmpty = computed(() => !photo.loading && !photo.hasMore && photo.photos.length === 0)
</script>

<template>
  <section class="photos-view" :aria-label="t('gallery.title')">
    <!-- 照片计数（克制的一行） -->
    <p v-if="photo.totalCount > 0" class="count-line">
      {{ t('gallery.count', { n: photo.totalCount }) }}
    </p>

    <!-- 选择模式工具条 -->
    <SelectionBar
      v-if="selectedIds.size > 0"
      :count="selectedIds.size"
      :total-count="photo.totalCount"
      :all-selected="allSelected"
      @toggle-all="toggleAll"
      @share="onGenerateShare"
      @edit="batchEdit"
      @delete="batchDelete"
      @cancel="selectedIds = new Set()"
    />

    <!-- 首屏骨架 -->
    <GridSkeleton v-if="showSkeleton" :count="12" />

    <!-- 空态 -->
    <EmptyState
      v-else-if="showEmpty"
      :title="t('gallery.emptyTitle')"
      :hint="hasFilters ? t('gallery.emptyHintFiltered') : t('gallery.emptyHint')"
    />

    <!-- 虚拟化网格 -->
    <PhotoGrid
      v-else
      :photos="photo.photos"
      :search-query="photo.searchQuery"
      :selected-ids="selectedIds"
      :loading="photo.loading"
      :has-more="photo.hasMore"
      @load-more="photo.loadMore()"
      @view="(p) => ui.openViewer(p, photo.photos)"
      @edit="ui.editPhoto = $event"
      @edit-image="openImageEditor"
      @delete="deletePhoto"
      @toggle-select="toggleSelect"
    />

    <ImageEditor
      :src="editorSrc"
      :visible="!!editorPhoto"
      @close="editorPhoto = null"
      @done="onImageEditDone"
    />
  </section>
</template>

<style scoped>
.photos-view {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.count-line {
  font-size: 12px;
  color: var(--c-text-dim);
  margin-bottom: 12px;
  padding-left: 2px;
}

@media (max-width: 768px) {
  .photos-view {
    padding: 14px 12px;
  }
  .count-line {
    margin-bottom: 8px;
  }
}
</style>

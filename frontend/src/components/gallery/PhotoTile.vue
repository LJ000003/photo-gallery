<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
  ScissorOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { thumbUrl } from '../../utils/webp'
import { appendMediaParams, appendTokenParam, mediaUrlWithVersion } from '../../utils/token'
import { formatSize } from '../../utils/format'
import { api } from '../../api'
import { useToastStore } from '../../stores/toast'
import type { Photo } from '../../types/photo'

/**
 * 无框悬浮照片片（苹果系极简）：
 * 照片即主体，无边框无卡片；悬停浮现操作（查看/编辑/裁剪/删除）与选择圈
 * 搜索模式下照片下方显示名称条（带高亮），便于扫视结果
 * 保留趣味性：桌面端轻微 3D 倾斜（±3°，CSS 实现，无 GSAP）
 */
const props = withDefaults(
  defineProps<{
    photo: Photo
    selected?: boolean
    searchQuery?: string
    /** 是否显示选择圈（相册详情等纯浏览场景传 false） */
    selectable?: boolean
    /** 显式查询 token（分享页 viewer token）；photo.mediaToken 缺失时回退 */
    token?: string
  }>(),
  { selectable: true, searchQuery: '', token: '' },
)

const emit = defineEmits<{
  view: [p: Photo]
  edit: [p: Photo]
  'edit-image': [p: Photo]
  delete: [id: number]
  toggleSelect: [id: number]
}>()

const { t } = useI18n()
const toast = useToastStore()

/* ---------- 搜索高亮 ---------- */
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

/** 缩略图 URL：优先 per-photo 短时签名，缺失时回退显式 token（分享页）；
 *  版本号兜底拼接（transform 后绕过 7 天缓存） */
function tileSrc(p: Photo, w: number): string {
  const base = thumbUrl(p.id, w)
  const signed = p.mediaToken ? appendMediaParams(base, p) : appendTokenParam(base, props.token)
  return mediaUrlWithVersion(signed, p)
}

/* ---------- 处理失败重试 ---------- */
const retrying = ref(false)
async function retryProcessing(): Promise<void> {
  retrying.value = true
  try {
    const res = await api(`/api/photos/${props.photo.id}/retry-processing`, { method: 'POST' })
    if (res.ok) {
      // 乐观更新本地状态（照片对象来自 store，属性变更会驱动 UI）；lint 规则与旧版行为一致
      /* eslint-disable-next-line vue/no-mutating-props */
      props.photo.processingStatus = 'PROCESSING'
      /* eslint-disable-next-line vue/no-mutating-props */
      props.photo.errorMessage = undefined
      toast.success(t('gallery.retryProcessing'))
    } else {
      toast.error(t('common.unknownError'))
    }
  } catch {
    toast.error(t('common.networkError'))
  } finally {
    retrying.value = false
  }
}

/* ---------- 删除（确认后轻微淡出） ---------- */
const deleting = ref(false)
function onDelete(): void {
  Modal.confirm({
    title: t('actions.delete'),
    content: t('edit.deleteConfirm', { name: props.photo.name || `#${props.photo.id}` }),
    okText: t('actions.delete'),
    okButtonProps: { danger: true },
    cancelText: t('actions.cancel'),
    onOk: () => {
      deleting.value = true
      setTimeout(() => emit('delete', props.photo.id), 180)
    },
  })
}

/* ---------- 趣味性：CSS 3D 微倾斜（仅指针设备） ---------- */
const tiltX = ref(0)
const tiltY = ref(0)
function onMove(e: MouseEvent): void {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width - 0.5
  const y = (e.clientY - rect.top) / rect.height - 0.5
  tiltX.value = x * 3
  tiltY.value = -y * 3
}
function onLeave(): void {
  tiltX.value = 0
  tiltY.value = 0
}

/* ---------- 键盘可达性 ---------- */
function onKeydown(e: KeyboardEvent): void {
  // 焦点在内嵌按钮（编辑/删除/重试）时不拦截：Enter/Space 应激活按钮而非打开查看器
  if (e.target !== e.currentTarget) return
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    emit('view', props.photo)
  }
}
</script>

<template>
  <figure
    class="photo-tile"
    :class="{ selected, deleting, 'has-caption': searchQuery }"
    role="button"
    tabindex="0"
    :aria-label="photo.name || t('gallery.photoAria', { id: photo.id })"
    :style="{
      '--tilt-x': `${tiltX}deg`,
      '--tilt-y': `${tiltY}deg`,
    }"
    @mousemove="onMove"
    @mouseleave="onLeave"
    @keydown="onKeydown"
  >
    <div class="tile-photo" @click="emit('view', photo)">
      <img
        class="tile-img"
        :src="tileSrc(photo, 400)"
        :srcset="`${tileSrc(photo, 200)} 200w, ${tileSrc(photo, 400)} 400w`"
        sizes="(max-width: 768px) calc((100vw - 20px) / 2), (max-width: 1400px) calc((100vw - 280px) / 5), 240px"
        :alt="photo.name"
        loading="lazy"
        decoding="async"
      />

      <!-- 悬停操作层（分享页等纯浏览场景 selectable=false 时隐藏：事件无人监听） -->
      <div class="tile-overlay">
        <div v-if="selectable" class="overlay-actions">
          <button
            class="action-btn"
            :aria-label="t('actions.edit')"
            :title="t('actions.edit')"
            @click.stop="emit('edit', photo)"
          >
            <EditOutlined />
          </button>
          <button
            class="action-btn"
            :aria-label="t('upload.editing')"
            :title="t('upload.editing')"
            @click.stop="emit('edit-image', photo)"
          >
            <ScissorOutlined />
          </button>
          <button
            class="action-btn danger"
            :aria-label="t('actions.delete')"
            :title="t('actions.delete')"
            @click.stop="onDelete"
          >
            <DeleteOutlined />
          </button>
        </div>
        <div class="overlay-caption">
          <span class="caption-name">
            <template v-for="(s, i) in nameSegments" :key="i">
              <mark v-if="s.hl" class="search-hl">{{ s.text }}</mark>
              <template v-else>{{ s.text }}</template>
            </template>
          </span>
          <span class="caption-meta">
            {{ formatSize(photo.fileSize)
            }}<template v-if="photo.category"> · {{ photo.category.name }}</template>
          </span>
        </div>
      </div>

      <!-- 处理中 -->
      <div v-if="photo.processingStatus === 'PROCESSING'" class="tile-status processing">
        <span class="spinner"></span>
        <span>{{ t('gallery.processing') }}</span>
      </div>

      <!-- 处理失败（遮罩点击不穿透打开查看器——图片可能不存在） -->
      <div v-if="photo.processingStatus === 'FAILED'" class="tile-status failed" @click.stop>
        <WarningOutlined class="warn-icon" />
        <span class="warn-text">{{ photo.errorMessage || t('gallery.processFailed') }}</span>
        <button class="retry-btn" :disabled="retrying" @click.stop="retryProcessing">
          <ReloadOutlined />
          {{ retrying ? '…' : t('gallery.retryProcessing') }}
        </button>
      </div>

      <!-- 选择圈 -->
      <button
        v-if="selectable"
        class="check-bubble"
        :class="{ checked: selected }"
        :aria-label="t(selected ? 'selection.deselect' : 'selection.select')"
        @click.stop="emit('toggleSelect', photo.id)"
      >
        <CheckOutlined v-if="selected" class="check-mark" />
      </button>
    </div>

    <!-- 搜索模式下显示名称条 -->
    <figcaption v-if="searchQuery" class="tile-caption">
      <span class="caption-name">
        <template v-for="(s, i) in nameSegments" :key="i">
          <mark v-if="s.hl" class="search-hl">{{ s.text }}</mark>
          <template v-else>{{ s.text }}</template>
        </template>
      </span>
      <span class="caption-meta">
        {{ formatSize(photo.fileSize)
        }}<template v-if="photo.category"> · {{ photo.category.name }}</template>
      </span>
    </figcaption>
  </figure>
</template>

<style scoped>
.photo-tile {
  margin: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  outline: none;
  transform: perspective(800px) rotateX(var(--tilt-y, 0deg)) rotateY(var(--tilt-x, 0deg));
  transition:
    transform 0.25s ease,
    opacity 0.2s ease;
}
.photo-tile.deleting {
  opacity: 0;
  transform: scale(0.92);
}
.tile-photo {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  overflow: hidden;
  background: var(--c-surface-2);
  transition:
    box-shadow 0.25s ease,
    transform 0.25s ease;
}
.photo-tile:hover .tile-photo {
  box-shadow: var(--shadow-photo);
}
.photo-tile:focus-visible .tile-photo {
  outline: 2px solid var(--c-accent);
  outline-offset: 2px;
}
.tile-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}
.photo-tile:hover .tile-img {
  transform: scale(1.03);
}

/* 悬停操作层 */
.tile-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.62) 0%,
    rgba(0, 0, 0, 0.18) 38%,
    transparent 60%
  );
  opacity: 0;
  transition: opacity 0.25s ease;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 10px;
}
.photo-tile:hover .tile-overlay,
.photo-tile:focus-within .tile-overlay,
.photo-tile.selected .tile-overlay {
  opacity: 1;
}
.overlay-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
  opacity: 0;
  transform: translateY(-4px);
}
.photo-tile:hover .action-btn,
.photo-tile:focus-within .action-btn,
.photo-tile.selected .action-btn {
  opacity: 1;
  transform: translateY(0);
}
.action-btn:hover {
  background: rgba(0, 0, 0, 0.72);
}
.action-btn.danger:hover {
  background: var(--c-danger);
}
.action-btn:active {
  transform: scale(0.92);
}
.overlay-caption {
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 2px;
  pointer-events: none;
}
.caption-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.caption-meta {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
}

/* 搜索模式名称条 */
.tile-caption {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
}
.tile-caption .caption-name {
  color: var(--c-text);
  flex: 1;
  min-width: 0;
}
.tile-caption .caption-meta {
  color: var(--c-text-dim);
  flex-shrink: 0;
}
.search-hl {
  background: var(--c-warning-soft);
  color: var(--c-warning);
  border-radius: 3px;
  padding: 0 1px;
}

/* 状态遮罩 */
.tile-status {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 2;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}
.tile-status.processing {
  pointer-events: none;
}
.spinner {
  width: 26px;
  height: 26px;
  border: 3px solid rgba(255, 255, 255, 0.2);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.tile-status.failed {
  background: rgba(0, 0, 0, 0.62);
}
.warn-icon {
  font-size: 22px;
  color: var(--c-warning);
}
.warn-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-warning);
  text-align: center;
}
.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--c-warning);
  background: var(--c-warning-soft);
  color: var(--c-warning);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.retry-btn:hover:not(:disabled) {
  filter: brightness(1.15);
}
.retry-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

/* 选择圈 */
.check-bubble {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
  opacity: 0;
  padding: 0;
  font-size: 12px;
}
.photo-tile:hover .check-bubble,
.photo-tile:focus-within .check-bubble,
.photo-tile.selected .check-bubble {
  opacity: 1;
}
.check-bubble.checked {
  background: var(--c-accent);
  border-color: var(--c-accent);
}
.check-mark {
  color: #fff;
  font-size: 12px;
}
.photo-tile.selected .tile-photo {
  box-shadow:
    0 0 0 3px var(--c-accent),
    var(--shadow-photo);
}

@media (max-width: 768px) {
  .photo-tile {
    transform: none;
  }
  .check-bubble {
    opacity: 1;
    top: 6px;
    left: 6px;
    width: 24px;
    height: 24px;
  }
  .tile-overlay {
    display: none;
  }
}
</style>

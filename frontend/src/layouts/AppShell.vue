<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterView, useRouter } from 'vue-router'

import KonamiGate from '../components/auth/KonamiGate.vue'
import TopBar from '../components/topbar/TopBar.vue'
import MobileTabBar from '../components/topbar/MobileTabBar.vue'
import PhotoViewer from '../components/viewer/PhotoViewer.vue'
import UploadDrawer from '../components/upload/UploadDrawer.vue'
import PhotoEditDrawer from '../components/editor/PhotoEditDrawer.vue'
import BatchEditDrawer from '../components/editor/BatchEditDrawer.vue'
import ShareDialog from '../components/common/ShareDialog.vue'
import HelpModal from '../components/topbar/HelpModal.vue'
import ToastStack from '../components/common/ToastStack.vue'

import { usePhotoStore } from '../stores/photo'
import { useUiStore } from '../stores/ui'
import { useDataStore } from '../stores/data'
import { useToastStore } from '../stores/toast'
import { requestToken } from '../api'
import { useKeyboardShortcuts } from '../composables/useKeyboardShortcuts'
import type { Photo } from '../types/photo'

const { t } = useI18n()
const router = useRouter()
const photo = usePhotoStore()
const ui = useUiStore()
const toast = useToastStore()

// 必须在 setup 顶层调用（旧版踩过的坑：放进 onMounted 会让内部钩子失效）
useKeyboardShortcuts()

const konamiReset = ref(0)
const konamiSuccess = ref(0)

function scrollToTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function onUnlock(keys: string[]): Promise<void> {
  try {
    const token = await requestToken(keys)
    ui.setToken(token)
    // 先触发 KonamiGate 成功动画（HP +30），1.5s 后再解锁界面
    konamiSuccess.value++
    setTimeout(() => {
      ui.unlock()
      router.replace(window.location.pathname + window.location.search)
      photo.loadMore()
      void useDataStore().loadAll()
    }, 1500)
  } catch (err) {
    konamiReset.value++
    toast.error(err instanceof Error ? err.message : t('auth.failed'))
  }
}

function onEditSaved(): void {
  ui.editPhoto = null
  photo.resetAndReload()
}

function onUploaded(): void {
  photo.resetAndReload()
  photo.startProcessingPoll()
}

function onBatchSaved(updated: Photo[]): void {
  ui.batchEditPhotos = null
  // 有过滤条件时整体重载（避免不再匹配过滤条件的照片残留）；否则原地替换，保持虚拟滚动位置
  if (
    photo.selectedTagIds.length > 0 ||
    photo.selectedCategoryIds.length > 0 ||
    photo.searchQuery
  ) {
    photo.resetAndReload()
  } else {
    photo.applyBatchEdit(updated)
  }
  void useDataStore().refreshAlbums()
  toast.success(t('batchEdit.saved'))
}

onMounted(() => {
  if (ui.unlocked && !ui.token) {
    ui.reLock()
  }
  if (ui.unlocked) {
    photo.loadMore()
    void useDataStore().loadAll()
  }
  window.addEventListener('scroll', () => {
    ui.showBackTop = window.scrollY > 400
  })
})
</script>

<template>
  <KonamiGate
    v-if="!ui.unlocked"
    :reset-trigger="konamiReset"
    :success-trigger="konamiSuccess"
    @unlocked="onUnlock"
  />
  <template v-if="ui.unlocked">
    <TopBar />
    <main class="shell-main">
      <RouterView />
    </main>

    <button v-show="ui.showBackTop" class="back-top" aria-label="回到顶部" @click="scrollToTop">
      ↑
    </button>

    <MobileTabBar />

    <!-- 全局浮层 -->
    <PhotoViewer v-if="ui.viewPhoto" :photo="ui.viewPhoto" @close="ui.closeViewer()" />
    <UploadDrawer v-model:open="ui.uploadOpen" @uploaded="onUploaded" />
    <PhotoEditDrawer
      v-if="ui.editPhoto"
      :photo="ui.editPhoto"
      @close="ui.editPhoto = null"
      @saved="onEditSaved"
    />
    <BatchEditDrawer
      v-if="ui.batchEditPhotos"
      :photos="ui.batchEditPhotos"
      @close="ui.batchEditPhotos = null"
      @saved="onBatchSaved"
    />
    <!-- 分享弹窗单例：旧版在 PhotosView 与 PhotoViewer 各挂一个，
         状态是模块级单例，两个实例会同时弹出 -->
    <ShareDialog />
    <HelpModal />
    <ToastStack />
  </template>
</template>

<style scoped>
.shell-main {
  min-height: calc(100dvh - 56px);
}

.back-top {
  position: fixed;
  right: 24px;
  bottom: 32px;
  z-index: 100;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  background: var(--c-surface);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--c-border);
  color: var(--c-text);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.back-top:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
  transform: translateY(-2px);
}
.back-top:active {
  transform: scale(0.95);
}

@media (max-width: 768px) {
  .back-top {
    right: 12px;
    bottom: calc(76px + env(safe-area-inset-bottom));
    width: 40px;
    height: 40px;
    font-size: 16px;
  }
  .shell-main {
    padding-bottom: 72px;
  }
}
</style>

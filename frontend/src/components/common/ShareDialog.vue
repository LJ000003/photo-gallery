<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CopyOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { Button, Input, Modal, Spin } from 'ant-design-vue'
import { usePhotoActions } from '../../composables/usePhotoActions'

/**
 * 分享链接弹窗：生成结果展示 + 复制
 * 状态来自 usePhotoActions 单例（shareModal/shareUrl/shareLoading）
 */
const { t } = useI18n()
const { shareModal, shareUrl, shareLoading, copyShareLink } = usePhotoActions()

const photoCount = computed(() => shareModal.value?.photoIds.length ?? 0)
</script>

<template>
  <Modal
    :open="!!shareModal"
    :title="t('share.generate')"
    :footer="null"
    width="440px"
    @cancel="shareModal = null"
  >
    <div class="share-body">
      <p class="share-desc">
        {{ t('selection.selected', { n: photoCount }) }} ·
        {{ t('share.expire') }}
      </p>

      <Spin :spinning="shareLoading">
        <div class="share-row">
          <Input
            :value="shareUrl"
            readonly
            :placeholder="t('share.generating')"
            class="share-input"
          />
          <Button type="primary" :disabled="!shareUrl || shareLoading" @click="copyShareLink">
            <CopyOutlined />
            {{ t('share.copy') }}
          </Button>
        </div>
      </Spin>

      <p v-if="shareUrl" class="share-hint">
        <LinkOutlined />
        {{ t('share.expire') }}
      </p>
    </div>
  </Modal>
</template>

<style scoped>
.share-body {
  padding: 8px 0 4px;
}
.share-desc {
  font-size: 13px;
  color: var(--c-text);
  margin-bottom: 16px;
}
.share-row {
  display: flex;
  gap: 10px;
}
.share-input {
  flex: 1;
}
.share-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--c-text-dim);
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>

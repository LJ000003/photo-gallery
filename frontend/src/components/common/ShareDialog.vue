<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CopyOutlined, DeleteOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { Button, Input, Modal, Popconfirm, Spin } from 'ant-design-vue'
import { usePhotoActions } from '../../composables/usePhotoActions'

/**
 * 分享链接弹窗：生成结果展示 + 复制 + 撤销
 * 状态来自 usePhotoActions 单例（shareModal/shareUrl/shareLoading/shareRevoking）
 */
const { t } = useI18n()
const { shareModal, shareUrl, shareLoading, copyShareLink, shareRevoking, revokeShare } =
  usePhotoActions()

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

      <div v-if="shareUrl" class="share-revoke">
        <Popconfirm
          :title="t('share.revokeConfirm')"
          :ok-text="t('share.revokeOk')"
          :cancel-text="t('share.revokeCancel')"
          @confirm="revokeShare"
        >
          <Button danger size="small" :loading="shareRevoking" :disabled="shareLoading">
            <DeleteOutlined />
            {{ t('share.revoke') }}
          </Button>
        </Popconfirm>
      </div>
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
.share-revoke {
  margin-top: 16px;
  border-top: 1px solid var(--c-border);
  padding-top: 12px;
}
</style>

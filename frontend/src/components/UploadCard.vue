<script setup>
import { ref, nextTick } from 'vue';
import { animate } from 'animejs';

const emit = defineEmits(['uploaded']);

const fileInput = ref(null);
const preview = ref(null);
const fileLabel = ref(null);
const uploadName = ref('');
const uploadDesc = ref('');
const previewSrc = ref('');
const showPreview = ref(false);
const submitting = ref(false);

async function extractErrorMessage(res) {
  try {
    const data = await res.json();
    return data.message || `请求失败（${res.status}）`;
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`;
  }
}

function onFileChange(e) {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (ev) => {
    previewSrc.value = ev.target.result;
    showPreview.value = true;
    nextTick(() => {
      animate(preview.value, { scale: [0.85, 1], opacity: [0, 1], duration: 500, ease: 'outExpo' });
    });
  };
  reader.readAsDataURL(file);
}

async function onSubmit() {
  const file = fileInput.value.files[0];
  if (!file) return alert('请选择一张照片');

  const fd = new FormData();
  fd.append('file', file);
  fd.append('name', uploadName.value.trim());
  fd.append('description', uploadDesc.value.trim());

  submitting.value = true;
  try {
    const res = await fetch('/api/photos', { method: 'POST', body: fd });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    uploadName.value = '';
    uploadDesc.value = '';
    showPreview.value = false;
    if (fileInput.value) fileInput.value.value = '';
    animate('.upload-card', {
      borderColor: ['rgba(255,255,255,0.08)', 'rgba(0,212,255,0.6)', 'rgba(255,255,255,0.08)'],
      boxShadow: ['0 8px 32px rgba(0,0,0,0.3)', '0 8px 32px rgba(0,0,0,0.3), 0 0 30px rgba(0,212,255,0.4)', '0 8px 32px rgba(0,0,0,0.3)'],
      duration: 1000,
      ease: 'outExpo'
    });
    emit('uploaded');
  } catch (err) {
    alert('上传失败: ' + err.message);
    showPreview.value = false;
    previewSrc.value = '';
    if (fileInput.value) fileInput.value.value = '';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="upload-card">
    <h2>上传照片</h2>
    <form @submit.prevent="onSubmit">
      <div class="file-area">
        <input ref="fileInput" type="file" id="fileInput" accept="image/*" @change="onFileChange" />
        <label ref="fileLabel" for="fileInput" :class="{ 'preview-hidden': showPreview }">
          <span class="file-icon">+</span>
          <span>点击选择图片</span>
        </label>
        <img ref="preview" v-if="showPreview" :src="previewSrc" alt="预览" />
      </div>
      <div class="form-row">
        <input v-model="uploadName" type="text" placeholder="照片名称（可选）" />
        <input v-model="uploadDesc" type="text" maxlength="500" placeholder="照片描述（可选）" />
        <button type="submit" :disabled="submitting">上传</button>
      </div>
    </form>
  </section>
</template>

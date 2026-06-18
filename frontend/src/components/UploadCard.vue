<script setup>
import { ref, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import gsap from 'gsap';
import { useStore } from '../store.js';
import { useToastStore } from '../stores/toast.js';
import { api } from '../api.js';

const toast = useToastStore();
const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'));

const emit = defineEmits(['uploaded']);

const { tags: allTags, categories: allCats, loadAll } = useStore();

const fileInput = ref(null);
const preview = ref(null);
const fileLabel = ref(null);
const uploadName = ref('');
const uploadDesc = ref('');
const previewSrc = ref('');
const showPreview = ref(false);
const submitting = ref(false);
const selectedCount = ref(0);
const previews = ref([]); // { name, url }
const selectedTagIds = ref([]);
const selectedCatId = ref(null);
const dragOver = ref(false);

async function extractErrorMessage(res) {
  try {
    const data = await res.json();
    return data.message || `请求失败（${res.status}）`;
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`;
  }
}

function onFileChange(e) {
  const files = e.target.files;
  revokePreviews();
  selectedCount.value = files.length;
  if (files.length === 0) return;

  if (files.length === 1) {
    const reader = new FileReader();
    reader.onload = (ev) => {
      previewSrc.value = ev.target.result;
      showPreview.value = true;
      nextTick(() => {
        gsap.fromTo(preview.value,
          { scale: 0.85, opacity: 0 },
          { scale: 1, opacity: 1, duration: 0.5, ease: 'expo.out' }
        );
      });
    };
    reader.readAsDataURL(files[0]);
  } else {
    showPreview.value = false;
    previewSrc.value = '';
    previews.value = Array.from(files).map(f => ({
      name: f.name,
      url: URL.createObjectURL(f)
    }));
  }
}

function processFiles(files) {
  if (!files || files.length === 0) return;
  // 同步到 fileInput 以便 onSubmit 读取
  const dt = new DataTransfer();
  for (const f of files) dt.items.add(f);
  if (fileInput.value) fileInput.value.files = dt.files;
  onFileChange({ target: { files: dt.files } });
}

function onDragOver(e) {
  e.preventDefault();
  dragOver.value = true;
}
function onDragLeave() { dragOver.value = false; }
function onDrop(e) {
  e.preventDefault();
  dragOver.value = false;
  processFiles(e.dataTransfer.files);
}
function onPaste(e) {
  if (e.clipboardData.files.length > 0) {
    e.preventDefault();
    processFiles(e.clipboardData.files);
  }
}

onMounted(() => { document.addEventListener('paste', onPaste); });
onUnmounted(() => { document.removeEventListener('paste', onPaste); });

function revokePreviews() {
  for (const p of previews.value) URL.revokeObjectURL(p.url);
  previews.value = [];
}

function toggleTag(id) {
  const idx = selectedTagIds.value.indexOf(id);
  if (idx > -1) selectedTagIds.value.splice(idx, 1);
  else selectedTagIds.value.push(id);
}

function clearSelection() {
  showPreview.value = false;
  previewSrc.value = '';
  selectedCount.value = 0;
  revokePreviews();
  if (fileInput.value) fileInput.value.value = '';
}

async function onSubmit() {
  const files = fileInput.value.files;
  if (files.length === 0) { toast.error('请选择照片'); return; }

  const fd = new FormData();
  for (const f of files) {
    fd.append('files', f);
  }
  fd.append('name', uploadName.value.trim());
  fd.append('description', uploadDesc.value.trim());
  selectedTagIds.value.forEach(id => fd.append('tagIds', id));
  if (selectedCatId.value) fd.append('categoryId', selectedCatId.value);

  submitting.value = true;
  try {
    const endpoint = files.length > 1 ? '/api/photos/batch' : '/api/photos';
    if (files.length > 1) {
      const res = await api(endpoint, { method: 'POST', body: fd });
      if (!res.ok) {
        const msg = await extractErrorMessage(res);
        throw new Error(msg);
      }
    } else {
      const singleFd = new FormData();
      singleFd.append('file', files[0]);
      singleFd.append('name', uploadName.value.trim());
      singleFd.append('description', uploadDesc.value.trim());
      selectedTagIds.value.forEach(id => singleFd.append('tagIds', id));
      if (selectedCatId.value) singleFd.append('categoryId', selectedCatId.value);
      const res = await api('/api/photos', { method: 'POST', body: singleFd });
      if (!res.ok) {
        const msg = await extractErrorMessage(res);
        throw new Error(msg);
      }
    }
    uploadName.value = '';
    uploadDesc.value = '';
    showPreview.value = false;
    selectedCount.value = 0;
    revokePreviews();
    if (fileInput.value) fileInput.value.value = '';
    gsap.to('.upload-card', {
      keyframes: [
        { borderColor: 'rgba(255,255,255,0.08)', boxShadow: '0 8px 32px rgba(0,0,0,0.3)' },
        { borderColor: 'rgba(0,212,255,0.6)', boxShadow: '0 8px 32px rgba(0,0,0,0.3), 0 0 30px rgba(0,212,255,0.4)' },
        { borderColor: 'rgba(255,255,255,0.08)', boxShadow: '0 8px 32px rgba(0,0,0,0.3)' },
      ],
      duration: 1, ease: 'expo.out'
    });
    emit('uploaded');
  } catch (err) {
    toast.error('上传失败: ' + err.message);
    clearSelection();
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
        <input ref="fileInput" type="file" id="fileInput" accept="image/*" multiple @change="onFileChange" />
        <label ref="fileLabel" for="fileInput"
          :class="{ 'preview-hidden': showPreview, 'drag-over': dragOver }"
          @dragover="onDragOver" @dragleave="onDragLeave" @drop="onDrop">
          <span class="file-icon">+</span>
          <span>{{ selectedCount > 0 ? `已选 ${selectedCount} 张` : '点击选择 / 拖拽 / 粘贴 (Ctrl+V)' }}</span>
        </label>
        <img ref="preview" v-if="showPreview" :src="previewSrc" alt="预览" />
      </div>
      <div v-if="previews.length > 0" class="preview-grid">
        <div v-for="p in previews" :key="p.name" class="preview-item">
          <img :src="p.url" :alt="p.name" />
          <span>{{ p.name }}</span>
        </div>
      </div>
      <div class="meta-row">
        <select v-model="selectedCatId" class="mini-select">
          <option :value="null">无分类</option>
          <option v-for="c in allCats" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <div class="tag-chips">
          <button v-for="t in allTags" :key="t.id" type="button"
            class="tag-chip"
            :class="{ on: selectedTagIds.includes(t.id) }"
            :style="selectedTagIds.includes(t.id) ? { background: t.color, borderColor: t.color } : {}"
            @click="toggleTag(t.id)">
            {{ t.name }}
          </button>
        </div>
      </div>
      <div class="form-row">
        <input v-model="uploadName" type="text" placeholder="照片名称（可选）" />
        <input v-model="uploadDesc" type="text" maxlength="500" placeholder="照片描述（可选）" />
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
</template>

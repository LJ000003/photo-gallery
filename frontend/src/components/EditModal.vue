<script setup>
import { ref, onMounted } from 'vue';
import { animate } from 'animejs';

const props = defineProps({ photo: Object });
const emit = defineEmits(['close', 'saved']);

const editName = ref('');
const editDesc = ref('');

onMounted(() => {
  editName.value = props.photo.name;
  editDesc.value = props.photo.description || '';

  const content = document.querySelector('#editModal .modal-content');
  const backdrop = document.querySelector('#editModal .modal-backdrop');
  animate(content, { scale: [0.85, 1], opacity: [0, 1], duration: 350, ease: 'outExpo' });
  animate(backdrop, { opacity: [0, 1], duration: 350, ease: 'linear' });
});

function onClose() {
  const content = document.querySelector('#editModal .modal-content');
  const backdrop = document.querySelector('#editModal .modal-backdrop');
  animate(content, { scale: [1, 0.9], opacity: [1, 0], duration: 200, ease: 'in' });
  animate(backdrop, { opacity: [1, 0], duration: 200, ease: 'linear', onComplete: () => emit('close') });
}

async function extractErrorMessage(res) {
  try {
    const data = await res.json();
    return data.message || `请求失败（${res.status}）`;
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`;
  }
}

async function onSubmit() {
  if (!editName.value.trim()) return alert('请输入照片名称');

  try {
    const res = await fetch(`/api/photos/${props.photo.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: editName.value.trim(), description: editDesc.value.trim() })
    });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    emit('saved');
  } catch (err) {
    alert(err.message);
  }
}
</script>

<template>
  <div id="editModal" class="modal">
    <div class="modal-backdrop" @click="onClose"></div>
    <div class="modal-content modal-small">
      <button class="modal-close" @click="onClose">&times;</button>
      <h2>编辑照片信息</h2>
      <form @submit.prevent="onSubmit">
        <label>名称</label>
        <input v-model="editName" type="text" required />
        <label>描述</label>
        <input v-model="editDesc" type="text" maxlength="500" />
        <button type="submit">保存</button>
      </form>
    </div>
  </div>
</template>

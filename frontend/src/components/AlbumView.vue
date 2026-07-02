<script setup>
import { ref, onMounted, computed, nextTick, watch } from 'vue';
import gsap from 'gsap';
import { api } from '../api.js';
import { useToastStore } from '../stores/toast.js';
import { useConfirm } from '../useConfirm.js';
import AlbumEditModal from './AlbumEditModal.vue';

const emit = defineEmits(['view']);
const toast = useToastStore();
const confirmFn = useConfirm();

const albums = ref([]);
const loading = ref(true);
const selectedAlbum = ref(null);
const albumPhotos = ref([]);
const photoPage = ref(0);
const photoHasMore = ref(false);
const photoLoading = ref(false);
const editingAlbum = ref(null);

// 排序
const sortBy = ref('time');
const sortOrder = ref('desc');

function setSort(key) {
  if (sortBy.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortBy.value = key;
    sortOrder.value = 'asc';
  }
  if (selectedAlbum.value) loadAlbumPhotos(true);
}

async function loadAlbums() {
  loading.value = true;
  try {
    const res = await api('/api/albums');
    const data = await res.json();
    albums.value = data.data || [];
  } catch { albums.value = []; } finally { loading.value = false; }
}

const sortedAlbums = computed(() => {
  const list = [...albums.value];
  const dir = sortOrder.value === 'asc' ? 1 : -1;
  if (sortBy.value === 'name') {
    list.sort((a, b) => dir * a.name.localeCompare(b.name));
  } else {
    list.sort((a, b) => dir * new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  }
  return list;
});

function selectAlbum(album) {
  if (album === null) {
    selectedAlbum.value = { id: 0, name: '未分配', photoCount: 0 };
  } else {
    selectedAlbum.value = album;
  }
  loadAlbumPhotos(true);
}

function backToList() { selectedAlbum.value = null; albumPhotos.value = []; }

async function loadAlbumPhotos(reset) {
  if (reset) { photoPage.value = 0; albumPhotos.value = []; photoHasMore.value = true; }
  if (photoLoading.value || !photoHasMore.value) return;
  photoLoading.value = true;
  const fieldMap = { time: 'createdAt', name: 'name' };
  const sortStr = `${fieldMap[sortBy.value]},${sortOrder.value}`;
  try {
    const url = selectedAlbum.value.id === 0
      ? `/api/photos?albumId=0&page=${photoPage.value}&size=20&sort=${sortStr}`
      : `/api/albums/${selectedAlbum.value.id}/photos?page=${photoPage.value}&size=20&sort=${sortStr}`;
    const res = await api(url);
    const json = await res.json();
    const { content, last, totalElements } = json.data;
    if (content && content.length) albumPhotos.value.push(...content);
    photoPage.value++;
    photoHasMore.value = !last;
    if (!selectedAlbum.value.photoCount && totalElements !== undefined) {
      selectedAlbum.value.photoCount = totalElements;
    }
  } catch {} finally { photoLoading.value = false; }
}

function onScroll() {
  if (!photoHasMore.value || photoLoading.value || !selectedAlbum.value) return;
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200) {
    loadAlbumPhotos(false);
  }
}

function formatDate(d) {
  if (!d) return '';
  return d.substring(0, 10);
}

function openEdit(album) {
  editingAlbum.value = album;
}

async function deleteAlbum(album) {
  if (!await confirmFn(`确定删除相册「${album.name}」？照片不会被删除。`, '删除相册')) return;
  albums.value = albums.value.filter(a => a.id !== album.id);
  try {
    const res = await api(`/api/albums/${album.id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('删除失败');
    toast.success('已删除');
  } catch (err) {
    toast.error(err.message);
    loadAlbums();
  }
}

async function onCreateAlbum() {
  editingAlbum.value = { id: null, name: '', description: '' };
}

function onAlbumSaved() {
  editingAlbum.value = null;
  loadAlbums();
}

function onAlbumDeleted() {
  editingAlbum.value = null;
  loadAlbums();
}

watch(() => selectedAlbum.value, (v) => {
  if (v) {
    window.addEventListener('scroll', onScroll, { passive: true });
  } else {
    window.removeEventListener('scroll', onScroll);
  }
});

watch(albumPhotos, () => {
  nextTick(() => {
    if (albumPhotos.value.length <= 20) {
      gsap.fromTo('.photo-card', { y: 30, opacity: 0 }, { y: 0, opacity: 1, stagger: 0.04, duration: 0.5, ease: 'expo.out' });
    }
  });
});

function tokenParam() {
  const t = localStorage.getItem('jwt_token');
  return t ? `?token=${t}` : '';
}

function formatSize(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1048576).toFixed(1) + ' MB';
}

onMounted(loadAlbums);
</script>

<template>
  <div class="album-wrap">
    <!-- 相册列表 -->
    <template v-if="!selectedAlbum">
      <div class="gallery-toolbar">
        <div class="sort-switch">
          <span class="sort-label">排序方式：</span>
          <div class="sort-track sort-2cols">
            <div class="sort-slider" :style="{ transform: `translateX(${sortBy === 'time' ? 0 : 100}%)` }"></div>
            <button class="sort-opt" :class="{ active: sortBy === 'time' }" @click="setSort('time')">
              时间
              <span v-if="sortBy === 'time'" class="sort-arrows">
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: sortOrder === 'asc' }"></i>
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: sortOrder === 'desc' }"></i>
              </span>
            </button>
            <button class="sort-opt" :class="{ active: sortBy === 'name' }" @click="setSort('name')">
              名称
              <span v-if="sortBy === 'name'" class="sort-arrows">
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: sortOrder === 'asc' }"></i>
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: sortOrder === 'desc' }"></i>
              </span>
            </button>
          </div>
        </div>
        <button class="btn-create-album" @click="onCreateAlbum">+ 新建相册</button>
      </div>
      <div class="album-grid">
        <div class="album-card unassigned" @click="selectAlbum(null)">
          <div class="album-cover">
            <div class="album-cover-placeholder">+</div>
          </div>
          <div class="album-body">
            <h4>未分配</h4>
            <p class="album-meta">未归属任何相册的照片</p>
          </div>
        </div>
        <div v-for="a in sortedAlbums" :key="a.id" class="album-card" @click="selectAlbum(a)">
          <div class="album-cover">
            <img v-if="a.coverPhotoId" :src="`/api/photos/${a.coverPhotoId}/thumbnail${tokenParam()}`" loading="lazy" />
            <div v-else class="album-cover-placeholder">?</div>
          </div>
          <div class="album-body">
            <h4>{{ a.name }}</h4>
            <p class="album-meta">{{ a.photoCount }} 张<span v-if="a.createdAt"> · {{ formatDate(a.createdAt) }}</span></p>
            <div class="album-actions">
              <button class="btn-edit" @click.stop="openEdit(a)">编辑</button>
              <button class="btn-del" @click.stop="deleteAlbum(a)">删除</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="loading" class="album-loading">加载中...</div>
      <div v-if="!loading && albums.length === 0" class="album-empty">暂无相册，在编辑照片时可以将其分配到此处的相册</div>
    </template>

    <template v-else>
      <div class="album-detail-header">
        <button class="btn-back" @click="backToList">← 返回</button>
        <h3>{{ selectedAlbum.name }} <span class="album-count">({{ selectedAlbum.photoCount }})</span></h3>
        <div class="sort-switch">
          <span class="sort-label">排序方式：</span>
          <div class="sort-track sort-2cols">
            <div class="sort-slider" :style="{ transform: `translateX(${sortBy === 'time' ? 0 : 100}%)` }"></div>
            <button class="sort-opt" :class="{ active: sortBy === 'time' }" @click="setSort('time')">
              时间
              <span v-if="sortBy === 'time'" class="sort-arrows">
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: sortOrder === 'asc' }"></i>
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: sortOrder === 'desc' }"></i>
              </span>
            </button>
            <button class="sort-opt" :class="{ active: sortBy === 'name' }" @click="setSort('name')">
              名称
              <span v-if="sortBy === 'name'" class="sort-arrows">
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: sortOrder === 'asc' }"></i>
                <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: sortOrder === 'desc' }"></i>
              </span>
            </button>
          </div>
        </div>
      </div>
      <div class="gallery">
        <div v-for="p in albumPhotos" :key="p.id" class="photo-card" @click="emit('view', p)">
          <div class="photo-thumb">
            <img :src="`/api/photos/${p.id}/thumbnail${tokenParam()}`" :alt="p.name" loading="lazy" />
            <div class="photo-overlay"><button class="btn-view mini">查看</button></div>
          </div>
          <div class="photo-body">
            <h4 class="photo-name">{{ p.name }}</h4>
            <p class="photo-meta">{{ formatSize(p.fileSize) }}</p>
          </div>
        </div>
      </div>
      <div v-if="photoLoading" class="sentinel">加载中...</div>
      <div v-if="!photoHasMore && albumPhotos.length > 0" class="end-hint">没有更多了</div>
      <div v-if="!photoLoading && albumPhotos.length === 0 && selectedAlbum.id !== 0" class="empty-hint">此相册还没有照片</div>
    </template>

    <AlbumEditModal v-if="editingAlbum" :album="editingAlbum" @close="editingAlbum = null" @saved="onAlbumSaved" @deleted="onAlbumDeleted" />
  </div>
</template>

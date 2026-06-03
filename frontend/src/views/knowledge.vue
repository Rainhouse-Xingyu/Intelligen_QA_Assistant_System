<template>
  <div class="admin-shell">
    <AdminSidebar active="knowledge" />

    <main class="admin-main">
      <AdminTopbar title="知识库管理" />

      <section class="knowledge-grid">
        <div class="left-column">
          <div class="admin-card">
            <h3 class="section-title">
              <UploadIcon />
              上传文档
            </h3>
            <label class="drop-zone" @drop.prevent="onDrop" @dragover.prevent>
              <input ref="fileInput" type="file" hidden @change="onFileChange" />
              <UploadIcon class="big-upload" />
              <strong>{{ selectedFile ? selectedFile.name : '拖拽文件到此处' }}</strong>
              <span>支持 Word / Excel / PDF / TXT</span>
            </label>
            <select v-model="uploadModule" class="select">
              <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
            </select>
            <button class="btn primary full" :disabled="uploading" @click="uploadDocument">
              {{ uploading ? '上传中...' : '开始上传' }}
            </button>
          </div>

          <div class="admin-card">
            <h3 class="section-title">
              <FileIcon />
              解析记录
            </h3>
            <div v-if="documents.length === 0" class="empty">暂无解析记录</div>
            <div v-for="doc in documents" :key="doc.id" class="doc-item">
              <div>
                <strong>{{ doc.fileName }}</strong>
                <span>{{ formatTime(doc.createdAt) }}</span>
              </div>
              <span :class="['tag', statusClass(doc.processStatus)]">{{ processText(doc.processStatus) }}</span>
            </div>
          </div>
        </div>

        <div class="admin-card entries-panel">
          <div class="panel-head">
            <h3 class="section-title">
              <SearchIcon />
              问答词条 <small>共 {{ entries.length }} 条</small>
            </h3>
            <button class="btn gold" @click="openCreate">
              <PlusIcon />
              新增词条
            </button>
          </div>

          <div class="filters">
            <input v-model="filters.keyword" class="input" placeholder="搜索问题或答案..." @keyup.enter="loadEntries" />
            <select v-model="filters.moduleType" class="select" @change="loadEntries">
              <option value="">全部模块</option>
              <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
            </select>
            <select v-model="filters.status" class="select" @change="loadEntries">
              <option value="">全部状态</option>
              <option value="1">启用</option>
              <option value="0">禁用</option>
            </select>
            <select v-model="filters.sourceType" class="select" @change="loadEntries">
              <option value="">全部来源</option>
              <option value="manual">manual</option>
              <option value="document">document</option>
            </select>
          </div>

          <div class="entry-list">
            <article v-for="entry in entries" :key="entry.id" class="entry-card">
              <div class="entry-body">
                <h4>问：{{ entry.question }}</h4>
                <p>答：{{ entry.answer }}</p>
                <div class="tags">
                  <span class="tag blue">{{ entry.moduleType || '未分类' }}</span>
                  <span class="tag blue">{{ entry.sourceType || 'manual' }}</span>
                  <span :class="['tag', entry.status === 1 ? 'green' : 'red']">{{ entry.status === 1 ? '启用' : '禁用' }}</span>
                </div>
              </div>
              <div class="entry-actions">
                <button class="btn text" @click="openEdit(entry)">
                  <EditIcon />
                  编辑
                </button>
                <button class="btn text danger" @click="disableEntry(entry.id)">
                  <TrashIcon />
                  删除
                </button>
              </div>
            </article>
            <div v-if="entries.length === 0" class="empty">暂无词条</div>
          </div>
        </div>
      </section>
    </main>

    <div v-if="modalOpen" class="modal-mask" @click.self="modalOpen = false">
      <form class="entry-modal" @submit.prevent="saveEntry">
        <button type="button" class="modal-close" @click="modalOpen = false">×</button>
        <h3>{{ editingId ? '编辑词条' : '新增词条' }}</h3>
        <label>问题</label>
        <input v-model="form.question" class="input" autofocus />
        <label>答案</label>
        <textarea v-model="form.answer" class="textarea"></textarea>
        <div class="modal-row">
          <label>
            模块
            <select v-model="form.moduleType" class="select">
              <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
            </select>
          </label>
          <label>
            来源
            <select v-model="form.sourceType" class="select">
              <option value="manual">manual</option>
              <option value="document">document</option>
            </select>
          </label>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn ghost" @click="modalOpen = false">取消</button>
          <button type="submit" class="btn primary">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { apiDelete, apiGet, apiJson, apiUpload } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, EditIcon, FileIcon, PlusIcon, SearchIcon, TrashIcon, UploadIcon } from './shared/adminParts'

const modules = ['考务通知', '教学运行', '学业帮扶', '心理辅导']
const entries = ref([])
const documents = ref([])
const selectedFile = ref(null)
const fileInput = ref(null)
const uploadModule = ref('考务通知')
const uploading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref(null)

const filters = reactive({
  keyword: '',
  moduleType: '',
  status: '',
  sourceType: ''
})

const form = reactive({
  question: '',
  answer: '',
  moduleType: '考务通知',
  sourceType: 'manual',
  status: 1
})

async function loadEntries() {
  entries.value = await apiGet('/api/kb/entries', filters)
}

async function loadDocuments() {
  documents.value = await apiGet('/api/kb/documents')
}

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
}

function onDrop(event) {
  selectedFile.value = event.dataTransfer.files?.[0] || null
}

async function uploadDocument() {
  if (!selectedFile.value) {
    fileInput.value?.click()
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', selectedFile.value)
    data.append('moduleType', uploadModule.value)
    await apiUpload('/api/kb/upload', data)
    selectedFile.value = null
    await Promise.all([loadDocuments(), loadEntries()])
  } finally {
    uploading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { question: '', answer: '', moduleType: '考务通知', sourceType: 'manual', status: 1 })
  modalOpen.value = true
}

function openEdit(entry) {
  editingId.value = entry.id
  Object.assign(form, {
    question: entry.question,
    answer: entry.answer,
    moduleType: entry.moduleType || '考务通知',
    sourceType: entry.sourceType || 'manual',
    status: entry.status ?? 1
  })
  modalOpen.value = true
}

async function saveEntry() {
  saving.value = true
  try {
    const payload = { ...form, id: editingId.value || undefined }
    if (editingId.value) {
      await apiJson('/api/kb/entries', payload, 'PUT')
    } else {
      await apiJson('/api/kb/entries', payload)
    }
    modalOpen.value = false
    await loadEntries()
  } finally {
    saving.value = false
  }
}

async function disableEntry(id) {
  if (!confirm('确认禁用/删除该词条？')) return
  await apiDelete(`/api/kb/entries/${id}`)
  await loadEntries()
}

function processText(status) {
  return ({ 0: '待解析', 1: '解析中', 2: '成功', 3: '失败' })[status] || '未知'
}

function statusClass(status) {
  return status === 2 ? 'green' : status === 3 ? 'red' : 'yellow'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadEntries()
  loadDocuments()
})
</script>

<style scoped>
.knowledge-grid {
  display: grid;
  grid-template-columns: 430px 1fr;
  gap: 22px;
}

.left-column {
  display: grid;
  gap: 22px;
}

.drop-zone {
  height: 170px;
  border: 2px dashed #bdd6ff;
  border-radius: 16px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  margin-bottom: 16px;
  color: #173875;
  cursor: pointer;
}

.drop-zone span {
  font-weight: 800;
}

.big-upload {
  width: 44px;
  height: 44px;
}

.full {
  width: 100%;
  margin-top: 12px;
}

.doc-item {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  padding: 0 14px;
  margin-top: 12px;
  background: #fff;
}

.doc-item strong,
.doc-item span {
  display: block;
  font-weight: 900;
}

.doc-item div span {
  margin-top: 4px;
  color: #49659c;
  font-size: 14px;
}

.panel-head,
.filters {
  display: flex;
  gap: 12px;
  align-items: center;
}

.panel-head {
  justify-content: space-between;
}

.section-title small {
  font-size: 14px;
  color: #49659c;
}

.filters {
  display: grid;
  grid-template-columns: 1.2fr repeat(3, 1fr);
  margin-bottom: 16px;
}

.entry-list {
  display: grid;
  gap: 12px;
}

.entry-card {
  min-height: 104px;
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  padding: 18px 20px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
}

.entry-body h4,
.entry-body p {
  margin: 0 0 8px;
}

.entry-body h4 {
  font-size: 18px;
}

.entry-body p {
  font-weight: 800;
}

.tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.entry-actions {
  display: grid;
  align-content: center;
  gap: 14px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.72);
  z-index: 20;
}

.entry-modal {
  width: min(590px, calc(100vw - 36px));
  border-radius: 14px;
  background: #fff;
  color: #173875;
  padding: 30px;
  position: relative;
  display: grid;
  gap: 12px;
}

.entry-modal h3 {
  margin: 0;
  font-size: 24px;
}

.entry-modal label {
  font-weight: 900;
}

.modal-close {
  position: absolute;
  right: 18px;
  top: 14px;
  border: 0;
  background: none;
  color: #6e82b1;
  font-size: 30px;
  cursor: pointer;
}

.modal-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

@media (max-width: 1200px) {
  .knowledge-grid {
    grid-template-columns: 1fr;
  }
}
</style>


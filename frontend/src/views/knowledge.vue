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
            <div class="upload-mode-row">
              <button
                type="button"
                :class="['mode-toggle', { active: commonQuestionMode }]"
                @click="commonQuestionMode = !commonQuestionMode"
              >
                常见问题
              </button>
              <span>{{ commonQuestionMode ? '本次上传将写入常见问题和问答词条' : '未选择时按原知识库流程保存' }}</span>
            </div>
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

          <div class="admin-card documents-card">
            <h3 class="section-title">
              <FileIcon />
              解析记录
            </h3>
            <div class="doc-selection-bar">
              <label class="check-control">
                <input
                  type="checkbox"
                  :checked="allDocumentsSelected"
                  :disabled="documents.length === 0"
                  @change="toggleAllDocuments"
                />
                <span>全选</span>
              </label>
              <span class="selection-count">已选 {{ selectedDocumentIds.length }} 条</span>
              <button class="btn text danger compact" :disabled="selectedDocumentIds.length === 0 || documentDeleting" @click="deleteSelectedDocuments">
                <TrashIcon />
                {{ documentDeleting ? '删除中...' : '删除选中' }}
              </button>
            </div>
            <div class="doc-list">
              <div v-if="documents.length === 0" class="empty">暂无解析记录</div>
              <div v-for="doc in documents" :key="doc.id" :class="['doc-item', { selected: isDocumentSelected(doc.id) }]">
                <label class="doc-check">
                  <input type="checkbox" :checked="isDocumentSelected(doc.id)" @change="toggleDocumentSelection(doc.id)" />
                </label>
                <div>
                  <strong>{{ doc.fileName }}</strong>
                  <span>{{ formatTime(doc.createdAt) }}</span>
                </div>
                <span :class="['tag', statusClass(doc.processStatus)]">{{ processText(doc.processStatus) }}</span>
                <button
                  v-if="doc.processStatus === 3"
                  class="btn text compact retry-btn"
                  :disabled="retryingDocumentId === doc.id"
                  @click="retryDocument(doc)"
                >
                  {{ retryingDocumentId === doc.id ? '重试中...' : '重试' }}
                </button>
              </div>
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
              <option value="common_question">常见问题</option>
            </select>
          </div>

          <div class="selection-bar">
            <label class="check-control">
              <input
                type="checkbox"
                :checked="allVisibleSelected"
                :disabled="entries.length === 0"
                @change="toggleAllEntries"
              />
              <span>全选</span>
            </label>
            <span class="selection-count">已选 {{ selectedEntryIds.length }} 条</span>
            <button class="btn text danger" :disabled="selectedEntryIds.length === 0 || batchDeleting" @click="deleteSelectedEntries">
              <TrashIcon />
              {{ batchDeleting ? '删除中...' : '删除选中' }}
            </button>
          </div>

          <div class="entry-list">
            <article v-for="entry in entries" :key="entry.id" :class="['entry-card', { selected: isEntrySelected(entry.id) }]">
              <label class="entry-check">
                <input type="checkbox" :checked="isEntrySelected(entry.id)" @change="toggleEntrySelection(entry.id)" />
              </label>
              <div class="entry-body">
                <h4>问：{{ entry.question }}</h4>
                <p>答：{{ entry.answer }}</p>
                <div class="tags">
                  <span class="tag blue">{{ entry.moduleType || '未分类' }}</span>
                  <span :class="['tag', entry.sourceType === 'common_question' ? 'yellow' : 'blue']">{{ sourceTypeText(entry.sourceType) }}</span>
                  <span :class="['tag', entry.status === 1 ? 'green' : 'red']">{{ entry.status === 1 ? '启用' : '禁用' }}</span>
                </div>
              </div>
              <div class="entry-actions">
                <button class="btn text" @click="openEdit(entry)">
                  <EditIcon />
                  编辑
                </button>
                <button class="btn text danger" @click="deleteEntry(entry.id)">
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
              <option value="common_question">常见问题</option>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { apiDelete, apiGet, apiJson, apiUpload } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, EditIcon, FileIcon, PlusIcon, SearchIcon, TrashIcon, UploadIcon } from './shared/adminParts'

const modules = ['考务通知', '教学运行', '学业帮扶', '心理辅导']
const entries = ref([])
const documents = ref([])
const selectedFile = ref(null)
const fileInput = ref(null)
const uploadModule = ref('考务通知')
const commonQuestionMode = ref(false)
const uploading = ref(false)
const saving = ref(false)
const batchDeleting = ref(false)
const documentDeleting = ref(false)
const retryingDocumentId = ref(null)
const modalOpen = ref(false)
const editingId = ref(null)
const selectedEntryIds = ref([])
const selectedDocumentIds = ref([])

const visibleEntryIds = computed(() => entries.value.map((entry) => entry.id))
const allVisibleSelected = computed(() => {
  return visibleEntryIds.value.length > 0 && visibleEntryIds.value.every((id) => selectedEntryIds.value.includes(id))
})
const visibleDocumentIds = computed(() => documents.value.map((document) => document.id))
const allDocumentsSelected = computed(() => {
  return visibleDocumentIds.value.length > 0 && visibleDocumentIds.value.every((id) => selectedDocumentIds.value.includes(id))
})

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
  const visibleIds = new Set(visibleEntryIds.value)
  selectedEntryIds.value = selectedEntryIds.value.filter((id) => visibleIds.has(id))
}

async function loadDocuments() {
  documents.value = await apiGet('/api/kb/documents')
  const visibleIds = new Set(visibleDocumentIds.value)
  selectedDocumentIds.value = selectedDocumentIds.value.filter((id) => visibleIds.has(id))
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
    data.append('commonQuestion', commonQuestionMode.value ? 'true' : 'false')
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

function isEntrySelected(id) {
  return selectedEntryIds.value.includes(id)
}

function toggleEntrySelection(id) {
  if (isEntrySelected(id)) {
    selectedEntryIds.value = selectedEntryIds.value.filter((selectedId) => selectedId !== id)
  } else {
    selectedEntryIds.value = [...selectedEntryIds.value, id]
  }
}

function toggleAllEntries() {
  selectedEntryIds.value = allVisibleSelected.value ? [] : [...visibleEntryIds.value]
}

function isDocumentSelected(id) {
  return selectedDocumentIds.value.includes(id)
}

function toggleDocumentSelection(id) {
  if (isDocumentSelected(id)) {
    selectedDocumentIds.value = selectedDocumentIds.value.filter((selectedId) => selectedId !== id)
  } else {
    selectedDocumentIds.value = [...selectedDocumentIds.value, id]
  }
}

function toggleAllDocuments() {
  selectedDocumentIds.value = allDocumentsSelected.value ? [] : [...visibleDocumentIds.value]
}

async function deleteSelectedDocuments() {
  if (selectedDocumentIds.value.length === 0) return
  const message = `确认删除选中的 ${selectedDocumentIds.value.length} 条解析记录？该操作会同步删除这些文档生成的问答词条和向量数据。`
  if (!confirm(message)) return
  documentDeleting.value = true
  try {
    await apiJson('/api/kb/documents/batch-delete', selectedDocumentIds.value, 'POST')
    selectedDocumentIds.value = []
    await Promise.all([loadDocuments(), loadEntries()])
  } finally {
    documentDeleting.value = false
  }
}

async function retryDocument(doc) {
  if (!doc || doc.processStatus !== 3 || retryingDocumentId.value) return
  retryingDocumentId.value = doc.id
  try {
    const query = new URLSearchParams()
    if (uploadModule.value) query.append('moduleType', uploadModule.value)
    await apiJson(`/api/kb/documents/${doc.id}/retry${query.toString() ? `?${query}` : ''}`, {}, 'POST')
    await Promise.all([loadDocuments(), loadEntries()])
  } catch (error) {
    alert(error.message || '重新解析失败')
    await loadDocuments()
  } finally {
    retryingDocumentId.value = null
  }
}

async function deleteEntry(id) {
  if (!confirm('确认真实删除该词条？删除后会同步移除向量数据库中的数据。')) return
  await apiDelete(`/api/kb/entries/${id}`)
  selectedEntryIds.value = selectedEntryIds.value.filter((selectedId) => selectedId !== id)
  await loadEntries()
}

async function deleteSelectedEntries() {
  if (selectedEntryIds.value.length === 0) return
  if (!confirm(`确认真实删除选中的 ${selectedEntryIds.value.length} 条词条？删除后会同步移除向量数据库中的数据。`)) return
  batchDeleting.value = true
  try {
    await apiJson('/api/kb/entries/batch-delete', selectedEntryIds.value, 'POST')
    selectedEntryIds.value = []
    await loadEntries()
  } finally {
    batchDeleting.value = false
  }
}

function processText(status) {
  return ({ 0: '待解析', 1: '解析中', 2: '成功', 3: '失败' })[status] || '未知'
}

function statusClass(status) {
  return status === 2 ? 'green' : status === 3 ? 'red' : 'yellow'
}

function sourceTypeText(sourceType) {
  return ({
    common_question: '常见问题',
    manual: 'manual',
    document: 'document',
    Excel: 'Excel'
  })[sourceType] || sourceType || 'manual'
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
  grid-template-rows: auto minmax(440px, 1fr);
  gap: 22px;
  min-height: 0;
}

.documents-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.upload-mode-row {
  min-height: 42px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 0 12px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-mode-row span {
  color: #49659c;
  font-size: 14px;
  font-weight: 900;
}

.mode-toggle {
  min-height: 30px;
  border: 1px solid #d8e4f5;
  border-radius: 9px;
  background: #fff;
  color: #173875;
  padding: 0 14px;
  font-size: 14px;
  font-weight: 900;
  cursor: pointer;
}

.mode-toggle.active {
  border-color: #8ee7bd;
  background: #eafff3;
  color: #1aa56a;
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

.doc-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  margin-top: 12px;
}

.doc-list .empty {
  height: 100%;
  display: grid;
  place-items: center;
}

.doc-item {
  min-height: 70px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  padding: 0 14px;
  margin-bottom: 12px;
  background: #fff;
}

.doc-item:last-child {
  margin-bottom: 0;
}

.doc-item.selected {
  border-color: #5f8df4;
  background: #f7faff;
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

.doc-selection-bar {
  min-height: 42px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 0 12px;
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.compact {
  padding-inline: 0;
}

.retry-btn {
  min-width: 56px;
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

.selection-bar {
  min-height: 46px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 0 14px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.check-control,
.entry-check,
.doc-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #173875;
  font-weight: 900;
  cursor: pointer;
}

.check-control input,
.entry-check input,
.doc-check input {
  width: 18px;
  height: 18px;
  accent-color: #245edb;
}

.selection-count {
  color: #49659c;
  font-weight: 900;
  margin-right: auto;
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
  grid-template-columns: 24px 1fr auto;
  gap: 16px;
}

.entry-card.selected {
  border-color: #5f8df4;
  background: #f7faff;
}

.entry-check {
  align-self: start;
  padding-top: 2px;
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

  .left-column {
    grid-template-rows: auto 440px;
  }
}
</style>


<template>
  <div class="admin-shell">
    <AdminSidebar active="survey" />

    <main class="admin-main">
      <AdminTopbar title="问卷调查" />

      <section class="survey-grid">
        <div class="left-column">
          <div class="admin-card">
            <h3 class="section-title">
              <UploadIcon />
              上传问卷模板
            </h3>
            <label>
              问卷标题
              <input v-model="form.title" class="input" placeholder="如 25-26-2 学生学业情况调查问卷" />
            </label>
            <label>
              问卷说明
              <textarea v-model="form.description" class="textarea compact" placeholder="选填"></textarea>
            </label>
            <label class="drop-zone" @drop.prevent="onDrop" @dragover.prevent>
              <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onFileChange" />
              <UploadIcon class="big-upload" />
              <strong>{{ selectedFile ? selectedFile.name : '选择或拖拽 Excel 模板' }}</strong>
              <span>当前规则：跳过前 4 列，从第 5 列读取题目</span>
            </label>
            <button class="btn primary full" :disabled="uploading" @click="uploadTemplate">
              {{ uploading ? '导入中...' : '导入为草稿' }}
            </button>
          </div>

          <div class="admin-card">
            <h3 class="section-title">
              <FileIcon />
              问卷列表
            </h3>
            <div v-if="surveys.length === 0" class="empty">暂无问卷</div>
            <article
              v-for="survey in surveys"
              :key="survey.id"
              :class="['survey-item', { active: selectedId === survey.id }]"
              @click="selectSurvey(survey.id)"
            >
              <div>
                <strong>{{ survey.title }}</strong>
                <span>{{ formatTime(survey.publishedAt || survey.createdAt) }}</span>
              </div>
              <span :class="['tag', statusClass(survey.status)]">{{ statusText(survey.status) }}</span>
            </article>
          </div>
        </div>

        <div class="admin-card detail-panel">
          <div class="panel-head">
            <h3 class="section-title">
              <FileIcon />
              {{ detail?.survey?.title || '问卷详情' }}
            </h3>
            <div v-if="detail?.survey" class="toolbar">
              <button v-if="detail.survey.status !== 1" class="btn gold" :disabled="acting" @click="publishSurvey">
                <CheckIcon />
                发布
              </button>
              <button v-if="detail.survey.status === 1" class="btn ghost" :disabled="acting" @click="closeSurvey">
                关闭
              </button>
            </div>
          </div>

          <div v-if="!detail" class="empty">选择左侧问卷查看详情</div>
          <template v-else>
            <div class="summary-line">
              <span class="tag blue">题目 {{ detail.questions?.length || 0 }} 道</span>
              <span class="tag green">提交 {{ submissions.length }} 份</span>
              <span class="tag yellow">范围：全校学生</span>
            </div>

            <section class="question-preview">
              <h4>题目预览</h4>
              <ol>
                <li v-for="question in detail.questions" :key="question.id">
                  <span>{{ question.questionText }}</span>
                  <em>{{ question.questionType === 2 ? '文本题 · 选填' : '量表题 · 必填' }}</em>
                </li>
              </ol>
            </section>

            <section class="submission-panel">
              <div class="panel-head small">
                <h4>提交记录</h4>
                <button class="btn text" @click="loadSubmissions">刷新</button>
              </div>
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>学生</th>
                    <th>账号</th>
                    <th>提交时间</th>
                    <th>答案数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in submissions" :key="item.id">
                    <td>{{ item.realName || '-' }}</td>
                    <td>{{ item.username || item.userId }}</td>
                    <td>{{ formatTime(item.submitTime) }}</td>
                    <td>{{ item.answers?.length || 0 }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="submissions.length === 0" class="empty slim">暂无提交记录</div>
            </section>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { apiForm, apiGet, apiUpload } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, CheckIcon, FileIcon, UploadIcon } from './shared/adminParts'

const surveys = ref([])
const detail = ref(null)
const submissions = ref([])
const selectedId = ref(null)
const selectedFile = ref(null)
const fileInput = ref(null)
const uploading = ref(false)
const acting = ref(false)

const form = reactive({
  title: '',
  description: ''
})

async function loadSurveys() {
  surveys.value = await apiGet('/api/survey/admin/list')
  if (!selectedId.value && surveys.value.length > 0) {
    await selectSurvey(surveys.value[0].id)
  }
}

async function selectSurvey(id) {
  selectedId.value = id
  detail.value = await apiGet(`/api/survey/admin/${id}`)
  await loadSubmissions()
}

async function loadSubmissions() {
  if (!selectedId.value) return
  submissions.value = await apiGet(`/api/survey/admin/${selectedId.value}/submissions`)
}

function onFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  if (!form.title && selectedFile.value?.name) {
    form.title = selectedFile.value.name.replace(/\.(xlsx|xls)$/i, '')
  }
}

function onDrop(event) {
  selectedFile.value = event.dataTransfer.files?.[0] || null
  if (!form.title && selectedFile.value?.name) {
    form.title = selectedFile.value.name.replace(/\.(xlsx|xls)$/i, '')
  }
}

async function uploadTemplate() {
  if (!selectedFile.value) {
    fileInput.value?.click()
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', selectedFile.value)
    if (form.title) data.append('title', form.title)
    if (form.description) data.append('description', form.description)
    const survey = await apiUpload('/api/survey/admin/import', data)
    selectedFile.value = null
    form.title = ''
    form.description = ''
    await loadSurveys()
    await selectSurvey(survey.id)
  } finally {
    uploading.value = false
  }
}

async function publishSurvey() {
  if (!selectedId.value) return
  acting.value = true
  try {
    await apiForm(`/api/survey/admin/${selectedId.value}/publish`)
    await loadSurveys()
    await selectSurvey(selectedId.value)
  } finally {
    acting.value = false
  }
}

async function closeSurvey() {
  if (!selectedId.value) return
  acting.value = true
  try {
    await apiForm(`/api/survey/admin/${selectedId.value}/close`)
    await loadSurveys()
    await selectSurvey(selectedId.value)
  } finally {
    acting.value = false
  }
}

function statusText(status) {
  return ({ 0: '草稿', 1: '已发布', 2: '已关闭' })[status] || '未知'
}

function statusClass(status) {
  if (status === 1) return 'green'
  if (status === 2) return 'red'
  return 'yellow'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadSurveys)
</script>

<style scoped>
.survey-grid {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 22px;
}

.left-column {
  display: grid;
  gap: 22px;
  align-content: start;
}

label {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  font-weight: 900;
}

.compact {
  min-height: 82px;
}

.drop-zone {
  min-height: 156px;
  border: 2px dashed #bdd6ff;
  border-radius: 16px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: #173875;
  text-align: center;
  cursor: pointer;
}

.drop-zone span {
  color: #8293bb;
  font-size: 14px;
}

.big-upload {
  width: 38px;
  height: 38px;
  color: #315dbc;
}

.full {
  width: 100%;
}

.survey-item {
  min-height: 78px;
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  margin-top: 12px;
}

.survey-item.active,
.survey-item:hover {
  border-color: #9ec6ff;
  box-shadow: 0 8px 18px rgba(49, 93, 188, 0.12);
}

.survey-item strong,
.survey-item span {
  display: block;
}

.survey-item strong {
  font-weight: 900;
}

.survey-item div > span {
  margin-top: 6px;
  color: #8293bb;
  font-weight: 800;
  font-size: 13px;
}

.detail-panel {
  min-width: 0;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
}

.panel-head.small {
  margin-top: 24px;
}

.panel-head.small h4,
.question-preview h4 {
  margin: 0;
  color: #173875;
  font-size: 18px;
}

.summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.question-preview {
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  padding: 18px;
  max-height: 420px;
  overflow: auto;
}

.question-preview ol {
  margin: 14px 0 0;
  padding-left: 24px;
}

.question-preview li {
  margin: 12px 0;
  font-weight: 850;
  line-height: 1.6;
}

.question-preview em {
  margin-left: 8px;
  color: #8293bb;
  font-style: normal;
  white-space: nowrap;
}

.slim {
  min-height: 70px;
}

@media (max-width: 1100px) {
  .survey-grid {
    grid-template-columns: 1fr;
  }
}
</style>

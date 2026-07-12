<template>
  <div class="admin-shell">
    <AdminSidebar active="survey" />

    <main class="admin-main">
      <AdminTopbar title="问卷调查" />

      <section v-if="selectedSubmission" class="submission-detail-page">
        <div class="admin-card submission-detail-card">
          <div class="answer-head">
            <div>
              <button class="btn ghost" type="button" @click="backToSurveyDetail">返回问卷详情</button>
              <h2>{{ selectedSubmission.realName || selectedSubmission.username || '学生' }} 的回答详情</h2>
              <p>
                账号：{{ selectedSubmission.username || selectedSubmission.userId }}
                · 提交时间：{{ formatTime(displayedSubmission.submitTime) }}
                · 学期：{{ termLabel(selectedTermKey) }}
              </p>
            </div>
            <span class="status-pill done">已提交</span>
          </div>

          <section class="trend-panel top-trend-panel">
            <div class="panel-head small">
              <h4>{{ submissionDisplayName }}历年趋势</h4>
              <button class="btn text" type="button" @click="loadSelectedTrend">刷新</button>
            </div>
            <div v-if="trendLoading" class="empty slim">趋势加载中...</div>
            <div v-else-if="!studentTrend.series?.length" class="empty slim">暂无可绘制的量表趋势</div>
            <SurveyTrendChart
              v-else
              :trend="studentTrend"
              :initial-term-key="selectedTermKey"
              :title="`${submissionDisplayName}历年趋势`"
              @term-change="onTermChange"
            />
          </section>

          <section class="admin-answer-list">
            <div v-if="studentSubmissionsLoading" class="empty slim">正在加载该学期的回答...</div>
            <div v-else-if="!displayedAnswers.length" class="empty slim">该学期暂无回答详情</div>
            <article
              v-for="answer in displayedAnswers"
              :key="answer.questionId"
              class="admin-answer-item"
            >
              <div class="question-title">
                <b>{{ answer.questionNo }}</b>
                <span>{{ answer.questionText }}</span>
                <em>{{ answer.questionType === 2 ? '文本题' : '量表题' }}</em>
              </div>
              <div class="admin-answer-value">{{ formatAnswerValue(answer) }}</div>
            </article>
          </section>
        </div>
      </section>

      <section v-else class="survey-grid">
        <div class="left-column">
          <div class="admin-card">
            <h3 class="section-title">
              <UploadIcon />
              上传问卷模板
            </h3>
            <label>
              模板名称
              <input v-model="templateForm.name" class="input" placeholder="如 学业状态跟踪模板" />
            </label>
            <label>
              模板说明
              <textarea v-model="templateForm.description" class="textarea compact" placeholder="选填"></textarea>
            </label>
            <label class="drop-zone" @drop.prevent="onTemplateDrop" @dragover.prevent>
              <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onTemplateFileChange" />
              <UploadIcon class="big-upload" />
              <strong>{{ selectedFile ? selectedFile.name : '选择或拖拽 Excel 模板' }}</strong>
              <span>导入后可在发布任务时选择该模板</span>
            </label>
            <button class="btn primary full" :disabled="uploading" @click="uploadTemplate">
              {{ uploading ? '上传中...' : '上传为模板' }}
            </button>
          </div>

          <div class="admin-card">
            <h3 class="section-title">
              <CheckIcon />
              发布问卷任务
            </h3>
            <label>
              选择模板
              <select v-model.number="taskForm.templateId" class="input">
                <option :value="null">请选择模板</option>
                <option v-for="template in templates" :key="template.id" :value="template.id">
                  {{ template.name }}
                </option>
              </select>
            </label>
            <label>
              任务标题
              <input v-model="taskForm.title" class="input" placeholder="如 2026 学生学业情况调查" />
            </label>
            <label>
              科目
              <input v-model="taskForm.subject" class="input" placeholder="如 高等数学、大学英语、心理健康" />
            </label>
            <label>
              问卷目的
              <textarea v-model="taskForm.purpose" class="textarea compact" placeholder="说明为什么发布这份问卷"></textarea>
            </label>
            <label>
              详细信息
              <textarea v-model="taskForm.description" class="textarea compact" placeholder="学生填写前看到的说明"></textarea>
            </label>
            <div class="two-cols">
              <label>
                覆盖范围
                <select v-model="taskForm.scopeType" class="input">
                  <option value="ALL">全校学生</option>
                  <option value="COLLEGE">指定学院</option>
                </select>
              </label>
              <label v-if="taskForm.scopeType === 'COLLEGE'">
                选择学院
                <select v-model="taskForm.scopeText" class="input">
                  <option value="">请选择学院</option>
                  <option v-for="college in colleges" :key="college" :value="college">{{ college }}</option>
                </select>
              </label>
              <label v-else>
                范围说明
                <input class="input" value="全校" disabled />
              </label>
            </div>
            <div class="two-cols">
              <label>
                学年
                <select v-model.number="taskForm.academicYear" class="input">
                  <option v-for="year in academicYears" :key="year" :value="year">
                    {{ year }}-{{ String(year + 1).slice(2) }} 学年
                  </option>
                </select>
              </label>
              <label>
                学期
                <select v-model.number="taskForm.termNo" class="input">
                  <option :value="1">第 1 学期</option>
                  <option :value="2">第 2 学期</option>
                  <option :value="3">第 3 学期</option>
                </select>
              </label>
            </div>
            <div class="two-cols">
              <label>
                开始时间
                <input v-model="taskForm.startTime" class="input" type="datetime-local" />
              </label>
              <label>
                结束时间
                <input v-model="taskForm.endTime" class="input" type="datetime-local" />
              </label>
            </div>
            <label class="check-row">
              <input v-model="taskForm.publishNow" type="checkbox" />
              创建后立即发布
            </label>
            <button class="btn gold full" :disabled="creating" @click="createTask">
              {{ creating ? '创建中...' : '创建问卷任务' }}
            </button>
          </div>
        </div>

        <div class="admin-card detail-panel">
          <div class="panel-head">
            <h3 class="section-title">
              <FileIcon />
              {{ detail?.survey?.title || '问卷任务' }}
            </h3>
            <div v-if="detail?.survey" class="toolbar">
              <button v-if="detail.survey.status !== 1" class="btn gold" :disabled="acting" @click="publishSurvey">
                <CheckIcon />
                发布
              </button>
              <button v-if="detail.survey.status === 1" class="btn ghost" :disabled="acting" @click="closeSurvey">
                关闭
              </button>
              <button class="btn danger" :disabled="acting" @click="deleteCurrentSurvey">
                删除问卷
              </button>
            </div>
          </div>

          <div class="survey-list-inline">
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
            <div v-if="surveys.length === 0" class="empty slim">暂无问卷任务</div>
          </div>

          <div v-if="!detail" class="empty">选择一份问卷查看详情</div>
          <template v-else>
            <div class="summary-line">
              <span class="tag blue">题目 {{ detail.questions?.length || 0 }} 道</span>
              <span class="tag green">提交 {{ submissions.length }} 份</span>
              <span class="tag yellow">范围：{{ detail.survey.scopeText || scopeText(detail.survey.scopeType) }}</span>
              <span class="tag blue">学年：{{ formatAcademicYear(detail.survey.academicYear) }}</span>
              <span class="tag red">学期：第 {{ detail.survey.termNo || '-' }} 学期</span>
            </div>

            <section class="task-info">
              <div class="info-item">
                <b>问卷名称</b>
                <span>{{ detail.survey.title || '-' }}</span>
              </div>
              <div class="info-item">
                <b>科目</b>
                <span>{{ detail.survey.subject || '-' }}</span>
              </div>
              <div class="info-item">
                <b>使用模板</b>
                <span>{{ templateName(detail.survey.templateId) }}</span>
              </div>
              <div class="info-item">
                <b>学年学期</b>
                <span>{{ formatAcademicYear(detail.survey.academicYear) }} · 第 {{ detail.survey.termNo || '-' }} 学期</span>
              </div>
              <div class="info-item">
                <b>覆盖范围</b>
                <span>{{ detail.survey.scopeText || scopeText(detail.survey.scopeType) }}</span>
              </div>
              <div class="info-item wide">
                <b>问卷目的</b>
                <span>{{ detail.survey.purpose || '-' }}</span>
              </div>
              <div class="info-item wide">
                <b>详细信息</b>
                <span>{{ detail.survey.description || '-' }}</span>
              </div>
            </section>

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
                <div class="toolbar">
                  <button class="btn text" @click="loadSubmissions">刷新</button>
                  <button class="btn text" :disabled="exporting || submissions.length === 0" @click="exportSubmissions">
                    {{ exporting ? '导出中...' : '导出 Excel' }}
                  </button>
                </div>
              </div>
              <table class="admin-table">
                <thead>
                  <tr>
                    <th>学生</th>
                    <th>账号</th>
                    <th>提交时间</th>
                    <th>答案数</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in submissions" :key="item.id">
                    <td>{{ item.realName || '-' }}</td>
                    <td>{{ item.username || item.userId }}</td>
                    <td>{{ formatTime(item.submitTime) }}</td>
                    <td>{{ item.answers?.length || 0 }}</td>
                    <td>
                      <button class="btn text detail-link" type="button" @click="selectSubmission(item)">回答详情</button>
                      <button class="btn text danger-link" type="button" @click="deleteSubmission(item)">删除</button>
                    </td>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { apiDelete, apiDownload, apiForm, apiGet, apiJson, apiUpload } from '../js/adminApi'
import '../css/admin.css'
import SurveyTrendChart from '../components/SurveyTrendChart.vue'
import { AdminSidebar, AdminTopbar, CheckIcon, FileIcon, UploadIcon } from './shared/adminParts'

const templates = ref([])
const surveys = ref([])
const detail = ref(null)
const submissions = ref([])
const selectedSubmission = ref(null)
const studentSubmissions = ref([])
const selectedId = ref(null)
const selectedFile = ref(null)
const fileInput = ref(null)
const studentTrend = ref({ series: [] })
const uploading = ref(false)
const creating = ref(false)
const acting = ref(false)
const trendLoading = ref(false)
const studentSubmissionsLoading = ref(false)
const exporting = ref(false)
const selectedTermKey = ref('')

const submissionDisplayName = computed(() => (
  selectedSubmission.value?.realName
  || selectedSubmission.value?.username
  || selectedSubmission.value?.userId
  || '学生'
))

const displayedSubmission = computed(() => {
  const sameTerm = studentSubmissions.value.filter(item => item.termKey === selectedTermKey.value)
  return sameTerm.find(item => item.surveyId === selectedSubmission.value?.surveyId)
    || sameTerm[0]
    || selectedSubmission.value
})

const displayedAnswers = computed(() => displayedSubmission.value?.answers || [])

const templateForm = reactive({
  name: '',
  description: ''
})

const academicYears = Array.from({ length: 4 }, (_, index) => new Date().getFullYear() - 2 + index)
const colleges = [
  '人工智能学院',
  '软件学院',
  '信息与商务管理学院',
  '智能与电子工程学院',
  '数字艺术与设计学院',
  '外国语学院',
  '健康医疗科技学院',
  '应用技术学院',
  '创新创业学院',
  '基础教学学院',
  '马克思主义学院',
  '国际教育学院',
  '继续教育学院'
]

const taskForm = reactive({
  templateId: null,
  title: '',
  subject: '',
  purpose: '',
  description: '',
  scopeType: 'ALL',
  scopeText: '',
  academicYear: currentAcademicYear(),
  termNo: currentTermNo(),
  startTime: '',
  endTime: '',
  publishNow: true
})

async function loadTemplates() {
  templates.value = await apiGet('/api/survey/admin/templates')
  if (!taskForm.templateId && templates.value.length) {
    taskForm.templateId = templates.value[0].id
  }
}

async function loadSurveys() {
  surveys.value = await apiGet('/api/survey/admin/list')
  if (!selectedId.value && surveys.value.length > 0) {
    await selectSurvey(surveys.value[0].id)
  }
}

async function selectSurvey(id) {
  selectedId.value = id
  selectedSubmission.value = null
  studentSubmissions.value = []
  selectedTermKey.value = ''
  studentTrend.value = { series: [] }
  detail.value = await apiGet(`/api/survey/admin/${id}`)
  await loadSubmissions()
}

async function loadSubmissions() {
  if (!selectedId.value) return
  submissions.value = await apiGet(`/api/survey/admin/${selectedId.value}/submissions`)
  if (selectedSubmission.value && !submissions.value.some(item => item.id === selectedSubmission.value.id)) {
    selectedSubmission.value = null
  }
}

async function selectSubmission(item) {
  selectedSubmission.value = item
  studentSubmissions.value = []
  selectedTermKey.value = surveyTermKey(findSurvey(item.surveyId))
  await Promise.all([loadSelectedTrend(), loadStudentSubmissions()])
}

function backToSurveyDetail() {
  selectedSubmission.value = null
  studentSubmissions.value = []
  selectedTermKey.value = ''
}

async function loadStudentSubmissions() {
  const userId = selectedSubmission.value?.userId
  if (!userId || !surveys.value.length) return
  studentSubmissionsLoading.value = true
  try {
    const grouped = await Promise.all(surveys.value.map(async survey => {
      const records = await apiGet(`/api/survey/admin/${survey.id}/submissions`)
      return (Array.isArray(records) ? records : [])
        .filter(item => String(item.userId) === String(userId))
        .map(item => ({
          ...item,
          surveyId: survey.id,
          surveyTitle: survey.title,
          academicYear: survey.academicYear,
          termNo: survey.termNo,
          termKey: surveyTermKey(survey)
        }))
    }))
    studentSubmissions.value = grouped.flat().sort((a, b) => {
      const yearDiff = Number(b.academicYear || 0) - Number(a.academicYear || 0)
      return yearDiff || Number(b.termNo || 0) - Number(a.termNo || 0)
    })
  } finally {
    studentSubmissionsLoading.value = false
  }
}

function onTermChange(termKey) {
  selectedTermKey.value = termKey || ''
}

async function loadSelectedTrend() {
  if (!selectedSubmission.value?.userId) return
  trendLoading.value = true
  try {
    studentTrend.value = await apiGet(`/api/survey/admin/students/${selectedSubmission.value.userId}/trends`)
  } finally {
    trendLoading.value = false
  }
}

function onTemplateFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
  if (!templateForm.name && selectedFile.value?.name) {
    templateForm.name = selectedFile.value.name.replace(/\.(xlsx|xls)$/i, '')
  }
}

function onTemplateDrop(event) {
  selectedFile.value = event.dataTransfer.files?.[0] || null
  if (!templateForm.name && selectedFile.value?.name) {
    templateForm.name = selectedFile.value.name.replace(/\.(xlsx|xls)$/i, '')
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
    if (templateForm.name) data.append('name', templateForm.name)
    if (templateForm.description) data.append('description', templateForm.description)
    const template = await apiUpload('/api/survey/admin/templates/import', data)
    selectedFile.value = null
    templateForm.name = ''
    templateForm.description = ''
    await loadTemplates()
    taskForm.templateId = template.id
  } finally {
    uploading.value = false
  }
}

async function createTask() {
  creating.value = true
  try {
    const payload = {
      ...taskForm,
      scopeText: taskForm.scopeType === 'ALL' ? '全校' : taskForm.scopeText,
      startTime: toApiDateTime(taskForm.startTime),
      endTime: toApiDateTime(taskForm.endTime)
    }
    const survey = await apiJson('/api/survey/admin/tasks', payload)
    resetTaskForm()
    await loadSurveys()
    await selectSurvey(survey.id)
  } finally {
    creating.value = false
  }
}

function resetTaskForm() {
  taskForm.title = ''
  taskForm.subject = ''
  taskForm.purpose = ''
  taskForm.description = ''
  taskForm.scopeType = 'ALL'
  taskForm.scopeText = ''
  taskForm.academicYear = currentAcademicYear()
  taskForm.termNo = currentTermNo()
  taskForm.startTime = ''
  taskForm.endTime = ''
  taskForm.publishNow = true
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

async function deleteCurrentSurvey() {
  if (!selectedId.value || !detail.value?.survey) return
  const confirmed = window.confirm(`确定删除问卷“${detail.value.survey.title}”吗？该问卷的题目、提交记录和答案都会被删除。`)
  if (!confirmed) return
  acting.value = true
  try {
    await apiDelete(`/api/survey/admin/${selectedId.value}`)
    selectedId.value = null
    detail.value = null
    submissions.value = []
    selectedSubmission.value = null
    await loadSurveys()
  } finally {
    acting.value = false
  }
}

async function deleteSubmission(item) {
  if (!item?.id) return
  const name = item.realName || item.username || item.userId
  const confirmed = window.confirm(`确定删除 ${name} 的这条提交记录吗？`)
  if (!confirmed) return
  await apiDelete(`/api/survey/admin/submissions/${item.id}`)
  if (selectedSubmission.value?.id === item.id) {
    selectedSubmission.value = null
  }
  await loadSubmissions()
}

async function exportSubmissions() {
  if (!selectedId.value || !detail.value?.survey) return
  exporting.value = true
  try {
    const title = String(detail.value.survey.title || `survey-${selectedId.value}`).replace(/[\\/:*?"<>|]/g, '_')
    await apiDownload(`/api/survey/admin/${selectedId.value}/submissions/export`, `${title}-提交记录.xlsx`)
  } finally {
    exporting.value = false
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

function scopeText(scopeType) {
  if (scopeType === 'COLLEGE') return '指定学院'
  return '全校学生'
}

function templateName(templateId) {
  const template = templates.value.find(item => item.id === templateId)
  return template?.name || '-'
}

const scaleAnswerLabels = {
  1: '完全符合',
  2: '比较符合',
  3: '一般符合',
  4: '比较不符合',
  5: '不符合'
}

const supportFrequencyLabels = {
  1: '每月1次',
  2: '每两周1次',
  3: '每周1次',
  4: '每周2次',
  5: '不需要对我进行帮扶'
}

function formatAnswerValue(answer) {
  if (!answer) return '-'
  if (answer.questionType === 2) {
    return answer.textAnswer || '未填写'
  }
  const labels = (answer.questionText || '').includes('帮扶频率')
    ? supportFrequencyLabels
    : scaleAnswerLabels
  const label = labels[answer.numericAnswer] || '未选择'
  return `${answer.numericAnswer || '-'} - ${label}`
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function findSurvey(surveyId) {
  return surveys.value.find(item => String(item.id) === String(surveyId)) || null
}

function surveyTermKey(survey) {
  if (!survey?.academicYear || !survey?.termNo) return ''
  return `${survey.academicYear}-${survey.termNo}`
}

function termLabel(termKey) {
  const survey = surveys.value.find(item => surveyTermKey(item) === termKey)
  return survey
    ? `${formatAcademicYear(survey.academicYear)} · 第 ${survey.termNo} 学期`
    : '-'
}

function currentAcademicYear() {
  const now = new Date()
  const year = now.getFullYear()
  return now.getMonth() + 1 >= 9 ? year : year - 1
}

function currentTermNo() {
  const month = new Date().getMonth() + 1
  if (month >= 2 && month <= 6) return 2
  if (month >= 7 && month <= 8) return 3
  return 1
}

function formatAcademicYear(year) {
  return year ? `${year}-${String(year + 1).slice(2)} 学年` : '-'
}

function toApiDateTime(value) {
  return value ? `${value}:00` : null
}

onMounted(async () => {
  await loadTemplates()
  await loadSurveys()
})
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

.two-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}
.two-cols label {
  min-width: 0;
}
.two-cols input[type="datetime-local"] {
  min-width: 0;
  font-size: 14px;
  padding: 0 10px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drop-zone {
  min-height: 146px;
  border: 2px dashed #bdd6ff;
  border-radius: 8px;
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
  margin: 24px 0 12px;
}

.panel-head.small h4,
.question-preview h4 {
  margin: 0;
  color: #173875;
  font-size: 18px;
}

.survey-list-inline {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 20px;
  max-height: 240px;
  overflow: auto;
}

.survey-item {
  min-height: 78px;
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #fff;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
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

.summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.task-info {
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #f8fbff;
  padding: 14px 16px;
  margin-bottom: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-item {
  min-height: 58px;
  border: 1px solid #e3edf9;
  border-radius: 8px;
  background: #fff;
  padding: 10px 12px;
  display: grid;
  gap: 5px;
  align-content: center;
}

.info-item.wide {
  grid-column: 1 / -1;
}

.info-item b {
  color: #173875;
  font-size: 13px;
  font-weight: 900;
}

.info-item span {
  color: #244a7f;
  font-weight: 850;
  line-height: 1.55;
  white-space: pre-wrap;
}

.question-preview {
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
  max-height: 360px;
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

.detail-link {
  color: #315dbc;
  font-weight: 900;
}

.danger-link {
  color: #d93c58;
  font-weight: 900;
}

.danger-link:hover {
  color: #a31330;
}

.submission-detail-card {
  min-height: calc(100vh - 162px);
  display: flex;
  flex-direction: column;
}

.answer-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
  margin-bottom: 20px;
}

.answer-head h2 {
  margin: 16px 0 0;
  color: #173875;
  font-size: 28px;
}

.answer-head p {
  margin: 8px 0 0;
  color: #65799f;
  font-weight: 800;
}

.status-pill {
  min-width: 76px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  display: inline-grid;
  place-items: center;
  font-weight: 900;
}

.status-pill.done {
  color: #1aa56a;
  background: #eafff3;
}

.admin-answer-list {
  display: grid;
  gap: 16px;
  overflow: auto;
  padding-right: 4px;
}

.admin-answer-item {
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.question-title {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 10px;
  align-items: start;
  color: #173875;
  font-weight: 900;
  line-height: 1.65;
}

.question-title b {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: #315dbc;
}

.question-title em {
  color: #8293bb;
  font-style: normal;
  white-space: nowrap;
}

.admin-answer-value {
  margin-top: 14px;
  margin-left: 46px;
  min-height: 42px;
  border: 1px solid #8ee7bd;
  border-radius: 8px;
  background: #eafff3;
  color: #0d7a50;
  display: flex;
  align-items: center;
  padding: 8px 14px;
  font-weight: 900;
  line-height: 1.45;
}

.trend-panel {
  min-width: 0;
  overflow: auto;
  margin-bottom: 22px;
}

.top-trend-panel {
  border-bottom: 1px solid #e3edf9;
  padding-bottom: 18px;
}

.slim {
  min-height: 70px;
}

.trend-panel :deep(.trend-card) {
  margin-bottom: 12px;
}

@media (max-width: 1200px) {
  .survey-grid {
    grid-template-columns: 1fr;
  }

  .survey-list-inline,
  .two-cols,
  .task-info {
    grid-template-columns: 1fr;
  }
}
</style>

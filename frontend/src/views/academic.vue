<template>
  <div class="admin-shell">
    <AdminSidebar active="academic" />

    <main class="admin-main">
      <AdminTopbar title="学业帮扶" />

      <section class="admin-content">
        <div class="admin-card diagnosis-card">
          <div class="panel-head">
            <h3 class="section-title">
              <SparkIcon />
              预警报告比对与帮扶报告
            </h3>
            <div class="panel-actions">
              <button class="btn ghost" :disabled="exportingDiagnosis" @click="exportDiagnosisReport">
                {{ exportingDiagnosis ? '导出中...' : '导出报告' }}
              </button>
              <button class="btn ghost" type="button" :disabled="!selectedDiagnosis" @click="printDiagnosisReport">
                打印报告
              </button>
              <button class="btn ghost" :disabled="diagnosisLoading" @click="loadSurveyDiagnoses">
                {{ diagnosisLoading ? '分析中...' : '刷新诊断' }}
              </button>
            </div>
          </div>
          <div class="report-core-banner">
            <div>
              <b>1</b>
              <span>学生完成问卷</span>
              <small>系统保存各题分数，用来识别弱项</small>
            </div>
            <i></i>
            <div>
              <b>2</b>
              <span>老师上传人工预警报告</span>
              <small>人工预警等级作为正式依据</small>
            </div>
            <i></i>
            <div>
              <b>3</b>
              <span>生成帮扶报告</span>
              <small>对比人工等级和问卷弱项，输出方案</small>
            </div>
          </div>
          <div class="diagnosis-layout">
            <div class="diagnosis-list">
              <article
                v-for="item in surveyDiagnoses"
                :key="item.submissionId"
                role="button"
                tabindex="0"
                :class="['diagnosis-item', { active: selectedDiagnosisId === item.submissionId }]"
                @click="selectedDiagnosisId = item.submissionId"
                @keydown.enter="selectedDiagnosisId = item.submissionId"
                @keydown.space.prevent="selectedDiagnosisId = item.submissionId"
              >
                <span :class="['risk-dot', warningLevelClass(item.finalWarningLevel || item.warningLevel)]"></span>
                <div>
                  <strong>{{ item.realName || item.username || item.studentId }}</strong>
                  <small>{{ item.surveyTitle }} · {{ formatTime(item.submitTime) }}</small>
                  <em>{{ item.comparisonResult || item.primaryProblem }}</em>
                </div>
                <div class="diagnosis-item-actions">
                  <b :class="['tag', warningLevelClass(item.finalWarningLevel || item.warningLevel)]">
                    {{ item.finalWarningLevel || item.warningLevel }}
                  </b>
                  <button
                    class="diagnosis-delete"
                    type="button"
                    :disabled="deletingDiagnosisId === item.submissionId"
                    @click.stop="deleteDiagnosis(item)"
                  >
                    {{ deletingDiagnosisId === item.submissionId ? '删除中...' : '删除' }}
                  </button>
                </div>
              </article>
              <div v-if="!diagnosisLoading && surveyDiagnoses.length === 0" class="empty compact-empty">
                暂无学生问卷提交，无法生成诊断
              </div>
            </div>

            <div v-if="selectedDiagnosis" class="diagnosis-detail">
              <div class="report-head">
                <div>
                <span :class="['tag', warningLevelClass(selectedDiagnosis.finalWarningLevel || selectedDiagnosis.warningLevel)]">
                    报告采用等级：{{ selectedDiagnosis.finalWarningLevel || selectedDiagnosis.warningLevel }}
                  </span>
                  <h4>{{ selectedDiagnosis.realName }} 的预警比对与帮扶报告</h4>
                  <p>{{ selectedDiagnosis.reportConclusion || selectedDiagnosis.summary }}</p>
                </div>
                <button
                  class="btn danger light"
                  type="button"
                  :disabled="deletingDiagnosisId === selectedDiagnosis.submissionId"
                  @click="deleteDiagnosis(selectedDiagnosis)"
                >
                  {{ deletingDiagnosisId === selectedDiagnosis.submissionId ? '删除中...' : '删除报告' }}
                </button>
              </div>

              <div class="formal-report-summary">
                <div>
                  <span>正式依据</span>
                  <strong>{{ selectedDiagnosis.reportWarningLevel || '未上传预警报告' }}</strong>
                  <small>来自老师上传/手动录入的人工预警等级</small>
                </div>
                <div>
                  <span>问卷作用</span>
                  <strong>{{ selectedDiagnosis.questionnaireWeaknessItems || weakAreaText(selectedDiagnosis) }}</strong>
                  <small>问卷分数只用于定位弱项，不直接决定预警等级</small>
                </div>
                <div>
                  <span>帮扶周期</span>
                  <strong>{{ selectedDiagnosis.supportCycle || supportCycleText(selectedDiagnosis.finalWarningLevel) }}</strong>
                  <small>根据人工预警等级生成，可由老师调整</small>
                </div>
              </div>

              <div class="compare-grid">
                <div class="compare-card">
                  <span>人工上传报告</span>
                  <strong :class="['tag', warningLevelClass(selectedDiagnosis.reportWarningLevel)]">
                    {{ selectedDiagnosis.reportWarningLevel || '未上传预警报告' }}
                  </strong>
                </div>
                <div class="compare-card">
                  <span>问卷风险参考</span>
                  <strong :class="['tag', warningLevelClass(selectedDiagnosis.surveyWarningLevel)]">
                    {{ selectedDiagnosis.surveyWarningLevel }}
                  </strong>
                  <small>仅作为风险参考，不自动覆盖人工等级</small>
                </div>
                <div class="compare-card wide">
                  <span>比对结论</span>
                  <strong>{{ selectedDiagnosis.comparisonResult }}</strong>
                  <small>{{ selectedDiagnosis.comparisonDetail }}</small>
                </div>
              </div>

              <div class="manual-report-panel">
                <h5>人工预警报告内容</h5>
                <div class="manual-report-grid">
                  <div>
                    <span>预警原因</span>
                    <strong>{{ selectedDiagnosis.reportWarningReason || '未填写' }}</strong>
                  </div>
                  <div>
                    <span>主要弱项</span>
                    <strong>{{ selectedDiagnosis.reportWeaknessItems || '未填写' }}</strong>
                  </div>
                  <div class="wide">
                    <span>建议帮扶措施</span>
                    <strong>{{ selectedDiagnosis.reportHelpMeasures || '未填写' }}</strong>
                  </div>
                  <div>
                    <span>辅导员</span>
                    <strong>{{ selectedDiagnosis.reportCounselor || '未填写' }}</strong>
                  </div>
                  <div>
                    <span>联系电话</span>
                    <strong>{{ selectedDiagnosis.reportContactPhone || '未填写' }}</strong>
                  </div>
                  <div class="wide">
                    <span>备注</span>
                    <strong>{{ selectedDiagnosis.reportRemark || '未填写' }}</strong>
                  </div>
                </div>
              </div>

              <div class="support-report-panel">
                <h5>弱项提升报告</h5>
                <div class="support-report-grid">
                  <div>
                    <span>学生信息</span>
                    <strong>
                      {{ selectedDiagnosis.realName || '-' }} / {{ selectedDiagnosis.username || selectedDiagnosis.studentId || '-' }}
                    </strong>
                    <small>{{ selectedDiagnosis.className || '班级未填写' }} · {{ selectedDiagnosis.surveyTitle }} · {{ formatTime(selectedDiagnosis.submitTime) }}</small>
                  </div>
                  <div>
                    <span>人工预警等级</span>
                    <strong>{{ selectedDiagnosis.reportWarningLevel || '未上传预警报告' }}</strong>
                    <small>报告采用人工上传或手动设置的等级</small>
                  </div>
                  <div>
                    <span>问卷识别弱项</span>
                    <strong>{{ selectedDiagnosis.questionnaireWeaknessItems || weakAreaText(selectedDiagnosis) }}</strong>
                    <small>来源于学生问卷分数，仅用于弱项定位</small>
                  </div>
                  <div>
                    <span>帮扶周期</span>
                    <strong>{{ selectedDiagnosis.supportCycle || supportCycleText(selectedDiagnosis.finalWarningLevel) }}</strong>
                    <small>可由辅导员根据实际情况调整</small>
                  </div>
                  <div class="wide">
                    <span>提升目标</span>
                    <strong>{{ selectedDiagnosis.improvementGoal || improvementGoalText(selectedDiagnosis) }}</strong>
                  </div>
                  <div class="wide">
                    <span>提升方式与跟进安排</span>
                    <strong>{{ selectedDiagnosis.helpPlan || selectedDiagnosis.reportHelpMeasures || '未填写' }}</strong>
                  </div>
                  <div class="wide">
                    <span>评估方式</span>
                    <strong>{{ selectedDiagnosis.evaluationMethod || '周期结束后结合问卷弱项变化、作业完成率、阶段测验表现和辅导员访谈记录进行复评。' }}</strong>
                  </div>
                  <div class="wide">
                    <span>报告结论</span>
                    <strong>{{ selectedDiagnosis.reportConclusion || selectedDiagnosis.summary || '暂无结论' }}</strong>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="diagnosis-detail empty">选择左侧学生查看诊断报告</div>
          </div>
        </div>

        <div class="admin-card warning-level-card">
          <h3 class="section-title">
            <CapIcon />
            预警等级管理
          </h3>
          <div v-if="editingWarningLevelId" class="editing-warning-tip">
            正在编辑：{{ warningLevelForm.studentName || warningLevelForm.studentNo }}
            <button class="btn text" type="button" @click="resetWarningLevelForm">取消编辑</button>
          </div>
          <div class="warning-level-form">
            <input v-model="warningLevelForm.studentNo" class="input" placeholder="学号" />
            <input v-model="warningLevelForm.className" class="input" placeholder="班级" />
            <input v-model="warningLevelForm.studentName" class="input" placeholder="姓名" />
            <select v-model="warningLevelForm.warningLevel" class="select">
              <option v-for="level in warningLevels" :key="level" :value="level">{{ level }}</option>
            </select>
            <input v-model="warningLevelForm.warningReason" class="input wide-field" placeholder="预警原因，如近期测验成绩波动" />
            <input v-model="warningLevelForm.weaknessItems" class="input wide-field" placeholder="主要弱项，如作业问题、考试问题" />
            <textarea v-model="warningLevelForm.helpMeasures" class="textarea compact wide-field" placeholder="建议帮扶措施，如每周一次作业跟踪，考前安排答疑"></textarea>
            <input v-model="warningLevelForm.counselor" class="input" placeholder="辅导员" />
            <input v-model="warningLevelForm.contactPhone" class="input" placeholder="联系电话" />
            <input v-model="warningLevelForm.remark" class="input wide-field" placeholder="备注" />
            <button class="btn primary warning-save-btn" type="button" :disabled="savingWarningLevel" @click="saveWarningLevel">
              {{ savingWarningLevel ? '保存中...' : editingWarningLevelId ? '保存修改' : '手动设置' }}
            </button>
          </div>
          <div class="warning-level-upload">
            <input ref="warningFileInput" type="file" accept=".xlsx,.xls" hidden @change="onWarningFileChange" />
            <button class="btn ghost" type="button" :disabled="uploadingWarningLevel" @click="openWarningFilePicker">
              {{ uploadingWarningLevel ? '上传中...' : '一键上传 Excel' }}
            </button>
            <button class="btn ghost" type="button" :disabled="downloadingWarningTemplate" @click="downloadWarningTemplate">
              {{ downloadingWarningTemplate ? '下载中...' : '下载模板' }}
            </button>
            <button class="btn ghost" type="button" :disabled="exportingWarningLevels || warningLevelRows.length === 0" @click="exportWarningLevels">
              {{ exportingWarningLevels ? '导出中...' : '导出报告' }}
            </button>
            <span>模板列顺序：学号、班级、姓名、预警等级、预警原因、主要弱项、建议帮扶措施、辅导员、联系电话、备注</span>
          </div>
          <div
            v-if="warningLevelMessage"
            :class="['upload-message', warningLevelMessageType]"
          >
            {{ warningLevelMessage }}
          </div>
          <table class="admin-table warning-level-table">
            <thead>
              <tr>
                <th>学号</th>
                <th>班级</th>
                <th>姓名</th>
                <th>预警等级</th>
                <th>预警原因</th>
                <th>主要弱项</th>
                <th>辅导员</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in warningLevelRows"
                :key="item.id || item.studentNo"
                :class="{ editing: editingWarningLevelId === item.id }"
                @click="editWarningLevel(item)"
              >
                <td>{{ item.studentNo }}</td>
                <td>{{ item.className || '-' }}</td>
                <td>{{ item.studentName }}</td>
                <td><span :class="['tag', warningLevelClass(item.warningLevel)]">{{ item.warningLevel }}</span></td>
                <td>{{ item.warningReason || '-' }}</td>
                <td>{{ item.weaknessItems || '-' }}</td>
                <td>{{ item.counselor || '-' }}</td>
                <td>{{ formatTime(item.createdAt) }}</td>
                <td>
                  <button class="btn text" type="button" @click.stop="editWarningLevel(item)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="warningLevelRows.length === 0" class="empty">暂无预警等级数据</div>
        </div>

        <form class="admin-card" @submit.prevent="saveProfile">
          <h3 class="section-title">
            <CapIcon />
            学生画像管理
          </h3>
          <div class="profile-grid">
            <label>
              用户 ID
              <input v-model.number="profile.userId" class="input" placeholder="如 2021001234" />
            </label>
            <label>
              当前 GPA
              <input v-model="profile.gpa" class="input" placeholder="2.5" />
            </label>
            <label>
              要求 GPA
              <input v-model="profile.requiredGpa" class="input" placeholder="2.0" />
            </label>
            <label>
              挂科数
              <input v-model.number="profile.failedCoursesCnt" class="input" placeholder="0" />
            </label>
            <label>
              心理标签
              <input v-model="profile.psychologicalTag" class="input" placeholder="如 焦虑、自我效能感低" />
            </label>
            <label>
              辅导员
              <input v-model="profile.counselor" class="input" placeholder="如 张老师" />
            </label>
            <label>
              风险等级
              <select v-model.number="profile.riskLevel" class="select">
                <option :value="0">无风险</option>
                <option :value="1">橙色预警</option>
                <option :value="2">红色预警</option>
              </select>
            </label>
          </div>
          <div class="actions-right">
            <button class="btn primary" :disabled="savingProfile" type="submit">
              <FileIcon />
              {{ savingProfile ? '保存中...' : '保存画像' }}
            </button>
          </div>
        </form>

        <div class="admin-card warning-panel">
          <h3 class="section-title gold-title">
            <SparkIcon />
            学业预警生成
          </h3>
          <div class="warning-row">
            <label>
              学生 ID
              <input v-model.number="warning.studentId" class="input" placeholder="如 2021001234" />
            </label>
            <label>
              学期
              <select v-model="warning.term" class="select">
                <option>2025-2026-1</option>
                <option>2025-2026-2</option>
                <option>2026-2027-1</option>
              </select>
            </label>
            <button class="btn gold" :disabled="generating" @click="generateWarning">
              <SparkIcon />
              {{ generating ? '生成中...' : '生成预警' }}
            </button>
          </div>

          <div v-if="record" class="warning-result">
            <h4>{{ record.warningReason || '学业预警结果' }}</h4>
            <p>{{ record.aiSuggestedPlan }}</p>
            <div class="result-actions">
              <span class="tag yellow">记录ID: {{ record.id }}</span>
              <button class="btn ghost" :disabled="creatingPdf" @click="generatePdf">
                {{ creatingPdf ? '生成报告中...' : '生成 PDF 报告' }}
              </button>
              <a v-if="record.reportPdfUrl" class="btn ghost" :href="record.reportPdfUrl" target="_blank">查看报告</a>
            </div>
          </div>
          <div v-else class="empty">填写学生信息后点击“生成预警”</div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { apiDelete, apiDownload, apiForm, apiGet, apiJson, apiUpload } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, CapIcon, FileIcon, SparkIcon } from './shared/adminParts'

const savingProfile = ref(false)
const generating = ref(false)
const creatingPdf = ref(false)
const savingWarningLevel = ref(false)
const uploadingWarningLevel = ref(false)
const downloadingWarningTemplate = ref(false)
const exportingWarningLevels = ref(false)
const exportingDiagnosis = ref(false)
const record = ref(null)
const warningLevelRows = ref([])
const surveyDiagnoses = ref([])
const diagnosisLoading = ref(false)
const selectedDiagnosisId = ref(null)
const deletingDiagnosisId = ref(null)
const editingWarningLevelId = ref(null)
const warningLevelMessage = ref('')
const warningLevelMessageType = ref('info')
const warningFileInput = ref(null)
const warningLevels = ['正常', '黄色预警', '橙色预警', '红色预警']

const selectedDiagnosis = computed(() => (
  surveyDiagnoses.value.find(item => item.submissionId === selectedDiagnosisId.value) || surveyDiagnoses.value[0] || null
))

const warningLevelForm = reactive({
  id: null,
  studentNo: '',
  className: '',
  studentName: '',
  warningLevel: '正常',
  warningReason: '',
  weaknessItems: '',
  helpMeasures: '',
  counselor: '',
  contactPhone: '',
  remark: ''
})

const profile = reactive({
  userId: null,
  gpa: '',
  requiredGpa: '',
  failedCoursesCnt: 0,
  psychologicalTag: '',
  counselor: '',
  riskLevel: 0
})

const warning = reactive({
  studentId: null,
  term: '2025-2026-1'
})

async function loadWarningLevels() {
  warningLevelRows.value = await apiGet('/api/academic/warning-levels')
}

async function loadSurveyDiagnoses() {
  diagnosisLoading.value = true
  try {
    surveyDiagnoses.value = await apiGet('/api/academic/survey-diagnoses', { limit: 120 })
    if (!selectedDiagnosisId.value && surveyDiagnoses.value.length) {
      selectedDiagnosisId.value = surveyDiagnoses.value[0].submissionId
    }
    if (selectedDiagnosisId.value && !surveyDiagnoses.value.some(item => item.submissionId === selectedDiagnosisId.value)) {
      selectedDiagnosisId.value = surveyDiagnoses.value[0]?.submissionId || null
    }
  } finally {
    diagnosisLoading.value = false
  }
}

async function deleteDiagnosis(item) {
  if (!item?.submissionId || deletingDiagnosisId.value) return
  const studentName = item.realName || item.username || item.studentId || '该学生'
  const confirmed = window.confirm(`确认删除「${studentName}」的这份问卷诊断与帮扶报告吗？\n删除后会同步删除该次问卷提交和回答记录。`)
  if (!confirmed) return

  deletingDiagnosisId.value = item.submissionId
  try {
    await apiDelete(`/api/survey/admin/submissions/${item.submissionId}`)
    if (selectedDiagnosisId.value === item.submissionId) {
      selectedDiagnosisId.value = null
    }
    await loadSurveyDiagnoses()
  } finally {
    deletingDiagnosisId.value = null
  }
}

async function saveWarningLevel() {
  savingWarningLevel.value = true
  warningLevelMessage.value = ''
  try {
    await apiJson('/api/academic/warning-levels', warningLevelForm)
    const message = editingWarningLevelId.value
      ? '人工预警报告已修改，导出报告会使用最新内容。'
      : '手动预警报告已保存，并已刷新问卷诊断比对。'
    resetWarningLevelForm()
    await loadWarningLevels()
    await loadSurveyDiagnoses()
    warningLevelMessage.value = message
    warningLevelMessageType.value = 'success'
  } catch (e) {
    warningLevelMessage.value = e.message || '保存预警报告失败'
    warningLevelMessageType.value = 'error'
  } finally {
    savingWarningLevel.value = false
  }
}

function editWarningLevel(item) {
  if (!item) return
  editingWarningLevelId.value = item.id || null
  warningLevelForm.id = item.id || null
  warningLevelForm.studentNo = item.studentNo || ''
  warningLevelForm.className = item.className || ''
  warningLevelForm.studentName = item.studentName || ''
  warningLevelForm.warningLevel = item.warningLevel || '正常'
  warningLevelForm.warningReason = item.warningReason || ''
  warningLevelForm.weaknessItems = item.weaknessItems || ''
  warningLevelForm.helpMeasures = item.helpMeasures || ''
  warningLevelForm.counselor = item.counselor || ''
  warningLevelForm.contactPhone = item.contactPhone || ''
  warningLevelForm.remark = item.remark || ''
  warningLevelMessage.value = '已带入该学生的人工预警报告，可在上方修改后保存。'
  warningLevelMessageType.value = 'info'
}

function resetWarningLevelForm() {
  editingWarningLevelId.value = null
  warningLevelForm.id = null
  warningLevelForm.studentNo = ''
  warningLevelForm.className = ''
  warningLevelForm.studentName = ''
  warningLevelForm.warningLevel = '正常'
  warningLevelForm.warningReason = ''
  warningLevelForm.weaknessItems = ''
  warningLevelForm.helpMeasures = ''
  warningLevelForm.counselor = ''
  warningLevelForm.contactPhone = ''
  warningLevelForm.remark = ''
}

async function onWarningFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadingWarningLevel.value = true
  warningLevelMessage.value = `正在上传 ${file.name} ...`
  warningLevelMessageType.value = 'info'
  try {
    const formData = new FormData()
    formData.append('file', file)
    const count = await apiUpload('/api/academic/warning-levels/import', formData)
    await loadWarningLevels()
    await loadSurveyDiagnoses()
    if (count > 0) {
      warningLevelMessage.value = `导入成功：已更新 ${count} 条预警等级数据。`
      warningLevelMessageType.value = 'success'
    } else {
      warningLevelMessage.value = 'Excel 已读取，但没有导入有效数据。请确认至少包含：学号、姓名、预警等级。'
      warningLevelMessageType.value = 'error'
    }
  } catch (e) {
    warningLevelMessage.value = e.message || '上传失败，请检查 Excel 模板内容。'
    warningLevelMessageType.value = 'error'
  } finally {
    uploadingWarningLevel.value = false
    event.target.value = ''
  }
}

function openWarningFilePicker() {
  warningLevelMessage.value = ''
  if (warningFileInput.value) {
    warningFileInput.value.value = ''
    warningFileInput.value.click()
  }
}

async function downloadWarningTemplate() {
  downloadingWarningTemplate.value = true
  try {
    await apiDownload('/api/academic/warning-levels/template', '预警等级导入模板.xlsx')
  } finally {
    downloadingWarningTemplate.value = false
  }
}

async function exportWarningLevels() {
  exportingWarningLevels.value = true
  try {
    await apiDownload('/api/academic/warning-levels/export', '人工预警等级报告.xlsx')
  } finally {
    exportingWarningLevels.value = false
  }
}

async function exportDiagnosisReport() {
  exportingDiagnosis.value = true
  try {
    await apiDownload('/api/academic/survey-diagnoses/export?limit=300', '预警报告比对与帮扶报告.xlsx')
  } finally {
    exportingDiagnosis.value = false
  }
}

function printDiagnosisReport() {
  window.print()
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await apiJson('/api/academic/profile/update', {
      ...profile,
      gpa: profile.gpa === '' ? null : Number(profile.gpa),
      requiredGpa: profile.requiredGpa === '' ? null : Number(profile.requiredGpa)
    })
    if (!warning.studentId && profile.userId) warning.studentId = profile.userId
  } finally {
    savingProfile.value = false
  }
}

async function generateWarning() {
  if (!warning.studentId) return
  generating.value = true
  try {
    record.value = await apiForm('/api/academic/generate-warning', {
      studentId: warning.studentId,
      term: warning.term
    })
  } finally {
    generating.value = false
  }
}

async function generatePdf() {
  if (!record.value?.id) return
  creatingPdf.value = true
  try {
    const url = await apiForm('/api/academic/generate-pdf-report', { recordId: record.value.id })
    record.value = { ...record.value, reportPdfUrl: url }
  } finally {
    creatingPdf.value = false
  }
}

function warningLevelClass(level) {
  if (!level || level.includes('未上传')) return 'blue'
  if (level === '正常') return 'green'
  if (level === '黄色预警') return 'yellow'
  if (level === '橙色预警') return 'orange'
  return 'red'
}

function formatScore(value) {
  return Number(value || 0).toFixed(1)
}

function weakAreaText(diagnosis) {
  const fromManual = diagnosis?.reportWeaknessItems
  if (fromManual) return fromManual
  const areas = (diagnosis?.dimensions || [])
    .filter(item => item.score == null || item.score >= 2.7)
    .slice(0, 4)
    .map(item => item.label || item.dimension)
    .filter(Boolean)
  return areas.length ? [...new Set(areas)].join('、') : '综合学习状态'
}

function improvementGoalText(diagnosis) {
  return `围绕${weakAreaText(diagnosis)}进行专项提升，周期结束后弱项表现较当前明显改善，形成稳定学习计划和跟进记录。`
}

function supportCycleText(level) {
  if (!level || String(level).includes('未上传')) return '待补录人工预警等级后确定周期'
  if (String(level).includes('红')) return '8周重点帮扶：每周跟进1次，第4周阶段复盘，第8周复评'
  if (String(level).includes('橙')) return '6周专项帮扶：每两周跟进1次，第6周复评'
  if (String(level).includes('黄')) return '4周观察帮扶：每两周跟进1次，第4周复评'
  return '2-4周常规观察：根据学生状态安排复评'
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  loadWarningLevels()
  loadSurveyDiagnoses()
})
</script>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.diagnosis-card {
  display: grid;
  gap: 16px;
}
.report-core-banner {
  border: 1px solid #bdd6ff;
  border-radius: 18px;
  background: linear-gradient(135deg, #eef6ff 0%, #ffffff 58%, #fff8e8 100%);
  padding: 16px 18px;
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr;
  align-items: stretch;
  gap: 14px;
  box-shadow: 0 12px 28px rgba(49, 93, 188, 0.08);
}
.report-core-banner div {
  display: grid;
  grid-template-columns: 42px 1fr;
  grid-template-rows: auto auto;
  column-gap: 12px;
  align-content: center;
}
.report-core-banner b {
  grid-row: 1 / 3;
  width: 42px;
  height: 42px;
  border-radius: 999px;
  color: #fff;
  background: #315dbc;
  display: grid;
  place-items: center;
  font-size: 20px;
  box-shadow: 0 8px 18px rgba(49, 93, 188, 0.22);
}
.report-core-banner span {
  color: #082f7a;
  font-size: 17px;
  font-weight: 1000;
}
.report-core-banner small {
  margin-top: 5px;
  color: #49659c;
  font-weight: 800;
  line-height: 1.45;
}
.report-core-banner i {
  align-self: center;
  width: 48px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, #8bb7ff, #ffc743);
  position: relative;
}
.report-core-banner i::after {
  content: "";
  position: absolute;
  right: -2px;
  top: 50%;
  width: 8px;
  height: 8px;
  border-right: 2px solid #ffc743;
  border-top: 2px solid #ffc743;
  transform: translateY(-50%) rotate(45deg);
}
.diagnosis-layout {
  display: grid;
  grid-template-columns: 420px minmax(0, 1fr);
  gap: 18px;
  min-height: 460px;
}
.diagnosis-list {
  display: grid;
  align-content: start;
  gap: 10px;
  max-height: 650px;
  overflow: auto;
  padding-right: 4px;
}
.diagnosis-item {
  min-height: 92px;
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #f8fbff;
  color: #173875;
  padding: 12px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
}
.diagnosis-item > div:not(.diagnosis-item-actions) {
  min-width: 0;
}
.diagnosis-item.active,
.diagnosis-item:hover {
  border-color: #8bb7ff;
  background: #eef6ff;
}
.diagnosis-item strong,
.diagnosis-item small,
.diagnosis-item em {
  display: block;
}
.diagnosis-item small {
  margin-top: 4px;
  color: #6e82b1;
  font-weight: 800;
}
.diagnosis-item em {
  margin-top: 6px;
  color: #315dbc;
  font-style: normal;
  font-weight: 900;
}
.diagnosis-item-actions {
  display: grid;
  justify-items: end;
  gap: 8px;
}
.diagnosis-delete {
  min-height: 30px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  color: #d93c58;
  background: #ffe8ee;
  font-weight: 900;
  cursor: pointer;
}
.diagnosis-delete:hover {
  background: #ffd1dd;
}
.diagnosis-delete:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.risk-dot {
  width: 12px;
  height: 48px;
  border-radius: 99px;
  background: #35b77a;
}
.risk-dot.yellow {
  background: #ffc743;
}
.risk-dot.orange {
  background: #ff8a3d;
}
.risk-dot.red {
  background: #f0446b;
}
.tag.orange {
  color: #bc5a00;
  background: #fff0e5;
  border-color: #ffc799;
}
.diagnosis-detail {
  border: 1px solid #d8e4f5;
  border-radius: 16px;
  background: #fff;
  padding: 20px;
  display: grid;
  align-content: start;
  gap: 18px;
}
.report-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.report-head > div {
  min-width: 0;
}
.btn.danger.light {
  flex: 0 0 auto;
  color: #d93c58;
  background: #ffe8ee;
  box-shadow: none;
}
.btn.danger.light:hover {
  background: #ffd1dd;
}
.report-head h4 {
  margin: 10px 0 8px;
  color: #082f7a;
  font-size: 22px;
}
.report-head p {
  margin: 0;
  color: #49659c;
  line-height: 1.7;
  font-weight: 900;
}
.formal-report-summary {
  border: 1px solid #bdd6ff;
  border-radius: 18px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 54%, #fff7ec 100%);
  padding: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  box-shadow: inset 5px 0 0 #315dbc, 0 12px 26px rgba(49, 93, 188, 0.06);
}
.formal-report-summary div {
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  padding: 14px;
}
.formal-report-summary span,
.formal-report-summary strong,
.formal-report-summary small {
  display: block;
}
.formal-report-summary span {
  color: #6e82b1;
  font-weight: 900;
}
.formal-report-summary strong {
  margin-top: 8px;
  color: #082f7a;
  font-size: 18px;
  line-height: 1.5;
}
.formal-report-summary small {
  margin-top: 6px;
  color: #49659c;
  font-weight: 800;
  line-height: 1.45;
}
.compare-grid {
  display: grid;
  grid-template-columns: 180px 180px 1fr;
  gap: 12px;
}
.compare-card {
  min-height: 96px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 14px;
  display: grid;
  align-content: start;
  gap: 10px;
}
.compare-card span,
.compare-card small {
  color: #6e82b1;
  font-weight: 800;
}
.compare-card > strong:not(.tag) {
  color: #173875;
  font-size: 17px;
}
.compare-card small {
  line-height: 1.55;
}
.manual-report-panel {
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #f8fbff;
  padding: 16px;
}
.manual-report-panel h5 {
  margin: 0 0 12px;
  color: #173875;
  font-size: 17px;
}
.manual-report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.manual-report-grid div {
  border: 1px solid #d8e4f5;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
}
.manual-report-grid .wide {
  grid-column: 1 / -1;
}
.manual-report-grid span,
.manual-report-grid strong {
  display: block;
}
.manual-report-grid span {
  color: #6e82b1;
  font-weight: 800;
}
.manual-report-grid strong {
  margin-top: 6px;
  color: #173875;
  line-height: 1.6;
  white-space: pre-wrap;
}
.support-report-panel {
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fffdf8;
  padding: 16px;
}
.support-report-panel h5 {
  margin: 0 0 12px;
  color: #173875;
  font-size: 17px;
}
.support-report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.support-report-grid div {
  border: 1px solid #d8e4f5;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
}
.support-report-grid .wide {
  grid-column: 1 / -1;
}
.support-report-grid span,
.support-report-grid strong,
.support-report-grid small {
  display: block;
}
.support-report-grid span {
  color: #6e82b1;
  font-weight: 800;
}
.support-report-grid strong {
  margin-top: 6px;
  color: #173875;
  line-height: 1.7;
  white-space: pre-wrap;
}
.support-report-grid small {
  margin-top: 5px;
  color: #49659c;
  font-weight: 800;
  line-height: 1.5;
}
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.dimension-card {
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #f8fbff;
  padding: 12px;
}
.dimension-title {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #173875;
  font-weight: 900;
}
.dimension-title span {
  color: #6e82b1;
}
.dimension-bar {
  height: 10px;
  border-radius: 99px;
  background: #e6eefb;
  overflow: hidden;
  margin: 10px 0;
}
.dimension-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ffc743, #f0446b);
}
.dimension-card small {
  color: #49659c;
  line-height: 1.5;
  font-weight: 800;
}
.evidence-panel,
.plan-panel {
  border-top: 1px solid #edf2f7;
  padding-top: 16px;
}
.evidence-panel h5,
.plan-panel h5 {
  margin: 0 0 12px;
  color: #173875;
  font-size: 17px;
}
.evidence-list {
  display: grid;
  gap: 10px;
}
.evidence-row {
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 12px;
  border: 1px solid #d8e4f5;
  border-radius: 10px;
  padding: 10px;
  background: #fffdf8;
}
.evidence-row span {
  color: #bc5a00;
  font-weight: 900;
}
.evidence-row strong,
.evidence-row small {
  display: block;
}
.evidence-row small {
  margin-top: 5px;
  color: #49659c;
  font-weight: 800;
}
.plan-panel p {
  margin: 0;
  color: #173875;
  line-height: 1.8;
  font-weight: 900;
  white-space: pre-wrap;
}
.compact-empty {
  min-height: 96px;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.warning-level-card {
  display: grid;
  gap: 14px;
}
.warning-level-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}
.editing-warning-tip {
  min-height: 42px;
  border: 1px solid #bdd6ff;
  border-radius: 10px;
  background: #eef6ff;
  color: #315dbc;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 900;
}
.warning-level-form .wide-field {
  grid-column: span 2;
}
.warning-level-form .warning-save-btn {
  grid-column: 4;
  min-height: 52px;
}
.warning-level-upload {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: #49659c;
  font-weight: 900;
}
.warning-level-upload span {
  flex: 1 1 360px;
}
.upload-message {
  min-height: 42px;
  border-radius: 10px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  color: #315dbc;
  background: #eef6ff;
  font-weight: 900;
}
.upload-message.success {
  color: #159461;
  background: #e7fff2;
}
.upload-message.error {
  color: #d93c58;
  background: #ffe9ee;
}
.warning-level-table {
  margin-top: 4px;
}
.warning-level-table td {
  max-width: 260px;
  line-height: 1.55;
  white-space: normal;
}
.warning-level-table tbody tr {
  cursor: pointer;
}
.warning-level-table tbody tr.editing {
  background: #eef6ff;
  box-shadow: inset 4px 0 0 #315dbc;
}

label {
  display: grid;
  gap: 8px;
  font-weight: 900;
}

.actions-right {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

.warning-panel {
  min-height: 300px;
}

.gold-title {
  color: #173875;
}

.gold-title :deep(svg) {
  color: #ffc743;
}

.warning-row {
  display: grid;
  grid-template-columns: 1fr 280px 150px;
  gap: 14px;
  align-items: end;
}

.warning-result {
  margin-top: 28px;
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  padding: 22px;
}

.warning-result h4 {
  margin: 0 0 12px;
  font-size: 20px;
}

.warning-result p {
  margin: 0;
  line-height: 1.7;
  font-weight: 800;
  white-space: pre-wrap;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.result-actions a {
  text-decoration: none;
}

@media (max-width: 1100px) {
  .profile-grid,
  .warning-level-form,
  .warning-row,
  .diagnosis-layout,
  .dimension-grid,
  .compare-grid,
  .formal-report-summary,
  .report-core-banner {
    grid-template-columns: 1fr;
  }
  .report-core-banner i {
    width: 2px;
    height: 24px;
    justify-self: center;
  }
  .report-core-banner i::after {
    right: 50%;
    top: auto;
    bottom: -2px;
    transform: translateX(50%) rotate(135deg);
  }
  .warning-level-form .wide-field,
  .warning-level-form .warning-save-btn {
    grid-column: auto;
  }
}

@media print {
  :global(body) {
    background: #fff !important;
  }
  :deep(.admin-sidebar),
  :deep(.admin-topbar),
  .panel-actions,
  .diagnosis-list,
  .report-core-banner,
  .warning-level-card,
  form.admin-card,
  .warning-panel,
  .report-head .btn {
    display: none !important;
  }
  .admin-main,
  .admin-content,
  .diagnosis-card,
  .diagnosis-layout,
  .diagnosis-detail {
    display: block !important;
    width: 100% !important;
    max-width: none !important;
    min-height: auto !important;
    padding: 0 !important;
    margin: 0 !important;
    border: 0 !important;
    box-shadow: none !important;
    background: #fff !important;
  }
  .compare-grid,
  .dimension-grid,
  .manual-report-grid,
  .support-report-grid {
    grid-template-columns: 1fr 1fr !important;
  }
  .manual-report-grid .wide,
  .support-report-grid .wide,
  .compare-card.wide {
    grid-column: 1 / -1 !important;
  }
}
</style>

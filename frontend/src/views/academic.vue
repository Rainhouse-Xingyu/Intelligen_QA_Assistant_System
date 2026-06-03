<template>
  <div class="admin-shell">
    <AdminSidebar active="academic" />

    <main class="admin-main">
      <AdminTopbar title="学业帮扶" />

      <section class="admin-content">
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
import { reactive, ref } from 'vue'
import { apiForm, apiJson } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, CapIcon, FileIcon, SparkIcon } from './shared/adminParts'

const savingProfile = ref(false)
const generating = ref(false)
const creatingPdf = ref(false)
const record = ref(null)

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
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
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
  .warning-row {
    grid-template-columns: 1fr;
  }
}
</style>


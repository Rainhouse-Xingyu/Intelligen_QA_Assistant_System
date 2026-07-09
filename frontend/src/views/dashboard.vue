<template>
  <div class="admin-shell">
    <AdminSidebar active="dashboard" />

    <main class="admin-main">
      <header class="dashboard-head">
        <h2>数据统计看板</h2>
        <div class="toolbar">
          <select v-model.number="days" class="select narrow" @change="loadData">
            <option :value="7">最近 7 天</option>
            <option :value="14">最近 14 天</option>
            <option :value="30">最近 30 天</option>
          </select>
          <button class="btn ghost" :disabled="loading" @click="rebuild">
            <RefreshIcon />
            重建统计
          </button>
        </div>
      </header>

      <section class="stats-grid">
        <div v-for="card in statCards" :key="card.label" class="admin-card metric-card">
          <div class="metric-label">
            <span :style="{ background: card.color }"></span>
            {{ card.label }}
          </div>
          <strong :style="{ color: card.color }">{{ card.value }}</strong>
          <p>近 {{ days }} 天</p>
        </div>
      </section>

      <section class="dashboard-grid">
        <div class="admin-card">
          <h3 class="section-title">常见问题 Top 5</h3>
          <div v-for="(item, index) in hotQuestions.slice(0, 5)" :key="item.name" class="hot-row">
            <div>
              <b>#{{ index + 1 }}</b>
              <span>{{ item.name }}</span>
              <em>{{ item.value || 0 }}</em>
            </div>
            <div class="bar">
              <i :style="{ width: barWidth(item.value) }"></i>
            </div>
          </div>
          <div v-if="hotQuestions.length === 0" class="empty">暂无常见问题数据</div>
        </div>

        <div class="admin-card">
          <h3 class="section-title">
            <AlertIcon />
            Top 未识别问题
          </h3>
          <div v-for="item in topUnrecognized.slice(0, 3)" :key="item.questionText" class="unknown-card">
            <div>
              <strong>{{ item.questionText }}</strong>
              <span>出现 {{ item.frequency || 0 }} 次</span>
            </div>
            <button class="btn text" @click="markHandled(item)">
              <CheckIcon />
              处理
            </button>
          </div>
          <div v-if="topUnrecognized.length === 0" class="empty">暂无待处理问题</div>
        </div>
      </section>

      <section class="admin-card hot-config-card">
        <div class="panel-head">
          <h3 class="section-title">首页热门问题配置</h3>
          <button class="btn text" @click="loadHotConfigs">
            <RefreshIcon />
            刷新
          </button>
        </div>
        <div class="hot-config-form">
          <input v-model="hotConfigForm.questionText" class="input" placeholder="热门问题" />
          <input v-model="hotConfigForm.answerText" class="input" placeholder="直接回答内容" />
          <select v-model="hotConfigForm.moduleType" class="select">
            <option value="">未分类</option>
            <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
          </select>
          <input v-model="hotConfigForm.validUntil" class="input" type="datetime-local" />
          <button class="btn gold" :disabled="savingHotConfig" @click="saveHotConfig">
            {{ savingHotConfig ? '保存中...' : '保存配置' }}
          </button>
        </div>
        <div class="hot-config-list">
          <div v-for="item in hotConfigs" :key="item.id" class="hot-config-row">
            <div>
              <strong>{{ item.questionText }}</strong>
              <span>{{ item.moduleType || '未分类' }} · 截止 {{ formatDateTime(item.validUntil) }}</span>
            </div>
            <button class="btn text danger" @click="deleteHotConfig(item)">删除</button>
          </div>
          <div v-if="hotConfigs.length === 0" class="empty">暂无手选热门问题配置</div>
        </div>
      </section>

      <section class="admin-card">
        <div class="panel-head">
          <h3 class="section-title">未识别问题列表</h3>
          <button class="btn text" @click="loadData">
            <RefreshIcon />
            刷新
          </button>
        </div>
        <table class="admin-table">
          <thead>
            <tr>
              <th>问题</th>
              <th>模块</th>
              <th>频率</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in unrecognized" :key="item.id">
              <td>{{ item.questionText }}</td>
              <td>{{ item.moduleType || '-' }}</td>
              <td>{{ item.frequency || 1 }}</td>
              <td><span :class="['tag', item.status === 1 ? 'green' : 'yellow']">{{ item.status === 1 ? '已处理' : '未处理' }}</span></td>
              <td>{{ formatDate(item.createTime) }}</td>
              <td>
                <button v-if="item.status !== 1" class="btn text" @click="markHandled(item)">标记已处理</button>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { apiDelete, apiForm, apiGet, apiJson } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AlertIcon, CheckIcon, RefreshIcon } from './shared/adminParts'

const days = ref(7)
const loading = ref(false)
const overview = ref({})
const hotQuestions = ref([])
const unrecognized = ref([])
const hotConfigs = ref([])
const savingHotConfig = ref(false)
const modules = ['考务通知', '教学运行', '学业帮扶', '心理辅导']
const hotConfigForm = ref({
  questionText: '',
  answerText: '',
  moduleType: '',
  validUntil: '',
  enabled: 1,
  sortOrder: 0
})

const topUnrecognized = computed(() => overview.value.topUnrecognized || [])
const statCards = computed(() => [
  { label: '强命中', value: overview.value.strongHitCount || 0, color: '#35b77a' },
  { label: '弱命中', value: overview.value.weakHitCount || 0, color: '#f2a10b' },
  { label: '未命中', value: overview.value.noHitCount || 0, color: '#ef476f' },
  { label: '未识别待处理', value: overview.value.unrecognizedPending || 0, color: '#ffc743' }
])

async function loadData() {
  loading.value = true
  try {
    const [overviewData, hotData, listData, configData] = await Promise.all([
      apiGet('/api/stat/fallback-overview', { days: days.value }),
      apiGet('/api/stat/hot-questions', { days: days.value, limit: 20 }),
      apiGet('/api/admin/unrecognized/list', { current: 1, size: 50 }),
      apiGet('/api/stat/hot-question-configs')
    ])
    overview.value = overviewData || {}
    hotQuestions.value = hotData || []
    unrecognized.value = listData?.records || []
    hotConfigs.value = configData || []
  } finally {
    loading.value = false
  }
}

async function rebuild() {
  loading.value = true
  try {
    await apiForm('/api/stat/hot-questions/rebuild', { days: days.value })
    await loadData()
  } finally {
    loading.value = false
  }
}

async function markHandled(item) {
  if (!item.id && !item.questionText) return
  const found = item.id ? item : unrecognized.value.find(row => row.questionText === item.questionText && row.status !== 1)
  if (!found) return
  await apiForm('/api/admin/unrecognized/update-status', { id: found.id, status: 1 })
  await loadData()
}

async function loadHotConfigs() {
  hotConfigs.value = await apiGet('/api/stat/hot-question-configs')
}

async function saveHotConfig() {
  if (!hotConfigForm.value.questionText || !hotConfigForm.value.answerText) return
  savingHotConfig.value = true
  try {
    await apiJson('/api/stat/hot-question-configs', {
      ...hotConfigForm.value,
      validUntil: hotConfigForm.value.validUntil ? `${hotConfigForm.value.validUntil}:00` : null
    })
    hotConfigForm.value = {
      questionText: '',
      answerText: '',
      moduleType: '',
      validUntil: '',
      enabled: 1,
      sortOrder: 0
    }
    await loadHotConfigs()
  } finally {
    savingHotConfig.value = false
  }
}

async function deleteHotConfig(item) {
  if (!item?.id) return
  await apiDelete(`/api/stat/hot-question-configs/${item.id}`)
  await loadHotConfigs()
}

function barWidth(value) {
  const max = Math.max(...hotQuestions.value.map(item => Number(item.value || 0)), 1)
  return `${Math.max(6, (Number(value || 0) / max) * 100)}%`
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function formatDateTime(value) {
  if (!value) return '长期有效'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadData)
</script>

<style scoped>
.dashboard-head {
  min-height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dashboard-head h2 {
  margin: 0;
  font-size: 25px;
  color: #082f7a;
}

.narrow {
  width: 170px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 22px;
}

.metric-card {
  min-height: 130px;
}

.metric-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 900;
}

.metric-label span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.metric-card strong {
  display: block;
  margin-top: 14px;
  font-size: 44px;
  line-height: 1;
}

.metric-card p {
  margin: 8px 0 0;
  font-weight: 800;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
}
.hot-config-card {
  margin-top: 22px;
}
.hot-config-form {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) minmax(220px, 1.5fr) 150px 190px auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.hot-config-list {
  display: grid;
  gap: 10px;
}
.hot-config-row {
  min-height: 58px;
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #f8fbff;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.hot-config-row strong {
  display: block;
  color: #173875;
}
.hot-config-row span {
  display: block;
  margin-top: 4px;
  color: #6e82b1;
  font-weight: 800;
}

.hot-row {
  margin-top: 14px;
}

.hot-row div:first-child {
  display: grid;
  grid-template-columns: 42px 1fr auto;
  gap: 8px;
  align-items: center;
  font-weight: 900;
}

.hot-row b,
.hot-row em {
  color: #ffc743;
  font-style: normal;
}

.bar {
  height: 10px;
  margin-top: 8px;
  border-radius: 10px;
  background: rgba(13, 21, 40, 0.22);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #0d1528;
}

.unknown-card {
  min-height: 74px;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  background: #fff;
  margin-top: 12px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.unknown-card strong,
.unknown-card span {
  display: block;
  font-weight: 900;
}

.unknown-card span {
  color: #49659c;
  margin-top: 4px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

@media (max-width: 1200px) {
  .stats-grid,
  .dashboard-grid {
    grid-template-columns: 1fr 1fr;
  }
  .hot-config-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .stats-grid,
  .dashboard-grid,
  .hot-config-form {
    grid-template-columns: 1fr;
  }
}
</style>

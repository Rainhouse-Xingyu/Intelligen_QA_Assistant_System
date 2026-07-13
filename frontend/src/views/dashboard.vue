<template>
  <div class="admin-shell">
    <AdminSidebar active="dashboard" />

    <main class="admin-main">
      <AdminTopbar title="数据统计" />

      <div class="admin-content dashboard-content">
        <section class="dashboard-controls admin-card">
          <div>
            <h3>数据统计看板</h3>
            <p>查看问答命中、热门问题与未识别问题处理情况</p>
          </div>
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
        </section>

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
            <div class="toolbar">
              <button class="btn gold" @click="openHotPicker">
                <PlusIcon />
                新增
              </button>
              <button class="btn text" @click="loadHotConfigs">
                <RefreshIcon />
                刷新
              </button>
            </div>
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
            <div class="toolbar">
              <button class="btn text" :disabled="selectedUnrecognizedIds.length === 0 || batchHandling" @click="batchMarkHandled">
                <CheckIcon />
                {{ batchHandling ? '处理中...' : '批量已处理' }}
              </button>
              <button class="btn text" :disabled="exportingUnrecognized || unrecognized.length === 0" @click="exportUnrecognized">
                {{ exportingUnrecognized ? '导出中...' : '导出 Excel' }}
              </button>
              <button class="btn text" @click="loadData">
                <RefreshIcon />
                刷新
              </button>
            </div>
          </div>
          <table class="admin-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    :checked="allUnrecognizedSelected"
                    :disabled="unrecognized.length === 0"
                    @change="toggleAllUnrecognized"
                  />
                </th>
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
                <td>
                  <input type="checkbox" :checked="isUnrecognizedSelected(item.id)" @change="toggleUnrecognized(item.id)" />
                </td>
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
      </div>
    </main>

    <div v-if="hotPickerOpen" class="modal-mask" @click.self="closeHotPicker">
      <div class="hot-picker-modal">
        <div class="modal-head">
          <h3>从知识库选择热门问题</h3>
          <button type="button" class="modal-close" @click="closeHotPicker">×</button>
        </div>
        <div class="hot-picker-tools">
          <input v-model="hotPickerKeyword" class="input" placeholder="模糊搜索问题或答案" @keyup.enter="searchKbEntries" />
          <select v-model="hotPickerModule" class="select" @change="searchKbEntries">
            <option value="">全部模块</option>
            <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
          </select>
          <button class="btn ghost" :disabled="hotPickerLoading" @click="searchKbEntries">
            {{ hotPickerLoading ? '搜索中...' : '搜索' }}
          </button>
        </div>
        <label class="hot-picker-deadline">
          <span>截止时间</span>
          <input
            v-model="hotConfigValidUntil"
            class="input"
            type="datetime-local"
            :min="currentDateTime"
          />
          <small>可选，留空表示长期有效</small>
        </label>
        <div class="hot-picker-list">
          <label v-for="entry in kbEntries" :key="entry.id" :class="['hot-picker-row', { selected: selectedKbEntryIds.includes(entry.id) }]">
            <input
              type="checkbox"
              :checked="selectedKbEntryIds.includes(entry.id)"
              :disabled="isKbEntryDisabled(entry.id)"
              @change="toggleKbEntry(entry.id)"
            />
            <div>
              <strong>{{ entry.question }}</strong>
              <p>{{ entry.answer }}</p>
              <span>{{ entry.moduleType || '未分类' }}</span>
            </div>
          </label>
          <div v-if="!hotPickerLoading && kbEntries.length === 0" class="empty slim">暂无匹配知识库问题</div>
        </div>
        <div class="modal-actions">
          <span :class="['selection-count', { valid: hotPickerSelectionValid }]">
            已选 {{ selectedKbEntryIds.length }} / 5 条
            <em>{{ hotPickerSelectionTip }}</em>
          </span>
          <button class="btn ghost" type="button" @click="closeHotPicker">取消</button>
          <button class="btn primary" :disabled="savingHotConfig || !hotPickerSelectionValid" @click="saveSelectedHotConfigs">
            {{ savingHotConfig ? '保存中...' : '加入热门问题' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { apiDelete, apiDownload, apiForm, apiGet, apiJson } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, AlertIcon, CheckIcon, PlusIcon, RefreshIcon } from './shared/adminParts'

const days = ref(7)
const loading = ref(false)
const overview = ref({})
const hotQuestions = ref([])
const unrecognized = ref([])
const hotConfigs = ref([])
const savingHotConfig = ref(false)
const batchHandling = ref(false)
const exportingUnrecognized = ref(false)
const selectedUnrecognizedIds = ref([])
const hotPickerOpen = ref(false)
const hotPickerKeyword = ref('')
const hotPickerModule = ref('')
const hotPickerLoading = ref(false)
const kbEntries = ref([])
const selectedKbEntryIds = ref([])
const hotConfigValidUntil = ref('')
const modules = ['考务通知', '教学运行', '学业帮扶', '心理辅导']
const REQUIRED_HOT_CONFIG_COUNT = 5

const topUnrecognized = computed(() => overview.value.topUnrecognized || [])
const visibleUnrecognizedIds = computed(() => unrecognized.value.map(item => item.id).filter(Boolean))
const allUnrecognizedSelected = computed(() => {
  return visibleUnrecognizedIds.value.length > 0
    && visibleUnrecognizedIds.value.every(id => selectedUnrecognizedIds.value.includes(id))
})
const statCards = computed(() => [
  { label: '强命中', value: overview.value.strongHitCount || 0, color: '#35b77a' },
  { label: '弱命中', value: overview.value.weakHitCount || 0, color: '#f2a10b' },
  { label: '未命中', value: overview.value.noHitCount || 0, color: '#ef476f' },
  { label: '未识别待处理', value: overview.value.unrecognizedPending || 0, color: '#ffc743' }
])
const hotPickerSelectionValid = computed(() => selectedKbEntryIds.value.length === REQUIRED_HOT_CONFIG_COUNT)
const hotPickerSelectionTip = computed(() => {
  const count = selectedKbEntryIds.value.length
  if (count === REQUIRED_HOT_CONFIG_COUNT) return '数量正确，可以保存'
  if (count < REQUIRED_HOT_CONFIG_COUNT) return `还需选择 ${REQUIRED_HOT_CONFIG_COUNT - count} 条`
  return `已超出 ${count - REQUIRED_HOT_CONFIG_COUNT} 条`
})

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
    selectedUnrecognizedIds.value = selectedUnrecognizedIds.value.filter(id => visibleUnrecognizedIds.value.includes(id))
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

function isUnrecognizedSelected(id) {
  return selectedUnrecognizedIds.value.includes(id)
}

function toggleUnrecognized(id) {
  if (!id) return
  selectedUnrecognizedIds.value = isUnrecognizedSelected(id)
    ? selectedUnrecognizedIds.value.filter(selectedId => selectedId !== id)
    : [...selectedUnrecognizedIds.value, id]
}

function toggleAllUnrecognized() {
  selectedUnrecognizedIds.value = allUnrecognizedSelected.value ? [] : [...visibleUnrecognizedIds.value]
}

async function loadHotConfigs() {
  hotConfigs.value = await apiGet('/api/stat/hot-question-configs')
}

async function openHotPicker() {
  hotPickerOpen.value = true
  selectedKbEntryIds.value = []
  hotConfigValidUntil.value = ''
  await searchKbEntries()
}

function closeHotPicker() {
  hotPickerOpen.value = false
  hotConfigValidUntil.value = ''
}

async function searchKbEntries() {
  hotPickerLoading.value = true
  try {
    kbEntries.value = await apiGet('/api/kb/entries', {
      keyword: hotPickerKeyword.value,
      moduleType: hotPickerModule.value,
      status: 1
    })
  } finally {
    hotPickerLoading.value = false
  }
}

function toggleKbEntry(id) {
  if (!id) return
  if (selectedKbEntryIds.value.includes(id)) {
    selectedKbEntryIds.value = selectedKbEntryIds.value.filter(selectedId => selectedId !== id)
    return
  }
  if (selectedKbEntryIds.value.length >= REQUIRED_HOT_CONFIG_COUNT) {
    return
  }
  selectedKbEntryIds.value = [...selectedKbEntryIds.value, id]
}

function isKbEntryDisabled(id) {
  return selectedKbEntryIds.value.length >= REQUIRED_HOT_CONFIG_COUNT
    && !selectedKbEntryIds.value.includes(id)
}

async function saveSelectedHotConfigs() {
  if (!hotPickerSelectionValid.value) {
    return
  }
  const selectedEntries = kbEntries.value.filter(entry => selectedKbEntryIds.value.includes(entry.id))
  if (selectedEntries.length !== REQUIRED_HOT_CONFIG_COUNT) return
  savingHotConfig.value = true
  try {
    await Promise.all(selectedEntries.map((entry, index) => apiJson('/api/stat/hot-question-configs', {
      questionText: entry.question,
      answerText: entry.answer,
      moduleType: entry.moduleType,
      validUntil: hotConfigValidUntil.value || null,
      enabled: 1,
      sortOrder: hotConfigs.value.length + index
    })))
    closeHotPicker()
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

async function batchMarkHandled() {
  if (selectedUnrecognizedIds.value.length === 0) return
  batchHandling.value = true
  try {
    await apiJson('/api/admin/unrecognized/batch-update-status', {
      ids: selectedUnrecognizedIds.value,
      status: 1
    })
    selectedUnrecognizedIds.value = []
    await loadData()
  } finally {
    batchHandling.value = false
  }
}

async function exportUnrecognized() {
  exportingUnrecognized.value = true
  try {
    const query = selectedUnrecognizedIds.value.length
      ? `?ids=${selectedUnrecognizedIds.value.join(',')}`
      : ''
    await apiDownload(`/api/admin/unrecognized/export${query}`, '未识别问题列表.xlsx')
  } finally {
    exportingUnrecognized.value = false
  }
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

const currentDateTime = computed(() => {
  const date = new Date()
  date.setSeconds(0, 0)
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
})

onMounted(loadData)
</script>

<style scoped>
.dashboard-content {
  align-content: start;
}

.dashboard-controls {
  min-height: 88px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.dashboard-controls h3 {
  margin: 0;
  font-size: 22px;
  color: #082f7a;
}

.dashboard-controls p {
  margin: 6px 0 0;
  color: #6e82b1;
  font-weight: 800;
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
.hot-config-list {
  display: grid;
  gap: 10px;
  margin-top: 16px;
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
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(8, 24, 56, 0.38);
  display: grid;
  place-items: center;
  padding: 20px;
}
.hot-picker-modal {
  width: min(860px, calc(100vw - 40px));
  max-height: min(760px, calc(100vh - 56px));
  border-radius: 14px;
  background: #fff;
  padding: 22px;
  display: grid;
  grid-template-rows: auto auto auto minmax(220px, 1fr) auto;
  gap: 16px;
  box-shadow: 0 24px 80px rgba(13, 21, 40, 0.24);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.modal-head h3 {
  margin: 0;
  color: #173875;
}
.modal-close {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: #f1f6ff;
  color: #173875;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.hot-picker-tools {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px auto;
  gap: 12px;
}
.hot-picker-deadline {
  display: grid;
  grid-template-columns: auto minmax(220px, 320px) 1fr;
  align-items: center;
  gap: 12px;
  color: #173875;
  font-weight: 900;
}
.hot-picker-deadline small {
  color: #6e82b1;
  font-weight: 700;
}
.hot-picker-list {
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 10px;
}
.hot-picker-row {
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #f8fbff;
  padding: 12px;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  cursor: pointer;
}
.hot-picker-row.selected {
  border-color: #3182ce;
  background: #ebf8ff;
}
.hot-picker-row:has(input:disabled):not(.selected) {
  cursor: not-allowed;
  opacity: 0.58;
}
.hot-picker-row strong,
.hot-picker-row span {
  display: block;
  color: #173875;
}
.hot-picker-row p {
  margin: 6px 0;
  color: #49659c;
  line-height: 1.5;
  font-weight: 800;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
.modal-actions span {
  margin-right: auto;
  color: #49659c;
  font-weight: 900;
}
.modal-actions .selection-count {
  display: grid;
  gap: 4px;
}
.selection-count em {
  color: #d93c58;
  font-size: 13px;
  font-style: normal;
}
.selection-count.valid {
  color: #159461;
}
.selection-count.valid em {
  color: #159461;
}

@media (max-width: 1200px) {
  .stats-grid,
  .dashboard-grid {
    grid-template-columns: 1fr 1fr;
  }
  .hot-picker-tools {
    grid-template-columns: 1fr 1fr;
  }
  .hot-picker-deadline {
    grid-template-columns: auto 1fr;
  }
  .hot-picker-deadline small {
    grid-column: 2;
  }
}

@media (max-width: 760px) {
  .stats-grid,
  .dashboard-grid,
  .hot-picker-tools {
    grid-template-columns: 1fr;
  }
  .hot-picker-deadline {
    grid-template-columns: 1fr;
  }
  .hot-picker-deadline small {
    grid-column: auto;
  }
}
</style>

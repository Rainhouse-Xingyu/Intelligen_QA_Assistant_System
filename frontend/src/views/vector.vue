<template>
  <div class="admin-shell">
    <AdminSidebar active="vector" />

    <main class="admin-main">
      <AdminTopbar title="向量检索调试" />

      <section class="admin-content">
        <div class="admin-card">
          <h3 class="section-title">
            <SearchIcon />
            检索测试
          </h3>
          <div class="search-row">
            <input v-model="query" class="input" placeholder="请输入要检索的问题" @keyup.enter="search" />
            <select v-model="moduleType" class="select">
              <option value="">全部模块</option>
              <option v-for="module in modules" :key="module" :value="module">{{ module }}</option>
            </select>
            <select v-model.number="topK" class="select">
              <option :value="3">TopK 3</option>
              <option :value="5">TopK 5</option>
              <option :value="10">TopK 10</option>
            </select>
            <button class="btn primary" :disabled="loading" @click="search">
              <SearchIcon />
              {{ loading ? '检索中' : '检索' }}
            </button>
          </div>
        </div>

        <div class="admin-card summary-card">
          <span>命中状态：</span>
          <span :class="['tag', hitClass]">{{ response?.hitLabel || '-' }}</span>
          <span>最高得分：</span>
          <strong class="score">{{ formatScore(response?.topScore) }}</strong>
          <span>答案来源：</span>
          <strong>{{ response?.answerSource || response?.answer ? 'RAG' : '-' }}</strong>
          <span>耗时：</span>
          <strong>{{ response?.responseTimeMs ?? '-' }}ms</strong>
        </div>

        <div class="admin-card">
          <h3 class="section-title">检索结果（{{ results.length }}）</h3>
          <div v-if="results.length === 0" class="empty">输入问题后点击检索</div>
          <article v-for="(item, index) in results" :key="item.knowledgeId || index" class="result-card">
            <div class="result-main">
              <div class="result-title">
                <strong>#{{ index + 1 }}</strong>
                <span>知识ID: {{ item.knowledgeId }}</span>
                <span class="tag blue">{{ item.moduleType || '未分类' }}</span>
              </div>
              <p><b>Q:</b> {{ item.question }}</p>
              <p><b>A:</b> {{ item.answer }}</p>
            </div>
            <div class="result-score">
              <span>向量: {{ formatScore(item.vectorScore) }}</span>
              <span>重排: {{ formatScore(item.rerankScore) }}</span>
              <span>最终: <b>{{ formatScore(item.finalScore) }}</b></span>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { apiJson } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, SearchIcon } from './shared/adminParts'

const modules = ['考务通知', '教学运行', '学业帮扶', '心理辅导']
const query = ref('挂科了怎么办')
const moduleType = ref('')
const topK = ref(5)
const response = ref(null)
const loading = ref(false)

const results = computed(() => response.value?.references || response.value?.results || [])
const hitClass = computed(() => {
  const status = response.value?.hitStatus
  if (status === 2) return 'green'
  if (status === 1) return 'yellow'
  if (status === 0) return 'red'
  return 'blue'
})

async function search() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    response.value = await apiJson('/api/chat/ai-core', {
      query: query.value,
      moduleType: moduleType.value || undefined
    })
    if (topK.value && response.value?.references) {
      response.value.references = response.value.references.slice(0, topK.value)
    }
  } finally {
    loading.value = false
  }
}

function formatScore(value) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return '-'
  return Number(value).toFixed(2)
}
</script>

<style scoped>
.search-row {
  display: grid;
  grid-template-columns: 1fr 220px 170px 104px;
  gap: 14px;
}

.summary-card {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 16px;
  font-weight: 900;
}

.score,
.result-title strong,
.result-score b {
  color: #ffc743;
  font-size: 20px;
}

.result-card {
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  min-height: 118px;
  padding: 18px 20px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 18px;
  margin-top: 14px;
}

.result-title,
.result-score {
  display: flex;
  align-items: center;
  gap: 14px;
  font-weight: 900;
}

.result-main p {
  margin: 12px 0 0;
  font-weight: 800;
}

.result-score {
  min-width: 280px;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .search-row,
  .result-card {
    grid-template-columns: 1fr;
  }

  .result-score {
    justify-content: flex-start;
  }
}
</style>


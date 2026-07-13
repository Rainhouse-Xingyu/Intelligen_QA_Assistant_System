<template>
  <div class="student-survey-page">
    <header class="survey-header">
      <button class="back-btn" @click="$emit('go-home')">返回首页</button>
      <div>
        <h1>问卷调查</h1>
        <p>已发布问卷会提前显示，未到开始时间时只能查看，不能作答</p>
      </div>
      <button class="refresh-btn" :disabled="loading" @click="reloadAll">刷新</button>
    </header>

    <main class="survey-layout">
      <aside class="survey-list">
        <div class="list-head">
          <strong>待填写</strong>
          <span>{{ surveys.length }} 份</span>
        </div>
        <article
          v-for="item in surveys"
          :key="item.survey.id"
          :class="['survey-card', { active: selectedId === item.survey.id }]"
          @click="selectSurvey(item.survey.id)"
        >
          <div>
            <strong>{{ item.survey.title }}</strong>
            <span>{{ formatTime(item.survey.startTime || item.survey.publishedAt || item.survey.createdAt) }}</span>
          </div>
          <em :class="{ upcoming: !isSurveyOpen(item.survey) }">
            {{ isSurveyOpen(item.survey) ? '待填写' : '未开始' }}
          </em>
        </article>
        <div v-if="surveys.length === 0" class="empty-state">暂无待填写问卷</div>
      </aside>

      <section class="answer-panel">
        <div v-if="!current" class="empty-state">请选择一份问卷</div>
        <template v-else>
          <div class="answer-head">
            <div>
              <h2>{{ current.survey.title }}</h2>
              <p>{{ current.survey.purpose || current.survey.description || '请根据实际情况完成问卷。' }}</p>
              <small>
                结束时间：{{ formatTime(current.survey.endTime) }}
                <span v-if="current.survey.startTime"> · 开始时间：{{ formatTime(current.survey.startTime) }}</span>
                <span v-if="current.survey.scopeText"> · 范围：{{ current.survey.scopeText }}</span>
              </small>
            </div>
            <span :class="['status-pill', isCurrentOpen ? 'todo' : 'upcoming']">
              {{ isCurrentOpen ? '未提交' : '未开始' }}
            </span>
          </div>

          <div v-if="!isCurrentOpen" class="locked-notice">
            这份问卷将在 {{ formatTime(current.survey.startTime) }} 开始，现在可以提前查看题目，到时间后才能选择答案并提交。
          </div>

          <form class="question-list" @submit.prevent="submitSurvey">
            <article
              v-for="question in current.questions"
              :id="`question-${question.id}`"
              :key="question.id"
              :class="['question-item', { locked: !isCurrentOpen, missing: missingQuestionId === question.id }]"
            >
              <div class="question-title">
                <b>{{ question.questionNo }}</b>
                <span>{{ question.questionText }}</span>
                <em>{{ question.required === 1 ? '必填' : '选填' }}</em>
              </div>
              <div v-if="missingQuestionId === question.id" class="question-missing-tip">
                第 {{ question.questionNo }} 题未作答
              </div>

              <div v-if="question.questionType === 1" class="scale-options">
                <label
                  v-for="option in getQuestionOptions(question)"
                  :key="option.value"
                  :class="{ selected: answers[question.id] === option.value }"
                >
                  <input
                    v-model.number="answers[question.id]"
                    type="radio"
                    :name="`q-${question.id}`"
                    :value="option.value"
                    :disabled="!isCurrentOpen"
                    @change="clearMissing(question.id)"
                  />
                  <strong class="option-dot"></strong>
                  <span>{{ option.label }}</span>
                </label>
              </div>

              <textarea
                v-else
                v-model="textAnswers[question.id]"
                class="text-answer"
                placeholder="可以写下你的想法，也可以留空"
                :disabled="!isCurrentOpen"
                @input="clearMissing(question.id)"
              ></textarea>
            </article>

            <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>

            <div class="submit-row">
              <span>{{ current.questions.length }} 道题</span>
              <button class="submit-btn" type="submit" :disabled="submitting || !isCurrentOpen">
                {{ !isCurrentOpen ? '未到开始时间' : submitting ? '提交中...' : '提交问卷' }}
              </button>
            </div>
          </form>
        </template>
      </section>

    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { apiGet, apiJson } from '../js/adminApi'

const emit = defineEmits(['go-home', 'navigate-login'])

const scaleOptions = [
  { value: 1, label: '完全符合' },
  { value: 2, label: '比较符合' },
  { value: 3, label: '一般符合' },
  { value: 4, label: '比较不符合' },
  { value: 5, label: '不符合' }
]

const supportFrequencyOptions = [
  { value: 1, label: '每月1次' },
  { value: 2, label: '每两周1次' },
  { value: 3, label: '每周1次' },
  { value: 4, label: '每周2次' },
  { value: 5, label: '不需要对我进行帮扶' }
]

const surveys = ref([])
const selectedId = ref(null)
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const missingQuestionId = ref(null)
const answers = reactive({})
const textAnswers = reactive({})

const current = computed(() => surveys.value.find(item => item.survey.id === selectedId.value) || null)
const now = ref(Date.now())
const isCurrentOpen = computed(() => current.value ? isSurveyOpen(current.value.survey) : false)

function isSurveyOpen(survey) {
  if (!survey) return false
  const start = survey.startTime ? new Date(survey.startTime).getTime() : null
  const end = survey.endTime ? new Date(survey.endTime).getTime() : null
  return (!start || start <= now.value) && (!end || end >= now.value)
}

function getQuestionOptions(question) {
  const text = question?.questionText || ''
  if (text.includes('帮扶频率')) {
    return supportFrequencyOptions
  }
  return scaleOptions
}

async function reloadAll() {
  await loadSurveys()
}

async function loadSurveys() {
  loading.value = true
  errorMessage.value = ''
  try {
    surveys.value = await apiGet('/api/survey/student/list')
    if (surveys.value.length === 0) {
      selectedId.value = null
      clearAnswers()
      return
    }
    if (!selectedId.value || !surveys.value.some(item => item.survey.id === selectedId.value)) {
      selectSurvey(surveys.value[0].survey.id)
    }
  } catch (e) {
    errorMessage.value = e.message || '加载问卷失败'
  } finally {
    loading.value = false
  }
}

function selectSurvey(id) {
  selectedId.value = id
  errorMessage.value = ''
  missingQuestionId.value = null
  clearAnswers()
}

function clearAnswers() {
  Object.keys(answers).forEach(key => delete answers[key])
  Object.keys(textAnswers).forEach(key => delete textAnswers[key])
}

async function submitSurvey() {
  if (!current.value) return
  errorMessage.value = ''
  missingQuestionId.value = null
  if (!isCurrentOpen.value) {
    errorMessage.value = `问卷还未开始，请在 ${formatTime(current.value.survey.startTime)} 后再填写`
    return
  }
  const missing = current.value.questions.find(isQuestionMissing)
  if (missing) {
    missingQuestionId.value = missing.id
    errorMessage.value = `第 ${missing.questionNo} 题未作答`
    scrollToQuestion(missing.id)
    return
  }

  submitting.value = true
  try {
    const payload = {
      answers: current.value.questions.map(question => ({
        questionId: question.id,
        numericAnswer: question.questionType === 1 ? answers[question.id] : undefined,
        textAnswer: question.questionType === 2 ? (textAnswers[question.id] || '') : undefined
      }))
    }
    await apiJson(`/api/survey/student/${current.value.survey.id}/submit`, payload)
    await reloadAll()
    emit('go-home')
  } catch (e) {
    errorMessage.value = e.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

function isQuestionMissing(question) {
  if (question.questionType === 1) {
    return !answers[question.id]
  }
  return question.required === 1 && !(textAnswers[question.id] || '').trim()
}

function clearMissing(questionId) {
  if (missingQuestionId.value !== questionId) return
  const question = current.value?.questions.find(item => item.id === questionId)
  if (question && !isQuestionMissing(question)) {
    missingQuestionId.value = null
    errorMessage.value = ''
  }
}

function scrollToQuestion(questionId) {
  requestAnimationFrame(() => {
    const target = document.getElementById(`question-${questionId}`)
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

let timer = null

onMounted(() => {
  reloadAll()
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 30000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.student-survey-page {
  min-height: 100vh;
  background: linear-gradient(125deg, #f8fbff 0%, #dbe9fb 52%, #f6f3e9 100%);
  color: #173875;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
  padding: 24px;
}

.survey-header {
  min-height: 86px;
  display: grid;
  grid-template-columns: 120px 1fr 110px;
  align-items: center;
  gap: 18px;
  margin-bottom: 22px;
}

.survey-header h1 {
  margin: 0;
  font-size: 32px;
}

.survey-header p {
  margin: 6px 0 0;
  color: #65799f;
  font-weight: 800;
}

.back-btn,
.refresh-btn,
.submit-btn {
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  padding: 0 18px;
  font-size: 16px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(20, 50, 90, 0.12);
}

.back-btn,
.refresh-btn {
  background: #fff;
  color: #173875;
}

.submit-btn {
  min-width: 128px;
  color: #fff;
  background: #0d1528;
}

.submit-btn:disabled,
.refresh-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.survey-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 22px;
}

.survey-list,
.answer-panel {
  border: 1px solid #dbe8fb;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 10px 28px rgba(22, 54, 100, 0.08);
}

.survey-list,
.trend-panel {
  padding: 18px;
  align-self: start;
}

.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.list-head strong {
  font-size: 18px;
}

.list-head span {
  color: #65799f;
  font-weight: 900;
}

.survey-card {
  min-height: 92px;
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #fff;
  padding: 14px;
  display: grid;
  gap: 12px;
  cursor: pointer;
  margin-bottom: 12px;
}

.survey-card.active,
.survey-card:hover {
  border-color: #9ec6ff;
  box-shadow: 0 8px 18px rgba(49, 93, 188, 0.12);
}

.survey-card strong,
.survey-card span,
.survey-card em {
  display: block;
}

.survey-card strong {
  font-weight: 900;
  line-height: 1.35;
}

.survey-card span {
  margin-top: 6px;
  color: #8293bb;
  font-weight: 800;
  font-size: 13px;
}

.survey-card em {
  justify-self: start;
  min-height: 26px;
  padding: 4px 12px;
  border-radius: 8px;
  color: #b47600;
  background: #fff3c4;
  font-style: normal;
  font-weight: 900;
}

.survey-card em.upcoming {
  color: #315dbc;
  background: #eaf2ff;
}

.answer-panel {
  min-height: calc(100vh - 132px);
  padding: 28px;
}

.answer-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
  margin-bottom: 20px;
}

.answer-head h2 {
  margin: 0;
  font-size: 28px;
}

.answer-head p {
  margin: 8px 0 0;
  color: #65799f;
  font-weight: 800;
}

.answer-head small {
  display: block;
  margin-top: 8px;
  color: #8293bb;
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

.status-pill.todo {
  color: #b47600;
  background: #fff3c4;
}

.status-pill.upcoming {
  color: #315dbc;
  background: #eaf2ff;
}

.locked-notice {
  min-height: 48px;
  border: 1px solid #bdd6ff;
  border-radius: 8px;
  background: #f1f6ff;
  color: #315dbc;
  display: flex;
  align-items: center;
  padding: 0 14px;
  margin-bottom: 18px;
  font-weight: 900;
}

.question-list {
  display: grid;
  gap: 16px;
}

.question-item {
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.question-item.locked {
  background: #f8fbff;
}

.question-item.missing {
  border-color: #f0446b;
  background: #fff8fa;
  box-shadow: 0 10px 24px rgba(240, 68, 107, 0.12);
}

.question-title {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 10px;
  align-items: start;
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
}

.question-missing-tip {
  min-height: 36px;
  border: 1px solid #ffd0dc;
  border-radius: 8px;
  background: #ffe9ee;
  color: #d93c58;
  display: flex;
  align-items: center;
  padding: 0 12px;
  margin-top: 12px;
  font-weight: 900;
}

.scale-options {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.scale-options label {
  min-height: 72px;
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #f8fbff;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 4px;
  padding: 8px;
  text-align: center;
  cursor: pointer;
  font-weight: 900;
}

.scale-options label:has(input:disabled),
.text-answer:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.scale-options label.selected {
  border-color: #315dbc;
  background: #eaf2ff;
  box-shadow: inset 0 0 0 1px #315dbc;
}

.scale-options input {
  position: absolute;
  opacity: 0;
}

.scale-options strong {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #315dbc;
  background: #fff;
}

.scale-options label.selected strong {
  background: #315dbc;
  box-shadow: inset 0 0 0 3px #fff;
}

.scale-options span {
  font-size: 14px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.text-answer {
  width: 100%;
  min-height: 120px;
  margin-top: 14px;
  border: 1px solid #d8e4f5;
  border-radius: 8px;
  background: #f8fbff;
  color: #173875;
  font-size: 16px;
  font-weight: 800;
  padding: 12px;
  resize: vertical;
  outline: none;
}

.submit-row {
  min-height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 900;
}

.error-message {
  min-height: 44px;
  border: 1px solid #ffd0dc;
  border-radius: 8px;
  background: #ffe9ee;
  color: #d93c58;
  display: flex;
  align-items: center;
  padding: 0 14px;
  font-weight: 900;
}

.empty-state {
  min-height: 140px;
  display: grid;
  place-items: center;
  color: #173875;
  font-weight: 900;
  text-align: center;
}

@media (max-width: 1280px) {
  .survey-layout,
  .survey-header {
    grid-template-columns: 1fr;
  }

  .scale-options {
    grid-template-columns: 1fr;
  }
}
</style>

import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { apiGet, apiForm } from './adminApi'

const HISTORY_RETENTION_DAYS = 30
const HISTORY_RETENTION_MS = HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000

export default {
  name: 'Home',
  emits: ['start-chat', 'navigate-login', 'navigate-survey'],
  setup(props, { emit }) {
    const question = ref('')
    const selectedCategory = ref(null)
    const shakeAlert = ref(false)
    const logoRef = ref(null)
    const logoTone = ref('logo--dark')
    const commonQuestions = ref([])
    const categories = [
      { label: '考务', value: '考务通知' },
      { label: '教学', value: '教学运行' },
      { label: '心理', value: '心理辅导' }
    ]

    // --- Login state ---
    const isLoggedIn = ref(false)
    const userInfo = ref(null)
    const pendingSurveyCount = ref(0)

    const isStudent = computed(() => userInfo.value?.role === 1)
    const hasPendingSurvey = computed(() => isLoggedIn.value && isStudent.value && pendingSurveyCount.value > 0)

    const parseRgb = (value) => {
      const match = value?.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([.\d]+))?\)/)
      if (!match) return null
      const alpha = match[4] === undefined ? 1 : Number(match[4])
      if (alpha === 0) return null
      return {
        r: Number(match[1]),
        g: Number(match[2]),
        b: Number(match[3])
      }
    }

    const findBackgroundColor = (element) => {
      let current = element

      while (current && current !== document.documentElement) {
        const color = parseRgb(getComputedStyle(current).backgroundColor)
        if (color) return color
        current = current.parentElement
      }

      return parseRgb(getComputedStyle(document.body).backgroundColor)
        || parseRgb(getComputedStyle(document.documentElement).backgroundColor)
        || { r: 255, g: 255, b: 255 }
    }

    const getLuminance = ({ r, g, b }) => (0.299 * r) + (0.587 * g) + (0.114 * b)

    const updateLogoTone = () => {
      const logo = logoRef.value
      if (!logo) return

      const rect = logo.getBoundingClientRect()
      if (!rect.width || !rect.height) return

      const samplePoints = [
        [rect.left + rect.width / 2, rect.top + rect.height / 2],
        [rect.left + 8, rect.top + 8],
        [rect.right - 8, rect.top + 8],
        [rect.left + 8, rect.bottom - 8],
        [rect.right - 8, rect.bottom - 8]
      ]

      const luminanceSamples = samplePoints
        .map(([x, y]) => document.elementFromPoint(x, y))
        .filter(Boolean)
        .map(findBackgroundColor)
        .map(getLuminance)

      const average = luminanceSamples.length
        ? luminanceSamples.reduce((sum, value) => sum + value, 0) / luminanceSamples.length
        : 255

      const nextTone = average > 150 ? 'logo--dark' : 'logo--light'
      if (logoTone.value !== nextTone) {
        logoTone.value = nextTone
      }
    }

    // --- Sidebar state ---
    const conversations = ref([])

    const loadConversations = () => {
      try {
        const stored = localStorage.getItem('chat_conversations')
        const parsed = stored ? JSON.parse(stored) : []
        const now = Date.now()
        conversations.value = (Array.isArray(parsed) ? parsed : [])
          .filter(conv => {
            const time = new Date(conv.updatedAt || conv.createdAt || 0).getTime()
            return Number.isFinite(time) && now - time <= HISTORY_RETENTION_MS
          })
        saveConversations()
      } catch {
        conversations.value = []
      }
    }

    const saveConversations = () => {
      localStorage.setItem('chat_conversations', JSON.stringify(conversations.value))
    }

    const newConversation = () => {
      question.value = ''
      selectedCategory.value = null
    }

    const selectConversation = (conv) => {
      const fallbackQuestion = conv.firstQuestion || conv.title || ''
      const restoredMessages = Array.isArray(conv.messages) && conv.messages.length
        ? conv.messages
        : fallbackQuestion
          ? [
              { type: 'user', content: fallbackQuestion },
              { type: 'bot', content: '这是之前的历史提问，当前本地没有保存当时的回答。你可以继续追问，我会接着这条会话帮你处理。' }
            ]
          : []
      emit('start-chat', {
        question: '',
        category: conv.category || null,
        historyId: conv.id,
        messages: restoredMessages
      })
    }

    const deleteConversation = (id) => {
      conversations.value = conversations.value.filter(c => c.id !== id)
      saveConversations()
    }

    const normalizeQuestionItem = (item) => ({
      ...item,
      questionText: item.questionText || item.question_text || item.name || item.question || '',
      answerText: item.answerText || item.answer_text || item.answer || '',
      moduleType: item.moduleType || item.module_type || ''
    })

    const loadCommonQuestions = async () => {
      try {
        const data = await apiGet('/api/chat/common-questions', { limit: 8 })
        commonQuestions.value = Array.isArray(data)
          ? data.map(normalizeQuestionItem).filter(item => item.questionText).slice(0, 8)
          : []
      } catch {
        commonQuestions.value = []
      }
    }

    const handleCommonQuestion = (item) => {
      const normalized = normalizeQuestionItem(item)
      if (!normalized.questionText) return
      question.value = normalized.questionText
      if (normalized.moduleType) {
        selectedCategory.value = normalized.moduleType
      }
      handleSendFromHome(normalized)
    }

    // --- Auth methods ---
    const getToken = () => {
      return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
    }

    const loadSurveyStatus = async () => {
      pendingSurveyCount.value = 0
      if (!isLoggedIn.value || !isStudent.value) return

      try {
        const surveys = await apiGet('/api/survey/student/list')
        pendingSurveyCount.value = Array.isArray(surveys)
          ? surveys.filter(item => item && item.submitted !== true).length
          : 0
      } catch {
        pendingSurveyCount.value = 0
      }
    }

    const restoreSession = async () => {
      const token = getToken()
      if (!token) return

      try {
        userInfo.value = await apiGet('/api/auth/info')
        isLoggedIn.value = true
        loadConversations()
        await loadSurveyStatus()
      } catch {
        clearAuth()
      }
    }

    const clearAuth = () => {
      localStorage.removeItem('token')
      localStorage.removeItem('user_info')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user_info')
      isLoggedIn.value = false
      userInfo.value = null
      pendingSurveyCount.value = 0
    }

    const handleLogout = async () => {
      try {
        const token = getToken()
        if (token) {
          await apiForm('/api/auth/logout')
        }
      } catch {
        // ignore
      }
      clearAuth()
      conversations.value = []
    }

    const handleLoginClick = () => {
      if (isLoggedIn.value) {
        handleLogout()
      } else {
        emit('navigate-login')
      }
    }

    const handleSurveyClick = () => {
      if (!isLoggedIn.value) {
        emit('navigate-login')
        return
      }
      if (!isStudent.value) return
      emit('navigate-survey')
    }

    const toggleCategory = (category) => {
      selectedCategory.value = selectedCategory.value === category ? null : category
    }

    const handleSendFromHome = (directItem = null) => {
      if (!question.value.trim()) return
      const trimmedQuestion = question.value.trim()
      const directAnswer = directItem?.answerText?.trim()
      const initialMessages = directAnswer
        ? [
            { type: 'user', content: trimmedQuestion },
            { type: 'bot', content: directAnswer, durationMs: 0, answerSource: 'COMMON_DIRECT' }
          ]
        : []

      let historyId = ''

      if (isLoggedIn.value) {
        historyId = 'conv_' + Date.now()
        const conv = {
          id: historyId,
          title: trimmedQuestion.substring(0, 30),
          firstQuestion: trimmedQuestion,
          category: selectedCategory.value,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          messages: initialMessages
        }
        conversations.value.unshift(conv)
        saveConversations()
      }

      emit('start-chat', {
        question: directAnswer ? '' : trimmedQuestion,
        category: selectedCategory.value,
        historyId,
        messages: initialMessages
      })
      question.value = ''
    }

    let logoToneTimer = 0
    let logoToneFrame = 0

    const scheduleLogoToneUpdate = () => {
      if (logoToneFrame) return
      logoToneFrame = window.requestAnimationFrame(() => {
        logoToneFrame = 0
        updateLogoTone()
      })
    }

    onMounted(() => {
      restoreSession()
      loadCommonQuestions()
      nextTick(scheduleLogoToneUpdate)
      window.addEventListener('resize', scheduleLogoToneUpdate)
      window.addEventListener('scroll', scheduleLogoToneUpdate, true)
      logoToneTimer = window.setInterval(scheduleLogoToneUpdate, 1000)
    })

    onUnmounted(() => {
      window.removeEventListener('resize', scheduleLogoToneUpdate)
      window.removeEventListener('scroll', scheduleLogoToneUpdate, true)
      if (logoToneFrame) {
        window.cancelAnimationFrame(logoToneFrame)
      }
      window.clearInterval(logoToneTimer)
    })

    return {
      question,
      selectedCategory,
      shakeAlert,
      logoRef,
      logoTone,
      commonQuestions,
      categories,
      isLoggedIn,
      userInfo,
      isStudent,
      pendingSurveyCount,
      hasPendingSurvey,
      conversations,
      handleSendFromHome,
      handleLoginClick,
      handleSurveyClick,
      toggleCategory,
      loadCommonQuestions,
      handleCommonQuestion,
      newConversation,
      selectConversation,
      deleteConversation
    }
  }
}

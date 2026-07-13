import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { apiGet, apiForm } from './adminApi'

const HISTORY_RETENTION_DAYS = 30
const HISTORY_RETENTION_MS = HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000
const DEFAULT_NAV_CATEGORIES = [
  {
    id: 'fallback-exam',
    label: '考务资料',
    value: '考务通知',
    children: [
      { id: 'fallback-exam-arrange', label: '考试安排', value: '考试安排' },
      { id: 'fallback-exam-score', label: '成绩查询', value: '成绩查询' },
      { id: 'fallback-exam-makeup', label: '补考缓考', value: '补考缓考' }
    ]
  },
  {
    id: 'fallback-study',
    label: '教学帮扶',
    value: '学业帮扶',
    children: [
      { id: 'fallback-study-warning', label: '学业预警', value: '学业预警' },
      { id: 'fallback-study-course', label: '选课重修', value: '选课重修' },
      { id: 'fallback-study-resource', label: '学习支持', value: '学习支持' }
    ]
  },
  {
    id: 'fallback-mental',
    label: '心理指导',
    value: '心理辅导',
    children: [
      { id: 'fallback-mental-consult', label: '心理咨询', value: '心理咨询' },
      { id: 'fallback-mental-stress', label: '压力调适', value: '压力调适' },
      { id: 'fallback-mental-help', label: '求助渠道', value: '求助渠道' }
    ]
  }
]

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
    const commonQuestionsLoading = ref(false)
    const hotQuestions = ref([])
    const categoryTree = ref([])
    const commonQuestionModule = ref('')
    const hotBubbleVisible = ref(false)
    const mascotPosition = ref({ x: 28, y: 96 })
    const mascotDragging = ref(false)
    const mascotBubblePlacement = computed(() => {
      const margin = 390
      const verticalMargin = 250
      const x = mascotPosition.value.x
      const y = mascotPosition.value.y
      if (x > window.innerWidth - margin) return 'bubble-left'
      if (y > window.innerHeight - verticalMargin) return 'bubble-up'
      return 'bubble-right'
    })
    let hotBubbleTimer = 0
    let dragOffset = { x: 0, y: 0 }
    const categories = [
      { label: '考务', value: '考务通知' },
      { label: '教学', value: '教学运行' },
      { label: '学业', value: '学业帮扶' },
      { label: '心理', value: '心理辅导' }
    ]
    const commonQuestionCategories = [
      { label: '全部', value: '' },
      ...categories
    ]
    const mascotQuestions = computed(() => (
      hotQuestions.value.length ? hotQuestions.value : commonQuestions.value.slice(0, 5)
    ))
    const topNavigationCategories = computed(() => {
      const source = categoryTree.value.length ? categoryTree.value : DEFAULT_NAV_CATEGORIES
      return source.slice(0, 3)
    })

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

    const normalizeCategoryItem = (item) => ({
      id: item.id,
      parentId: item.parentId ?? item.parent_id ?? null,
      label: item.name || item.label || '',
      value: item.name || item.value || '',
      level: Number(item.level || 0),
      sortOrder: Number(item.sortOrder ?? item.sort_order ?? 0),
      children: []
    })

    const sortCategories = (items) => [...items].sort((a, b) => {
      if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder
      return String(a.label).localeCompare(String(b.label), 'zh-CN')
    })

    const buildCategoryTree = (items) => {
      const normalized = Array.isArray(items)
        ? items.map(normalizeCategoryItem).filter(item => item.id != null && item.label)
        : []
      const byId = new Map(normalized.map(item => [item.id, item]))
      normalized.forEach(item => {
        if (item.parentId != null && byId.has(item.parentId)) {
          byId.get(item.parentId).children.push(item)
        }
      })
      return sortCategories(normalized.filter(item => item.level === 1 || item.parentId == null))
        .map(item => ({
          ...item,
          children: sortCategories(item.children.filter(child => child.level === 2 || child.children.length))
        }))
        .filter(item => item.label)
    }

    const loadCategoryTree = async () => {
      try {
        categoryTree.value = buildCategoryTree(await apiGet('/api/kb/categories'))
      } catch {
        categoryTree.value = []
      }
    }

    const loadCommonQuestions = async () => {
      commonQuestionsLoading.value = true
      try {
        const params = { limit: 8 }
        if (commonQuestionModule.value) {
          params.moduleType = commonQuestionModule.value
        }
        const data = await apiGet('/api/chat/common-questions', params)
        commonQuestions.value = Array.isArray(data)
          ? data.map(normalizeQuestionItem).filter(item => item.questionText).slice(0, 8)
          : []
      } catch {
        commonQuestions.value = []
      } finally {
        commonQuestionsLoading.value = false
      }
    }

    const loadHotQuestions = async () => {
      try {
        const data = await apiGet('/api/chat/suggested-questions', { limit: 5 })
        hotQuestions.value = Array.isArray(data)
          ? data.map(normalizeQuestionItem).filter(item => item.questionText).slice(0, 5)
          : []
      } catch {
        hotQuestions.value = []
      }
    }

    const showHotBubble = () => {
      hotBubbleVisible.value = true
      window.clearTimeout(hotBubbleTimer)
      hotBubbleTimer = window.setTimeout(() => {
        hotBubbleVisible.value = false
      }, 5000)
    }

    const closeHotBubble = () => {
      hotBubbleVisible.value = false
      window.clearTimeout(hotBubbleTimer)
    }

    const handleMascotTap = async () => {
      if (!hotQuestions.value.length) {
        await loadHotQuestions()
      }
      showHotBubble()
    }

    const getMascotBounds = () => ({
      maxX: Math.max(0, window.innerWidth - 138),
      maxY: Math.max(0, window.innerHeight - 154)
    })

    const clampMascotPosition = (x, y) => {
      const { maxX, maxY } = getMascotBounds()
      return {
        x: Math.min(maxX, Math.max(0, x)),
        y: Math.min(maxY, Math.max(0, y))
      }
    }

    const changeCommonQuestionModule = async (moduleType) => {
      commonQuestionModule.value = moduleType
      await loadCommonQuestions()
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

    const handleHotQuestion = (item) => {
      const normalized = normalizeQuestionItem(item)
      if (!normalized.questionText) return
      if (normalized.moduleType) {
        selectedCategory.value = normalized.moduleType
      }
      question.value = normalized.questionText
      handleSendFromHome(normalized)
      hotBubbleVisible.value = false
    }

    const handleCategoryShortcut = (group, child = null) => {
      const moduleType = group?.value || group?.label || ''
      if (!moduleType) return
      selectedCategory.value = moduleType
      const childLabel = child?.label || child?.value || ''
      const categoryText = childLabel
        ? `${group.label} > ${childLabel}`
        : group.label
      question.value = `我想查询「${categoryText}」相关问题`
      handleSendFromHome()
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
        await loadHotQuestions()
        showHotBubble()
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

    const startMascotDrag = (event) => {
      const pointer = event.touches?.[0] || event
      mascotDragging.value = true
      dragOffset = {
        x: pointer.clientX - mascotPosition.value.x,
        y: pointer.clientY - mascotPosition.value.y
      }
      window.addEventListener('mousemove', onMascotDrag)
      window.addEventListener('mouseup', stopMascotDrag)
      window.addEventListener('touchmove', onMascotDrag, { passive: false })
      window.addEventListener('touchend', stopMascotDrag)
    }

    const onMascotDrag = (event) => {
      if (!mascotDragging.value) return
      event.preventDefault?.()
      const pointer = event.touches?.[0] || event
      mascotPosition.value = clampMascotPosition(
        pointer.clientX - dragOffset.x,
        pointer.clientY - dragOffset.y
      )
    }

    const stopMascotDrag = () => {
      mascotDragging.value = false
      window.removeEventListener('mousemove', onMascotDrag)
      window.removeEventListener('mouseup', stopMascotDrag)
      window.removeEventListener('touchmove', onMascotDrag)
      window.removeEventListener('touchend', stopMascotDrag)
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
      loadCategoryTree()
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
      window.clearTimeout(hotBubbleTimer)
      stopMascotDrag()
    })

    return {
      question,
      selectedCategory,
      shakeAlert,
      logoRef,
      logoTone,
      commonQuestions,
      commonQuestionsLoading,
      hotQuestions,
      mascotQuestions,
      topNavigationCategories,
      commonQuestionModule,
      commonQuestionCategories,
      hotBubbleVisible,
      mascotPosition,
      mascotDragging,
      mascotBubblePlacement,
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
      changeCommonQuestionModule,
      handleCommonQuestion,
      handleHotQuestion,
      handleCategoryShortcut,
      showHotBubble,
      closeHotBubble,
      handleMascotTap,
      startMascotDrag,
      newConversation,
      selectConversation,
      deleteConversation
    }
  }
}

import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { apiGet, apiForm, apiUpload } from './adminApi'

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
    const isRecording = ref(false)
    const isVoiceProcessing = ref(false)
    const voiceStatus = ref('')
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
    let mediaRecorder = null
    let wavRecorder = null
    let recordingStream = null
    let recordingChunks = []
    let voiceStatusTimer = 0
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

    const setVoiceStatus = (status, clearAfter = 0) => {
      window.clearTimeout(voiceStatusTimer)
      voiceStatus.value = status
      if (clearAfter > 0) {
        voiceStatusTimer = window.setTimeout(() => {
          voiceStatus.value = ''
        }, clearAfter)
      }
    }

    const stopRecordingStream = () => {
      if (recordingStream) {
        recordingStream.getTracks().forEach(track => track.stop())
        recordingStream = null
      }
    }

    const pickRecordingMimeType = () => {
      if (!window.MediaRecorder?.isTypeSupported) return ''
      const candidates = [
        'audio/ogg;codecs=opus',
        'audio/mp4',
        'audio/webm;codecs=opus',
        'audio/webm'
      ]
      return candidates.find(type => MediaRecorder.isTypeSupported(type)) || ''
    }

    const audioFileExtension = (mimeType = '') => {
      if (mimeType.includes('ogg')) return 'ogg'
      if (mimeType.includes('mp4')) return 'm4a'
      if (mimeType.includes('webm')) return 'webm'
      return 'webm'
    }

    const writeString = (view, offset, text) => {
      for (let i = 0; i < text.length; i += 1) {
        view.setUint8(offset + i, text.charCodeAt(i))
      }
    }

    const encodeWav = (buffers, sampleRate) => {
      const sampleCount = buffers.reduce((sum, buffer) => sum + buffer.length, 0)
      const wavBuffer = new ArrayBuffer(44 + sampleCount * 2)
      const view = new DataView(wavBuffer)
      writeString(view, 0, 'RIFF')
      view.setUint32(4, 36 + sampleCount * 2, true)
      writeString(view, 8, 'WAVE')
      writeString(view, 12, 'fmt ')
      view.setUint32(16, 16, true)
      view.setUint16(20, 1, true)
      view.setUint16(22, 1, true)
      view.setUint32(24, sampleRate, true)
      view.setUint32(28, sampleRate * 2, true)
      view.setUint16(32, 2, true)
      view.setUint16(34, 16, true)
      writeString(view, 36, 'data')
      view.setUint32(40, sampleCount * 2, true)

      let offset = 44
      buffers.forEach(buffer => {
        for (let i = 0; i < buffer.length; i += 1) {
          const sample = Math.max(-1, Math.min(1, buffer[i]))
          view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
          offset += 2
        }
      })
      return new Blob([view], { type: 'audio/wav' })
    }

    const createWavRecorder = async (stream) => {
      const AudioContext = window.AudioContext || window.webkitAudioContext
      if (!AudioContext) {
        throw new Error('当前浏览器暂不支持录音功能，可以先用文字提问。')
      }
      const audioContext = new AudioContext()
      if (audioContext.state === 'suspended') {
        await audioContext.resume()
      }
      const source = audioContext.createMediaStreamSource(stream)
      const processor = audioContext.createScriptProcessor(4096, 1, 1)
      const buffers = []
      processor.onaudioprocess = (event) => {
        buffers.push(new Float32Array(event.inputBuffer.getChannelData(0)))
      }
      source.connect(processor)
      processor.connect(audioContext.destination)
      return {
        stop: async () => {
          processor.disconnect()
          source.disconnect()
          await audioContext.close()
          return encodeWav(buffers, audioContext.sampleRate)
        }
      }
    }

    const openHomeChat = (questionText, initialMessages, questionToSend = '') => {
      const trimmedQuestion = questionText.trim()
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
        question: questionToSend,
        category: selectedCategory.value,
        historyId,
        messages: initialMessages
      })
      question.value = ''
    }

    const sendVoiceMessage = async (audioBlob, extension = 'webm') => {
      if (!audioBlob || !audioBlob.size) {
        isRecording.value = false
        setVoiceStatus('没有录到声音，请重试。', 4000)
        return
      }
      isVoiceProcessing.value = true
      setVoiceStatus('正在识别语音，请稍候...')
      try {
        const formData = new FormData()
        formData.append('audioFile', audioBlob, `question.${extension}`)
        const data = await apiUpload('/api/chat/voice-detail', formData)
        const recognizedText = data?.recognizedText || data?.originalQuestion || ''
        if (!recognizedText.trim()) {
          throw new Error('没有识别到有效问题，请说得更清楚一些。')
        }
        const initialMessages = [
          { type: 'user', content: recognizedText, inputType: 'voice' },
          {
            type: 'bot',
            content: data?.answer || '语音已识别，但暂时没有生成有效回复。',
            durationMs: data?.responseTimeMs || 0,
            answerSource: data?.answerSource,
            mediaUrl: data?.mediaUrl
          }
        ]
        openHomeChat(recognizedText, initialMessages)
      } catch (e) {
        setVoiceStatus(e.message || '语音处理失败，请稍后重试。', 5000)
      } finally {
        isVoiceProcessing.value = false
      }
    }

    const toggleVoiceRecording = async () => {
      if (isVoiceProcessing.value) return
      if (isRecording.value) {
        if (mediaRecorder) {
          mediaRecorder.stop()
          return
        }
        if (wavRecorder) {
          const recorder = wavRecorder
          wavRecorder = null
          isRecording.value = false
          setVoiceStatus('正在识别语音，请稍候...')
          try {
            const blob = await recorder.stop()
            stopRecordingStream()
            sendVoiceMessage(blob, 'wav')
          } catch (e) {
            stopRecordingStream()
            setVoiceStatus(e.message || '录音保存失败，请重试。', 5000)
          }
        }
        return
      }
      if (!navigator.mediaDevices?.getUserMedia || (!window.MediaRecorder && !(window.AudioContext || window.webkitAudioContext))) {
        setVoiceStatus('当前浏览器不支持录音，请使用文字提问。', 5000)
        return
      }
      try {
        recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true })
        recordingChunks = []
        const mimeType = pickRecordingMimeType()
        if (mimeType && !mimeType.includes('webm')) {
          mediaRecorder = new MediaRecorder(recordingStream, { mimeType })
          mediaRecorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) recordingChunks.push(event.data)
          }
          mediaRecorder.onstop = () => {
            const recorderMimeType = mediaRecorder?.mimeType || mimeType
            const blob = new Blob(recordingChunks, { type: recorderMimeType })
            mediaRecorder = null
            isRecording.value = false
            stopRecordingStream()
            sendVoiceMessage(blob, audioFileExtension(recorderMimeType))
          }
          mediaRecorder.start()
        } else {
          wavRecorder = await createWavRecorder(recordingStream)
        }
        isRecording.value = true
        setVoiceStatus('正在录音，再次点击麦克风结束')
      } catch (e) {
        isRecording.value = false
        mediaRecorder = null
        if (wavRecorder) {
          try {
            await wavRecorder.stop()
          } catch {
            // Ignore cleanup failure.
          }
          wavRecorder = null
        }
        stopRecordingStream()
        setVoiceStatus(e.message || '没有获取到麦克风权限，请检查浏览器权限。', 5000)
      }
    }

    const voiceButtonTitle = computed(() => {
      if (isVoiceProcessing.value) return '正在识别语音'
      return isRecording.value ? '停止录音' : '语音提问'
    })

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

      openHomeChat(trimmedQuestion, initialMessages, directAnswer ? '' : trimmedQuestion)
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
      window.clearTimeout(voiceStatusTimer)
      stopRecordingStream()
      stopMascotDrag()
    })

    return {
      question,
      isRecording,
      isVoiceProcessing,
      voiceStatus,
      voiceButtonTitle,
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
      toggleVoiceRecording,
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

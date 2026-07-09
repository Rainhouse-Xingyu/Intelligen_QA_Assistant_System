import { ref, onMounted, nextTick } from 'vue'
import { apiForm, apiGet, apiUpload } from './adminApi'

const HISTORY_STORAGE_KEY = 'chat_conversations'
const CONTACT_URL = 'https://jw.neusoft.edu.cn/25565/'

export default {
  name: 'Dialogue',
  props: {
    initialQuestion: {
      type: String,
      default: ''
    },
    initialCategory: {
      type: String,
      default: ''
    },
    historyId: {
      type: String,
      default: ''
    },
    initialMessages: {
      type: Array,
      default: () => []
    }
  },
  emits: ['go-home'],
  setup(props, { emit }) {
    const messages = ref([])
    const chatInput = ref('')
    const isLoading = ref(false)
    const isRecording = ref(false)
    const messagesArea = ref(null)
    const suggestedQuestions = ref([])
    let mediaRecorder = null
    let recordingStream = null
    let recordingChunks = []

    const goHome = () => {
      emit('go-home')
    }

    const handleChatEnter = (e) => {
      if (e.shiftKey) {
        chatInput.value += '\n'
      } else {
        sendMessage()
      }
    }

    const stopRecordingStream = () => {
      if (recordingStream) {
        recordingStream.getTracks().forEach(track => track.stop())
        recordingStream = null
      }
    }

    const scrollToBottom = async () => {
      await nextTick()
      if (messagesArea.value) {
        messagesArea.value.scrollTop = messagesArea.value.scrollHeight
      }
    }

    const formatContent = (text) => {
      return String(text || '').replace(/\n/g, '<br/>')
    }

    const normalizeSuggestedQuestion = (item) => {
      const questionText = item.questionText || item.question_text || item.name || item.question || ''
      return {
        ...item,
        questionText,
        answerText: item.answerText || item.answer_text || item.answer || '',
        moduleType: item.moduleType || item.module_type || ''
      }
    }

    const loadSuggestedQuestions = async () => {
      try {
        const data = await apiGet('/api/chat/suggested-questions', { limit: 3 })
        suggestedQuestions.value = Array.isArray(data)
          ? data.map(normalizeSuggestedQuestion).filter(item => item.questionText).slice(0, 3)
          : []
      } catch (e) {
        console.warn('加载常见问题失败', e)
        suggestedQuestions.value = []
      } finally {
        scrollToBottom()
      }
      return suggestedQuestions.value
    }

    const ensureSuggestedQuestions = async (forceReload = false) => {
      if (forceReload || !suggestedQuestions.value.length) {
        await loadSuggestedQuestions()
      }
      return suggestedQuestions.value
    }

    const isCommonQuestionRequest = (text) => {
      const normalized = text.replace(/[\s。！？!?，,、.]/g, '')
      const hasFaqKeyword = /常见问题|热门问题|高频问题|问题列表|FAQ|faq/.test(normalized)
      if (!hasFaqKeyword) return false
      return /有什么|有哪些|都有|列表|推荐|显示|展示|列出|列出来|显示出来|展示出来|看看|看一下|给我|发我|是什么|入口|菜单/.test(normalized)
        || /^(常见问题|热门问题|高频问题|问题列表|FAQ|faq)$/.test(normalized)
    }

    const showSuggestedQuestionsMessage = async () => {
      const items = await ensureSuggestedQuestions(true)
      if (!items.length) {
        messages.value.push({ type: 'bot', content: '当前暂无可展示的常见问题。' })
      } else {
        messages.value.push({ type: 'bot', kind: 'faq', items: [...items] })
      }
      scrollToBottom()
    }

    const handleSuggestedQuestion = async (item) => {
      const questionText = item.questionText || item.question_text || item.name || ''
      const answerText = item.answerText || item.answer_text || item.answer || ''
      if (!questionText || isLoading.value) return
      if (answerText) {
        messages.value.push({ type: 'user', content: questionText })
        messages.value.push({ type: 'bot', content: answerText, durationMs: 0, answerSource: 'COMMON_DIRECT' })
        persistHistory()
        scrollToBottom()
        return
      }
      chatInput.value = questionText
      await sendMessage(false, item.moduleType || item.module_type || '')
    }

    const formatDuration = (durationMs) => {
      const seconds = Math.max(0, Number(durationMs || 0) / 1000)
      return `${seconds.toFixed(seconds >= 10 ? 0 : 1)} 秒`
    }

    const canShowFeedback = (msg) => {
      return msg?.type === 'bot' && msg.kind !== 'faq' && msg.answerSource !== 'COMMON_DIRECT'
    }

    const markResolved = (msg) => {
      msg.feedback = 'resolved'
      persistHistory()
    }

    const markUnresolved = (msg) => {
      msg.feedback = 'unresolved'
      persistHistory()
      window.location.href = CONTACT_URL
    }

    const persistHistory = () => {
      if (!props.historyId) return
      try {
        const stored = localStorage.getItem(HISTORY_STORAGE_KEY)
        const conversations = stored ? JSON.parse(stored) : []
        if (!Array.isArray(conversations)) return
        const index = conversations.findIndex(conv => conv.id === props.historyId)
        if (index < 0) return
        conversations[index] = {
          ...conversations[index],
          messages: messages.value.map(msg => ({ ...msg })),
          updatedAt: new Date().toISOString()
        }
        localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(conversations))
      } catch (e) {
        console.warn('保存历史对话失败', e)
      }
    }

    const sendMessage = async (isInitial = false, forcedCategory = '') => {
      let textToSend = chatInput.value
      let categoryToSend = forcedCategory || null
      
      if (isInitial) {
        textToSend = props.initialQuestion
        categoryToSend = props.initialCategory
      }
      
      if (!textToSend.trim() || isLoading.value) return;
      
      const userText = textToSend
      messages.value.push({ type: 'user', content: userText })
      persistHistory()
      if (!isInitial) {
        chatInput.value = ''
      }

      if (isCommonQuestionRequest(userText)) {
        await showSuggestedQuestionsMessage()
        return
      }
      
      isLoading.value = true
      scrollToBottom()
      const requestStartedAt = Date.now()

      try {
        const data = await apiForm('/api/chat/text-detail', {
          query: userText,
          moduleType: categoryToSend
        })
        const durationMs = data?.responseTimeMs ?? (Date.now() - requestStartedAt)
        messages.value.push({
          type: 'bot',
          content: data?.answer || '暂时没有找到和这个问题匹配的答案，我已经记录下来，后续会继续完善知识库。',
          durationMs,
          answerSource: data?.answerSource
        })
        persistHistory()
      } catch (e) {
        console.error(e)
        messages.value.push({
          type: 'bot',
          content: e.message || '网络错误，请稍后再试。',
          durationMs: Date.now() - requestStartedAt
        })
        persistHistory()
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    const toggleVoiceRecording = async () => {
      if (isLoading.value) return
      if (isRecording.value && mediaRecorder) {
        mediaRecorder.stop()
        return
      }
      if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
        messages.value.push({ type: 'bot', content: '当前浏览器暂不支持录音功能，可以先用文字提问。' })
        persistHistory()
        scrollToBottom()
        return
      }
      try {
        recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true })
        recordingChunks = []
        mediaRecorder = new MediaRecorder(recordingStream)
        mediaRecorder.ondataavailable = (event) => {
          if (event.data && event.data.size > 0) {
            recordingChunks.push(event.data)
          }
        }
        mediaRecorder.onstop = () => {
          const mimeType = mediaRecorder?.mimeType || 'audio/webm'
          const blob = new Blob(recordingChunks, { type: mimeType })
          isRecording.value = false
          stopRecordingStream()
          sendVoiceMessage(blob)
        }
        isRecording.value = true
        mediaRecorder.start()
      } catch (e) {
        isRecording.value = false
        stopRecordingStream()
        messages.value.push({ type: 'bot', content: '没有获取到麦克风权限，可以检查浏览器权限后再试。' })
        persistHistory()
        scrollToBottom()
      }
    }

    const sendVoiceMessage = async (audioBlob) => {
      if (!audioBlob || !audioBlob.size) return
      isLoading.value = true
      scrollToBottom()
      const requestStartedAt = Date.now()
      try {
        const formData = new FormData()
        formData.append('audioFile', audioBlob, 'question.webm')
        const data = await apiUpload('/api/chat/voice-detail', formData)
        const recognizedText = data?.recognizedText || data?.originalQuestion || '语音消息'
        const durationMs = data?.responseTimeMs ?? (Date.now() - requestStartedAt)
        messages.value.push({ type: 'user', content: recognizedText, inputType: 'voice' })
        messages.value.push({
          type: 'bot',
          content: data?.answer || '语音已识别，但暂时没有生成有效回复。',
          durationMs,
          answerSource: data?.answerSource,
          mediaUrl: data?.mediaUrl
        })
        persistHistory()
      } catch (e) {
        messages.value.push({
          type: 'bot',
          content: e.message || '语音处理失败，请稍后再试。',
          durationMs: Date.now() - requestStartedAt
        })
        persistHistory()
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    onMounted(() => {
      if (props.initialMessages.length) {
        messages.value = props.initialMessages.map(msg => ({ ...msg }))
        scrollToBottom()
        return
      }
      if (props.initialQuestion) {
        sendMessage(true)
      }
    })

    return {
      messages,
      chatInput,
      isLoading,
      isRecording,
      messagesArea,
      suggestedQuestions,
      goHome,
      handleChatEnter,
      formatContent,
      formatDuration,
      canShowFeedback,
      markResolved,
      markUnresolved,
      handleSuggestedQuestion,
      toggleVoiceRecording,
      sendMessage
    }
  }
}

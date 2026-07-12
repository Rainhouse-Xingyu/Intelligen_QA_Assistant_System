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
    const needVoiceAnswer = ref(false)
    const messagesArea = ref(null)
    const suggestedQuestions = ref([])
    const conversations = ref([])
    const activeHistoryId = ref(props.historyId || '')
    const isStudentSession = ref(false)
    let mediaRecorder = null
    let wavRecorder = null
    let recordingStream = null
    let recordingChunks = []

    const goHome = () => {
      emit('go-home')
    }

    const getStoredUserInfo = () => {
      const raw = localStorage.getItem('user_info') || sessionStorage.getItem('user_info')
      if (!raw) return null
      try {
        return JSON.parse(raw)
      } catch {
        return null
      }
    }

    const loadConversations = () => {
      try {
        const stored = localStorage.getItem(HISTORY_STORAGE_KEY)
        const parsed = stored ? JSON.parse(stored) : []
        conversations.value = Array.isArray(parsed) ? parsed : []
      } catch {
        conversations.value = []
      }
    }

    const saveConversations = () => {
      localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(conversations.value))
    }

    const ensureConversation = (firstQuestion = '') => {
      if (!isStudentSession.value) return ''
      if (activeHistoryId.value) return activeHistoryId.value
      const id = 'conv_' + Date.now()
      activeHistoryId.value = id
      conversations.value.unshift({
        id,
        title: firstQuestion.substring(0, 30) || '新对话',
        firstQuestion,
        category: props.initialCategory || null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        messages: []
      })
      saveConversations()
      return id
    }

    const formatHistoryTime = (value) => {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return ''
      return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    }

    const newConversation = () => {
      activeHistoryId.value = ''
      messages.value = []
      chatInput.value = ''
      scrollToBottom()
    }

    const selectConversation = (conv) => {
      if (!conv) return
      activeHistoryId.value = conv.id
      messages.value = Array.isArray(conv.messages) ? conv.messages.map(msg => ({ ...msg })) : []
      chatInput.value = ''
      scrollToBottom()
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
      const historyId = activeHistoryId.value || props.historyId
      if (!historyId) return
      try {
        const stored = localStorage.getItem(HISTORY_STORAGE_KEY)
        const conversations = stored ? JSON.parse(stored) : []
        if (!Array.isArray(conversations)) return
        const index = conversations.findIndex(conv => conv.id === historyId)
        if (index < 0) return
        conversations[index] = {
          ...conversations[index],
          messages: messages.value.map(msg => ({ ...msg })),
          updatedAt: new Date().toISOString()
        }
        localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(conversations))
        loadConversations()
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
      ensureConversation(userText)
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
          moduleType: categoryToSend,
          needTts: needVoiceAnswer.value ? 'true' : 'false'
        })
        const durationMs = data?.responseTimeMs ?? (Date.now() - requestStartedAt)
        messages.value.push({
          type: 'bot',
          content: data?.answer || '暂时没有找到和这个问题匹配的答案，我已经记录下来，后续会继续完善知识库。',
          durationMs,
          answerSource: data?.answerSource,
          mediaUrl: data?.mediaUrl
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
      if (isRecording.value) {
        if (mediaRecorder) {
          mediaRecorder.stop()
          return
        }
        if (wavRecorder) {
          const recorder = wavRecorder
          wavRecorder = null
          isRecording.value = false
          try {
            const blob = await recorder.stop()
            stopRecordingStream()
            sendVoiceMessage(blob, 'wav')
          } catch (e) {
            stopRecordingStream()
            messages.value.push({ type: 'bot', content: e.message || '录音保存失败，可以再试一次。' })
            persistHistory()
            scrollToBottom()
          }
        }
        return
      }
      if (!navigator.mediaDevices?.getUserMedia || (!window.MediaRecorder && !(window.AudioContext || window.webkitAudioContext))) {
        messages.value.push({ type: 'bot', content: '当前浏览器暂不支持录音功能，可以先用文字提问。' })
        persistHistory()
        scrollToBottom()
        return
      }
      try {
        recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true })
        recordingChunks = []
        const mimeType = pickRecordingMimeType()
        if (mimeType && !mimeType.includes('webm')) {
          mediaRecorder = new MediaRecorder(recordingStream, { mimeType })
          mediaRecorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              recordingChunks.push(event.data)
            }
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
        messages.value.push({ type: 'bot', content: e.message || '没有获取到麦克风权限，可以检查浏览器权限后再试。' })
        persistHistory()
        scrollToBottom()
      }
    }

    const sendVoiceMessage = async (audioBlob, extension = 'webm') => {
      if (!audioBlob || !audioBlob.size) {
        if (isRecording.value) {
          isRecording.value = false
        }
        return
      }
      isLoading.value = true
      scrollToBottom()
      const requestStartedAt = Date.now()
      try {
        const formData = new FormData()
        formData.append('audioFile', audioBlob, `question.${extension}`)
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
      const userInfo = getStoredUserInfo()
      isStudentSession.value = userInfo?.role === 1
      loadConversations()
      if (props.initialMessages.length) {
        messages.value = props.initialMessages.map(msg => ({ ...msg }))
        activeHistoryId.value = props.historyId || activeHistoryId.value
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
      needVoiceAnswer,
      messagesArea,
      suggestedQuestions,
      conversations,
      activeHistoryId,
      isStudentSession,
      goHome,
      formatHistoryTime,
      newConversation,
      selectConversation,
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

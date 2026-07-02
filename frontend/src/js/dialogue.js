import { ref, onMounted, nextTick } from 'vue'
import { apiForm, apiGet } from './adminApi'

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
    }
  },
  emits: ['go-home'],
  setup(props, { emit }) {
    const messages = ref([])
    const chatInput = ref('')
    const isLoading = ref(false)
    const messagesArea = ref(null)
    const suggestedQuestions = ref([])

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

    const scrollToBottom = async () => {
      await nextTick()
      if (messagesArea.value) {
        messagesArea.value.scrollTop = messagesArea.value.scrollHeight
      }
    }

    const formatContent = (text) => {
      return text.replace(/\n/g, '<br/>')
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
        const data = await apiGet('/api/chat/suggested-questions')
        suggestedQuestions.value = Array.isArray(data)
          ? data.map(normalizeSuggestedQuestion).filter(item => item.questionText)
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

      if (!answerText) {
        chatInput.value = questionText
        await sendMessage(false)
        return
      }

      messages.value.push({ type: 'user', content: questionText })
      messages.value.push({ type: 'bot', content: answerText })
      scrollToBottom()
    }

    const sendMessage = async (isInitial = false) => {
      let textToSend = chatInput.value
      let categoryToSend = null
      
      if (isInitial) {
        textToSend = props.initialQuestion
        categoryToSend = props.initialCategory
      }
      
      if (!textToSend.trim() || isLoading.value) return;
      
      const userText = textToSend
      messages.value.push({ type: 'user', content: userText })
      if (!isInitial) {
        chatInput.value = ''
      }

      if (isCommonQuestionRequest(userText)) {
        await showSuggestedQuestionsMessage()
        return
      }
      
      isLoading.value = true
      scrollToBottom()

      try {
        const data = await apiForm('/api/chat/text', {
          query: userText,
          moduleType: categoryToSend
        })
        messages.value.push({
          type: 'bot',
          content: data || '暂时没有找到和这个问题匹配的答案，我已经记录下来，后续会继续完善知识库。'
        })
      } catch (e) {
        console.error(e)
        messages.value.push({ type: 'bot', content: e.message || '网络错误，请稍后再试。' })
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    onMounted(() => {
      loadSuggestedQuestions()
      if (props.initialQuestion) {
        sendMessage(true)
      }
    })

    return {
      messages,
      chatInput,
      isLoading,
      messagesArea,
      suggestedQuestions,
      goHome,
      handleChatEnter,
      formatContent,
      handleSuggestedQuestion,
      sendMessage
    }
  }
}

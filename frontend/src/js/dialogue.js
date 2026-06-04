import { ref, onMounted, nextTick } from 'vue'
import { apiForm } from './adminApi'

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
      
      isLoading.value = true
      scrollToBottom()

      try {
        const data = await apiForm('/api/chat/text', {
          query: userText,
          moduleType: categoryToSend
        })
        messages.value.push({ type: 'bot', content: data || '收到。' })
      } catch (e) {
        console.error(e)
        messages.value.push({ type: 'bot', content: e.message || '网络错误，请稍后再试。' })
      } finally {
        isLoading.value = false
        scrollToBottom()
      }
    }

    onMounted(() => {
      if (props.initialQuestion) {
        sendMessage(true)
      }
    })

    return {
      messages,
      chatInput,
      isLoading,
      messagesArea,
      goHome,
      handleChatEnter,
      formatContent,
      sendMessage
    }
  }
}

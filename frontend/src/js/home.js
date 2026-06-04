import { ref, computed, onMounted } from 'vue'
import { apiGet, apiForm } from './adminApi'

export default {
  name: 'Home',
  emits: ['start-chat', 'navigate-login'],
  setup(props, { emit }) {
    const question = ref('')
    const selectedCategory = ref(null)
    const shakeAlert = ref(false)
    const categories = [
      { label: '考务', value: '考务通知' },
      { label: '教学', value: '教学运行' },
      { label: '心理', value: '心理辅导' }
    ]

    // --- Login state ---
    const isLoggedIn = ref(false)
    const userInfo = ref(null)

    const isStudent = computed(() => userInfo.value?.role === 1)

    // --- Sidebar state ---
    const conversations = ref([])

    const loadConversations = () => {
      try {
        const stored = localStorage.getItem('chat_conversations')
        conversations.value = stored ? JSON.parse(stored) : []
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
      emit('start-chat', {
        question: conv.firstQuestion || conv.title,
        category: conv.category || null,
        historyId: conv.id
      })
    }

    const deleteConversation = (id) => {
      conversations.value = conversations.value.filter(c => c.id !== id)
      saveConversations()
    }

    // --- Auth methods ---
    const getToken = () => {
      return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
    }

    const restoreSession = async () => {
      const token = getToken()
      if (!token) return

      try {
        userInfo.value = await apiGet('/api/auth/info')
        isLoggedIn.value = true
        loadConversations()
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

    const handleSendFromHome = () => {
      if (!question.value.trim()) return
      if (!selectedCategory.value) {
        shakeAlert.value = true
        setTimeout(() => {
          shakeAlert.value = false
        }, 500)
        return
      }

      if (isLoggedIn.value) {
        const conv = {
          id: 'conv_' + Date.now(),
          title: question.value.trim().substring(0, 30),
          firstQuestion: question.value.trim(),
          category: selectedCategory.value,
          createdAt: new Date().toISOString()
        }
        conversations.value.unshift(conv)
        saveConversations()
      }

      emit('start-chat', {
        question: question.value,
        category: selectedCategory.value
      })
      question.value = ''
    }

    onMounted(() => {
      restoreSession()
    })

    return {
      question,
      selectedCategory,
      shakeAlert,
      categories,
      isLoggedIn,
      userInfo,
      isStudent,
      conversations,
      handleSendFromHome,
      handleLoginClick,
      newConversation,
      selectConversation,
      deleteConversation
    }
  }
}

import { ref } from 'vue'

export default {
  name: 'Home',
  emits: ['start-chat'],
  setup(props, { emit }) {
    const question = ref('')
    const selectedCategory = ref(null)
    const shakeAlert = ref(false)
    const categories = [
      { label: '考务', value: '考务通知' },
      { label: '教学', value: '教学运行' },
      { label: '心理', value: '心理辅导' }
    ]

    const handleSendFromHome = () => {
      if (!question.value.trim()) return;
      if (!selectedCategory.value) {
        shakeAlert.value = true;
        setTimeout(() => {
          shakeAlert.value = false;
        }, 500);
        return;
      }
      
      emit('start-chat', {
        question: question.value,
        category: selectedCategory.value
      })
      question.value = ''
    }

    return {
      question,
      selectedCategory,
      shakeAlert,
      categories,
      handleSendFromHome
    }
  }
}

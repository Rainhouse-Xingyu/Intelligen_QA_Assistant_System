<template>
  <div class="app-container">
    <SurveyAdmin v-if="currentPage === 'survey-admin'" />
    <StudentSurvey
      v-else-if="currentPage === 'student-survey'"
      @go-home="currentPage = 'home'"
      @navigate-login="currentPage = 'login'"
    />
    <Dashboard v-else-if="currentPage === 'dashboard'" />
    <Academic v-else-if="currentPage === 'academic'" />
    <LoginPage
      v-else-if="currentPage === 'login'"
      @login-success="onLoginSuccess"
      @go-home="currentPage = 'home'"
    />
    <Home
      v-else-if="currentPage === 'home'"
      @start-chat="onStartChat"
      @navigate-login="currentPage = 'login'"
      @navigate-survey="currentPage = 'student-survey'"
    />
    <Dialogue
      v-else-if="currentPage === 'chat'"
      :initial-question="currentQuestion"
      :initial-category="currentCategory"
      :history-id="currentHistoryId"
      :initial-messages="currentMessages"
      @go-home="currentPage = 'home'"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Home from './views/home.vue'
import Dialogue from './views/dialogue.vue'
import SurveyAdmin from './views/survey.vue'
import StudentSurvey from './views/studentSurvey.vue'
import Dashboard from './views/dashboard.vue'
import Academic from './views/academic.vue'
import LoginPage from './views/LoginPage.vue'

const routeMap = {
  '/admin/survey': 'survey-admin',
  '/survey': 'student-survey',
  '/admin/dashboard': 'dashboard',
  '/admin/academic': 'academic'
}

const currentPage = ref(routeMap[window.location.pathname] || 'home')
const currentQuestion = ref('')
const currentCategory = ref('')
const currentHistoryId = ref('')
const currentMessages = ref([])

const onStartChat = (payload) => {
  currentQuestion.value = payload.question || ''
  currentCategory.value = payload.category || ''
  currentHistoryId.value = payload.historyId || ''
  currentMessages.value = Array.isArray(payload.messages) ? payload.messages : []
  currentPage.value = 'chat'
}

const onLoginSuccess = (userInfo) => {
  if (userInfo.role === 3) {
    currentPage.value = 'dashboard'
  } else if (userInfo.role === 2) {
    currentPage.value = 'academic'
  } else {
    currentPage.value = 'home'
  }
}
</script>

<style>
.app-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>

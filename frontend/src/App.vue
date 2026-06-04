<template>
  <div class="app-container">
    <Knowledge v-if="currentPage === 'knowledge'" />
    <VectorPage v-else-if="currentPage === 'vector'" />
    <Dashboard v-else-if="currentPage === 'dashboard'" />
    <Academic v-else-if="currentPage === 'academic'" />
    <AdminChat v-else-if="currentPage === 'admin-chat'" />
    <LoginPage v-else-if="currentPage === 'login'" @login-success="onLoginSuccess" />
    <Home v-else-if="currentPage === 'home'" @start-chat="onStartChat" @navigate-login="currentPage = 'login'" />
    <Dialogue
      v-else-if="currentPage === 'chat'"
      :initial-question="currentQuestion"
      :initial-category="currentCategory"
      @go-home="currentPage = 'home'"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Home from './views/home.vue'
import Dialogue from './views/dialogue.vue'
import Knowledge from './views/knowledge.vue'
import VectorPage from './views/vector.vue'
import Dashboard from './views/dashboard.vue'
import Academic from './views/academic.vue'
import AdminChat from './views/chat.vue'
import LoginPage from './views/LoginPage.vue'

const routeMap = {
  '/admin/knowledge': 'knowledge',
  '/admin/vector': 'vector',
  '/admin/dashboard': 'dashboard',
  '/admin/academic': 'academic',
  '/admin/chat': 'admin-chat'
}

const currentPage = ref(routeMap[window.location.pathname] || 'home')
const currentQuestion = ref('')
const currentCategory = ref('')

const onStartChat = (payload) => {
  currentQuestion.value = payload.question
  currentCategory.value = payload.category
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

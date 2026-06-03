<template>
  <div class="app-container">
    <Knowledge v-if="currentPage === 'knowledge'" />
    <VectorPage v-else-if="currentPage === 'vector'" />
    <Dashboard v-else-if="currentPage === 'dashboard'" />
    <Academic v-else-if="currentPage === 'academic'" />
    <AdminChat v-else-if="currentPage === 'admin-chat'" />
    <Home v-else-if="currentPage === 'home'" @start-chat="onStartChat" />
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
</script>

<style>
.app-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>

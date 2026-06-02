<template>
  <div class="app-container">
    <Home v-if="currentPage === 'home'" @start-chat="onStartChat" />
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

const currentPage = ref('home')
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

<template>
  <div class="chat-page-wrapper">
    <div class="chat-page">
      <!-- Chat Header -->
      <header class="chat-header">
         <div class="bot-info">
           <div class="bot-avatar">✨</div>
           <div class="bot-text">
             <h2>智能对话助手</h2>
             <p><span class="online-dot"></span> 在线 · 随时为你服务</p>
           </div>
         </div>
         <button class="back-btn" @click="goHome">返回首页</button>
      </header>
      
      <!-- Messages -->
      <div class="messages-area" ref="messagesArea">
         <div class="message bot-message">
           <div class="msg-avatar">✨</div>
           <div class="msg-bubble">你好，我是你的智能助手 ✨<br/>有什么想聊的，随时告诉我。</div>
         </div>
         <div v-if="suggestedQuestions.length" class="message bot-message">
           <div class="msg-avatar">✨</div>
           <div class="msg-bubble faq-bubble">
             <div class="faq-title">常见问题</div>
             <div class="faq-list">
               <button
                 v-for="(item, index) in suggestedQuestions"
                 :key="item.id || item.questionText || index"
                 class="faq-item"
                 @click="handleSuggestedQuestion(item)"
               >
                 <span class="faq-index">{{ index + 1 }}</span>
                 <span>{{ item.questionText }}</span>
               </button>
             </div>
           </div>
         </div>
         <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.type === 'user' ? 'user-message' : 'bot-message']">
            <div v-if="msg.type === 'bot'" class="msg-avatar">✨</div>
            <div v-if="msg.kind === 'faq'" class="msg-bubble faq-bubble">
              <div class="faq-title">常见问题</div>
              <div class="faq-list">
                <button
                  v-for="(item, faqIndex) in msg.items"
                  :key="item.id || item.questionText || faqIndex"
                  class="faq-item"
                  @click="handleSuggestedQuestion(item)"
                >
                  <span class="faq-index">{{ faqIndex + 1 }}</span>
                  <span>{{ item.questionText }}</span>
                </button>
              </div>
            </div>
            <div v-else class="msg-bubble" v-html="formatContent(msg.content)"></div>
         </div>
         
         <!-- Loading indicator -->
         <div v-if="isLoading" class="message bot-message">
           <div class="msg-avatar">✨</div>
           <div class="msg-bubble typing-indicator">
             <span></span><span></span><span></span>
           </div>
         </div>
      </div>
      
      <!-- Input Area -->
      <div class="chat-input-area">
         <textarea 
           v-model="chatInput" 
           placeholder="给助手发条消息... (Enter 发送, Shift+Enter 换行)"
           @keydown.enter.prevent="handleChatEnter"
         ></textarea>
         <button class="send-btn" :class="{ active: chatInput.trim().length > 0 }" @click="sendMessage(false)">
           <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
           </svg>
         </button>
      </div>
    </div>
  </div>
</template>

<script src="../js/dialogue.js"></script>
<style src="../css/dialogue.css" scoped></style>

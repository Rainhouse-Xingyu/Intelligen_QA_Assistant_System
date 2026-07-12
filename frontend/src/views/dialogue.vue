<template>
  <div class="chat-page-wrapper">
    <aside v-if="isStudentSession" class="dialogue-history-sidebar">
      <div class="dialogue-history-head">
        <strong>历史对话</strong>
        <button type="button" title="新对话" @click="newConversation">
          <svg viewBox="0 0 24 24">
            <path d="M12 5v14"></path>
            <path d="M5 12h14"></path>
          </svg>
        </button>
      </div>
      <div class="dialogue-history-list">
        <button
          v-for="conv in conversations"
          :key="conv.id"
          type="button"
          :class="{ active: conv.id === activeHistoryId }"
          @click="selectConversation(conv)"
        >
          <span>{{ conv.title || conv.firstQuestion || '新对话' }}</span>
          <em>{{ formatHistoryTime(conv.updatedAt || conv.createdAt) }}</em>
        </button>
        <div v-if="conversations.length === 0" class="dialogue-history-empty">暂无历史对话</div>
      </div>
    </aside>
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
         <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.type === 'user' ? 'user-message' : 'bot-message']">
            <div v-if="msg.type === 'bot'" class="msg-avatar">✨</div>
            <div v-if="msg.kind === 'faq'" class="msg-bubble faq-bubble">
              <div class="faq-title">热门问题</div>
              <div class="faq-list">
                <button
                  v-for="(item, faqIndex) in msg.items"
                  :key="item.id || item.questionText || faqIndex"
                  class="faq-item"
                  @click="handleSuggestedQuestion(item)"
                >
                  <span class="faq-index">{{ faqIndex + 1 }}</span>
                  <span>{{ item.questionText }}</span>
                  <em v-if="item.value">点击 {{ item.value }}</em>
                </button>
              </div>
            </div>
            <div v-else class="msg-bubble">
              <div v-html="formatContent(msg.content)"></div>
              <audio v-if="msg.mediaUrl" class="msg-audio" controls :src="msg.mediaUrl"></audio>
              <div v-if="msg.type === 'bot' && msg.durationMs !== undefined" class="msg-duration">
                已处理 {{ formatDuration(msg.durationMs) }}
              </div>
              <div v-if="canShowFeedback(msg)" class="msg-feedback">
                <button
                  type="button"
                  :class="{ active: msg.feedback === 'resolved' }"
                  @click="markResolved(msg)"
                >已解决</button>
                <button
                  type="button"
                  :class="{ active: msg.feedback === 'unresolved' }"
                  @click="markUnresolved(msg)"
                >未解决</button>
              </div>
            </div>
         </div>
         
         <!-- Loading indicator -->
         <div v-if="isLoading" class="message bot-message">
           <div class="msg-avatar">✨</div>
           <div class="msg-bubble thinking-indicator">
             <span>正在思考</span>
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
         <button
           class="voice-btn"
           :class="{ recording: isRecording }"
           :title="isRecording ? '停止录音' : '语音提问'"
           @click="toggleVoiceRecording"
         >
           <svg viewBox="0 0 24 24" width="19" height="19" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
             <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"></path>
             <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
             <path d="M12 19v3"></path>
             <path d="M8 22h8"></path>
           </svg>
         </button>
         <button
           class="tts-btn"
           :class="{ active: needVoiceAnswer }"
           :title="needVoiceAnswer ? '已开启语音回答' : '开启语音回答'"
           @click="needVoiceAnswer = !needVoiceAnswer"
         >
           <svg viewBox="0 0 24 24" width="19" height="19" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
             <path d="M11 5 6 9H3v6h3l5 4V5z"></path>
             <path d="M16 9.5a4 4 0 0 1 0 5"></path>
             <path d="M19 7a8 8 0 0 1 0 10"></path>
           </svg>
         </button>
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

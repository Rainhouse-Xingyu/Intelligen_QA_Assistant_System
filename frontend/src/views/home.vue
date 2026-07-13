<template>
  <div class="home-page">
    <!-- Navbar -->
    <nav class="navbar">
      <div ref="logoRef" :class="['logo', logoTone]">
         <img src="/dnui-logo-white.png" alt="DNUI Logo" />
      </div>
      <div class="nav-menus">
         <div
           v-for="group in topNavigationCategories"
           :key="group.id || group.label"
           class="nav-item nav-category-item"
           tabindex="0"
         >
           {{ group.label }} <span class="arrow">^</span>
           <div class="dropdown category-dropdown">
             <button
               v-if="!group.children || group.children.length === 0"
               type="button"
               class="dropdown-item"
               @click="handleCategoryShortcut(group)"
             >查看全部</button>
             <button
               v-for="child in group.children"
               :key="child.id || child.label"
               type="button"
               class="dropdown-item"
               @click="handleCategoryShortcut(group, child)"
             >
               <span>{{ child.label }}</span>
               <small v-if="child.children && child.children.length">{{ child.children.length }} 个细分项</small>
             </button>
           </div>
         </div>
      </div>
      <div class="nav-actions">
        <div class="nav-item" @click="handleLoginClick">
          {{ isLoggedIn ? (userInfo.realName || userInfo.username) + ' | 退出' : '登录' }}
        </div>
      </div>
    </nav>

    <!-- Logged in as student: sidebar + main content -->
    <div v-if="isLoggedIn && isStudent" class="student-home-layout">
      <aside class="admin-sidebar home-history-sidebar">
        <div class="admin-brand">
          <div class="brand-mark">
            <svg viewBox="0 0 24 24" class="icon">
              <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"></path>
            </svg>
          </div>
          <div>
            <h1>智能问答</h1>
            <p>{{ userInfo.realName || userInfo.username }}</p>
          </div>
        </div>
        <button class="btn primary" @click="newConversation">
          <svg viewBox="0 0 24 24" class="icon">
            <path d="M12 5v14"></path>
            <path d="M5 12h14"></path>
          </svg>
          新对话
        </button>
        <button
          :class="['btn', 'ghost', 'survey-sidebar-btn', { 'has-alert': hasPendingSurvey }]"
          :title="hasPendingSurvey ? `还有 ${pendingSurveyCount} 份问卷未完成` : '问卷调查'"
          @click="handleSurveyClick"
        >
          <svg viewBox="0 0 24 24" class="icon">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <path d="M14 2v6h6"></path>
            <path d="M9 13h6"></path>
            <path d="M9 17h6"></path>
          </svg>
          问卷调查
          <span v-if="hasPendingSurvey" class="survey-alert-dot"></span>
        </button>
        <nav class="admin-nav home-history-list">
          <a
            v-for="conv in conversations"
            :key="conv.id"
            href="#"
            @click.prevent="selectConversation(conv)"
          >
            <svg viewBox="0 0 24 24" class="icon">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <span>{{ conv.title }}</span>
            <button class="history-delete-btn" title="删除历史对话" @click.stop.prevent="deleteConversation(conv.id)">
              <svg viewBox="0 0 24 24" class="icon">
                <path d="M3 6h18"></path>
                <path d="M8 6V4h8v2"></path>
                <path d="M19 6l-1 14H6L5 6"></path>
              </svg>
            </button>
          </a>
          <div v-if="conversations.length === 0" class="empty">暂无历史对话</div>
        </nav>
        <div class="admin-foot">大连东软信息学院<br/>智能问答 · v1.0</div>
      </aside>

      <main class="home-main">
        <h1 class="main-title">你好，今天想聊点什么？</h1>
        <p class="sub-title">可以直接输入问题，分类只是可选筛选</p>

        <div class="search-box-container">
           <textarea
             v-model="question"
             placeholder="请输入你想问的问题..."
             @keydown.enter.prevent="handleSendFromHome"
           ></textarea>

           <div class="search-bottom">
             <div class="category-buttons" :class="{ 'shake-alert': shakeAlert }">
                <button
                  v-for="cat in categories"
                  :key="cat.value"
                  :class="['category-btn', { active: selectedCategory === cat.value }]"
                  @click="toggleCategory(cat.value)"
                >{{ cat.label }}</button>
             </div>
             <div class="search-actions">
               <button
                 class="voice-btn"
                 :class="{ recording: isRecording, processing: isVoiceProcessing }"
                 :disabled="isVoiceProcessing"
                 :title="voiceButtonTitle"
                 :aria-label="voiceButtonTitle"
                 @click="toggleVoiceRecording"
               >
                 <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                   <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"></path>
                   <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
                   <path d="M12 19v3"></path>
                   <path d="M8 22h8"></path>
                 </svg>
               </button>
               <button class="send-btn" @click="handleSendFromHome">
                 <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                   <line x1="12" y1="19" x2="12" y2="5"></line>
                   <polyline points="5 12 12 5 19 12"></polyline>
                 </svg>
               </button>
             </div>
           </div>
           <p v-if="voiceStatus" class="voice-status">{{ voiceStatus }}</p>
        </div>

        <section class="common-question-panel">
          <div class="common-panel-head">
            <h2>常见问题</h2>
            <button class="refresh-common-btn" @click="loadCommonQuestions">换一批</button>
          </div>
          <div class="common-category-tabs">
            <button
              v-for="cat in commonQuestionCategories"
              :key="cat.value || 'all'"
              :class="{ active: commonQuestionModule === cat.value }"
              @click="changeCommonQuestionModule(cat.value)"
            >{{ cat.label }}</button>
          </div>
          <div class="common-question-grid">
            <p v-if="commonQuestionsLoading" class="common-question-empty">正在加载...</p>
            <p v-else-if="!commonQuestions.length" class="common-question-empty">该分类暂无常见问题</p>
            <button
              v-for="(item, index) in commonQuestions"
              :key="item.id || item.questionText || index"
              class="common-question-card"
              @click="handleCommonQuestion(item)"
            >
              <span>{{ index + 1 }}</span>
              <strong>{{ item.questionText }}</strong>
            </button>
          </div>
        </section>
      </main>
    </div>

    <!-- Not logged in: just main content -->
    <main v-else class="home-main">
      <h1 class="main-title">你好，今天想聊点什么？</h1>
      <p class="sub-title">可以直接输入问题，分类只是可选筛选</p>

      <div class="search-box-container">
         <textarea
           v-model="question"
           placeholder="请输入你想问的问题..."
           @keydown.enter.prevent="handleSendFromHome"
         ></textarea>

         <div class="search-bottom">
           <div class="category-buttons" :class="{ 'shake-alert': shakeAlert }">
              <button
                v-for="cat in categories"
                :key="cat.value"
                :class="['category-btn', { active: selectedCategory === cat.value }]"
                @click="toggleCategory(cat.value)"
              >{{ cat.label }}</button>
           </div>
           <div class="search-actions">
             <button
               class="voice-btn"
               :class="{ recording: isRecording, processing: isVoiceProcessing }"
               :disabled="isVoiceProcessing"
               :title="voiceButtonTitle"
               :aria-label="voiceButtonTitle"
               @click="toggleVoiceRecording"
             >
               <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                 <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"></path>
                 <path d="M19 10v2a7 7 0 0 1-14 0v-2"></path>
                 <path d="M12 19v3"></path>
                 <path d="M8 22h8"></path>
               </svg>
             </button>
             <button class="send-btn" @click="handleSendFromHome">
               <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                 <line x1="12" y1="19" x2="12" y2="5"></line>
                 <polyline points="5 12 12 5 19 12"></polyline>
               </svg>
             </button>
           </div>
         </div>
         <p v-if="voiceStatus" class="voice-status">{{ voiceStatus }}</p>
      </div>

      <section class="common-question-panel">
        <div class="common-panel-head">
          <h2>常见问题</h2>
          <button class="refresh-common-btn" @click="loadCommonQuestions">换一批</button>
        </div>
        <div class="common-category-tabs">
          <button
            v-for="cat in commonQuestionCategories"
            :key="cat.value || 'all'"
            :class="{ active: commonQuestionModule === cat.value }"
            @click="changeCommonQuestionModule(cat.value)"
          >{{ cat.label }}</button>
        </div>
        <div class="common-question-grid">
          <p v-if="commonQuestionsLoading" class="common-question-empty">正在加载...</p>
          <p v-else-if="!commonQuestions.length" class="common-question-empty">该分类暂无常见问题</p>
          <button
            v-for="(item, index) in commonQuestions"
            :key="item.id || item.questionText || index"
            class="common-question-card"
            @click="handleCommonQuestion(item)"
          >
            <span>{{ index + 1 }}</span>
            <strong>{{ item.questionText }}</strong>
          </button>
        </div>
      </section>
    </main>

    <div
      v-if="isLoggedIn && isStudent"
      class="mascot-hotspot"
      :class="[{ dragging: mascotDragging }, mascotBubblePlacement]"
      :style="{
        left: `${mascotPosition.x}px`,
        top: `${mascotPosition.y}px`
      }"
      @mousedown.prevent="startMascotDrag"
      @touchstart.prevent="startMascotDrag"
      @mouseenter="showHotBubble"
      @click.stop="handleMascotTap"
    >
      <div class="mascot-bubble" v-if="hotBubbleVisible" @mousedown.stop @touchstart.stop>
        <div class="mascot-bubble-title">热门问题</div>
        <button
          class="mascot-bubble-close"
          type="button"
          aria-label="关闭热门问题"
          title="关闭"
          @click.stop="closeHotBubble"
        >×</button>
        <button
          v-for="(item, index) in mascotQuestions"
          :key="item.id || item.questionText || index"
          type="button"
          @click.stop="handleHotQuestion(item)"
        >
          <span>{{ index + 1 }}</span>
          <strong>{{ item.questionText }}</strong>
        </button>
        <p v-if="!mascotQuestions.length" class="mascot-bubble-empty">正在准备热门问题...</p>
      </div>
      <div
        class="mascot-avatar dragon-mascot"
        title="Dong Xiaolong"
        aria-label="Dong Xiaolong assistant"
      >
        <img class="dragon-image" src="/dong-xiaolong-mascot.png?v=3" alt="东小龙" />
      </div>
    </div>
  </div>
</template>

<script src="../js/home.js"></script>
<style src="../css/home.css" scoped></style>
<style src="../css/admin.css" scoped></style>

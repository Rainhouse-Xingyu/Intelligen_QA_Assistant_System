<template>
  <div class="home-page">
    <!-- Navbar -->
    <nav class="navbar">
      <div class="logo">
         <img src="/nui_small_white.png" alt="DNUI Logo" />
      </div>
      <div class="nav-menus">
         <div class="nav-item">
           考务资料 <span class="arrow">^</span>
           <div class="dropdown">
             <div class="dropdown-item">1</div>
             <div class="dropdown-item">2</div>
             <div class="dropdown-item">3</div>
           </div>
         </div>
         <div class="nav-item">
           教学帮扶 <span class="arrow">^</span>
           <div class="dropdown">
             <div class="dropdown-item">1</div>
             <div class="dropdown-item">2</div>
             <div class="dropdown-item">3</div>
           </div>
         </div>
         <div class="nav-item">
           心理指导 <span class="arrow">^</span>
           <div class="dropdown">
             <div class="dropdown-item">1</div>
             <div class="dropdown-item">2</div>
             <div class="dropdown-item">3</div>
           </div>
         </div>
      </div>
      <div class="nav-item" @click="handleLoginClick">
        {{ isLoggedIn ? (userInfo.realName || userInfo.username) + ' | 退出' : '登录' }}
      </div>
    </nav>

    <!-- Logged in as student: sidebar + main content -->
    <div v-if="isLoggedIn && isStudent" style="display: flex; flex: 1; min-height: 0;">
      <aside class="admin-sidebar">
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
        <nav class="admin-nav">
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
          </a>
          <div v-if="conversations.length === 0" class="empty">暂无历史对话</div>
        </nav>
        <div class="admin-foot">大连东软信息学院<br/>智能问答 · v1.0</div>
      </aside>

      <main class="home-main">
        <h1 class="main-title">你好，今天想聊点什么？</h1>
        <p class="sub-title">选择一个方向，然后把问题告诉我</p>

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
                  @click="selectedCategory = cat.value"
                >{{ cat.label }}</button>
             </div>
             <button class="send-btn" @click="handleSendFromHome">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="12" y1="19" x2="12" y2="5"></line>
                  <polyline points="5 12 12 5 19 12"></polyline>
                </svg>
             </button>
           </div>
        </div>
      </main>
    </div>

    <!-- Not logged in: just main content -->
    <main v-else class="home-main">
      <h1 class="main-title">你好，今天想聊点什么？</h1>
      <p class="sub-title">选择一个方向，然后把问题告诉我</p>

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
                @click="selectedCategory = cat.value"
              >{{ cat.label }}</button>
           </div>
           <button class="send-btn" @click="handleSendFromHome">
              <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="19" x2="12" y2="5"></line>
                <polyline points="5 12 12 5 19 12"></polyline>
              </svg>
           </button>
         </div>
      </div>
    </main>
  </div>
</template>

<script src="../js/home.js"></script>
<style src="../css/home.css" scoped></style>
<style src="../css/admin.css" scoped></style>

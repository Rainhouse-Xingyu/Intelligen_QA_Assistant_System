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
    </nav>

    <!-- Main Content -->
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
</template>

<script src="../js/home.js"></script>
<style src="../css/home.css" scoped></style>

<template>
  <div class="admin-shell">
    <AdminSidebar active="chat" />

    <main class="admin-main">
      <AdminTopbar title="人工客服" />

      <section class="service-status admin-card">
        <div class="online-state">
          <span :class="{ on: online }"></span>
          在线接待中
          <button class="switch" :class="{ on: online }" @click="toggleOnline"><i></i></button>
        </div>
        <strong>客服：王老师</strong>
      </section>

      <section class="chat-grid">
        <aside class="admin-card queue-panel">
          <h3 class="section-title">等待队列 <span class="badge">{{ sessions.length }}</span></h3>
          <button
            v-for="session in sessions"
            :key="session.sessionId"
            :class="['queue-item', { active: current?.sessionId === session.sessionId }]"
            @click="currentId = session.sessionId"
          >
            <strong>{{ session.nickname }}</strong>
            <span>{{ session.latestQuestion }}</span>
            <em>{{ session.waitMinutes }}'</em>
          </button>
        </aside>

        <section class="admin-card chat-panel">
          <header class="chat-panel-head">
            <div>
              <h3>{{ current?.nickname || '请选择会话' }}</h3>
              <p>学生 · {{ current?.userId || '-' }}</p>
            </div>
            <button class="btn ghost" :disabled="!current" @click="finishSession">
              <CheckIcon />
              结束会话
            </button>
          </header>

          <div class="message-area">
            <div v-if="current" class="system-line">— 用户 {{ current.userId }} 已转入人工客服 —</div>
            <div v-for="(message, index) in currentMessages" :key="index" :class="['service-message', message.sender]">
              <span>{{ message.content }}</span>
              <time>{{ message.time }}</time>
            </div>
            <div v-if="!current" class="empty">等待用户转人工</div>
          </div>

          <footer class="reply-box">
            <textarea
              v-model="reply"
              class="textarea"
              placeholder="输入回复内容，Enter 发送 / Shift+Enter 换行"
              @keydown.enter.prevent="handleEnter"
            ></textarea>
            <button class="btn primary send-button" :disabled="!current || !reply.trim()" @click="sendReply">
              <SendIcon />
              发送
            </button>
          </footer>
        </section>

        <aside class="admin-card info-panel">
          <h3 class="section-title">用户信息</h3>
          <dl>
            <dt>用户 ID</dt>
            <dd>{{ current?.userId || '-' }}</dd>
            <dt>昵称</dt>
            <dd>{{ current?.nickname || '-' }}</dd>
            <dt>角色</dt>
            <dd>学生</dd>
            <dt>等待时长</dt>
            <dd>{{ current ? `${current.waitMinutes} 分钟` : '-' }}</dd>
            <dt>最近问题</dt>
            <dd class="question-box">{{ current?.latestQuestion || '-' }}</dd>
            <dt>转人工原因</dt>
            <dd><span class="tag yellow">AI 未命中</span></dd>
          </dl>
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { apiForm, wsUrl } from '../js/adminApi'
import '../css/admin.css'
import { AdminSidebar, AdminTopbar, CheckIcon, SendIcon } from './shared/adminParts'

const adminId = localStorage.getItem('userId') || '1'
const online = ref(false)
const socket = ref(null)
const currentId = ref(null)
const reply = ref('')
const sessions = ref([
  {
    sessionId: 1,
    userId: 'u001',
    nickname: '用户001',
    latestQuestion: '挂科了怎么办',
    waitMinutes: 3,
    messages: [
      { sender: 'user', content: '挂科了怎么办？', time: '10:03' },
      { sender: 'user', content: '之前 AI 回复的不太清楚，能详细说补考和重修的区别吗？', time: '10:03' }
    ]
  },
  {
    sessionId: 2,
    userId: 'u002',
    nickname: '用户002',
    latestQuestion: '奖学金评定细则',
    waitMinutes: 7,
    messages: [{ sender: 'user', content: '奖学金评定细则是什么？', time: '10:06' }]
  },
  {
    sessionId: 3,
    userId: 'u003',
    nickname: '用户003',
    latestQuestion: '课表调整流程',
    waitMinutes: 12,
    messages: [{ sender: 'user', content: '课表调整流程怎么走？', time: '10:08' }]
  }
])

const current = computed(() => sessions.value.find(item => item.sessionId === currentId.value) || sessions.value[0] || null)
const currentMessages = computed(() => current.value?.messages || [])

function connectWs() {
  closeWs()
  socket.value = new WebSocket(wsUrl(`/ws/chat/admin/${adminId}`))
  socket.value.onopen = () => {
    online.value = true
  }
  socket.value.onmessage = event => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'NEW_TASK') addTask(data)
      if (data.type === 'USER_MSG') appendUserMessage(data)
    } catch {
      // Ignore malformed push payloads from manual tests.
    }
  }
  socket.value.onclose = () => {
    online.value = false
  }
}

function closeWs() {
  if (socket.value) {
    socket.value.close()
    socket.value = null
  }
}

function toggleOnline() {
  if (online.value) {
    closeWs()
  } else {
    connectWs()
  }
}

function addTask(data) {
  const exists = sessions.value.some(item => item.sessionId === data.sessionId)
  if (exists) return
  sessions.value.unshift({
    sessionId: data.sessionId,
    userId: String(data.userId),
    nickname: `用户${data.userId}`,
    latestQuestion: data.msg || '用户请求人工介入',
    waitMinutes: 0,
    messages: [{ sender: 'user', content: data.msg || '用户请求人工介入', time: nowTime() }]
  })
  currentId.value = data.sessionId
}

function appendUserMessage(data) {
  let session = sessions.value.find(item => item.sessionId === data.sessionId)
  if (!session) {
    addTask(data)
    session = sessions.value.find(item => item.sessionId === data.sessionId)
  }
  session.latestQuestion = data.content
  session.messages.push({ sender: 'user', content: data.content, time: nowTime() })
}

function handleEnter(event) {
  if (event.shiftKey) {
    reply.value += '\n'
    return
  }
  sendReply()
}

async function sendReply() {
  if (!current.value || !reply.value.trim()) return
  const content = reply.value.trim()
  await apiForm('/api/chat/admin/reply', {
    sessionId: current.value.sessionId,
    content
  })
  current.value.messages.push({ sender: 'admin', content, time: nowTime() })
  reply.value = ''
}

async function finishSession() {
  if (!current.value) return
  await apiForm('/api/chat/admin/finish', { sessionId: current.value.sessionId })
  sessions.value = sessions.value.filter(item => item.sessionId !== current.value.sessionId)
  currentId.value = sessions.value[0]?.sessionId || null
}

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

onMounted(() => {
  currentId.value = sessions.value[0]?.sessionId || null
})

onBeforeUnmount(closeWs)
</script>

<style scoped>
.service-status {
  min-height: 56px;
  padding: 0 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.online-state {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 900;
}

.online-state > span {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #b8c6dc;
}

.online-state > span.on {
  background: #8adfc8;
}

.switch {
  width: 48px;
  height: 26px;
  border: 0;
  border-radius: 20px;
  background: #0d1528;
  padding: 3px;
  cursor: pointer;
}

.switch i {
  display: block;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s;
}

.switch.on i {
  transform: translateX(22px);
}

.chat-grid {
  display: grid;
  grid-template-columns: 300px 1fr 310px;
  gap: 22px;
  min-height: 650px;
}

.queue-panel,
.chat-panel,
.info-panel {
  padding: 22px;
}

.badge {
  margin-left: auto;
  display: inline-grid;
  place-items: center;
  min-width: 38px;
  height: 28px;
  border-radius: 9px;
  background: #ffc743;
}

.queue-item {
  width: 100%;
  min-height: 82px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #173875;
  text-align: left;
  padding: 14px 16px;
  position: relative;
  cursor: pointer;
}

.queue-item.active {
  background: #e2efff;
  outline: 1px solid #b8d5ff;
}

.queue-item strong,
.queue-item span {
  display: block;
  font-weight: 900;
}

.queue-item span {
  margin-top: 6px;
}

.queue-item em {
  position: absolute;
  right: 14px;
  top: 18px;
  font-style: normal;
  font-weight: 900;
}

.chat-panel {
  display: grid;
  grid-template-rows: 78px 1fr 104px;
  padding: 0;
  overflow: hidden;
}

.chat-panel-head {
  border-bottom: 1px solid #d8e4f5;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-panel-head h3,
.chat-panel-head p {
  margin: 0;
}

.chat-panel-head p {
  margin-top: 4px;
  font-weight: 800;
}

.message-area {
  padding: 30px;
  overflow: auto;
  background: rgba(255, 255, 255, 0.5);
}

.system-line {
  text-align: center;
  font-weight: 900;
  margin-bottom: 24px;
}

.service-message {
  display: grid;
  justify-items: start;
  margin-bottom: 18px;
}

.service-message.admin {
  justify-items: end;
}

.service-message span {
  max-width: 70%;
  border: 1px solid #d8e4f5;
  border-radius: 14px;
  background: #fff;
  padding: 12px 18px;
  font-weight: 800;
}

.service-message.admin span {
  background: #0d1528;
  color: #fff;
}

.service-message time {
  margin-top: 6px;
  font-weight: 800;
}

.reply-box {
  border-top: 1px solid #d8e4f5;
  padding: 16px;
  display: grid;
  grid-template-columns: 1fr 104px;
  gap: 12px;
}

.reply-box .textarea {
  min-height: 72px;
}

.send-button {
  height: 72px;
}

.info-panel dl {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 18px 12px;
  margin: 0;
  font-weight: 900;
}

.info-panel dt {
  color: #49659c;
}

.info-panel dd {
  margin: 0;
}

.question-box {
  grid-column: 1 / -1;
  border: 1px solid #d8e4f5;
  border-radius: 12px;
  padding: 12px;
  background: #fff;
}

@media (max-width: 1280px) {
  .chat-grid {
    grid-template-columns: 1fr;
  }
}
</style>


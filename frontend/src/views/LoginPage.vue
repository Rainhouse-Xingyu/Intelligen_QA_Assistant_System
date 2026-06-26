<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { apiGet, apiJson } from '../js/adminApi'
import MascotLottie from '../components/MascotLottie.vue'

const emit = defineEmits(['login-success'])

const username = ref('')
const password = ref('')
const passwordVisible = ref(false)
const loading = ref(false)
const errorMsg = ref('')
const rememberMe = ref(false)
const mascotMode = ref('idle')
const passwordInput = ref(null)

const passwordType = computed(() => (passwordVisible.value ? 'text' : 'password'))
const activeMascotMode = computed(() => (loading.value ? 'happy' : mascotMode.value))

function togglePassword() {
  passwordVisible.value = !passwordVisible.value
  mascotMode.value = passwordVisible.value ? 'peek' : 'cover'

  nextTick(() => {
    passwordInput.value?.focus()
  })
}

function handlePasswordFocus() {
  mascotMode.value = passwordVisible.value ? 'peek' : 'cover'
}

function handlePasswordBlur(event) {
  if (event.relatedTarget?.classList?.contains('eye-button')) {
    return
  }

  if (mascotMode.value === 'peek') {
    mascotMode.value = 'return'
    return
  }

  if (mascotMode.value === 'cover') {
    mascotMode.value = 'uncover'
  }
}

function handleUsernameFocus() {
  if (mascotMode.value === 'peek') {
    mascotMode.value = 'return'
    return
  }

  if (mascotMode.value === 'cover') {
    mascotMode.value = 'uncover'
    return
  }

  if (mascotMode.value === 'return' || mascotMode.value === 'uncover') {
    return
  }

  mascotMode.value = 'idle'
}

function getRoleName(role) {
  if (role === 1) return '学生'
  if (role === 2) return '教师'
  if (role === 3) return '管理员'
  return '未知'
}

async function handleLogin() {
  errorMsg.value = ''

  if (!username.value.trim()) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true

  try {
    const loginData = await apiJson('/api/auth/login', {
      username: username.value.trim(),
      password: password.value,
    })

    if (loginData?.token) {
      const token = loginData.token

      if (rememberMe.value) {
        localStorage.setItem('token', token)
        localStorage.setItem('remembered_user', username.value.trim())
      } else {
        sessionStorage.setItem('token', token)
        localStorage.removeItem('remembered_user')
      }

      const userInfo = await apiGet('/api/auth/info')
      const storage = rememberMe.value ? localStorage : sessionStorage
      storage.setItem('user_info', JSON.stringify(userInfo))
      storage.setItem('user_role', getRoleName(userInfo.role))

      emit('login-success', userInfo)
    } else {
      errorMsg.value = '登录失败，请重试'
    }
  } catch (err) {
    errorMsg.value = err.message || '网络连接失败，请检查服务器是否已启动'
    console.error('登录请求出错:', err)
  } finally {
    loading.value = false
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !loading.value) {
    handleLogin()
  }
}

onMounted(() => {
  const savedUser = localStorage.getItem('remembered_user')
  if (savedUser) {
    username.value = savedUser
    rememberMe.value = true
  }
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <main class="login-stage">
    <div class="login-container">
      <div class="mascot-section">
        <MascotLottie :mode="activeMascotMode" />
      </div>

      <div class="form-section">
        <div class="form-header">
          <p class="subtitle">Dalian Neusoft University of Information</p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <div class="field-group">
            <label class="field-label" for="username">用户名</label>
            <div class="input-wrapper">
              <span class="input-icon user-icon"></span>
              <input
                id="username"
                v-model="username"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
                :disabled="loading"
                @focus="handleUsernameFocus"
              />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label" for="password">密码</label>
            <div class="input-wrapper">
              <span class="input-icon lock-icon"></span>
              <input
                id="password"
                ref="passwordInput"
                v-model="password"
                :type="passwordType"
                placeholder="请输入密码"
                autocomplete="current-password"
                :disabled="loading"
                @focus="handlePasswordFocus"
                @blur="handlePasswordBlur"
              />
              <button
                class="eye-button"
                type="button"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                @mousedown.prevent
                @click="togglePassword"
              >
                <span class="eye-icon" :class="{ crossed: !passwordVisible }"></span>
              </button>
            </div>
          </div>

          <div class="options-row">
            <label class="checkbox-label">
              <input v-model="rememberMe" type="checkbox" :disabled="loading" />
              <span class="checkmark"></span>
              <span>记住我</span>
            </label>
            <a href="#" class="forgot-link" @click.prevent>忘记密码？</a>
          </div>

          <Transition name="fade">
            <div v-if="errorMsg" class="error-alert">
              <span class="error-icon">!</span>
              <span>{{ errorMsg }}</span>
            </div>
          </Transition>

          <button class="login-btn" type="submit" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span v-else>登 录</span>
          </button>
        </form>

        <div class="divider">
          <span>其他入口</span>
        </div>

        <p class="footer-text">
          大连东软信息学院 · 智能问答助手系统
        </p>
      </div>
    </div>
  </main>
</template>

<style scoped>
.login-stage {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 28px;
}

.login-container {
  position: relative;
  display: grid;
  width: min(960px, 100%);
  min-height: 600px;
  grid-template-columns: minmax(280px, 1fr) minmax(320px, 1.1fr);
  align-items: center;
  gap: 0;
  overflow: hidden;
  border: 1px solid rgba(47, 140, 255, 0.18);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 28px 80px rgba(16, 72, 155, 0.16);
}

.mascot-section {
  position: relative;
  display: grid;
  min-width: 280px;
  min-height: 400px;
  place-items: center;
  overflow: hidden;
  padding: 36px 24px;
  background:
    radial-gradient(circle at 30% 24%, rgba(47, 140, 255, 0.14), transparent 34%),
    radial-gradient(circle at 78% 76%, rgba(255, 183, 47, 0.16), transparent 30%),
    linear-gradient(180deg, rgba(237, 247, 255, 0.34), rgba(255, 248, 231, 0.2));
}

.mascot-section::before {
  position: absolute;
  inset: 26px 22px;
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.46), rgba(255, 255, 255, 0.16) 58%, transparent 76%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.5), rgba(234, 245, 255, 0.18));
  content: '';
}

.mascot-section :deep(.mascot-lottie-shell) {
  z-index: 1;
  filter: drop-shadow(0 18px 26px rgba(22, 103, 223, 0.16));
}

.form-section {
  display: grid;
  gap: 20px;
  align-content: center;
  padding: 40px 36px;
}

.form-header {
  text-align: center;
  margin-bottom: 4px;
}

.form-header h1 {
  margin: 0;
  color: #082f7a;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.3;
}

.subtitle {
  margin: 4px 0 0;
  color: #7a9cc7;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.login-form {
  display: grid;
  gap: 16px;
}

.field-group {
  display: grid;
  gap: 6px;
}

.field-label {
  color: #365997;
  font-size: 13px;
  font-weight: 700;
}

.input-wrapper {
  display: grid;
  grid-template-columns: 42px 1fr;
  align-items: center;
  overflow: hidden;
  border: 2px solid #b9d8ff;
  border-radius: 14px;
  background: #fff;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}

.input-wrapper:focus-within {
  border-color: #2f8cff;
  box-shadow: 0 0 0 4px rgba(47, 140, 255, 0.12);
}

.input-wrapper:has(.eye-button) {
  grid-template-columns: 42px 1fr 48px;
}

.input-icon {
  display: block;
  width: 20px;
  height: 20px;
  margin: 0 auto;
  opacity: 0.45;
}

.user-icon {
  background: currentColor;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z'/%3E%3C/svg%3E") center / contain no-repeat;
}

.lock-icon {
  background: currentColor;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2z'/%3E%3C/svg%3E") center / contain no-repeat;
}

.input-wrapper input {
  min-width: 0;
  height: 50px;
  padding: 0 14px;
  border: 0;
  color: #0a2b72;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  outline: 0;
}

.input-wrapper input::placeholder {
  color: #a8c4e0;
  font-weight: 500;
}

.input-wrapper input:disabled {
  opacity: 0.6;
}

.eye-button {
  display: grid;
  width: 48px;
  height: 50px;
  place-items: center;
  border: 0;
  color: #0a2b72;
  background: #eaf4ff;
  cursor: pointer;
  transition: background 150ms ease;
}

.eye-button:hover {
  background: #d8ebff;
}

.eye-icon {
  position: relative;
  display: block;
  width: 26px;
  height: 18px;
  border: 2.5px solid currentColor;
  border-radius: 50% / 58%;
}

.eye-icon::before,
.eye-icon::after {
  position: absolute;
  content: '';
}

.eye-icon::before {
  top: 50%;
  left: 50%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  transform: translate(-50%, -50%);
}

.eye-icon::after {
  top: 50%;
  left: -3px;
  width: 32px;
  height: 2.5px;
  border-radius: 999px;
  background: #ffb72f;
  opacity: 0;
  transform: rotate(-36deg);
  transition: opacity 200ms ease;
}

.eye-icon.crossed::after {
  opacity: 1;
}

.options-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #4a6b94;
  font-size: 13px;
  font-weight: 600;
  user-select: none;
}

.checkbox-label input[type='checkbox'] {
  display: none;
}

.checkmark {
  position: relative;
  display: block;
  width: 18px;
  height: 18px;
  border: 2px solid #b9d8ff;
  border-radius: 5px;
  background: #fff;
  transition: all 150ms ease;
}

.checkbox-label input:checked + .checkmark {
  border-color: #1667df;
  background: #1667df;
}

.checkbox-label input:checked + .checkmark::after {
  position: absolute;
  top: 2px;
  left: 5px;
  width: 5px;
  height: 9px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  content: '';
}

.forgot-link {
  color: #1667df;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: opacity 150ms ease;
}

.forgot-link:hover {
  opacity: 0.7;
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid #ffc5c5;
  border-radius: 10px;
  background: #fff5f5;
  color: #c53030;
  font-size: 13px;
  font-weight: 600;
}

.error-icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  place-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #c53030;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}

.login-btn {
  display: grid;
  width: 100%;
  height: 50px;
  place-items: center;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #1667df 0%, #2f8cff 100%);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(22, 103, 223, 0.3);
  transition: transform 150ms ease, box-shadow 150ms ease, opacity 150ms ease;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 32px rgba(22, 103, 223, 0.4);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.spinner {
  display: block;
  width: 22px;
  height: 22px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

.divider {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
  color: #a8c4e0;
  font-size: 12px;
  font-weight: 600;
}

.divider::before,
.divider::after {
  content: '';
  height: 1px;
  background: linear-gradient(90deg, transparent, #d0e4f7, transparent);
}

.social-login {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 44px;
  border: 1.5px solid #d0e4f7;
  border-radius: 12px;
  background: #fff;
  color: #365997;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms ease;
}

.social-btn:hover {
  border-color: #2f8cff;
  background: #f0f7ff;
}

.social-icon {
  font-size: 18px;
}

.footer-text {
  margin: 0;
  text-align: center;
  color: #a8c4e0;
  font-size: 11px;
  font-weight: 600;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 250ms ease, transform 250ms ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .login-stage {
    padding: 12px;
  }

  .login-container {
    grid-template-columns: 1fr;
    min-height: auto;
    border-radius: 22px;
  }

  .mascot-section {
    min-width: 0;
    min-height: 230px;
    padding: 18px;
  }

  .mascot-section::before {
    inset: 14px;
  }

  .mascot-section :deep(.mascot-lottie-shell) {
    width: min(230px, 72vw);
  }

  .form-section {
    padding: 28px 24px;
  }

  .form-header h1 {
    font-size: 22px;
  }
}
</style>

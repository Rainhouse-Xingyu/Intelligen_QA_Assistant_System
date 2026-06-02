<script setup>
import { computed, ref } from 'vue'

const hideFrames = [
  '/lottie/dongxiaolong-frames/hide-01-normal.png',
  '/lottie/dongxiaolong-frames/hide-02-hands-up.png',
  '/lottie/dongxiaolong-frames/hide-03-cover.png',
  '/lottie/dongxiaolong-frames/hide-04-hold.png',
]

const showFrames = [
  '/lottie/dongxiaolong-frames/show-01-ready.png',
  '/lottie/dongxiaolong-frames/show-02-side-shift.png',
  '/lottie/dongxiaolong-frames/show-03-peek.png',
  '/lottie/dongxiaolong-frames/show-04-hold.png',
]

const password = ref('DNUi-2026')
const visible = ref(false)
const frameSrc = ref(hideFrames[3])
const isAnimating = ref(false)

let animationTimer = 0

const passwordType = computed(() => (visible.value ? 'text' : 'password'))
const actionLabel = computed(() => (visible.value ? '隐藏密码' : '显示密码'))
const stateLabel = computed(() => (visible.value ? '探头看（显示密码）' : '捂眼睛（隐藏密码）'))

function playFrames(frames, done) {
  window.clearInterval(animationTimer)
  isAnimating.value = true
  let index = 0
  frameSrc.value = frames[index]

  animationTimer = window.setInterval(() => {
    index += 1
    if (index >= frames.length) {
      window.clearInterval(animationTimer)
      frameSrc.value = frames[frames.length - 1]
      isAnimating.value = false
      done()
      return
    }
    frameSrc.value = frames[index]
  }, 135)
}

function togglePassword() {
  if (isAnimating.value) return

  if (visible.value) {
    playFrames([...hideFrames], () => {
      visible.value = false
    })
    return
  }

  playFrames([...showFrames], () => {
    visible.value = true
  })
}
</script>

<template>
  <main class="password-stage">
    <section class="demo-panel" aria-label="东小龙密码显隐动画演示">
      <div class="sprite-panel">
        <div class="sprite-card" :class="{ 'is-animating': isAnimating }">
          <img :src="frameSrc" alt="东小龙密码显隐动作帧" />
        </div>
        <div class="timeline" aria-hidden="true">
          <span v-for="frame in 4" :key="frame" :class="{ active: isAnimating || frame === 4 }"></span>
        </div>
      </div>

      <div class="login-card">
        <p class="eyebrow">东小龙素材库 · Lottie</p>
        <h1>查看密码 / 隐藏密码</h1>

        <label class="field-label" for="password">密码</label>
        <div class="password-field">
          <input id="password" v-model="password" :type="passwordType" autocomplete="current-password" />
          <button class="eye-button" type="button" :aria-label="actionLabel" @click="togglePassword">
            <span class="eye-icon" :class="{ crossed: !visible }"></span>
          </button>
        </div>

        <div class="status-row">
          <span>{{ stateLabel }}</span>
          <a href="/lottie/password-visibility-toggle.json" download>下载 Lottie JSON</a>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import lottie from 'lottie-web/build/player/lottie_light'

const props = defineProps({
  mode: {
    type: String,
    default: 'idle',
  },
})

const animationEl = ref(null)
let animation = null
let currentMode = ''
let isReady = false
let playToken = 0
let removeCompleteListener = null

const segments = {
  idle: {
    loop: [0, 59],
  },
  cover: {
    intro: [60, 89],
    loop: [90, 119],
  },
  peek: {
    intro: [120, 149],
    loop: [150, 179],
  },
  happy: {
    loop: [180, 239],
  },
  return: {
    intro: [240, 269],
    loop: [270, 299],
  },
  uncover: {
    intro: [300, 329],
    loop: [330, 359],
  },
}

function playMode(mode) {
  if (!animation || !isReady || currentMode === mode) return

  const config = segments[mode] || segments.idle
  const token = ++playToken
  currentMode = mode

  if (removeCompleteListener) {
    removeCompleteListener()
    removeCompleteListener = null
  }

  if (config.intro) {
    const handleComplete = () => {
      if (token !== playToken || currentMode !== mode) return
      if (removeCompleteListener) {
        removeCompleteListener()
        removeCompleteListener = null
      }
      animation.setSpeed(1)
      animation.loop = true
      animation.goToAndStop(config.loop[0], true)
      animation.playSegments(config.loop, true)
    }

    animation.loop = false
    animation.setSpeed(3.2)
    animation.addEventListener('complete', handleComplete)
    removeCompleteListener = () => animation?.removeEventListener('complete', handleComplete)
    animation.playSegments(config.intro, true)
    return
  }

  animation.setSpeed(1)
  animation.loop = true
  animation.goToAndStop(config.loop[0], true)
  animation.playSegments(config.loop, true)
}

onMounted(() => {
  animation = lottie.loadAnimation({
    container: animationEl.value,
    renderer: 'svg',
    loop: true,
    autoplay: false,
    path: '/lottie/mascot-login.json',
    rendererSettings: {
      preserveAspectRatio: 'xMidYMid meet',
    },
  })
  animation.setSubframe(true)

  animation.addEventListener('DOMLoaded', () => {
    isReady = true
    playMode(props.mode)
  })
})

watch(
  () => props.mode,
  (mode) => {
    playMode(mode)
  },
)

onBeforeUnmount(() => {
  if (removeCompleteListener) {
    removeCompleteListener()
    removeCompleteListener = null
  }

  if (animation) {
    animation.destroy()
    animation = null
  }
  isReady = false
  currentMode = ''
})
</script>

<template>
  <div class="mascot-lottie-shell" aria-hidden="true">
    <div ref="animationEl" class="mascot-lottie"></div>
  </div>
</template>

<style scoped>
.mascot-lottie-shell {
  position: relative;
  display: grid;
  width: min(360px, 100%);
  aspect-ratio: 1;
  place-items: center;
}

.mascot-lottie {
  width: 100%;
  height: 100%;
}
</style>

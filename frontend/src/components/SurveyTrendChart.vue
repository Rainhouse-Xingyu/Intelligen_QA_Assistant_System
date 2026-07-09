<template>
  <article class="trend-board">
    <aside class="term-legend">
      <strong>学期</strong>
      <span
        v-for="term in allTerms"
        :key="term.key"
        :class="{ active: activeTermKeys.has(term.key) }"
      >
        <i :style="{ background: term.color }"></i>
        {{ term.label }}
      </span>
    </aside>

    <div class="chart-wrap">
      <header class="chart-head">
        <strong>{{ title }}</strong>
        <span>横轴为题号，曲线为不同学期</span>
      </header>

      <div
        ref="scrollRef"
        class="chart-scroll"
        :class="{ dragging: isDragging }"
        @pointerdown="startDrag"
        @pointermove="onDrag"
        @pointerup="stopDrag"
        @pointercancel="stopDrag"
        @pointerleave="stopDrag"
      >
      <svg
        class="trend-chart"
        :style="{ width: `${chartWidth}px` }"
        :viewBox="`0 0 ${chartWidth} 450`"
        role="img"
        aria-label="学生问卷趋势"
      >
        <defs>
          <filter id="trend-soft-shadow" x="-10%" y="-10%" width="120%" height="120%">
            <feDropShadow dx="0" dy="4" stdDeviation="5" flood-color="#315dbc" flood-opacity="0.16" />
          </filter>
        </defs>

        <g class="grid">
          <line v-for="tick in yTicks" :key="tick" :x1="left" :x2="right" :y1="scaleY(tick)" :y2="scaleY(tick)" />
          <text v-for="tick in yTicks" :key="`y-${tick}`" :x="left - 12" :y="scaleY(tick) + 4">{{ tick }}</text>
        </g>

        <g class="axis-labels">
          <text
            v-for="question in questions"
            :key="question.code"
            :x="scaleX(question.index)"
            :y="bottom + 28"
          >
            {{ question.no }}
          </text>
          <foreignObject
            v-for="question in questions"
            :key="`${question.code}-preview`"
            :x="scaleX(question.index) - questionLabelWidth / 2"
            :y="bottom + 38"
            :width="questionLabelWidth"
            height="52"
          >
            <title>{{ question.text }}</title>
            <div class="question-preview-label" xmlns="http://www.w3.org/1999/xhtml">
              {{ question.text }}
            </div>
          </foreignObject>
          <text :x="(left + right) / 2" :y="bottom + 98" class="axis-title">问题题号</text>
          <text :x="left - 34" :y="top - 8" class="axis-title">分值</text>
        </g>

        <g v-for="line in visibleLines" :key="line.key" class="term-line">
          <path
            :d="line.path"
            :stroke="line.color"
            fill="none"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
            filter="url(#trend-soft-shadow)"
          />
          <circle
            v-for="point in line.points"
            :key="`${line.key}-${point.questionCode}`"
            :cx="point.x"
            :cy="point.y"
            r="4"
            :fill="line.color"
          />
          <text
            v-for="point in line.points"
            :key="`${line.key}-${point.questionCode}-label`"
            :x="point.x"
            :y="point.y - 10"
            :fill="line.color"
            class="value-label"
          >
            {{ point.value }}
          </text>
        </g>
      </svg>
      </div>

      <div v-if="!visibleLines.length" class="empty-chart">暂无可绘制的量表趋势</div>
    </div>
  </article>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  trend: {
    type: Object,
    required: true
  },
  title: {
    type: String,
    default: '学生历年趋势'
  }
})

const left = 56
const baseRight = 728
const top = 34
const bottom = 306
const questionStep = 138
const questionLabelWidth = 112
const minValue = 1
const maxValue = 5
const yTicks = [5, 4, 3, 2, 1]

const palette = [
  '#37c9da',
  '#8d7cf6',
  '#19a979',
  '#f59e0b',
  '#ef5b78',
  '#4f83ff',
  '#7c3aed',
  '#10b981',
  '#f97316',
  '#db2777',
  '#0891b2',
  '#64748b'
]

const scrollRef = ref(null)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartScrollLeft = ref(0)

const chartWidth = computed(() => Math.max(760, left + Math.max(questions.value.length - 1, 1) * questionStep + 80))
const right = computed(() => Math.max(baseRight, chartWidth.value - 32))

function clamp(value) {
  return Math.max(minValue, Math.min(maxValue, Number(value || minValue)))
}

function scaleY(value) {
  const ratio = (maxValue - clamp(value)) / (maxValue - minValue)
  return top + ratio * (bottom - top)
}

function scaleX(index) {
  if (questions.value.length <= 1) return (left + right.value) / 2
  return left + questionStep * index
}

function academicInfo(point = {}) {
  if (point.academicYear && point.termNo) {
    return {
      year: Number(point.academicYear),
      termNo: Number(point.termNo),
      key: `${point.academicYear}-${point.termNo}`,
      label: `${point.academicYear}-${String(Number(point.academicYear) + 1).slice(2)} 学年 第${point.termNo}学期`
    }
  }
  const date = point.submitTime ? new Date(point.submitTime) : new Date()
  const month = Number.isNaN(date.getTime()) ? 9 : date.getMonth() + 1
  const year = Number.isNaN(date.getTime()) ? new Date().getFullYear() : date.getFullYear()
  const startYear = month >= 9 ? year : year - 1
  let termNo = 1
  if (month >= 2 && month <= 6) termNo = 2
  if (month >= 7 && month <= 8) termNo = 3
  return {
    year: startYear,
    termNo,
    key: `${startYear}-${termNo}`,
    label: `${startYear}-${String(startYear + 1).slice(2)} 学年 第${termNo}学期`
  }
}

const questions = computed(() => {
  return (props.trend?.series || [])
    .map((series, index) => ({
      code: series.questionCode || `q-${index + 1}`,
      no: index + 1,
      index,
      text: series.questionText || series.indicatorName || `第${index + 1}题`
    }))
})

const allTerms = computed(() => {
  const years = new Set()
  for (const series of props.trend?.series || []) {
    for (const point of series.points || []) {
      years.add(academicInfo(point).year)
    }
  }
  const sortedYears = [...years].sort((a, b) => a - b)
  const fourYears = sortedYears.length
    ? sortedYears.slice(-4)
    : Array.from({ length: 4 }, (_, index) => new Date().getFullYear() - 3 + index)
  return fourYears.flatMap(year => [1, 2, 3].map(termNo => ({
    year,
    termNo,
    key: `${year}-${termNo}`,
    label: `${year}-${String(year + 1).slice(2)} 第${termNo}学期`,
    color: palette[((year * 3 + termNo) % palette.length + palette.length) % palette.length]
  })))
})

function smoothPath(points) {
  if (!points.length) return ''
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`
  const commands = [`M ${points[0].x} ${points[0].y}`]
  for (let index = 1; index < points.length; index++) {
    const previous = points[index - 1]
    const current = points[index]
    const midX = (previous.x + current.x) / 2
    commands.push(`C ${midX} ${previous.y}, ${midX} ${current.y}, ${current.x} ${current.y}`)
  }
  return commands.join(' ')
}

const lineMap = computed(() => {
  const map = new Map()
  const termColorMap = new Map(allTerms.value.map(term => [term.key, term.color]))
  ;(props.trend?.series || []).forEach((series, questionIndex) => {
    ;(series.points || []).forEach(point => {
      if (point.value === undefined || point.value === null) return
      const term = academicInfo(point)
      if (!termColorMap.has(term.key)) return
      if (!map.has(term.key)) {
        map.set(term.key, {
          key: term.key,
          label: term.label,
          color: termColorMap.get(term.key),
          points: []
        })
      }
      map.get(term.key).points.push({
        questionCode: series.questionCode || `q-${questionIndex + 1}`,
          x: scaleX(questionIndex),
        y: scaleY(point.value),
        value: point.value,
        questionIndex
      })
    })
  })
  return map
})

const visibleLines = computed(() => {
  return [...lineMap.value.values()]
    .map(line => {
      const deduped = new Map()
      line.points
        .sort((a, b) => a.questionIndex - b.questionIndex)
        .forEach(point => deduped.set(point.questionIndex, point))
      const points = [...deduped.values()]
      return {
        ...line,
        points,
        path: smoothPath(points)
      }
    })
    .filter(line => line.points.length > 0)
})

const activeTermKeys = computed(() => new Set(visibleLines.value.map(line => line.key)))

function startDrag(event) {
  if (!scrollRef.value) return
  isDragging.value = true
  dragStartX.value = event.clientX
  dragStartScrollLeft.value = scrollRef.value.scrollLeft
  scrollRef.value.setPointerCapture?.(event.pointerId)
}

function onDrag(event) {
  if (!isDragging.value || !scrollRef.value) return
  const delta = event.clientX - dragStartX.value
  scrollRef.value.scrollLeft = dragStartScrollLeft.value - delta
}

function stopDrag(event) {
  if (!isDragging.value) return
  isDragging.value = false
  scrollRef.value?.releasePointerCapture?.(event.pointerId)
}
</script>

<style scoped>
.trend-board {
  display: grid;
  grid-template-columns: 188px minmax(0, 1fr);
  gap: 14px;
  border: 1px solid #dbe8f8;
  border-radius: 8px;
  background: #fff;
  padding: 14px;
  box-shadow: 0 10px 24px rgba(22, 54, 100, 0.08);
}

.term-legend {
  border: 1px solid #dfe8f3;
  border-radius: 8px;
  background: #f8fbff;
  padding: 12px;
  display: grid;
  align-content: start;
  gap: 8px;
}

.term-legend strong {
  color: #173875;
  font-size: 15px;
}

.term-legend span {
  min-height: 28px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9aa7b8;
  font-size: 12px;
  font-weight: 800;
}

.term-legend span.active {
  color: #173875;
}

.term-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  opacity: 0.36;
}

.term-legend span.active i {
  opacity: 1;
}

.chart-wrap {
  position: relative;
  min-width: 0;
}

.chart-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.chart-head strong {
  color: #173875;
  font-size: 16px;
}

.chart-head span {
  color: #8293bb;
  font-size: 12px;
  font-weight: 800;
}

.chart-scroll {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  cursor: grab;
  scrollbar-color: #9ec6ff #edf4ff;
  scrollbar-width: thin;
}

.chart-scroll.dragging {
  cursor: grabbing;
  user-select: none;
}

.chart-scroll::-webkit-scrollbar {
  height: 10px;
}

.chart-scroll::-webkit-scrollbar-track {
  background: #edf4ff;
  border-radius: 999px;
}

.chart-scroll::-webkit-scrollbar-thumb {
  background: #9ec6ff;
  border-radius: 999px;
}

.trend-chart {
  min-width: 760px;
  height: 450px;
  border-radius: 8px;
  background: #f3f6f9;
  display: block;
}

.grid line {
  stroke: #dfe5ea;
  stroke-width: 1;
}

.grid text,
.axis-labels text {
  fill: #87919b;
  font-size: 12px;
  font-weight: 800;
  text-anchor: middle;
}

.grid text {
  text-anchor: end;
}

.axis-title {
  fill: #65799f;
  font-size: 13px;
}

.question-preview-label {
  width: 100%;
  height: 44px;
  color: #65799f;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.35;
  text-align: center;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.term-line circle {
  stroke: #fff;
  stroke-width: 2;
}

.value-label {
  font-size: 12px;
  font-weight: 900;
  text-anchor: middle;
}

.empty-chart {
  position: absolute;
  inset: 56px 0 0;
  display: grid;
  place-items: center;
  color: #65799f;
  font-weight: 900;
}

@media (max-width: 980px) {
  .trend-board {
    grid-template-columns: 1fr;
  }

  .term-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

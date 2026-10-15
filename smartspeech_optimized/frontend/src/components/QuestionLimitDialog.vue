<script setup>
import {
  ArrowRight,
  BarChart3,
  ClipboardCheck,
  LoaderCircle,
} from 'lucide-vue-next'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  questionCount: { type: Number, default: 12 },
  questionLimit: { type: Number, default: 12 },
})

const emit = defineEmits(['continue', 'evaluate'])
const dialogRef = ref(null)
let previousFocus = null

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement
      await nextTick()
      dialogRef.value?.querySelector('[data-autofocus]')?.focus()
      return
    }
    previousFocus?.focus?.()
    previousFocus = null
  },
)

onBeforeUnmount(() => previousFocus?.focus?.())

function handleKeydown(event) {
  if (event.key !== 'Tab') return
  const controls = [...dialogRef.value.querySelectorAll(
    'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )]
  if (!controls.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="dialog-backdrop">
        <section
          ref="dialogRef"
          class="limit-dialog"
          role="dialog"
          aria-modal="true"
          aria-label="本轮面试已完成"
          tabindex="-1"
          @keydown="handleKeydown"
        >
          <span class="dialog-icon"><ClipboardCheck :size="24" /></span>
          <span class="dialog-kicker">本轮检查点</span>
          <h2>已完成 {{ questionCount }}/{{ questionLimit }} 题</h2>
          <p>
            按面试规则，本轮到此结束，不再生成第 {{ questionLimit + 1 }} 个问题。你可以使用相同岗位、难度和简历开启新一轮，也可以直接综合评估当前表现。
          </p>
          <div class="round-note">
            继续下一轮后，题号与追问次数重新计算；当前轮问答不会生成报告。
          </div>
          <footer>
            <button
              class="button button-secondary"
              type="button"
              :disabled="loading"
              data-autofocus
              @click="emit('evaluate')"
            >
              <LoaderCircle v-if="loading" class="spin" :size="17" />
              <BarChart3 v-else :size="17" />
              {{ loading ? '正在处理' : '整体评估' }}
            </button>
            <button
              class="button button-primary"
              type="button"
              :disabled="loading"
              @click="emit('continue')"
            >
              <LoaderCircle v-if="loading" class="spin" :size="17" />
              <ArrowRight v-else :size="17" />
              继续下一轮
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 115;
  padding: 20px;
  display: grid;
  place-items: center;
  background: rgb(12 34 55 / 58%);
  backdrop-filter: blur(6px);
}

.limit-dialog {
  width: min(520px, 100%);
  padding: 28px;
  border: 1px solid #bdd5e9;
  border-radius: 10px;
  background: #fff;
  box-shadow: var(--shadow-lg);
}

.dialog-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.dialog-kicker {
  margin-top: 16px;
  display: block;
  color: var(--teal-dark);
  font-family: var(--font-data);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .08em;
}

.limit-dialog h2 {
  margin-top: 5px;
  color: var(--ink);
  font-size: 23px;
}

.limit-dialog p {
  margin-top: 10px;
  color: var(--muted-strong);
  font-size: 13px;
  line-height: 1.75;
}

.round-note {
  margin-top: 16px;
  padding: 11px 13px;
  border-left: 3px solid #6ea2d1;
  color: var(--teal-dark);
  background: #f1f7fd;
  font-size: 11px;
  line-height: 1.65;
}

.limit-dialog footer {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
  gap: 9px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity .16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 560px) {
  .limit-dialog {
    padding: 22px;
  }

  .limit-dialog footer {
    flex-direction: column-reverse;
  }

  .limit-dialog footer .button {
    width: 100%;
  }
}
</style>

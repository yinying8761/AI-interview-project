<script setup>
import { AlertTriangle, LoaderCircle, LogOut, X } from 'lucide-vue-next'

defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  answerCount: { type: Number, default: 0 },
})

const emit = defineEmits(['close', 'confirm'])
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="dialog-backdrop" @click.self="!loading && emit('close')">
        <section class="finish-dialog" role="dialog" aria-modal="true" aria-label="结束面试">
          <button
            class="icon-button dialog-close"
            type="button"
            title="关闭"
            :disabled="loading"
            @click="emit('close')"
          >
            <X :size="18" />
          </button>
          <span class="dialog-icon"><AlertTriangle :size="23" /></span>
          <h2>结束面试并生成报告？</h2>
          <p>
            系统将使用本轮 {{ answerCount }} 条回答，以及表情、注意力采样结果综合评分。
            结束后不能继续补充回答。
          </p>
          <div class="formula-note">
            最终得分 = 问答得分 × 80% + （表情得分 × 50% + 注意力得分 × 50%）× 20%
          </div>
          <footer>
            <button
              class="button button-secondary"
              type="button"
              :disabled="loading"
              @click="emit('close')"
            >
              继续面试
            </button>
            <button
              class="button button-primary"
              type="button"
              :disabled="loading"
              @click="emit('confirm')"
            >
              <LoaderCircle v-if="loading" class="spin" :size="17" />
              <LogOut v-else :size="17" />
              {{ loading ? '正在综合评估' : '结束并评估' }}
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
  z-index: 110;
  padding: 20px;
  display: grid;
  place-items: center;
  background: rgb(10 24 29 / 62%);
  backdrop-filter: blur(5px);
}

.finish-dialog {
  position: relative;
  width: min(500px, 100%);
  padding: 26px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.dialog-close {
  position: absolute;
  top: 13px;
  right: 13px;
}

.dialog-icon {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  color: #9a593d;
  background: var(--amber-soft);
}

.finish-dialog h2 {
  margin: 15px 0 0;
  color: var(--ink);
  font-size: 20px;
}

.finish-dialog p {
  margin: 9px 0 0;
  color: var(--muted-strong);
  font-size: 13px;
  line-height: 1.7;
}

.formula-note {
  margin-top: 15px;
  padding: 11px 12px;
  border-left: 3px solid var(--teal);
  color: var(--teal-dark);
  background: var(--teal-soft);
  font-size: 11px;
  line-height: 1.6;
}

.finish-dialog footer {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity .16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

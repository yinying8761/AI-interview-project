import { createApp, h, ref } from 'vue'
import MiniSparkline from './components/MiniSparkline.vue'

const values = ref([])

createApp({
  setup() {
    return () => h('main', { style: 'width: 360px; padding: 20px' }, [
      h(MiniSparkline, {
        values: values.value,
        height: 80,
        maxPoints: 20,
      }),
    ])
  },
}).mount('#app')

window.pushScore = (score) => {
  values.value = [...values.value, score].slice(-20)
}

window.readPath = () => {
  const path = document.querySelector('.trend-line')
  return path?.getAttribute('d') || ''
}

<script setup>
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const auth = useAuthStore()
const showShell = computed(() => !route.meta.public)

onMounted(() => {
  if (!auth.ready) auth.checkSession()
})
</script>

<template>
  <div class="app-root">
    <AppHeader v-if="showShell" />
    <RouterView />
  </div>
</template>

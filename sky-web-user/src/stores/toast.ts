import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const visible = ref(false)
  let timer = 0

  function show(text: string) {
    message.value = text
    visible.value = true
    window.clearTimeout(timer)
    timer = window.setTimeout(() => (visible.value = false), 2200)
  }

  return { message, visible, show }
})

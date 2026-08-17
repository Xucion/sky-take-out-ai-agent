import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/main.css'

const imageFallback = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`
  <svg xmlns="http://www.w3.org/2000/svg" width="320" height="240" viewBox="0 0 320 240">
    <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fff1df"/><stop offset="1" stop-color="#ffd3b4"/></linearGradient></defs>
    <rect width="320" height="240" rx="24" fill="url(#g)"/>
    <circle cx="160" cy="105" r="52" fill="#fff" opacity=".75"/>
    <path d="M123 105h74M132 87c5 12 5 24 0 36M151 82c5 15 5 30 0 45M170 82c-5 15-5 30 0 45M189 87c-5 12-5 24 0 36" stroke="#ee6735" stroke-width="7" stroke-linecap="round" fill="none"/>
    <text x="160" y="188" text-anchor="middle" font-family="sans-serif" font-size="18" font-weight="700" fill="#a75a38">新鲜现做</text>
  </svg>`)}`

document.addEventListener('error', (event) => {
  const image = event.target
  if (image instanceof HTMLImageElement && image.src !== imageFallback) image.src = imageFallback
}, true)

createApp(App).use(createPinia()).use(router).mount('#app')

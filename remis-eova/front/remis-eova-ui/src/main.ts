import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 应用入口：挂载 Pinia 与路由（阶段 2 / T01 地基）
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')

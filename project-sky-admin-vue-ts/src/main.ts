import { configureCompat, createApp } from 'vue'
import 'normalize.css'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import moment from 'moment'
import * as echarts from 'echarts'

import '@/styles/index.scss'
import '@/styles/home.scss'
import '@/styles/newRJWMsystem.scss'
import '@/styles/icon/iconfont.css'
import App from '@/App.vue'
import store from '@/store'
import router from '@/router'
import '@/permission'
import { checkProcessEnv } from '@/utils/common'

configureCompat({
  MODE: 2,
  COMPONENT_V_MODEL: false,
  INSTANCE_ATTRS_CLASS_STYLE: false,
  TRANSITION_GROUP_ROOT: false
})

const app = createApp(App)
app.use(store)
app.use(router)
app.use(ElementPlus)
app.config.globalProperties.moment = moment
app.config.globalProperties.$checkProcessEnv = checkProcessEnv
app.config.globalProperties.$echarts = echarts
app.mount('#app')

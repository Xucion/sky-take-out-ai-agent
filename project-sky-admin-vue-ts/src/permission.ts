import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { RouteLocationNormalized } from 'vue-router'
import { UserModule } from '@/store/modules/user'
import Cookies from 'js-cookie'

NProgress.configure({ 'showSpinner': false })

router.beforeEach(async (to: RouteLocationNormalized, _: RouteLocationNormalized, next: any) => {
  NProgress.start()
  if (Cookies.get('token')) {
    next()
  } else {
    if (!to.meta.notNeedAuth) {
      next('/login')
    } else {
      next()
    }
  }
})

router.afterEach((to: RouteLocationNormalized) => {
  NProgress.done()
  document.title = String(to.meta.title || '苍穹外卖')
})

import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: () => import('../views/MenuView.vue') },
    { path: '/checkout', component: () => import('../views/CheckoutView.vue') },
    { path: '/addresses', component: () => import('../views/AddressListView.vue') },
    { path: '/addresses/new', component: () => import('../views/AddressEditView.vue') },
    { path: '/addresses/:id', component: () => import('../views/AddressEditView.vue') },
    { path: '/orders', component: () => import('../views/OrdersView.vue') },
    { path: '/orders/:id', component: () => import('../views/OrderDetailView.vue') },
    { path: '/support', component: () => import('../views/CustomerServiceView.vue') },
    { path: '/profile', component: () => import('../views/ProfileView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && auth.token) return '/'
})

export default router

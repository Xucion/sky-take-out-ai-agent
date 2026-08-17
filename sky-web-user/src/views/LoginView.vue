<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../api'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ name: '', phone: '', password: '', confirmPassword: '' })
const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()
const route = useRoute()

async function submit() {
  if (!/^1\d{10}$/.test(form.phone)) return toast.show('请输入正确的11位手机号')
  if (form.password.length < 6) return toast.show('密码至少需要6位')
  if (mode.value === 'register' && form.password !== form.confirmPassword) return toast.show('两次输入的密码不一致')

  loading.value = true
  try {
    const result = mode.value === 'login'
      ? await login({ phone: form.phone, password: form.password })
      : await register({ name: form.name, phone: form.phone, password: form.password })
    auth.setSession(result.token, { id: result.id, name: result.name, phone: result.phone })
    toast.show(mode.value === 'login' ? '登录成功' : '注册成功')
    await router.replace(String(route.query.redirect || '/'))
  } catch (error) {
    toast.show((error as Error).message)
  } finally {
    loading.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.password = ''
  form.confirmPassword = ''
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero">
      <div class="brand-mark">SKY</div>
      <p class="eyebrow">SKY TAKE-OUT</p>
      <h1>认真吃饭，<br />是一天最好的停顿。</h1>
      <p class="intro">新鲜现做，热气腾腾送到你手里。</p>
    </section>

    <section class="login-panel">
      <div class="mode-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>
      <form @submit.prevent="submit">
        <label v-if="mode === 'register'" class="login-field">
          <span>昵称</span><input v-model.trim="form.name" maxlength="32" placeholder="怎么称呼你" autocomplete="name" />
        </label>
        <label class="login-field">
          <span>手机号</span><input v-model.trim="form.phone" maxlength="11" inputmode="numeric" placeholder="请输入手机号" autocomplete="tel" />
        </label>
        <label class="login-field">
          <span>密码</span><input v-model="form.password" type="password" maxlength="32" placeholder="6—32位密码" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" />
        </label>
        <label v-if="mode === 'register'" class="login-field">
          <span>确认密码</span><input v-model="form.confirmPassword" type="password" maxlength="32" placeholder="请再次输入密码" autocomplete="new-password" />
        </label>
        <button class="login-submit" :disabled="loading">{{ loading ? '请稍候…' : mode === 'login' ? '进入点餐' : '创建账号' }}</button>
      </form>
      <p class="switch-tip">
        {{ mode === 'login' ? '第一次来？' : '已经有账号？' }}
        <button @click="switchMode">{{ mode === 'login' ? '注册新账号' : '直接登录' }}</button>
      </p>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: minmax(320px,1.15fr) minmax(360px,.85fr); background: #fff; }
.login-hero { position: relative; overflow: hidden; padding: clamp(44px,7vw,100px); display: flex; flex-direction: column; justify-content: center; color: #fff; background: radial-gradient(circle at 80% 15%,rgba(255,206,146,.45),transparent 25%), linear-gradient(145deg,#ff7a3d,#d8431d); }
.login-hero::after { content:""; position:absolute; width:450px; height:450px; border:80px solid rgba(255,255,255,.08); border-radius:50%; right:-160px; bottom:-190px; }
.brand-mark { position:absolute; top:38px; left:clamp(44px,7vw,100px); font-weight:900; letter-spacing:.2em; font-size:20px; }
.eyebrow { font-weight:800; letter-spacing:.18em; opacity:.75; font-size:13px; }
.login-hero h1 { margin:14px 0 20px; font-size:clamp(42px,6vw,76px); line-height:1.08; letter-spacing:-.05em; max-width:720px; }
.intro { font-size:18px; opacity:.84; }
.login-panel { width:min(420px,calc(100% - 44px)); margin:auto; }
.mode-tabs { display:flex; gap:28px; margin-bottom:36px; }
.mode-tabs button { border:0; background:none; padding:0 0 10px; font-size:24px; font-weight:800; color:#bbb4af; border-bottom:3px solid transparent; }
.mode-tabs button.active { color:var(--ink); border-color:var(--orange); }
.login-field { display:block; margin-bottom:20px; }
.login-field span { display:block; font-size:13px; color:#706965; margin-bottom:8px; font-weight:700; }
.login-field input { width:100%; border:0; border-bottom:1px solid #dcd6d1; padding:11px 0; outline:none; font-size:16px; }
.login-field input:focus { border-color:var(--orange); }
.login-submit { width:100%; border:0; border-radius:14px; margin-top:18px; min-height:52px; color:#fff; background:linear-gradient(135deg,#ff7942,#e94c1e); font-weight:800; font-size:16px; box-shadow:0 10px 25px rgba(230,76,28,.25); }
.login-submit:disabled { opacity:.6; }
.switch-tip { text-align:center; color:#958e89; margin-top:22px; font-size:14px; }
.switch-tip button { border:0; background:none; color:var(--orange-dark); font-weight:700; }
@media(max-width:760px){.login-page{display:block;background:#fff7f0}.login-hero{min-height:300px;padding:70px 28px 42px;justify-content:flex-end;border-radius:0 0 34px 34px}.brand-mark{top:26px;left:28px}.login-hero h1{font-size:42px}.intro{font-size:15px}.login-panel{background:#fff;margin:-18px auto 0;position:relative;padding:28px 24px 34px;border-radius:24px;box-shadow:var(--shadow)}.mode-tabs{margin-bottom:25px}}
</style>

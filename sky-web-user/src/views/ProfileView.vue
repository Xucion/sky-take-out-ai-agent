<script setup lang="ts">
import { useRouter } from 'vue-router'
import BottomNav from '../components/BottomNav.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
function logout() { if(!window.confirm('确定退出登录吗？'))return; auth.logout(); router.replace('/login') }
</script>

<template>
  <main class="page narrow profile-page">
    <section class="profile-hero"><div class="mini-logo">SKY</div><div class="avatar">{{ auth.profile?.name?.slice(0,1) || '食' }}</div><h1>{{ auth.profile?.name || '苍穹用户' }}</h1><p>{{ auth.profile?.phone }}</p></section>
    <div class="content profile-content">
      <section class="card quick"><RouterLink to="/orders"><strong>全部订单</strong><span>查看历史点餐记录 ›</span></RouterLink><RouterLink to="/addresses"><strong>收货地址</strong><span>管理常用地址 ›</span></RouterLink></section>
      <section class="card service"><h2>更多服务</h2><a href="tel:400-000-0000"><span>☎</span><div><b>联系商家</b><small>遇到问题，打电话问问</small></div><i>›</i></a><div class="service-row"><span>◉</span><div><b>关于苍穹外卖</b><small>新鲜、准时、认真做饭</small></div></div></section>
      <button class="logout" @click="logout">退出登录</button>
    </div>
    <BottomNav active="profile" />
  </main>
</template>

<style scoped>
.profile-page{background:#f6f3f0}.profile-hero{text-align:center;background:radial-gradient(circle at 50% 0,rgba(255,208,150,.6),transparent 42%),linear-gradient(145deg,#fff5e8,#ffe7d0);padding:26px 20px 40px;border-radius:0 0 30px 30px}.mini-logo{font-weight:950;letter-spacing:.2em;text-align:left}.avatar{width:78px;height:78px;margin:30px auto 13px;border-radius:26px;background:linear-gradient(145deg,#ff7840,#e8491b);color:#fff;display:grid;place-items:center;font-size:34px;font-weight:900;box-shadow:0 12px 28px rgba(232,73,27,.25)}.profile-hero h1{font-size:23px;margin:0 0 6px}.profile-hero p{color:var(--muted);margin:0;font-size:13px}.profile-content{display:grid;gap:14px;margin-top:-18px}.quick{padding:3px 18px}.quick a{display:flex;align-items:center;justify-content:space-between;padding:18px 0;border-bottom:1px solid var(--line)}.quick a:last-child{border:0}.quick strong{font-size:15px}.quick span{font-size:12px;color:var(--muted)}.service{padding:18px}.service h2{font-size:17px;margin:0 0 8px}.service a,.service-row{display:grid;grid-template-columns:35px 1fr auto;align-items:center;gap:10px;padding:14px 0;border-bottom:1px solid var(--line)}.service-row{border:0}.service a>span,.service-row>span{font-size:20px;color:var(--orange-dark)}.service a>div,.service-row>div{display:grid;gap:4px}.service small{color:var(--muted)}.service i{font-style:normal;font-size:22px;color:#aaa}.logout{border:0;background:#fff;color:#d3483b;min-height:50px;border-radius:16px;font-weight:700;box-shadow:var(--shadow)}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cancelOrder, getOrder, remindOrder, repeatOrder, type Order } from '../api'
import { useToastStore } from '../stores/toast'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const order = ref<Order | null>(null)
const loading = ref(true)
const statusText: Record<number,string> = {1:'等待付款',2:'等待商家接单',3:'商家正在备餐',4:'骑手配送中',5:'订单已完成',6:'订单已取消'}
const statusHint: Record<number,string> = {1:'请尽快完成支付',2:'商家很快会处理你的订单',3:'美味正在制作，请耐心等待',4:'餐品正在向你赶来',5:'感谢你的选择，期待下次见',6:'这个订单已经取消'}
const itemTotal = computed(() => order.value?.orderDetailList?.reduce((sum,item)=>sum+Number(item.amount)*item.number,0) || 0)

async function load() {
  try { order.value = await getOrder(Number(route.params.id)) } catch (error) { toast.show((error as Error).message) }
  finally { loading.value = false }
}
async function cancel() { if(!order.value||!window.confirm('确定取消这个订单吗？'))return; try{await cancelOrder(order.value.id);toast.show('订单已取消');await load()}catch(error){toast.show((error as Error).message)} }
async function repeat() { if(!order.value)return; try{await repeatOrder(order.value.id);toast.show('商品已加入购物车');router.push('/')}catch(error){toast.show((error as Error).message)} }
async function remind() { if(!order.value)return; try{await remindOrder(order.value.id);toast.show('已提醒商家')}catch(error){toast.show((error as Error).message)} }
onMounted(load)
</script>

<template>
  <main class="page narrow detail-page">
    <header class="topbar transparent"><button class="back" @click="router.back()">‹</button><h1>订单详情</h1></header>
    <div v-if="loading" class="empty">正在加载订单…</div>
    <template v-else-if="order">
      <section class="status-hero"><div class="status-symbol">{{ order.status === 5 ? '✓' : order.status === 6 ? '×' : '◷' }}</div><div><h2>{{ statusText[order.status] }}</h2><p>{{ statusHint[order.status] }}</p></div></section>
      <div class="content detail-content">
        <section class="card detail-card"><h3>餐品</h3><div v-for="item in order.orderDetailList" :key="item.id" class="detail-item"><img :src="item.image" :alt="item.name"/><div><b>{{ item.name }}</b><small>{{ item.dishFlavor }}</small><span>× {{ item.number }}</span></div><strong>¥{{ (Number(item.amount)*item.number).toFixed(2) }}</strong></div><div class="money-row"><span>餐品小计</span><b>¥{{ itemTotal.toFixed(2) }}</b></div><div class="money-row total"><span>实付</span><b class="price">{{ Number(order.amount).toFixed(2) }}</b></div></section>
        <section class="card detail-card info"><h3>配送信息</h3><dl><dt>收货地址</dt><dd>{{ order.address }}</dd><dt>收货人</dt><dd>{{ order.consignee }} {{ order.phone }}</dd><dt>预计送达</dt><dd>{{ order.estimatedDeliveryTime || '尽快送达' }}</dd></dl></section>
        <section class="card detail-card info"><h3>订单信息</h3><dl><dt>订单号码</dt><dd>{{ order.number }}</dd><dt>下单时间</dt><dd>{{ order.orderTime }}</dd><dt>订单备注</dt><dd>{{ order.remark || '无' }}</dd></dl></section>
      </div>
      <footer class="detail-actions"><button v-if="order.status===1||order.status===2" class="ghost" @click="cancel">取消订单</button><button v-if="order.status>=2&&order.status<=4" class="secondary" @click="remind">催一下</button><button v-if="order.status===5||order.status===6" class="primary" @click="repeat">再来一单</button></footer>
    </template>
  </main>
</template>

<style scoped>
.detail-page{background:#f6f3f0;padding-bottom:85px}.transparent{background:#332e2b;color:#fff;border:0}.status-hero{background:linear-gradient(145deg,#332e2b,#554840);color:#fff;padding:18px 24px 34px;display:flex;align-items:center;gap:15px;border-radius:0 0 26px 26px}.status-symbol{width:52px;height:52px;border:1px solid rgba(255,255,255,.3);border-radius:50%;display:grid;place-items:center;font-size:27px}.status-hero h2{margin:0 0 5px;font-size:22px}.status-hero p{margin:0;color:#cfc6c0;font-size:12px}.detail-content{display:grid;gap:14px;margin-top:-12px}.detail-card{padding:18px}.detail-card h3{font-size:17px;margin:0 0 13px}.detail-item{display:grid;grid-template-columns:55px 1fr auto;gap:11px;align-items:center;padding:8px 0}.detail-item img{width:55px;height:55px;border-radius:10px;object-fit:cover}.detail-item>div{display:grid;gap:3px}.detail-item small,.detail-item span{font-size:10px;color:var(--muted)}.money-row{display:flex;justify-content:space-between;border-top:1px solid var(--line);padding-top:13px;margin-top:10px;font-size:13px}.money-row.total{border:0;margin:0}.money-row.total b{font-size:20px}.info dl{display:grid;grid-template-columns:76px 1fr;gap:12px;margin:0;font-size:13px}.info dt{color:var(--muted)}.info dd{margin:0;text-align:right}.detail-actions{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(100%,760px);display:flex;justify-content:flex-end;gap:10px;background:#fff;padding:12px 18px calc(12px + env(safe-area-inset-bottom));box-shadow:0 -5px 20px rgba(40,30,20,.06)}
</style>

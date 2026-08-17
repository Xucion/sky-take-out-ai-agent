<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BottomNav from '../components/BottomNav.vue'
import { cancelOrder, getOrders, remindOrder, repeatOrder, type Order } from '../api'
import { useToastStore } from '../stores/toast'

const toast = useToastStore()
const orders = ref<Order[]>([])
const loading = ref(true)
const activeStatus = ref<number | undefined>()
const tabs = [{label:'全部',value:undefined},{label:'待付款',value:1},{label:'进行中',value:2},{label:'已完成',value:5}]
const statusText: Record<number,string> = {1:'待付款',2:'等待商家接单',3:'商家已接单',4:'配送中',5:'已完成',6:'已取消'}

async function load(status?: number) {
  activeStatus.value = status
  loading.value = true
  try {
    const result = await getOrders(1, 50, status)
    orders.value = result.records || []
  } catch (error) { toast.show((error as Error).message) }
  finally { loading.value = false }
}

async function cancel(order: Order) {
  if (!window.confirm('确定取消这个订单吗？')) return
  try { await cancelOrder(order.id); toast.show('订单已取消'); await load(activeStatus.value) } catch (error) { toast.show((error as Error).message) }
}
async function repeat(order: Order) {
  try { await repeatOrder(order.id); toast.show('商品已加入购物车') } catch (error) { toast.show((error as Error).message) }
}
async function remind(order: Order) {
  try { await remindOrder(order.id); toast.show('已提醒商家尽快处理') } catch (error) { toast.show((error as Error).message) }
}
onMounted(() => load())
</script>

<template>
  <main class="page narrow orders-page">
    <header class="orders-header"><p>MY ORDERS</p><h1>我的订单</h1></header>
    <nav class="order-tabs"><button v-for="tab in tabs" :key="tab.label" :class="{active:activeStatus===tab.value}" @click="load(tab.value)">{{ tab.label }}</button></nav>
    <div class="content">
      <div v-if="loading" class="empty">正在加载订单…</div>
      <div v-else-if="!orders.length" class="empty"><span class="emoji">🧾</span>这里还没有订单<br/><RouterLink class="go-menu" to="/">去点一份喜欢的餐</RouterLink></div>
      <div v-else class="order-list">
        <article v-for="order in orders" :key="order.id" class="card order-item">
          <RouterLink :to="`/orders/${order.id}`" class="order-link">
            <header><div><span>订单号 {{ order.number }}</span><small>{{ order.orderTime }}</small></div><b :class="`status-${order.status}`">{{ statusText[order.status] || '未知状态' }}</b></header>
            <div class="dish-preview">
              <img v-for="detail in order.orderDetailList?.slice(0,3)" :key="detail.id" :src="detail.image" :alt="detail.name" />
              <div><strong>{{ order.orderDetailList?.map(d=>d.name).join('、') }}</strong><span>共 {{ order.orderDetailList?.reduce((sum,d)=>sum+d.number,0) }} 件</span></div>
              <span class="order-price">¥{{ Number(order.amount).toFixed(2) }}</span>
            </div>
          </RouterLink>
          <footer>
            <button v-if="order.status === 1 || order.status === 2" class="ghost" @click="cancel(order)">取消订单</button>
            <button v-if="order.status >= 2 && order.status <= 4" class="secondary" @click="remind(order)">催一下</button>
            <button v-if="order.status === 5 || order.status === 6" class="primary" @click="repeat(order)">再来一单</button>
          </footer>
        </article>
      </div>
    </div>
    <BottomNav active="orders" />
  </main>
</template>

<style scoped>
.orders-page{background:#f6f3f0}.orders-header{background:linear-gradient(145deg,#302b28,#51453e);color:#fff;padding:33px 22px 28px}.orders-header p{letter-spacing:.16em;font-size:11px;color:#c8bdb5;margin:0}.orders-header h1{font-size:30px;margin:7px 0 0}.order-tabs{display:flex;position:sticky;top:0;z-index:10;background:#fff;border-bottom:1px solid var(--line);padding:0 12px}.order-tabs button{flex:1;border:0;background:none;padding:16px 5px 13px;border-bottom:3px solid transparent;color:var(--muted)}.order-tabs button.active{color:var(--orange-dark);border-color:var(--orange);font-weight:800}.order-list{display:grid;gap:14px}.order-item{padding:17px}.order-link>header{display:flex;justify-content:space-between;gap:10px;border-bottom:1px solid var(--line);padding-bottom:12px}.order-link>header div{display:grid;gap:4px}.order-link>header span{font-size:12px;font-weight:700}.order-link>header small{color:var(--muted);font-size:10px}.order-link>header b{font-size:13px;color:var(--orange-dark)}.order-link>header .status-5{color:var(--green)}.order-link>header .status-6{color:var(--muted)}.dish-preview{display:grid;grid-template-columns:repeat(3,48px) 1fr auto;gap:6px;align-items:center;padding:14px 0}.dish-preview img{width:48px;height:48px;border-radius:9px;object-fit:cover}.dish-preview div{display:grid;gap:5px;min-width:0;margin-left:4px}.dish-preview div strong{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:13px}.dish-preview div span{font-size:10px;color:var(--muted)}.order-price{font-weight:800;font-size:14px}.order-item footer{display:flex;justify-content:flex-end;gap:8px}.order-item footer button{min-height:34px;padding:0 14px;font-size:12px}.go-menu{display:inline-block;color:var(--orange-dark);margin-top:12px;font-weight:700}
</style>

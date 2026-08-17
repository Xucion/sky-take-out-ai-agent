<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAddress, getCart, getDefaultAddress, payOrder, submitOrder, type Address, type CartItem } from '../api'
import { useToastStore } from '../stores/toast'

const router = useRouter()
const toast = useToastStore()
const cart = ref<CartItem[]>([])
const address = ref<Address | null>(null)
const remark = ref('')
const tablewareStatus = ref(1)
const tablewareNumber = ref(1)
const loading = ref(true)
const submitting = ref(false)
const foodAmount = computed(() => cart.value.reduce((sum, item) => sum + Number(item.amount) * item.number, 0))
const packAmount = computed(() => cart.value.length ? 1 : 0)
const totalAmount = computed(() => foodAmount.value + packAmount.value)

function formatTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function load() {
  loading.value = true
  try {
    cart.value = await getCart() || []
    const selectedId = sessionStorage.getItem('sky_checkout_address_id')
    if (selectedId) address.value = await getAddress(Number(selectedId))
    else {
      try { address.value = await getDefaultAddress() } catch { address.value = null }
    }
  } catch (error) { toast.show((error as Error).message) }
  finally { loading.value = false }
}

async function submit() {
  if (!address.value?.id) return toast.show('请先选择收货地址')
  if (!cart.value.length) return toast.show('购物车是空的')
  submitting.value = true
  try {
    const deliveryTime = new Date(Date.now() + 30 * 60 * 1000)
    const result = await submitOrder({
      addressBookId: address.value.id,
      payMethod: 2,
      remark: remark.value,
      estimatedDeliveryTime: formatTime(deliveryTime),
      deliveryStatus: 1,
      tablewareNumber: tablewareStatus.value === 1 ? 0 : tablewareNumber.value,
      tablewareStatus: tablewareStatus.value,
      packAmount: packAmount.value,
      amount: totalAmount.value,
    })
    await payOrder(result.orderNumber, 2)
    sessionStorage.removeItem('sky_checkout_address_id')
    toast.show('下单成功')
    await router.replace(`/orders/${result.id}`)
  } catch (error) { toast.show((error as Error).message) }
  finally { submitting.value = false }
}

onMounted(load)
</script>

<template>
  <main class="page narrow checkout-page">
    <header class="topbar"><button class="back" @click="router.back()">‹</button><h1>确认订单</h1></header>
    <div v-if="loading" class="empty">正在准备订单…</div>
    <div v-else class="content checkout-content">
      <RouterLink to="/addresses?select=1" class="address-card card">
        <template v-if="address">
          <div class="address-icon">⌖</div>
          <div><strong>{{ address.provinceName }}{{ address.cityName }}{{ address.districtName }}{{ address.detail }}</strong><p>{{ address.consignee }} · {{ address.phone }}</p></div>
        </template>
        <template v-else><div class="address-icon">＋</div><strong>添加收货地址</strong></template>
        <span class="chevron">›</span>
      </RouterLink>

      <section class="card order-card">
        <h2>餐品明细</h2>
        <div v-for="item in cart" :key="item.id" class="checkout-item">
          <img :src="item.image" :alt="item.name" />
          <div><b>{{ item.name }}</b><small>{{ item.dishFlavor }}</small><span>× {{ item.number }}</span></div>
          <strong>¥{{ (Number(item.amount) * item.number).toFixed(2) }}</strong>
        </div>
        <div class="fee-row"><span>打包费</span><b>¥{{ packAmount.toFixed(2) }}</b></div>
        <div class="fee-row total"><span>合计</span><b class="price">{{ totalAmount.toFixed(2) }}</b></div>
      </section>

      <section class="card option-card">
        <label><span>预计送达</span><b>约 30 分钟</b></label>
        <label><span>支付方式</span><b>在线支付（演示）</b></label>
        <label class="column"><span>订单备注</span><textarea v-model.trim="remark" maxlength="100" placeholder="口味、包装等要求"></textarea></label>
        <div class="tableware"><span>餐具</span><div><button :class="{ active: tablewareStatus === 1 }" @click="tablewareStatus = 1">按餐量提供</button><button :class="{ active: tablewareStatus === 0 }" @click="tablewareStatus = 0">自选</button></div></div>
        <label v-if="tablewareStatus === 0" class="count-select"><span>餐具数量</span><select v-model="tablewareNumber"><option v-for="i in 10" :key="i" :value="i">{{ i }} 份</option></select></label>
      </section>
    </div>
    <footer class="submit-bar"><div><small>应付</small><strong class="price">{{ totalAmount.toFixed(2) }}</strong></div><button class="primary" :disabled="submitting || !cart.length" @click="submit">{{ submitting ? '提交中…' : '提交订单' }}</button></footer>
  </main>
</template>

<style scoped>
.checkout-page{background:#f5f2ef}.checkout-content{display:grid;gap:14px;padding-bottom:100px}.address-card{display:grid;grid-template-columns:42px 1fr auto;gap:12px;align-items:center;padding:20px}.address-icon{width:42px;height:42px;border-radius:14px;background:var(--cream);color:var(--orange-dark);display:grid;place-items:center;font-size:22px}.address-card strong{font-size:15px;line-height:1.5}.address-card p{margin:6px 0 0;color:var(--muted);font-size:13px}.chevron{font-size:25px;color:#aaa}.order-card,.option-card{padding:18px}.order-card h2{font-size:18px;margin:0 0 14px}.checkout-item{display:grid;grid-template-columns:58px 1fr auto;gap:11px;align-items:center;padding:9px 0}.checkout-item img{width:58px;height:58px;object-fit:cover;border-radius:11px}.checkout-item>div{display:grid;gap:3px}.checkout-item small,.checkout-item span{font-size:11px;color:var(--muted)}.checkout-item>strong{font-size:14px}.fee-row{display:flex;justify-content:space-between;padding:14px 0 2px;border-top:1px solid var(--line);margin-top:9px;font-size:14px}.fee-row.total{align-items:baseline;border:0;margin:0}.fee-row.total b{font-size:22px}.option-card>label,.tableware,.count-select{display:flex;justify-content:space-between;align-items:center;padding:14px 0;border-bottom:1px solid var(--line);font-size:14px}.option-card label:last-child{border:0}.option-card .column{display:block}.option-card textarea{width:100%;margin-top:10px;border:0;background:#f7f5f3;border-radius:12px;padding:12px;min-height:70px;resize:none;outline:none}.tableware>div{display:flex;gap:6px}.tableware button{border:1px solid var(--line);background:#fff;padding:7px 9px;border-radius:10px;font-size:11px}.tableware button.active{border-color:var(--orange);color:var(--orange-dark);background:var(--cream)}.count-select select{border:0;background:#f6f3f0;padding:7px;border-radius:8px}.submit-bar{position:fixed;z-index:20;bottom:0;left:50%;transform:translateX(-50%);width:min(100%,760px);background:#fff;padding:12px 18px calc(12px + env(safe-area-inset-bottom));display:flex;align-items:center;justify-content:space-between;box-shadow:0 -8px 25px rgba(40,30,20,.08)}.submit-bar>div{display:grid}.submit-bar small{color:var(--muted)}.submit-bar strong{font-size:23px}.submit-bar button{min-width:140px}
</style>

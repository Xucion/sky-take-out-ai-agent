<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteAddress, getAddresses, setDefaultAddress, type Address } from '../api'
import { useToastStore } from '../stores/toast'

const addresses = ref<Address[]>([])
const router = useRouter()
const route = useRoute()
const toast = useToastStore()
const selecting = route.query.select === '1'

async function load() {
  try { addresses.value = await getAddresses() || [] } catch (error) { toast.show((error as Error).message) }
}
function selectAddress(address: Address) {
  if (!selecting || !address.id) return
  sessionStorage.setItem('sky_checkout_address_id', String(address.id))
  router.back()
}
async function makeDefault(address: Address) {
  if (!address.id) return
  try { await setDefaultAddress(address.id); toast.show('已设为默认地址'); await load() } catch (error) { toast.show((error as Error).message) }
}
async function remove(address: Address) {
  if (!address.id || !window.confirm('确定删除这个地址吗？')) return
  try { await deleteAddress(address.id); toast.show('地址已删除'); await load() } catch (error) { toast.show((error as Error).message) }
}
onMounted(load)
</script>

<template>
  <main class="page narrow address-page">
    <header class="topbar"><button class="back" @click="router.back()">‹</button><h1>{{ selecting ? '选择收货地址' : '地址管理' }}</h1></header>
    <div class="content">
      <div v-if="!addresses.length" class="empty"><span class="emoji">📍</span>还没有收货地址</div>
      <div v-else class="address-list">
        <article v-for="address in addresses" :key="address.id" class="card address-item" @click="selectAddress(address)">
          <div class="address-main"><div class="person"><strong>{{ address.consignee }}</strong><span>{{ address.sex === '1' ? '先生' : '女士' }}</span><span>{{ address.phone }}</span></div><p><i v-if="address.label">{{ address.label }}</i>{{ address.provinceName }}{{ address.cityName }}{{ address.districtName }}{{ address.detail }}</p></div>
          <div class="address-actions" @click.stop><button v-if="address.isDefault !== 1" @click="makeDefault(address)">设为默认</button><span v-else>默认地址</span><RouterLink :to="`/addresses/${address.id}`">编辑</RouterLink><button class="remove" @click="remove(address)">删除</button></div>
        </article>
      </div>
    </div>
    <RouterLink to="/addresses/new" class="primary add-address">＋ 新增收货地址</RouterLink>
  </main>
</template>

<style scoped>
.address-page{background:#f6f3f0}.address-list{display:grid;gap:13px;padding-bottom:80px}.address-item{padding:18px}.person{display:flex;align-items:center;gap:9px}.person strong{font-size:17px}.person span{font-size:12px;color:var(--muted)}.address-main p{font-size:14px;line-height:1.55;margin:10px 0}.address-main i{font-style:normal;font-size:10px;color:var(--orange-dark);background:var(--cream);border-radius:5px;padding:3px 5px;margin-right:6px}.address-actions{display:flex;align-items:center;gap:16px;border-top:1px solid var(--line);padding-top:12px;font-size:12px}.address-actions button{border:0;background:none;padding:0;color:#615b57}.address-actions span{color:var(--orange-dark);margin-right:auto}.address-actions span~*{margin-left:0}.address-actions button:first-child{margin-right:auto}.address-actions .remove{color:#c54b40}.add-address{position:fixed;left:50%;bottom:calc(18px + env(safe-area-inset-bottom));transform:translateX(-50%);width:min(calc(100% - 32px),700px);display:flex;align-items:center;justify-content:center}
</style>

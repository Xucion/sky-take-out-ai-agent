<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { areaList } from '@vant/area-data'
import { createAddress, getAddress, updateAddress, type Address } from '../api'
import { useToastStore } from '../stores/toast'

const route = useRoute()
const router = useRouter()
const toast = useToastStore()
const saving = ref(false)
const editing = computed(() => Boolean(route.params.id))
const form = reactive<Address>({
  consignee:'', phone:'', sex:'1',
  provinceCode:'', provinceName:'', cityCode:'', cityName:'', districtCode:'', districtName:'',
  detail:'', label:'家', isDefault:0,
})

const provinces = Object.entries(areaList.province_list).map(([code, name]) => ({ code, name }))
const cities = computed(() => Object.entries(areaList.city_list)
  .filter(([code]) => Boolean(form.provinceCode) && code.slice(0, 2) === form.provinceCode!.slice(0, 2))
  .map(([code, name]) => ({ code, name })))
const districts = computed(() => Object.entries(areaList.county_list)
  .filter(([code]) => Boolean(form.cityCode) && code.slice(0, 4) === form.cityCode!.slice(0, 4))
  .map(([code, name]) => ({ code, name })))

function changeProvince() {
  form.provinceName = areaList.province_list[form.provinceCode || ''] || ''
  form.cityCode = ''
  form.cityName = ''
  form.districtCode = ''
  form.districtName = ''
}

function changeCity() {
  form.cityName = areaList.city_list[form.cityCode || ''] || ''
  form.districtCode = ''
  form.districtName = ''
}

function changeDistrict() {
  form.districtName = areaList.county_list[form.districtCode || ''] || ''
}

function inferLegacyCodes() {
  if (!form.provinceCode && form.provinceName) {
    form.provinceCode = provinces.find((item) => item.name === form.provinceName)?.code || ''
  }
  if (!form.cityCode && form.cityName) {
    form.cityCode = cities.value.find((item) => item.name === form.cityName)?.code || ''
  }
  if (!form.districtCode && form.districtName) {
    form.districtCode = districts.value.find((item) => item.name === form.districtName)?.code || ''
  }
}

async function save() {
  if (!form.consignee.trim()) return toast.show('请输入收货人')
  if (!/^1\d{10}$/.test(form.phone)) return toast.show('请输入正确的手机号')
  if (!form.provinceCode || !form.cityCode || !form.districtCode) return toast.show('请选择完整的省、市、区县')
  if (!form.provinceName || !form.cityName || !form.districtName || !form.detail.trim()) return toast.show('请完善省市区和详细地址')
  saving.value = true
  try {
    editing.value ? await updateAddress(form) : await createAddress(form)
    toast.show('地址已保存')
    router.back()
  } catch (error) { toast.show((error as Error).message) }
  finally { saving.value = false }
}

onMounted(async () => {
  if (!editing.value) return
  try {
    Object.assign(form, await getAddress(Number(route.params.id)))
    inferLegacyCodes()
  } catch (error) { toast.show((error as Error).message) }
})
</script>

<template>
  <main class="page narrow">
    <header class="topbar"><button class="back" @click="router.back()">‹</button><h1>{{ editing ? '编辑地址' : '新增地址' }}</h1></header>
    <div class="content"><form class="card form-card" @submit.prevent="save">
      <label class="field"><span>收货人</span><input v-model.trim="form.consignee" maxlength="20" placeholder="姓名" /></label>
      <label class="field"><span>性别</span><select v-model="form.sex"><option value="1">先生</option><option value="0">女士</option></select></label>
      <label class="field"><span>手机号</span><input v-model.trim="form.phone" maxlength="11" inputmode="numeric" placeholder="收货手机号" /></label>
      <label class="field"><span>省份</span><select v-model="form.provinceCode" required @change="changeProvince"><option value="" disabled>请选择省份</option><option v-for="item in provinces" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
      <label class="field"><span>城市</span><select v-model="form.cityCode" required :disabled="!form.provinceCode" @change="changeCity"><option value="" disabled>请选择城市</option><option v-for="item in cities" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
      <label class="field"><span>区县</span><select v-model="form.districtCode" required :disabled="!form.cityCode" @change="changeDistrict"><option value="" disabled>请选择区县</option><option v-for="item in districts" :key="item.code" :value="item.code">{{ item.name }}</option></select></label>
      <label class="field"><span>详细地址</span><textarea v-model.trim="form.detail" maxlength="100" placeholder="街道、门牌号、楼层和房间号"></textarea></label>
      <label class="field"><span>地址标签</span><select v-model="form.label"><option>家</option><option>公司</option><option>学校</option><option>其他</option></select></label>
      <label class="default-row"><input v-model="form.isDefault" type="checkbox" :true-value="1" :false-value="0" /><span>设为默认地址</span></label>
      <button class="primary save" :disabled="saving">{{ saving ? '保存中…' : '保存地址' }}</button>
    </form></div>
  </main>
</template>

<style scoped>
.default-row{display:flex;align-items:center;gap:9px;font-size:14px}.default-row input{accent-color:var(--orange);width:18px;height:18px}.save{width:100%;margin-top:24px}
</style>

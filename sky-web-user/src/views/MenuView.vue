<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import BottomNav from '../components/BottomNav.vue'
import {
  addCart, cleanCart, getCart, getCategories, getDishes, getSetmeals, getShopStatus, subCart,
  type CartItem, type Category, type Flavor, type Product,
} from '../api'
import { useToastStore } from '../stores/toast'

const toast = useToastStore()
const categories = ref<Category[]>([])
const activeCategory = ref<Category | null>(null)
const products = ref<Product[]>([])
const cart = ref<CartItem[]>([])
const loading = ref(true)
const shopStatus = ref(1)
const cartOpen = ref(false)
const flavorProduct = ref<Product | null>(null)
const selectedFlavors = ref<Record<string, string>>({})

const cartCount = computed(() => cart.value.reduce((sum, item) => sum + item.number, 0))
const cartAmount = computed(() => cart.value.reduce((sum, item) => sum + Number(item.amount) * item.number, 0))

function flavorOptions(flavor: Flavor) {
  try { return JSON.parse(flavor.value) as string[] } catch { return flavor.value.split(',') }
}

function itemPayload(item: CartItem) {
  return { dishId: item.dishId, setmealId: item.setmealId, dishFlavor: item.dishFlavor }
}

function productCount(product: Product) {
  return cart.value.filter((item) => item.dishId === product.id || item.setmealId === product.id)
    .reduce((sum, item) => sum + item.number, 0)
}

async function loadProducts(category: Category) {
  activeCategory.value = category
  loading.value = true
  try {
    products.value = category.type === 2 ? await getSetmeals(category.id) : await getDishes(category.id)
  } catch (error) { toast.show((error as Error).message) }
  finally { loading.value = false }
}

async function refreshCart() {
  try { cart.value = await getCart() || [] } catch (error) { toast.show((error as Error).message) }
}

async function addProduct(product: Product) {
  if (shopStatus.value === 0) return toast.show('店铺休息中，暂时无法下单')
  if (product.flavors?.length) {
    flavorProduct.value = product
    selectedFlavors.value = Object.fromEntries(product.flavors.map((f) => [f.name, flavorOptions(f)[0] || '']))
    return
  }
  await changeCart({ [activeCategory.value?.type === 2 ? 'setmealId' : 'dishId']: product.id }, 'add')
}

async function confirmFlavor() {
  if (!flavorProduct.value) return
  const dishFlavor = Object.entries(selectedFlavors.value).map(([name, value]) => `${name}:${value}`).join(';')
  await changeCart({ dishId: flavorProduct.value.id, dishFlavor }, 'add')
  flavorProduct.value = null
}

async function changeCart(payload: { dishId?: number; setmealId?: number; dishFlavor?: string }, action: 'add' | 'sub') {
  try {
    action === 'add' ? await addCart(payload) : await subCart(payload)
    await refreshCart()
  } catch (error) { toast.show((error as Error).message) }
}

async function clearAll() {
  if (!window.confirm('确定清空购物车吗？')) return
  try { await cleanCart(); cart.value = []; cartOpen.value = false } catch (error) { toast.show((error as Error).message) }
}

onMounted(async () => {
  try {
    const [status, categoryList] = await Promise.all([getShopStatus(), getCategories(), refreshCart()])
    shopStatus.value = status ?? 1
    categories.value = (categoryList || []).filter((item) => item.status === 1)
    if (categories.value.length) await loadProducts(categories.value[0])
  } catch (error) { toast.show((error as Error).message); loading.value = false }
})
</script>

<template>
  <main class="page narrow menu-page">
    <header class="menu-hero">
      <div class="hero-top"><div class="logo">SKY</div><span class="status" :class="{ closed: shopStatus === 0 }">{{ shopStatus === 0 ? '休息中' : '营业中' }}</span></div>
      <p>今天想吃点什么？</p>
      <h1>新鲜现做，<br /><em>每一口都值得期待。</em></h1>
      <div class="delivery-note"><b>约 30 分钟送达</b><span>配送费 ¥0 · 起送 ¥0</span></div>
    </header>

    <div v-if="shopStatus === 0" class="closed-banner">店铺正在休息，可以先看看菜单</div>
    <section class="menu-layout">
      <aside class="category-list">
        <button v-for="category in categories" :key="category.id" :class="{ active: activeCategory?.id === category.id }" @click="loadProducts(category)">
          {{ category.name }}
        </button>
      </aside>

      <div class="product-area">
        <div class="section-heading"><h2>{{ activeCategory?.name || '菜单' }}</h2><span>{{ products.length }} 道</span></div>
        <div v-if="loading" class="product-list">
          <div v-for="i in 4" :key="i" class="product-card loading-card"><div class="skeleton pic"></div><div class="grow"><div class="skeleton line"></div><div class="skeleton line short"></div></div></div>
        </div>
        <div v-else-if="!products.length" class="empty"><span class="emoji">🍽️</span>这个分类暂时还没有商品</div>
        <div v-else class="product-list">
          <article v-for="product in products" :key="product.id" class="product-card">
            <img :src="product.image" :alt="product.name" loading="lazy" />
            <div class="product-info">
              <h3>{{ product.name }}</h3>
              <p>{{ product.description || (activeCategory?.type === 2 ? '精选搭配，一份满足' : '新鲜食材，现点现做') }}</p>
              <div class="product-bottom"><span class="price">{{ Number(product.price).toFixed(2) }}</span><button class="add" @click="addProduct(product)">{{ product.flavors?.length ? '选规格' : '+' }}<i v-if="productCount(product)">{{ productCount(product) }}</i></button></div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <div v-if="cartCount" class="cart-bar">
      <button class="cart-bag" @click="cartOpen = true"><span>🛍</span><i>{{ cartCount }}</i></button>
      <button class="cart-summary" @click="cartOpen = true"><strong class="price">{{ cartAmount.toFixed(2) }}</strong><small>已选 {{ cartCount }} 件</small></button>
      <RouterLink to="/checkout" class="checkout-button">去结算</RouterLink>
    </div>

    <Transition name="fade">
      <div v-if="cartOpen" class="overlay" @click.self="cartOpen = false">
        <section class="sheet cart-sheet">
          <header><h2>购物车</h2><button @click="clearAll">清空</button></header>
          <div class="cart-items">
            <div v-for="item in cart" :key="item.id" class="cart-item">
              <img :src="item.image" :alt="item.name" />
              <div><b>{{ item.name }}</b><small>{{ item.dishFlavor }}</small><span class="price">{{ Number(item.amount).toFixed(2) }}</span></div>
              <div class="stepper"><button @click="changeCart(itemPayload(item),'sub')">−</button><span>{{ item.number }}</span><button @click="changeCart(itemPayload(item),'add')">+</button></div>
            </div>
          </div>
          <RouterLink to="/checkout" class="primary sheet-submit">去结算 · ¥{{ cartAmount.toFixed(2) }}</RouterLink>
        </section>
      </div>
    </Transition>

    <Transition name="fade">
      <div v-if="flavorProduct" class="overlay center" @click.self="flavorProduct = null">
        <section class="flavor-dialog">
          <button class="dialog-close" @click="flavorProduct = null">×</button>
          <img :src="flavorProduct.image" :alt="flavorProduct.name" />
          <h2>{{ flavorProduct.name }}</h2>
          <div v-for="flavor in flavorProduct.flavors" :key="flavor.id" class="flavor-group">
            <b>{{ flavor.name }}</b>
            <div><button v-for="option in flavorOptions(flavor)" :key="option" :class="{ active: selectedFlavors[flavor.name] === option }" @click="selectedFlavors[flavor.name] = option">{{ option }}</button></div>
          </div>
          <button class="primary confirm-flavor" @click="confirmFlavor">加入购物车 · ¥{{ Number(flavorProduct.price).toFixed(2) }}</button>
        </section>
      </div>
    </Transition>
    <BottomNav active="menu" />
  </main>
</template>

<style scoped>
.menu-page{background:#faf8f5}.menu-hero{padding:25px 24px 28px;background:radial-gradient(circle at 90% 20%,rgba(255,212,151,.9),transparent 28%),linear-gradient(145deg,#fff6e9,#ffe6ce);border-radius:0 0 28px 28px}.hero-top{display:flex;align-items:center;justify-content:space-between}.logo{font-weight:950;letter-spacing:.18em}.status{font-size:12px;color:var(--green);background:#e7f7ef;border-radius:999px;padding:5px 10px;font-weight:800}.status.closed{color:#8a8580;background:#eeeae6}.menu-hero>p{font-size:13px;color:#9d603b;margin:28px 0 7px;font-weight:700}.menu-hero h1{font-size:31px;line-height:1.18;margin:0;letter-spacing:-.03em}.menu-hero h1 em{font-style:normal;color:var(--orange-dark)}.delivery-note{display:flex;justify-content:space-between;align-items:center;margin-top:22px;padding-top:16px;border-top:1px solid rgba(151,90,46,.14);font-size:12px;color:#8c796b}.delivery-note b{color:#4a3c33}.closed-banner{margin:14px 16px 0;background:#3b3734;color:#fff;padding:10px 14px;border-radius:12px;text-align:center;font-size:13px}.menu-layout{display:grid;grid-template-columns:92px 1fr;align-items:start;min-height:500px}.category-list{position:sticky;top:0;display:flex;flex-direction:column;padding:14px 8px;gap:5px}.category-list button{border:0;background:transparent;min-height:48px;padding:8px 6px;border-radius:12px;color:#77716c;font-size:13px}.category-list button.active{background:#fff;color:var(--orange-dark);font-weight:800;box-shadow:0 6px 20px rgba(48,35,25,.07)}.product-area{padding:18px 14px 115px 5px}.section-heading{display:flex;align-items:baseline;justify-content:space-between;margin:0 3px 13px}.section-heading h2{font-size:20px;margin:0}.section-heading span{font-size:12px;color:var(--muted)}.product-list{display:grid;gap:12px}.product-card{display:flex;gap:12px;background:#fff;padding:10px;border-radius:16px;box-shadow:0 6px 20px rgba(62,46,34,.055)}.product-card>img,.loading-card .pic{width:98px;height:98px;object-fit:cover;border-radius:12px;background:#f0ece8}.product-info{flex:1;min-width:0;display:flex;flex-direction:column}.product-info h3{font-size:16px;margin:3px 0 5px}.product-info p{font-size:11px;line-height:1.4;color:var(--muted);margin:0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.product-bottom{display:flex;align-items:center;justify-content:space-between;margin-top:auto}.add{position:relative;border:0;background:var(--orange);color:#fff;min-width:31px;height:31px;padding:0 9px;border-radius:999px;font-size:16px;font-weight:800}.add i{position:absolute;right:-5px;top:-8px;background:#2f2a27;color:#fff;font-size:9px;min-width:16px;height:16px;border-radius:9px;line-height:16px;font-style:normal}.loading-card{height:118px}.loading-card .grow{flex:1;padding-top:8px}.loading-card .line{height:15px;border-radius:8px;margin-bottom:18px}.loading-card .short{width:65%}.cart-bar{position:fixed;z-index:31;bottom:calc(75px + env(safe-area-inset-bottom));left:50%;transform:translateX(-50%);width:min(calc(100% - 28px),700px);height:62px;border-radius:20px;background:#2f2b29;color:#fff;display:flex;align-items:center;box-shadow:0 12px 30px rgba(30,23,19,.3);overflow:visible}.cart-bag{position:relative;border:0;background:var(--orange);width:55px;height:55px;border-radius:20px;margin-left:5px;font-size:23px}.cart-bag i{position:absolute;right:-5px;top:-6px;background:#fff;color:var(--orange-dark);border:2px solid #2f2b29;font-style:normal;font-size:10px;min-width:20px;height:20px;line-height:16px;border-radius:10px}.cart-summary{border:0;background:none;color:#fff;text-align:left;flex:1;padding:0 14px}.cart-summary strong{display:block;font-size:18px}.cart-summary small{color:#aaa29d}.checkout-button{height:62px;padding:0 24px;background:linear-gradient(135deg,#ff7740,#e84b1d);display:flex;align-items:center;font-weight:800;border-radius:0 20px 20px 0}.overlay{position:fixed;z-index:80;inset:0;background:rgba(30,24,21,.55);display:flex;align-items:flex-end;justify-content:center}.overlay.center{align-items:center;padding:20px}.sheet{width:min(100%,760px);background:#fff;border-radius:24px 24px 0 0;padding:22px 18px calc(24px + env(safe-area-inset-bottom))}.cart-sheet header{display:flex;align-items:center;justify-content:space-between}.cart-sheet header h2{margin:0}.cart-sheet header button{border:0;background:none;color:var(--muted)}.cart-items{max-height:50vh;overflow:auto;margin:15px 0}.cart-item{display:grid;grid-template-columns:54px 1fr auto;gap:11px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}.cart-item img{width:54px;height:54px;object-fit:cover;border-radius:10px}.cart-item>div:nth-child(2){display:grid;gap:3px}.cart-item small{color:var(--muted);font-size:10px}.stepper{display:flex;align-items:center;gap:9px}.stepper button{border:0;width:27px;height:27px;border-radius:50%;background:#f1eeeb;font-size:17px}.stepper button:last-child{background:var(--orange);color:#fff}.sheet-submit{display:flex;align-items:center;justify-content:center}.flavor-dialog{position:relative;width:min(100%,440px);max-height:88vh;overflow:auto;background:#fff;border-radius:24px;padding:18px}.flavor-dialog>img{width:100%;height:190px;object-fit:cover;border-radius:16px}.flavor-dialog h2{margin:16px 2px}.dialog-close{position:absolute;right:27px;top:27px;border:0;background:rgba(0,0,0,.55);color:#fff;width:32px;height:32px;border-radius:50%;font-size:21px}.flavor-group{margin:18px 2px}.flavor-group>b{font-size:13px}.flavor-group>div{display:flex;flex-wrap:wrap;gap:8px;margin-top:9px}.flavor-group button{border:1px solid var(--line);background:#f8f6f4;padding:7px 13px;border-radius:999px;font-size:12px}.flavor-group button.active{border-color:var(--orange);background:var(--cream);color:var(--orange-dark)}.confirm-flavor{width:100%;margin-top:8px}.fade-enter-active,.fade-leave-active{transition:.2s}.fade-enter-from,.fade-leave-to{opacity:0}@media(max-width:420px){.product-card>img,.loading-card .pic{width:82px;height:88px}.menu-layout{grid-template-columns:82px}.product-area{padding-right:10px}.product-card{gap:9px}.product-info h3{font-size:15px}}
</style>

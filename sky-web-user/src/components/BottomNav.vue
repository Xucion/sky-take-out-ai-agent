<script setup lang="ts">
defineProps<{ active: 'menu' | 'orders' | 'support' | 'profile' }>()
</script>

<template>
  <nav class="bottom-nav" aria-label="主导航">
    <RouterLink to="/" class="nav-item" :class="{ active: active === 'menu' }">
      <span class="nav-icon">⌂</span><span>点餐</span>
    </RouterLink>
    <RouterLink to="/orders" class="nav-item" :class="{ active: active === 'orders' }">
      <span class="nav-icon">▤</span><span>订单</span>
    </RouterLink>
    <RouterLink to="/support" class="nav-item" :class="{ active: active === 'support' }">
      <span class="nav-icon">✦</span><span>客服</span>
    </RouterLink>
    <RouterLink to="/profile" class="nav-item" :class="{ active: active === 'profile' }">
      <span class="nav-icon">●</span><span>我的</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { useToastStore } from '../stores/toast'
const toast = useToastStore()
</script>

<template>
  <Transition name="toast">
    <div v-if="toast.visible" class="toast">{{ toast.message }}</div>
  </Transition>
</template>

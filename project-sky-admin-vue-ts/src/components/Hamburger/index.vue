<template>
  <div :class="[{'is-active': isActive}]" @click="toggleClick">
    <svg class="hamburger-icon" width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
      <path d="M2 4h16v2H2zm0 5h16v2H2zm0 5h16v2H2z" fill="currentColor" />
    </svg>
  </div>
</template>

<script lang="ts">
import { Options as Component, Prop, Vue } from 'vue-property-decorator'

@Component({
  'name': 'Hamburger'
})

export default class SkyComponent extends Vue {
  @Prop({ 'default': false }) private isActive!: boolean

  private toggleClick() {
    this.$emit('toggleClick');
  }
}
</script>

<style lang="scss" scoped>
.hamburger-icon {
  vertical-align: middle;
}

.is-active {
  transform: rotate(180deg);
}
</style>

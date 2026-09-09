<script setup lang="ts">
import { IconBrand, ICON_STROKE } from './icons';

/** Brand mark (dsh FishLogo seat): a Flower2 glyph riding currentColor. */
withDefaults(defineProps<{ size?: number }>(), { size: 24 });
</script>

<template>
  <IconBrand :size="size" :stroke-width="ICON_STROKE" class="mark" aria-hidden="true" />
</template>

<style scoped>
.mark {
  display: block;
  color: inherit;
}
</style>

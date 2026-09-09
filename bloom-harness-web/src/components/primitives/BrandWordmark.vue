<script setup lang="ts">
/**
 * Brand name (dsh BrandWordmark seat): "Bloom" wordmark plus the inverted
 * "HARNESS" badge, mirroring the dsh rect badge (52×14, rx 2).
 */
</script>

<template>
  <span class="wordmark" aria-label="BloomHarness">
    <span class="name">Bloom</span>
    <span class="badge" aria-hidden="true">HARNESS</span>
  </span>
</template>

<style scoped>
.wordmark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  min-width: 0;
  white-space: nowrap;
}

.name {
  font-size: 18px;
  font-weight: 600;
  line-height: 24px;
  letter-spacing: 0.04em;
  color: inherit;
}

.badge {
  display: inline-flex;
  align-items: center;
  height: 14px;
  padding: 0 5px;
  border-radius: 2px;
  background: var(--dsw-alias-label-primary);
  color: var(--dsw-alias-label-primary-inverted);
  font-size: 9px;
  font-weight: 700;
  line-height: 14px;
  letter-spacing: 0.08em;
}
</style>

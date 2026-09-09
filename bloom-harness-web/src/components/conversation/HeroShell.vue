<script setup lang="ts">
import BrandMark from '@/components/primitives/BrandMark.vue';

/**
 * dsh HeroShell: brand mark + headline for the blank (new session) state. The
 * workspace chip and the composer are siblings in ConversationRoot's stack.
 */
</script>

<template>
  <div class="heroShell">
    <div class="stack">
      <div class="headline">
        <span class="markHitbox">
          <BrandMark class="mark" :size="34" />
        </span>
        <span class="headlineText">让想法绽放</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.heroShell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 24px;
}

.stack {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  width: 100%;
  max-width: var(--dsh-composer-card-max-width);
  overflow: visible;
}

.headline {
  display: grid;
  grid-template-columns: 34px auto;
  column-gap: 10px;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  line-height: 32px;
  font-weight: 500;
  color: var(--dsw-alias-label-primary);
}

.markHitbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mark {
  display: block;
  overflow: visible;
  transform-origin: 50% 60%;
  color: var(--dsw-alias-label-primary);
}

@keyframes hero-mark-sway {
  0%,
  100% {
    transform: none;
  }
  35% {
    transform: rotate(-4deg) translate(-0.4px, -0.9px);
  }
  70% {
    transform: rotate(1.6deg) translate(0.3px, 0.2px);
  }
}

@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .markHitbox:hover .mark {
    animation: hero-mark-sway 1.6s ease-in-out infinite;
  }
}
</style>

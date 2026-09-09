<script setup lang="ts">
/**
 * dsh StateDot: solid two-ring dot for done / warning / error / idle, and an
 * 8-cell pixel chase for `ongoing`.
 */
withDefaults(
  defineProps<{ state: 'done' | 'warning' | 'ongoing' | 'error' | 'idle'; size?: number }>(),
  { size: 10 },
);

const CELLS: Array<[number, number]> = [
  [0, 0],
  [4, 0],
  [8, 0],
  [8, 4],
  [8, 8],
  [4, 8],
  [0, 8],
  [0, 4],
];
</script>

<template>
  <svg
    v-if="state === 'ongoing'"
    class="matrix"
    data-state="ongoing"
    :width="size"
    :height="size"
    viewBox="0 0 10 10"
    shape-rendering="crispEdges"
    aria-hidden="true"
  >
    <rect
      v-for="(cell, i) in CELLS"
      :key="i"
      class="cell"
      :x="cell[0]"
      :y="cell[1]"
      width="2"
      height="2"
      :style="{ animationDelay: `${(i - 8) * 125}ms` }"
    />
  </svg>
  <span
    v-else
    class="dot"
    :data-state="state"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  />
</template>

<style scoped>
.dot,
.matrix {
  --dsh-state-ongoing: var(--dsw-static-deepseek-450);
}

.dot {
  position: relative;
  display: inline-block;
  flex: none;
}

.dot::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  corner-shape: round;
  background: currentColor;
  opacity: 0.1;
}

.dot::after {
  content: '';
  position: absolute;
  inset: 20%;
  border-radius: 50%;
  corner-shape: round;
  background: currentColor;
}

.dot[data-state='done'] {
  color: var(--dsw-alias-state-success-primary);
}

.dot[data-state='warning'] {
  color: var(--dsw-alias-state-warn-primary);
}

.dot[data-state='error'] {
  color: var(--dsw-alias-state-error-primary);
}

.dot[data-state='idle'] {
  color: var(--dsw-alias-label-tertiary);
}

.matrix {
  flex: none;
  color: var(--dsh-state-ongoing);
}

.cell {
  fill: currentColor;
  opacity: 0.15;
  animation: dsh-state-dot-chase 1s infinite;
}

@keyframes dsh-state-dot-chase {
  0%,
  12.4% {
    opacity: 1;
  }
  12.5%,
  24.9% {
    opacity: 0.6;
  }
  25%,
  37.4% {
    opacity: 0.35;
  }
  37.5%,
  100% {
    opacity: 0.15;
  }
}
</style>

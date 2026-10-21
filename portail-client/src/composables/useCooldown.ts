import { computed, getCurrentScope, onScopeDispose, ref } from "vue";

/** Délai d'attente décompté seconde par seconde (ex. entre deux renvois d'email). */
export function useCooldown(seconds: number) {
  const remaining = ref(0);
  let timer: ReturnType<typeof setInterval> | null = null;

  function stop() {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function start() {
    stop();
    remaining.value = seconds;
    timer = setInterval(() => {
      remaining.value -= 1;
      if (remaining.value <= 0) {
        remaining.value = 0;
        stop();
      }
    }, 1000);
  }

  if (getCurrentScope()) {
    onScopeDispose(stop);
  }

  return {
    remaining,
    isActive: computed(() => remaining.value > 0),
    start,
  };
}

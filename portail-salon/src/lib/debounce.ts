/** Retarde `fn` jusqu'à `delayMs` après le dernier appel ; `cancel()` annule l'appel en attente. */
export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  delayMs: number,
) {
  let timer: ReturnType<typeof setTimeout> | undefined;

  const debounced = (...args: A) => {
    if (timer !== undefined) {
      clearTimeout(timer);
    }
    timer = setTimeout(() => {
      timer = undefined;
      fn(...args);
    }, delayMs);
  };
  const cancel = () => {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  };

  return Object.assign(debounced, { cancel });
}

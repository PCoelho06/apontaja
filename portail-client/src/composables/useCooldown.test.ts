import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { effectScope } from "vue";

import { useCooldown } from "@/composables/useCooldown";

describe("useCooldown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("est inactif tant qu'il n'a pas démarré", () => {
    const { remaining, isActive } = useCooldown(60);

    expect(remaining.value).toBe(0);
    expect(isActive.value).toBe(false);
  });

  it("décompte puis s'arrête à zéro", () => {
    const { remaining, isActive, start } = useCooldown(3);

    start();
    expect(remaining.value).toBe(3);
    expect(isActive.value).toBe(true);

    vi.advanceTimersByTime(1000);
    expect(remaining.value).toBe(2);

    vi.advanceTimersByTime(2000);
    expect(remaining.value).toBe(0);
    expect(isActive.value).toBe(false);

    vi.advanceTimersByTime(5000);
    expect(remaining.value).toBe(0);
  });

  it("redémarre depuis la durée complète", () => {
    const { remaining, start } = useCooldown(10);

    start();
    vi.advanceTimersByTime(4000);
    expect(remaining.value).toBe(6);

    start();
    expect(remaining.value).toBe(10);
  });

  it("s'arrête quand la portée est détruite", () => {
    const scope = effectScope();
    const cooldown = scope.run(() => useCooldown(10));

    cooldown?.start();
    vi.advanceTimersByTime(2000);
    expect(cooldown?.remaining.value).toBe(8);

    scope.stop();
    vi.advanceTimersByTime(5000);
    expect(cooldown?.remaining.value).toBe(8);
  });
});

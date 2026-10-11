import { effectScope } from "vue";
import { describe, expect, it, vi } from "vitest";

import type { PositionedAppointment } from "@/lib/agenda";
import { useAppointmentDrag } from "@/composables/useAppointmentDrag";

const APPOINTMENT: PositionedAppointment = {
  appointmentId: "a1",
  resourceId: "r1",
  startAt: "2099-01-05T09:00:00Z",
  endAt: "2099-01-05T10:00:00Z",
  status: "CONFIRMED",
  customerName: "Alice Martin",
  serviceName: "Coupe",
  start: 600,
  end: 660,
  lane: 0,
  lanes: 1,
  clipped: false,
};
const AXIS = { start: 540, end: 1080 };

function setup() {
  const onDrop = vi.fn();
  const scope = effectScope();
  const api = scope.run(() =>
    useAppointmentDrag({ axis: () => AXIS, pxPerMinute: 1, onDrop }),
  );
  if (!api) {
    throw new Error("scope inactif");
  }
  return { ...api, onDrop, scope };
}

function pointer(type: string, clientY = 0) {
  globalThis.dispatchEvent(new MouseEvent(type, { clientY }));
}

describe("useAppointmentDrag", () => {
  it("considère un mouvement sous le seuil comme un clic", () => {
    const { start, drag, onDrop, consumeClick } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 103);
    expect(drag.value?.active).toBe(false);
    pointer("pointerup", 103);

    expect(onDrop).not.toHaveBeenCalled();
    expect(drag.value).toBeNull();
    expect(consumeClick()).toBe(false);
  });

  it("déplace, arrondit au quart d'heure et dépose", () => {
    const { start, drag, onDrop } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 130); // +30 min
    expect(drag.value?.active).toBe(true);
    expect(drag.value?.previewStart).toBe(630);
    pointer("pointerup", 130);

    expect(onDrop).toHaveBeenCalledWith({
      appointmentId: "a1",
      resourceId: "r1",
      startMinutes: 630,
      duration: 60,
    });
    expect(drag.value).toBeNull();
  });

  it("ignore le clic qui suit un glisser, puis le rétablit", async () => {
    const { start, consumeClick } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 130);
    pointer("pointerup", 130);

    expect(consumeClick()).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(consumeClick()).toBe(false);
  });

  it("n'appelle pas onDrop quand le RDV revient à son créneau d'origine", () => {
    const { start, onDrop } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 120);
    pointer("pointermove", 100);
    pointer("pointerup", 100);

    expect(onDrop).not.toHaveBeenCalled();
  });

  it("annule avec Échap", () => {
    const { start, drag, onDrop } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 160);
    globalThis.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    pointer("pointerup", 160);

    expect(onDrop).not.toHaveBeenCalled();
    expect(drag.value).toBeNull();
  });

  it("annule quand le navigateur interrompt le pointeur", () => {
    const { start, onDrop } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 160);
    pointer("pointercancel", 160);

    expect(onDrop).not.toHaveBeenCalled();
  });

  it("borne le déplacement à la grille", () => {
    const { start, drag } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 5000);

    expect(drag.value?.previewStart).toBe(1020); // 18:00 - 60 min
    pointer("pointercancel");
  });

  it("retire ses écouteurs à la destruction du scope", () => {
    const { start, onDrop, scope } = setup();

    start(APPOINTMENT, 100);
    pointer("pointermove", 160);
    scope.stop();
    pointer("pointerup", 160);

    expect(onDrop).not.toHaveBeenCalled();
  });
});

import { onScopeDispose, ref } from "vue";

import type { Interval, PositionedAppointment } from "@/lib/agenda";
import { DRAG_THRESHOLD_PX, dragPreviewStart } from "@/lib/agendaDrag";

export interface DragState {
  appointmentId: string;
  resourceId: string;
  originStart: number;
  duration: number;
  startY: number;
  deltaPx: number;
  /** Vrai une fois le seuil dépassé : avant, c'est encore un clic. */
  active: boolean;
  previewStart: number;
}

export interface DropEvent {
  appointmentId: string;
  resourceId: string;
  startMinutes: number;
  duration: number;
}

export interface UseAppointmentDragOptions {
  axis: () => Interval;
  pxPerMinute: number;
  /** Appelé au dépôt, seulement si le RDV a réellement changé de créneau. */
  onDrop: (drop: DropEvent) => void;
}

/**
 * Glisser vertical d'un bloc de RDV avec les événements de pointeur. Les écouteurs ne
 * sont posés que pendant un glisser et retirés à la fin (dépôt, Échap, annulation) ou
 * à la destruction du composant.
 */
export function useAppointmentDrag(options: UseAppointmentDragOptions) {
  const drag = ref<DragState | null>(null);
  let suppressClick = false;

  function onPointerMove(event: MouseEvent) {
    const current = drag.value;
    if (!current) {
      return;
    }
    current.deltaPx = event.clientY - current.startY;
    if (!current.active && Math.abs(current.deltaPx) >= DRAG_THRESHOLD_PX) {
      current.active = true;
    }
    if (current.active) {
      current.previewStart = dragPreviewStart({
        originStart: current.originStart,
        duration: current.duration,
        deltaPx: current.deltaPx,
        pxPerMinute: options.pxPerMinute,
        axis: options.axis(),
      });
    }
  }

  function onPointerUp() {
    finish(true);
  }

  function onPointerCancel() {
    finish(false);
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === "Escape") {
      finish(false);
    }
  }

  function listen() {
    globalThis.addEventListener("pointermove", onPointerMove);
    globalThis.addEventListener("pointerup", onPointerUp);
    globalThis.addEventListener("pointercancel", onPointerCancel);
    globalThis.addEventListener("keydown", onKeyDown);
  }

  function unlisten() {
    globalThis.removeEventListener("pointermove", onPointerMove);
    globalThis.removeEventListener("pointerup", onPointerUp);
    globalThis.removeEventListener("pointercancel", onPointerCancel);
    globalThis.removeEventListener("keydown", onKeyDown);
  }

  function finish(drop: boolean) {
    const finished = drag.value;
    unlisten();
    drag.value = null;
    if (!finished?.active) {
      return;
    }
    // Le navigateur déclenche un clic juste après le relâchement : on l'ignore.
    suppressClick = true;
    globalThis.setTimeout(() => {
      suppressClick = false;
    }, 0);
    if (drop && finished.previewStart !== finished.originStart) {
      options.onDrop({
        appointmentId: finished.appointmentId,
        resourceId: finished.resourceId,
        startMinutes: finished.previewStart,
        duration: finished.duration,
      });
    }
  }

  function start(appointment: PositionedAppointment, clientY: number) {
    if (drag.value) {
      return;
    }
    drag.value = {
      appointmentId: appointment.appointmentId,
      resourceId: appointment.resourceId,
      originStart: appointment.start,
      duration: appointment.end - appointment.start,
      startY: clientY,
      deltaPx: 0,
      active: false,
      previewStart: appointment.start,
    };
    listen();
  }

  /** Vrai si le clic en cours est la fin d'un glisser et doit être ignoré. */
  function consumeClick(): boolean {
    return suppressClick;
  }

  onScopeDispose(() => {
    unlisten();
    drag.value = null;
  });

  return { drag, start, consumeClick };
}

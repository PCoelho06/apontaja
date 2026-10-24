import { describe, expect, it } from "vitest";

import type { ClientAppointment } from "@/lib/myAppointments";
import {
  cancellableUntilNote,
  cancellationState,
  mergeAppointments,
  replaceAppointment,
  statusInfo,
  whenLabel,
} from "@/lib/myAppointments";

const NOW = new Date("2099-06-01T12:00:00Z");

function appointment(
  id: string,
  overrides: Partial<ClientAppointment> = {},
): ClientAppointment {
  return {
    appointmentId: id,
    salon: { id: "s1", name: "Salão Teste", city: "Lisboa", timezone: "Europe/Lisbon" },
    service: { id: "sv1", name: "Corte" },
    resource: { id: "r1", name: "Ana" },
    startAt: "2099-06-10T09:00:00Z",
    endAt: "2099-06-10T09:30:00Z",
    status: "CONFIRMED",
    priceCents: 2500,
    durationMinutes: 30,
    cancellable: true,
    cancellableUntil: "2099-06-09T09:00:00Z",
    cancelledAt: null,
    ...overrides,
  };
}

describe("statusInfo", () => {
  it("traduit chaque statut", () => {
    expect(statusInfo({ status: "SCHEDULED", endAt: "2099-06-10T09:30:00Z" }, NOW)).toEqual({
      label: "Pedido pendente",
      tone: "wine",
    });
    expect(statusInfo({ status: "CONFIRMED", endAt: "2099-06-10T09:30:00Z" }, NOW).label).toBe(
      "Confirmada",
    );
    expect(statusInfo({ status: "COMPLETED", endAt: "2020-01-01T10:00:00Z" }, NOW).label).toBe(
      "Concluída",
    );
    expect(statusInfo({ status: "CANCELLED", endAt: "2099-06-10T09:30:00Z" }, NOW)).toEqual({
      label: "Cancelada",
      tone: "danger",
    });
    expect(statusInfo({ status: "NO_SHOW", endAt: "2020-01-01T10:00:00Z" }, NOW).label).toBe(
      "Não compareceu",
    );
  });

  it("signale une demande non traitée dont l'heure est passée", () => {
    const info = statusInfo({ status: "SCHEDULED", endAt: "2020-01-01T10:00:00Z" }, NOW);

    expect(info.label).toBe("Pedido sem resposta");
    expect(info.tone).toBe("neutral");
  });

  it("ne plante pas sur un statut inconnu", () => {
    expect(statusInfo({ status: "AUTRE", endAt: "2099-06-10T09:30:00Z" }, NOW).label).toBe(
      "Estado desconhecido",
    );
  });
});

describe("cancellationState", () => {
  it("propose l'annulation quand le back l'autorise", () => {
    expect(cancellationState(appointment("a"), NOW)).toBe("CANCELLABLE");
  });

  it("explique que le délai est passé pour un confirmé encore à venir", () => {
    const late = appointment("a", { cancellable: false });

    expect(cancellationState(late, NOW)).toBe("DEADLINE_PASSED");
  });

  it("ne dit rien d'un rendez-vous annulé, passé ou commencé", () => {
    expect(
      cancellationState(appointment("a", { cancellable: false, status: "CANCELLED" }), NOW),
    ).toBe("NONE");
    expect(
      cancellationState(
        appointment("a", { cancellable: false, status: "SCHEDULED" }),
        NOW,
      ),
    ).toBe("NONE");
    expect(
      cancellationState(
        appointment("a", { cancellable: false, startAt: "2020-01-01T09:00:00Z" }),
        NOW,
      ),
    ).toBe("NONE");
  });
});

describe("cancellableUntilNote", () => {
  it("donne la limite dans le fuseau du salon", () => {
    const note = cancellableUntilNote(appointment("a"));

    expect(note).toContain("Pode cancelar até");
    expect(note).toContain("10:00");
    expect(note).toContain("junho de 2099");
  });

  it("est vide sans limite", () => {
    expect(cancellableUntilNote(appointment("a", { cancellableUntil: null }))).toBe("");
  });
});

describe("whenLabel", () => {
  it("n'ajoute pas le fuseau quand il est celui du navigateur", () => {
    const text = whenLabel("2099-06-10T09:00:00Z", "Europe/Lisbon", "Europe/Lisbon");

    expect(text).toContain("10:00");
    expect(text).not.toContain("hora de");
  });

  it("signale le fuseau du salon quand il diffère", () => {
    const text = whenLabel("2099-06-10T09:00:00Z", "Europe/Lisbon", "Asia/Tokyo");

    expect(text).toContain("(hora de Europe/Lisbon)");
  });
});

describe("mergeAppointments / replaceAppointment", () => {
  it("ajoute sans doublon", () => {
    const merged = mergeAppointments(
      [appointment("a"), appointment("b")],
      [appointment("b"), appointment("c")],
    );

    expect(merged.map((item) => item.appointmentId)).toEqual(["a", "b", "c"]);
  });

  it("remplace au même rang", () => {
    const updated = appointment("b", { status: "CANCELLED", cancellable: false });

    const result = replaceAppointment(
      [appointment("a"), appointment("b"), appointment("c")],
      updated,
    );

    expect(result.map((item) => item.appointmentId)).toEqual(["a", "b", "c"]);
    expect(result[1]?.status).toBe("CANCELLED");
  });
});

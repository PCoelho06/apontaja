import { describe, expect, it } from "vitest";

import {
  addDays,
  dateInZone,
  differsFromBrowserZone,
  weekWindow,
} from "@/lib/salonCalendar";

describe("dateInZone", () => {
  it("donne la date locale du salon, pas celle de l'UTC", () => {
    const instant = new Date("2099-06-10T23:30:00Z");

    expect(dateInZone(instant, "UTC")).toBe("2099-06-10");
    expect(dateInZone(instant, "Europe/Lisbon")).toBe("2099-06-11");
    expect(dateInZone(instant, "America/New_York")).toBe("2099-06-10");
  });
});

describe("addDays", () => {
  it("avance et recule, y compris au changement de mois et d'année", () => {
    expect(addDays("2099-06-10", 7)).toBe("2099-06-17");
    expect(addDays("2099-06-28", 5)).toBe("2099-07-03");
    expect(addDays("2099-12-30", 3)).toBe("2100-01-02");
    expect(addDays("2099-03-01", -1)).toBe("2099-02-28");
  });
});

describe("weekWindow", () => {
  it("commence aujourd'hui et couvre 7 jours", () => {
    expect(weekWindow("2099-06-10", 60, 0)).toEqual({
      index: 0,
      from: "2099-06-10",
      to: "2099-06-16",
      isFirst: true,
      isLast: false,
    });
  });

  it("avance de 7 jours par semaine", () => {
    const window = weekWindow("2099-06-10", 60, 2);

    expect(window.from).toBe("2099-06-24");
    expect(window.to).toBe("2099-06-30");
  });

  it("borne la dernière fenêtre par l'horizon de réservation", () => {
    // Horizon 10 jours : dernière date autorisée = 2099-06-20, deux semaines (index 0 et 1).
    const window = weekWindow("2099-06-10", 10, 1);

    expect(window.from).toBe("2099-06-17");
    expect(window.to).toBe("2099-06-20");
    expect(window.isLast).toBe(true);
  });

  it("ramène l'index dans les bornes", () => {
    expect(weekWindow("2099-06-10", 10, 99).index).toBe(1);
    expect(weekWindow("2099-06-10", 10, -3).index).toBe(0);
  });

  it("n'a qu'une fenêtre quand l'horizon est inférieur à 7 jours", () => {
    const window = weekWindow("2099-06-10", 3, 0);

    expect(window.to).toBe("2099-06-13");
    expect(window.isFirst).toBe(true);
    expect(window.isLast).toBe(true);
  });
});

describe("differsFromBrowserZone", () => {
  it("détecte un fuseau différent", () => {
    expect(differsFromBrowserZone("Europe/Lisbon", "Europe/Lisbon")).toBe(false);
    expect(differsFromBrowserZone("Atlantic/Azores", "Europe/Lisbon")).toBe(true);
  });
});

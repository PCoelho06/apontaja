import { describe, expect, it } from "vitest";

import {
  formatDayLabel,
  formatDayMonth,
  formatDuration,
  formatDurationRange,
  formatLongDateTime,
  formatPrice,
  formatPriceRange,
  formatTime,
} from "@/lib/format";

/** `Intl` sépare nombre et symbole par une espace insécable : on la normalise pour comparer. */
function plain(text: string): string {
  return text.replace(/\s/g, " ");
}

describe("format (pt-PT)", () => {
  it("formate un prix en euros", () => {
    expect(plain(formatPrice(4005))).toBe("40,05 €");
    expect(plain(formatPrice(3500))).toBe("35,00 €");
  });

  it("formate une fourchette de prix seulement si les bornes diffèrent", () => {
    expect(plain(formatPriceRange(3500, 3500))).toBe("35,00 €");
    expect(plain(formatPriceRange(3500, 4000))).toBe("de 35,00 € a 40,00 €");
  });

  it("formate une durée", () => {
    expect(formatDuration(45)).toBe("45 min");
    expect(formatDuration(60)).toBe("1 h");
    expect(formatDuration(90)).toBe("1 h 30");
    expect(formatDuration(125)).toBe("2 h 05");
  });

  it("formate une fourchette de durée seulement si les bornes diffèrent", () => {
    expect(formatDurationRange(30, 30)).toBe("30 min");
    expect(formatDurationRange(30, 45)).toBe("de 30 min a 45 min");
  });

  it("affiche l'heure murale dans le fuseau du salon", () => {
    expect(formatTime("2099-06-10T09:00:00Z", "Europe/Lisbon")).toBe("10:00");
    expect(formatTime("2099-06-10T09:00:00Z", "Europe/Paris")).toBe("11:00");
  });

  it("affiche date et heure en portugais", () => {
    const text = formatLongDateTime("2099-06-10T09:00:00Z", "Europe/Lisbon");

    expect(text).toContain("quarta-feira");
    expect(text).toContain("10 de junho");
    expect(text).toContain("2099");
    expect(text).toContain("10:00");
  });

  it("affiche une date locale sans décalage de fuseau", () => {
    expect(formatDayLabel("2099-06-10")).toContain("quarta-feira");
    expect(formatDayLabel("2099-06-10")).toContain("10 de junho");
    expect(formatDayMonth("2099-06-10")).toBe("10 de junho");
  });
});

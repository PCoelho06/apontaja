import { describe, expect, it } from "vitest";

import {
  STATUS_BLOCK_CLASSES,
  STATUS_LABELS,
  STATUS_ORDER,
  STATUS_TONES,
} from "@/lib/appointmentStatus";

describe("appointmentStatus", () => {
  it("décrit chacun des cinq statuts partout", () => {
    expect(STATUS_ORDER).toHaveLength(5);
    for (const status of STATUS_ORDER) {
      expect(STATUS_LABELS[status]).toBeTruthy();
      expect(STATUS_TONES[status]).toBeTruthy();
      expect(STATUS_BLOCK_CLASSES[status]).toBeTruthy();
    }
  });
});

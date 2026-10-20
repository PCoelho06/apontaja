import { describe, expect, it } from "vitest";

import {
  absorbedIds,
  mergeableOthers,
  notMergeableLabel,
  reasonsLabel,
  type DuplicateGroup,
  type DuplicateMember,
} from "@/lib/customerDuplicates";

function member(id: string, mergeable = true): DuplicateMember {
  return {
    customerProfileId: id,
    firstName: "Marie",
    lastName: "Durand",
    email: null,
    phone: "0612345678",
    createdAt: "2020-01-01T00:00:00Z",
    lastVisitAt: null,
    nextVisitAt: null,
    mergeable,
    notMergeableReason: mergeable ? null : "SHARED",
  };
}

const group: DuplicateGroup = {
  reasons: ["EMAIL", "PHONE"],
  customers: [member("a"), member("b"), member("c", false), member("d")],
};

describe("customerDuplicates", () => {
  it("liste les profils fusionnables autres que le survivant", () => {
    expect(mergeableOthers(group, "a").map((m) => m.customerProfileId)).toEqual(
      ["b", "d"],
    );
  });

  it("retire les profils exclus par l'utilisateur", () => {
    expect(absorbedIds(group, "a", ["b"])).toEqual(["d"]);
    expect(absorbedIds(group, "a", [])).toEqual(["b", "d"]);
  });

  it("libelle les motifs et les refus", () => {
    expect(reasonsLabel(["EMAIL", "PHONE"])).toBe("Même email · Même téléphone");
    expect(notMergeableLabel("CLAIMED")).toContain("compte client");
    expect(notMergeableLabel(null)).toBe("");
  });
});

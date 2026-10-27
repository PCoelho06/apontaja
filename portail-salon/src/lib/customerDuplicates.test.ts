import { describe, expect, it } from "vitest";

import {
  absorbedIds,
  canKeep,
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
    expect(reasonsLabel(["EMAIL", "PHONE"])).toBe("Mesmo email · Mesmo telefone");
    expect(notMergeableLabel("CLAIMED")).toContain("conta de cliente");
    expect(notMergeableLabel(null)).toBe("");
  });

  it("autorise à conserver un profil réclamé s'il reste un profil à absorber", () => {
    const claimed: DuplicateMember = {
      ...member("k", false),
      notMergeableReason: "CLAIMED",
      claimed: true,
    };
    const withClaimed: DuplicateGroup = {
      reasons: ["EMAIL"],
      customers: [claimed, member("a"), member("c", false)],
    };

    expect(canKeep(withClaimed, claimed)).toBe(true);
    // « a » n'a rien à absorber : le seul autre profil est réclamé, donc non absorbable.
    expect(canKeep(withClaimed, member("a"))).toBe(false);
    expect(
      canKeep(
        { ...withClaimed, customers: [...withClaimed.customers, member("b")] },
        member("a"),
      ),
    ).toBe(true);
    // « c » est partagé avec un autre salon : ni absorbable ni conservable.
    expect(canKeep(withClaimed, withClaimed.customers[2])).toBe(false);
    // Rien à lui rattacher : le bouton n'a pas de sens.
    const alone: DuplicateGroup = {
      reasons: ["EMAIL"],
      customers: [claimed, member("c", false)],
    };
    expect(canKeep(alone, claimed)).toBe(false);
  });
});




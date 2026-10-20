export type DuplicateReason = "EMAIL" | "PHONE";
export type NotMergeableReason = "CLAIMED" | "SHARED";

export interface DuplicateMember {
  customerProfileId: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  createdAt: string;
  lastVisitAt: string | null;
  nextVisitAt: string | null;
  mergeable: boolean;
  notMergeableReason: NotMergeableReason | null;
}

export interface DuplicateGroup {
  reasons: DuplicateReason[];
  customers: DuplicateMember[];
}

export interface DuplicateReport {
  groups: DuplicateGroup[];
  truncated: boolean;
}

export function memberName(member: DuplicateMember): string {
  return `${member.firstName} ${member.lastName}`.trim();
}

export function memberContact(member: DuplicateMember): string {
  return [member.email, member.phone].filter(Boolean).join(" · ");
}

const REASON_LABELS: Record<DuplicateReason, string> = {
  EMAIL: "Même email",
  PHONE: "Même téléphone",
};

export function reasonsLabel(reasons: DuplicateReason[]): string {
  return reasons.map((reason) => REASON_LABELS[reason] ?? reason).join(" · ");
}

export function notMergeableLabel(reason: NotMergeableReason | null): string {
  if (reason === "CLAIMED") {
    return "Profil rattaché à un compte client : fusion impossible.";
  }
  if (reason === "SHARED") {
    return "Profil partagé avec un autre salon : fusion impossible.";
  }
  return "";
}

/** Profils du groupe qui peuvent être absorbés par `survivorId` (fusionnables, autres que lui). */
export function mergeableOthers(
  group: DuplicateGroup,
  survivorId: string,
): DuplicateMember[] {
  return group.customers.filter(
    (member) => member.mergeable && member.customerProfileId !== survivorId,
  );
}

/** Identifiants à absorber : les candidats moins ceux que l'utilisateur a exclus. */
export function absorbedIds(
  group: DuplicateGroup,
  survivorId: string,
  excluded: string[],
): string[] {
  return mergeableOthers(group, survivorId)
    .map((member) => member.customerProfileId)
    .filter((id) => !excluded.includes(id));
}

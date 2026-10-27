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
  /** Profil rattaché à un compte client : peut être conservé (survivant), jamais absorbé. */
  claimed?: boolean;
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
  EMAIL: "Mesmo email",
  PHONE: "Mesmo telefone",
};

export function reasonsLabel(reasons: DuplicateReason[]): string {
  return reasons.map((reason) => REASON_LABELS[reason] ?? reason).join(" · ");
}

export function notMergeableLabel(reason: NotMergeableReason | null): string {
  if (reason === "CLAIMED") {
    return "Perfil associado a uma conta de cliente: pode ser mantido, mas não absorvido.";
  }
  if (reason === "SHARED") {
    return "Perfil partilhado com outro salão: fusão impossível.";
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

/**
 * Un profil peut être conservé s'il est fusionnable, ou rattaché à un compte client (survivant autorisé),
 * et s'il reste au moins un autre profil fusionnable à lui rattacher.
 */
export function canKeep(group: DuplicateGroup, member: DuplicateMember): boolean {
  return (
    (member.mergeable || member.claimed === true) &&
    mergeableOthers(group, member.customerProfileId).length > 0
  );
}

export const CLAIMED_SURVIVOR_WARNING =
  "Este perfil está associado a uma conta de cliente. Após a fusão, o titular " +
  "verá o histórico de marcações dos perfis fundidos.";

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




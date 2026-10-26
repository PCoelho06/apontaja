/** Libellés pt-PT des rôles et formats de date du portail salon (jamais le texte du back). */

export function roleLabel(role: string): string {
  switch (role) {
    case "OWNER":
      return "Proprietário";
    case "MANAGER":
      return "Gestor";
    case "EMPLOYEE":
      return "Colaborador";
    case "ORGANIZATION_OWNER":
      return "Proprietário da organização";
    default:
      return role;
  }
}

/** Rôles proposables à l'invitation et au changement de rôle, dans l'ordre d'affichage. */
export const STAFF_ROLES = ["OWNER", "MANAGER", "EMPLOYEE"] as const;

export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleDateString("pt-PT");
}

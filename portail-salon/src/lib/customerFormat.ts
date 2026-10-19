import { formatInstant, isValidTimeZone } from "@/lib/zonedTime";

export function fullName(customer: {
  firstName: string;
  lastName: string;
}): string {
  return `${customer.firstName} ${customer.lastName}`;
}

/** Email et téléphone séparés par « · » ; chaîne vide si aucun des deux. */
export function contactLine(customer: {
  email?: string | null;
  phone?: string | null;
}): string {
  return [customer.email, customer.phone]
    .filter((part): part is string => Boolean(part))
    .join(" · ");
}

/** Date d'une visite en heure du salon ; « — » si absente, instant brut si le fuseau est invalide. */
export function visitLabel(
  iso: string | null | undefined,
  timeZone: string,
): string {
  if (!iso) {
    return "—";
  }
  return isValidTimeZone(timeZone) ? formatInstant(iso, timeZone) : iso;
}

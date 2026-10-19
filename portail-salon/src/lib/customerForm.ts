import type { NewCustomer } from "@/stores/customer";

export interface CustomerFormValues {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  internalNotes: string;
}

export type CustomerFormResult = { payload: NewCustomer } | { error: string };

/** Normalise la saisie ; le back exige prénom, nom et au moins un email ou un téléphone. */
export function buildCustomerPayload(
  values: CustomerFormValues,
): CustomerFormResult {
  const firstName = values.firstName.trim();
  const lastName = values.lastName.trim();
  const email = values.email.trim();
  const phone = values.phone.trim();
  const internalNotes = values.internalNotes.trim();
  if (!firstName || !lastName) {
    return { error: "Le prénom et le nom du client sont obligatoires." };
  }
  if (!email && !phone) {
    return {
      error: "Renseignez au moins un email ou un téléphone pour le client.",
    };
  }
  return {
    payload: {
      firstName,
      lastName,
      email: email || null,
      phone: phone || null,
      internalNotes: internalNotes || null,
    },
  };
}

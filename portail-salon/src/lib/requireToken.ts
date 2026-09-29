import { useAuthStore } from "@/stores/auth";

export function requireToken(): string {
  const auth = useAuthStore();
  if (!auth.accessToken) {
    throw new Error("Non authentifié.");
  }
  return auth.accessToken;
}

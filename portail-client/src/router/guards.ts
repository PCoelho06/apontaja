export interface GuardedMeta {
  requiresAuth?: boolean;
  guestOnly?: boolean;
}

export type NavigationDecision = true | { name: "login" } | { name: "home" };

/** Décide d'une navigation selon les méta de la route et l'état de la session. */
export function resolveNavigation(
  meta: GuardedMeta,
  isAuthenticated: boolean,
): NavigationDecision {
  if (meta.requiresAuth && !isAuthenticated) {
    return { name: "login" };
  }

  if (meta.guestOnly && isAuthenticated) {
    return { name: "home" };
  }

  return true;
}

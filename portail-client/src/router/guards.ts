import { safeRedirect } from "@/lib/safeRedirect";

export interface GuardedMeta {
  requiresAuth?: boolean;
  guestOnly?: boolean;
}

export type NavigationDecision =
  | true
  | { name: "login"; query?: { redirect: string } }
  | { name: "home" }
  | { path: string };

export interface NavigationContext {
  /** Chemin complet demandé (avec query) : mémorisé pour revenir dessus après la connexion. */
  fullPath?: string;
  /** Valeur brute du paramètre `redirect` de la route courante. */
  redirect?: unknown;
}

/** Décide d'une navigation selon les méta de la route et l'état de la session. */
export function resolveNavigation(
  meta: GuardedMeta,
  isAuthenticated: boolean,
  context: NavigationContext = {},
): NavigationDecision {
  if (meta.requiresAuth && !isAuthenticated) {
    const target = context.fullPath === "/" ? null : safeRedirect(context.fullPath);
    return target ? { name: "login", query: { redirect: target } } : { name: "login" };
  }

  if (meta.guestOnly && isAuthenticated) {
    const target = safeRedirect(context.redirect);
    return target ? { path: target } : { name: "home" };
  }

  return true;
}

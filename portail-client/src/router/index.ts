import { createRouter, createWebHistory } from "vue-router";

import { resolveNavigation } from "@/router/guards";
import { useAuthStore } from "@/stores/auth";

declare module "vue-router" {
  interface RouteMeta {
    requiresAuth?: boolean;
    guestOnly?: boolean;
    title?: string;
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/connexion",
      name: "login",
      component: () => import("@/views/LoginView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/inscription",
      name: "register",
      component: () => import("@/views/RegisterView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/verifiez-votre-email",
      name: "verify-email",
      component: () => import("@/views/VerifyEmailView.vue"),
    },
    {
      path: "/confirmer-email",
      name: "confirm-email",
      component: () => import("@/views/ConfirmEmailView.vue"),
    },
    {
      path: "/mot-de-passe-oublie",
      name: "forgot-password",
      component: () => import("@/views/ForgotPasswordView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/reinitialiser-mot-de-passe",
      name: "reset-password",
      component: () => import("@/views/ResetPasswordView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/cgu",
      name: "terms",
      component: () => import("@/views/LegalPlaceholderView.vue"),
      meta: { title: "Conditions générales d'utilisation" },
    },
    {
      path: "/confidentialite",
      name: "privacy",
      component: () => import("@/views/LegalPlaceholderView.vue"),
      meta: { title: "Politique de confidentialité" },
    },
    {
      path: "/salons/:salonId",
      name: "salon",
      component: () => import("@/views/SalonPublicView.vue"),
    },
    {
      path: "/salons/:salonId/reserver",
      name: "booking",
      component: () => import("@/views/BookingView.vue"),
    },
    {
      path: "/",
      name: "home",
      component: () => import("@/views/AccountHomeView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/:pathMatch(.*)*",
      redirect: "/",
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();

  return resolveNavigation(to.meta, auth.isAuthenticated, {
    fullPath: to.fullPath,
    redirect: to.query.redirect,
  });
});

export default router;




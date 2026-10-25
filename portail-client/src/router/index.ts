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
      path: "/entrar",
      name: "login",
      component: () => import("@/views/LoginView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/registar",
      name: "register",
      component: () => import("@/views/RegisterView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/verificar-email",
      name: "verify-email",
      component: () => import("@/views/VerifyEmailView.vue"),
    },
    {
      path: "/confirmar-email",
      name: "confirm-email",
      component: () => import("@/views/ConfirmEmailView.vue"),
    },
    {
      path: "/recuperar-palavra-passe",
      name: "forgot-password",
      component: () => import("@/views/ForgotPasswordView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/repor-palavra-passe",
      name: "reset-password",
      component: () => import("@/views/ResetPasswordView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/termos",
      name: "terms",
      component: () => import("@/views/LegalPlaceholderView.vue"),
      meta: { title: "Termos e Condições de Utilização" },
    },
    {
      path: "/privacidade",
      name: "privacy",
      component: () => import("@/views/LegalPlaceholderView.vue"),
      meta: { title: "Política de Privacidade" },
    },
    {
      path: "/salao/:salonId",
      name: "salon",
      component: () => import("@/views/SalonPublicView.vue"),
    },
    {
      path: "/salao/:salonId/reservar",
      name: "booking",
      component: () => import("@/views/BookingView.vue"),
    },
    {
      path: "/marcacoes",
      name: "my-appointments",
      component: () => import("@/views/MyAppointmentsView.vue"),
      meta: { requiresAuth: true },
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



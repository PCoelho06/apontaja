import { createRouter, createWebHistory } from "vue-router";

import { useAuthStore } from "@/stores/auth";

declare module "vue-router" {
  interface RouteMeta {
    requiresAuth?: boolean;
    guestOnly?: boolean;
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
      path: "/confirmer-email",
      name: "confirm-email",
      component: () => import("@/views/ConfirmEmailView.vue"),
    },
    {
      path: "/salons/nouveau",
      name: "create-salon",
      component: () => import("@/views/CreateSalonView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/salons/:salonId",
      name: "salon-detail",
      component: () => import("@/views/SalonDetailView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/salons/:salonId/catalogue",
      component: () => import("@/views/catalog/CatalogLayoutView.vue"),
      meta: { requiresAuth: true },
      children: [
        {
          path: "",
          redirect: (to) => ({
            name: "catalog-resources",
            params: to.params,
          }),
        },
        {
          path: "ressources",
          name: "catalog-resources",
          component: () => import("@/views/catalog/ResourcesView.vue"),
        },
        {
          path: "prestations",
          name: "catalog-services",
          component: () => import("@/views/catalog/ServicesView.vue"),
        },
        {
          path: "horaires",
          name: "catalog-schedule",
          component: () => import("@/views/catalog/ScheduleView.vue"),
        },
        {
          path: "fermetures",
          name: "catalog-closures",
          component: () => import("@/views/catalog/ClosuresView.vue"),
        },
      ],
    },
    {
      path: "/salons/:salonId/agenda",
      name: "agenda",
      component: () => import("@/views/AgendaView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/invitations/accepter",
      name: "accept-invitation",
      component: () => import("@/views/AcceptInvitationView.vue"),
    },
    {
      path: "/",
      name: "home",
      component: () => import("@/views/HomeView.vue"),
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

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: "login" };
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: "home" };
  }

  return true;
});

export default router;

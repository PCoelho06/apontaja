import { createRouter, createWebHistory } from "vue-router";

import { useAuthStore } from "@/stores/auth";

declare module "vue-router" {
  interface RouteMeta {
    requiresAuth?: boolean;
    guestOnly?: boolean;
    title?: string;
  }
}

// Chemins en pt-PT, renommés zone par zone (4h-1 : authentification et socle, préfixe /saloes).
// Les suffixes du catalogue, de l'agenda, des clients et des demandes suivent en 4h-2 à 4h-4.
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
      path: "/confirmar-email",
      name: "confirm-email",
      component: () => import("@/views/ConfirmEmailView.vue"),
    },
    {
      path: "/saloes/novo",
      name: "create-salon",
      component: () => import("@/views/CreateSalonView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/saloes/:salonId",
      name: "salon-detail",
      component: () => import("@/views/SalonDetailView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/saloes/:salonId/catalogue",
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
        {
          path: "reservation",
          name: "booking-settings",
          component: () => import("@/views/catalog/BookingSettingsView.vue"),
        },
      ],
    },
    {
      path: "/saloes/:salonId/agenda",
      name: "agenda",
      component: () => import("@/views/AgendaView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/saloes/:salonId/demandes",
      name: "booking-requests",
      component: () => import("@/views/BookingRequestsView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/saloes/:salonId/clients",
      name: "customers",
      component: () => import("@/views/CustomersView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "/saloes/:salonId/clients/doublons",
      name: "customer-duplicates",
      component: () => import("@/views/CustomerDuplicatesView.vue"),
      meta: { requiresAuth: true },
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
      path: "/convites/aceitar",
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

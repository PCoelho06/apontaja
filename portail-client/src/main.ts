import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import "./assets/style.css";
import { useAuthStore } from "./stores/auth";

const app = createApp(App);
app.use(createPinia());

const authStore = useAuthStore();

authStore.restoreSession().finally(() => {
  app.use(router);
  app.mount("#app");
});

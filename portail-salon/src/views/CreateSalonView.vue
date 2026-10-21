<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import AuthTextField from "@/components/AuthTextField.vue";
import { ApiError } from "@apontaja/web-core/api-client";
import { useSalonStore } from "@/stores/salon";
import { UiSelect } from "@apontaja/ui-kit";

const router = useRouter();
const salonStore = useSalonStore();

const name = ref("");
const address = ref("");
const postalCode = ref("");
const city = ref("");
const country = ref("France");
const timezone = ref("Europe/Paris");
const phone = ref("");

const fieldErrors = ref<Record<string, string>>({});
const generalError = ref<string | null>(null);
const isSubmitting = ref(false);

// Identifiants IANA proposés par le navigateur ; "Europe/Lisbon" reste toujours disponible.
const timezones = (() => {
  const zones =
    typeof Intl.supportedValuesOf === "function"
      ? Intl.supportedValuesOf("timeZone")
      : [];
  return zones.includes("Europe/Lisbon") ? zones : ["Europe/Lisbon", ...zones];
})();

async function handleSubmit() {
  fieldErrors.value = {};
  generalError.value = null;
  isSubmitting.value = true;

  try {
    await salonStore.createSalon({
      name: name.value,
      address: address.value,
      postalCode: postalCode.value,
      city: city.value,
      country: country.value,
      timezone: timezone.value,
      phone: phone.value || undefined,
    });

    // Pas encore d'écran de détail salon (Tranche 8) — retour à l'accueil pour l'instant.
    await router.push({ name: "home" });
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.fieldErrors) {
        fieldErrors.value = error.fieldErrors;
      } else {
        generalError.value = error.message;
      }
    } else {
      generalError.value = "Une erreur est survenue.";
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Créer un salon"
    subtitle="Renseignez les informations de votre salon pour commencer."
  >
    <form
      class="space-y-4"
      @submit.prevent="handleSubmit"
    >
      <AuthTextField
        id="name"
        v-model="name"
        label="Nom du salon"
        :error="fieldErrors.name"
      />
      <AuthTextField
        id="address"
        v-model="address"
        label="Adresse"
        :error="fieldErrors.address"
      />
      <div class="grid grid-cols-2 gap-4">
        <AuthTextField
          id="postalCode"
          v-model="postalCode"
          label="Code postal"
          :error="fieldErrors.postalCode"
        />
        <AuthTextField
          id="city"
          v-model="city"
          label="Ville"
          :error="fieldErrors.city"
        />
      </div>
      <AuthTextField
        id="country"
        v-model="country"
        label="Pays"
        :error="fieldErrors.country"
      />
      <UiSelect
        v-model="timezone"
        label="Fuseau horaire"
        :error="fieldErrors.timezone"
      >
        <option
          v-for="zone in timezones"
          :key="zone"
          :value="zone"
        >
          {{ zone }}
        </option>
      </UiSelect>
      <AuthTextField
        id="phone"
        v-model="phone"
        label="Téléphone (optionnel)"
        type="tel"
        :error="fieldErrors.phone"
      />

      <p
        v-if="generalError"
        class="text-sm text-danger"
      >
        {{ generalError }}
      </p>

      <button
        type="submit"
        :disabled="isSubmitting"
        class="w-full rounded-md bg-wine px-4 py-2 text-sm font-medium text-paper hover:bg-wine/90 focus:outline-none focus:ring-2 focus:ring-wine disabled:opacity-50"
      >
        {{ isSubmitting ? "Création..." : "Créer le salon" }}
      </button>
    </form>
  </AuthLayout>
</template>

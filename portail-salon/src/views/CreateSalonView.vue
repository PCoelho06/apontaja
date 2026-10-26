<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";

import AuthLayout from "@/layouts/AuthLayout.vue";
import { describeCreateSalonError } from "@/lib/salonErrors";
import { useSalonStore } from "@/stores/salon";
import { UiAlert, UiButton, UiInput, UiSelect } from "@apontaja/ui-kit";

const router = useRouter();
const salonStore = useSalonStore();

const name = ref("");
const address = ref("");
const postalCode = ref("");
const city = ref("");
const country = ref("Portugal");
const timezone = ref("Europe/Lisbon");
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

    await router.push({ name: "home" });
  } catch (error) {
    const failure = describeCreateSalonError(error);
    fieldErrors.value = failure.fieldErrors;
    generalError.value = failure.message;
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <AuthLayout
    title="Criar um salão"
    subtitle="Preencha as informações do seu salão para começar."
  >
    <form
      class="space-y-4"
      @submit.prevent="handleSubmit"
    >
      <UiInput
        v-model="name"
        label="Nome do salão"
        required
        :error="fieldErrors.name"
      />
      <UiInput
        v-model="address"
        label="Morada"
        autocomplete="street-address"
        required
        :error="fieldErrors.address"
      />
      <div class="grid grid-cols-2 gap-4">
        <UiInput
          v-model="postalCode"
          label="Código postal"
          autocomplete="postal-code"
          required
          :error="fieldErrors.postalCode"
        />
        <UiInput
          v-model="city"
          label="Cidade"
          required
          :error="fieldErrors.city"
        />
      </div>
      <UiInput
        v-model="country"
        label="País"
        required
        :error="fieldErrors.country"
      />
      <UiSelect
        v-model="timezone"
        label="Fuso horário"
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
      <UiInput
        v-model="phone"
        type="tel"
        label="Telefone (opcional)"
        autocomplete="tel"
        :error="fieldErrors.phone"
      />

      <UiAlert
        v-if="generalError"
        variant="error"
      >
        {{ generalError }}
      </UiAlert>

      <UiButton
        type="submit"
        class="w-full"
        :loading="isSubmitting"
      >
        {{ isSubmitting ? "A criar..." : "Criar o salão" }}
      </UiButton>
    </form>
  </AuthLayout>
</template>

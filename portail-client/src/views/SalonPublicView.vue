<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

import SessionActions from "@/components/SessionActions.vue";
import AccountLayout from "@/layouts/AccountLayout.vue";
import { describeLoadError } from "@/lib/bookingErrors";
import {
  formatDurationRange,
  formatPriceRange,
} from "@/lib/format";
import type { PublicOffering } from "@/lib/publicBooking";
import { cancellationNote, offeringTerms } from "@/lib/publicBooking";
import { useBookingStore } from "@/stores/booking";
import { ApiError } from "@apontaja/web-core/api-client";

import { UiAlert, UiButton } from "@apontaja/ui-kit";

type Phase = "loading" | "ready" | "unavailable" | "error";

const route = useRoute();
const router = useRouter();
const booking = useBookingStore();

const phase = ref<Phase>("loading");
const errorText = ref("");

const salonId = computed(() => String(route.params.salonId));
const salon = computed(() => booking.salon);
const offerings = computed(() => booking.offerings);

const addressLine = computed(() => {
  const current = salon.value;
  if (!current) {
    return "";
  }
  const place = [current.postalCode, current.city].filter(Boolean).join(" ");
  return [current.address, place].filter(Boolean).join(", ");
});

const confirmationNote = computed(() =>
  salon.value?.autoConfirm
    ? "As marcações são confirmadas de imediato."
    : "O salão confirma cada pedido de marcação; será avisado por email.",
);

function termsLabel(offering: PublicOffering): string {
  const terms = offeringTerms(offering);
  const duration = formatDurationRange(terms.durationMin, terms.durationMax);
  const price = formatPriceRange(terms.priceMin, terms.priceMax);
  return `${duration} · ${price}`;
}

function startBooking(serviceId: string) {
  void router.push({
    name: "booking",
    params: { salonId: salonId.value },
    query: { service: serviceId },
  });
}

onMounted(async () => {
  try {
    await booking.loadSalon(salonId.value);
    phase.value = "ready";
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      phase.value = "unavailable";
    } else {
      errorText.value = describeLoadError(error);
      phase.value = "error";
    }
  }
});
</script>

<template>
  <AccountLayout>
    <template #actions>
      <SessionActions />
    </template>

    <p
      v-if="phase === 'loading'"
      class="text-sm text-ink/70"
    >
      A carregar…
    </p>

    <UiAlert
      v-else-if="phase === 'unavailable'"
      variant="info"
    >
      Este salão não foi encontrado ou não aceita marcações online neste momento.
    </UiAlert>

    <UiAlert
      v-else-if="phase === 'error'"
      variant="error"
    >
      {{ errorText }}
    </UiAlert>

    <template v-else-if="salon">
      <h1 class="font-display text-2xl text-ink">
        {{ salon.name }}
      </h1>
      <p
        v-if="addressLine"
        class="mt-1 text-sm text-ink/70"
      >
        {{ addressLine }}
      </p>
      <p
        v-if="salon.phone"
        class="text-sm text-ink/70"
      >
        Telefone: {{ salon.phone }}
      </p>
      <p class="mt-3 text-sm text-ink/70">
        {{ confirmationNote }} {{ cancellationNote(salon.cancellationDeadlineHours) }}
      </p>

      <h2 class="mt-8 font-display text-lg text-ink">
        Escolha um serviço
      </h2>

      <p
        v-if="offerings.length === 0"
        class="mt-3 text-sm text-ink/70"
      >
        Este salão ainda não tem serviços disponíveis para marcação online.
      </p>

      <ul
        v-else
        class="mt-3 space-y-3"
      >
        <li
          v-for="offering in offerings"
          :key="offering.serviceId"
          class="flex items-start justify-between gap-4 rounded-md border border-border bg-white p-5"
        >
          <div>
            <h3 class="font-medium text-ink">
              {{ offering.name }}
            </h3>
            <p
              v-if="offering.description"
              class="mt-1 text-sm text-ink/70"
            >
              {{ offering.description }}
            </p>
            <p class="mt-2 text-sm text-ink/70">
              {{ termsLabel(offering) }}
            </p>
          </div>
          <UiButton
            size="sm"
            @click="startBooking(offering.serviceId)"
          >
            Marcar
          </UiButton>
        </li>
      </ul>
    </template>
  </AccountLayout>
</template>

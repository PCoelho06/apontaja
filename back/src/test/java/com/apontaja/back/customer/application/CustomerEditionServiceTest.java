package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.CustomerSearchRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerEditionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SalonCustomerLinkRepository salonCustomerLinkRepository;

    @Mock
    private CustomerSearchRepository searchRepository;

    @Mock
    private CustomerVisitLookup visitLookup;

    private CustomerEditionService service;

    private final UUID salonId = UUID.randomUUID();
    private CustomerProfile profile;
    private SalonCustomerLink link;

    @BeforeEach
    void setUp() {
        service = new CustomerEditionService(customerProfileRepository, salonCustomerLinkRepository,
                searchRepository, visitLookup, Clock.fixed(NOW, ZoneOffset.UTC));
        profile = new CustomerProfile(UUID.randomUUID(), "Alice", "Martin", "alice@example.com", null,
                Instant.parse("2026-01-01T00:00:00Z"));
        link = new SalonCustomerLink(UUID.randomUUID(), salonId, profile.getId(), CustomerLinkSource.MANUAL,
                "Allergie", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private void givenCustomerExists() {
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, profile.getId()))
                .thenReturn(Optional.of(link));
        when(customerProfileRepository.findAliveById(profile.getId())).thenReturn(Optional.of(profile));
    }

    private void givenUpcomingAppointmentAt(Instant start) {
        when(visitLookup.findVisits(eq(salonId), anyCollection(), eq(NOW)))
                .thenReturn(Map.of(profile.getId(), new CustomerVisits(null, start)));
    }

    private UpdateCustomerCommand update(String email, String phone, String notes) {
        return new UpdateCustomerCommand(salonId, profile.getId(), " Alicia ", " Durand ", email, phone, notes);
    }

    @Test
    void update_remplace_l_identite_et_les_notes_en_normalisant() {
        givenCustomerExists();

        CustomerCard card = service.update(update("  ", " 0612345678 ", "  Nouvelle note  "));

        assertThat(card.firstName()).isEqualTo("Alicia");
        assertThat(card.lastName()).isEqualTo("Durand");
        assertThat(card.email()).isNull();
        assertThat(card.phone()).isEqualTo("0612345678");
        assertThat(card.internalNotes()).isEqualTo("Nouvelle note");
        verify(customerProfileRepository).save(profile);
        verify(salonCustomerLinkRepository).save(link);
    }

    @Test
    void update_refuse_un_client_sans_email_ni_telephone() {
        givenCustomerExists();

        assertThatThrownBy(() -> service.update(update(" ", null, null)))
                .isInstanceOf(ContactRequiredException.class);

        verify(customerProfileRepository, never()).save(any());
        assertThat(profile.getFirstName()).isEqualTo("Alice");
    }

    @Test
    void update_refuse_un_client_anonymise() {
        profile.anonymize(NOW);
        givenCustomerExists();

        assertThatThrownBy(() -> service.update(update("a@example.com", null, null)))
                .isInstanceOf(CustomerAnonymizedException.class);
    }

    @Test
    void update_leve_client_introuvable_si_le_lien_n_existe_pas_dans_ce_salon() {
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, profile.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(update("a@example.com", null, null)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void le_consentement_marketing_est_enregistre_avec_la_date_du_jour() {
        givenCustomerExists();

        CustomerCard card = service.setMarketingConsent(salonId, profile.getId(), true);

        assertThat(card.marketingConsent()).isTrue();
        assertThat(card.marketingConsentAt()).isEqualTo(NOW);
        assertThat(card.marketingConsentRevokedAt()).isNull();
        verify(salonCustomerLinkRepository).save(link);
    }

    @Test
    void accepter_deux_fois_conserve_la_date_du_premier_consentement() {
        Instant first = Instant.parse("2026-02-01T09:00:00Z");
        link.grantMarketingConsent(first);
        givenCustomerExists();

        CustomerCard card = service.setMarketingConsent(salonId, profile.getId(), true);

        assertThat(card.marketingConsentAt()).isEqualTo(first);
        verify(salonCustomerLinkRepository, never()).save(any());
    }

    @Test
    void retirer_le_consentement_renseigne_la_date_de_retrait() {
        link.grantMarketingConsent(Instant.parse("2026-02-01T09:00:00Z"));
        givenCustomerExists();

        CustomerCard card = service.setMarketingConsent(salonId, profile.getId(), false);

        assertThat(card.marketingConsent()).isFalse();
        assertThat(card.marketingConsentRevokedAt()).isEqualTo(NOW);
        verify(salonCustomerLinkRepository).save(link);
    }

    @Test
    void retirer_un_consentement_qui_n_existe_pas_ne_change_rien() {
        givenCustomerExists();

        CustomerCard card = service.setMarketingConsent(salonId, profile.getId(), false);

        assertThat(card.marketingConsentRevokedAt()).isNull();
        verify(salonCustomerLinkRepository, never()).save(any());
    }

    @Test
    void le_consentement_d_un_client_anonymise_est_refuse() {
        profile.anonymize(NOW);
        givenCustomerExists();

        assertThatThrownBy(() -> service.setMarketingConsent(salonId, profile.getId(), true))
                .isInstanceOf(CustomerAnonymizedException.class);
    }

    @Test
    void anonymize_efface_les_donnees_personnelles_du_profil_et_du_lien() {
        link.grantMarketingConsent(Instant.parse("2026-02-01T09:00:00Z"));
        givenCustomerExists();
        when(searchRepository.existsAliveLinkInOtherSalon(profile.getId(), salonId)).thenReturn(false);
        when(visitLookup.findVisits(eq(salonId), anyCollection(), eq(NOW))).thenReturn(Map.of());

        service.anonymize(salonId, profile.getId());

        assertThat(profile.isAnonymized()).isTrue();
        assertThat(profile.getAnonymizedAt()).isEqualTo(NOW);
        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getAccountId()).isNull();
        assertThat(profile.getFirstName()).isNotEqualTo("Alice");
        assertThat(profile.getLastName()).isNotEqualTo("Martin");
        assertThat(link.getInternalNotes()).isNull();
        assertThat(link.hasMarketingConsent()).isFalse();
        assertThat(link.getMarketingConsentAt()).isNull();
        verify(customerProfileRepository).save(profile);
        verify(salonCustomerLinkRepository).save(link);
    }

    @Test
    void anonymize_est_idempotent() {
        profile.anonymize(Instant.parse("2026-03-01T00:00:00Z"));
        givenCustomerExists();

        service.anonymize(salonId, profile.getId());

        verify(customerProfileRepository, never()).save(any());
        assertThat(profile.getAnonymizedAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    void anonymize_refuse_un_profil_rattache_a_un_autre_salon() {
        givenCustomerExists();
        when(searchRepository.existsAliveLinkInOtherSalon(profile.getId(), salonId)).thenReturn(true);

        assertThatThrownBy(() -> service.anonymize(salonId, profile.getId()))
                .isInstanceOf(CustomerSharedException.class);

        assertThat(profile.isAnonymized()).isFalse();
    }

    @Test
    void anonymize_refuse_un_profil_reclame_par_un_compte() {
        // Aucun mutateur d'accountId avant la Phase 4c : réflexion, comme dans CustomerProfileJpaRepositoryIT.
        setAccountId(profile, UUID.randomUUID());
        givenCustomerExists();

        assertThatThrownBy(() -> service.anonymize(salonId, profile.getId()))
                .isInstanceOf(CustomerSharedException.class);

        verify(searchRepository, never()).existsAliveLinkInOtherSalon(any(), any());
    }

    @Test
    void anonymize_refuse_un_client_qui_a_un_rendez_vous_a_venir() {
        givenCustomerExists();
        when(searchRepository.existsAliveLinkInOtherSalon(profile.getId(), salonId)).thenReturn(false);
        givenUpcomingAppointmentAt(Instant.parse("2099-01-01T10:00:00Z"));

        assertThatThrownBy(() -> service.anonymize(salonId, profile.getId()))
                .isInstanceOf(CustomerHasUpcomingAppointmentsException.class);

        assertThat(profile.isAnonymized()).isFalse();
        verify(customerProfileRepository, never()).save(any());
    }

    @Test
    void anonymize_leve_client_introuvable_pour_un_autre_salon() {
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, profile.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.anonymize(salonId, profile.getId()))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    private static void setAccountId(CustomerProfile target, UUID accountId) {
        try {
            var field = CustomerProfile.class.getDeclaredField("accountId");
            field.setAccessible(true);
            field.set(target, accountId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

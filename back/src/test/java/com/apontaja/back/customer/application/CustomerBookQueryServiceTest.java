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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerBookQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private CustomerSearchRepository searchRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SalonCustomerLinkRepository salonCustomerLinkRepository;

    @Mock
    private CustomerVisitLookup visitLookup;

    @Mock
    private CustomerAppointmentHistory appointmentHistory;

    private CustomerBookQueryService service;

    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CustomerBookQueryService(searchRepository, customerProfileRepository,
                salonCustomerLinkRepository, visitLookup, appointmentHistory, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CustomerProfile profile(String firstName) {
        return new CustomerProfile(UUID.randomUUID(), firstName, "Nom", firstName + "@example.com", null, NOW);
    }

    private SalonCustomerLink link(UUID profileId) {
        return new SalonCustomerLink(UUID.randomUUID(), salonId, profileId, CustomerLinkSource.MANUAL, null, NOW);
    }

    @Test
    void search_combine_liens_profils_et_visites_dans_l_ordre_des_liens() {
        CustomerProfile alice = profile("Alice");
        CustomerProfile bob = profile("Bob");
        Instant lastVisit = Instant.parse("2026-05-01T09:00:00Z");
        when(searchRepository.searchAliveLinks(salonId, "ali", 1, 20)).thenReturn(
                new CustomerSearchRepository.LinkPage(List.of(link(alice.getId()), link(bob.getId())), 42));
        when(customerProfileRepository.findAliveByIds(anyCollection())).thenReturn(List.of(bob, alice));
        when(visitLookup.findVisits(eq(salonId), anyCollection(), eq(NOW)))
                .thenReturn(Map.of(alice.getId(), new CustomerVisits(lastVisit, null)));

        CustomerCardPage result = service.search(salonId, "  ali ", 1, 20);

        assertThat(result.cards()).extracting(CustomerCard::firstName).containsExactly("Alice", "Bob");
        assertThat(result.cards().get(0).lastVisitAt()).isEqualTo(lastVisit);
        assertThat(result.cards().get(1).lastVisitAt()).isNull();
        assertThat(result.totalElements()).isEqualTo(42);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    void search_sans_resultat_ne_consulte_ni_profils_ni_visites() {
        when(searchRepository.searchAliveLinks(salonId, null, 0, 20))
                .thenReturn(new CustomerSearchRepository.LinkPage(List.of(), 0));

        CustomerCardPage result = service.search(salonId, "   ", 0, 20);

        assertThat(result.cards()).isEmpty();
        verifyNoInteractions(customerProfileRepository, visitLookup);
    }

    @Test
    void search_borne_la_page_et_la_taille() {
        when(searchRepository.searchAliveLinks(salonId, null, 0, 100))
                .thenReturn(new CustomerSearchRepository.LinkPage(List.of(), 0));

        CustomerCardPage result = service.search(salonId, null, -3, 500);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
    }

    @Test
    void search_tronque_une_recherche_trop_longue_et_normalise_les_espaces() {
        String longQuery = "a".repeat(150);
        String expected = "a".repeat(CustomerBookQueryService.MAX_QUERY_LENGTH);
        when(searchRepository.searchAliveLinks(salonId, expected, 0, 20))
                .thenReturn(new CustomerSearchRepository.LinkPage(List.of(), 0));
        when(searchRepository.searchAliveLinks(salonId, "alice martin", 0, 20))
                .thenReturn(new CustomerSearchRepository.LinkPage(List.of(), 0));

        service.search(salonId, longQuery, 0, 20);
        service.search(salonId, "alice    martin", 0, 20);

        verify(searchRepository).searchAliveLinks(salonId, expected, 0, 20);
        verify(searchRepository).searchAliveLinks(salonId, "alice martin", 0, 20);
    }

    @Test
    void search_ignore_un_lien_dont_le_profil_n_est_plus_vivant() {
        CustomerProfile alice = profile("Alice");
        when(searchRepository.searchAliveLinks(salonId, null, 0, 20)).thenReturn(new CustomerSearchRepository
                .LinkPage(List.of(link(alice.getId()), link(UUID.randomUUID())), 2));
        when(customerProfileRepository.findAliveByIds(anyCollection())).thenReturn(List.of(alice));
        when(visitLookup.findVisits(eq(salonId), anyCollection(), eq(NOW))).thenReturn(Map.of());

        assertThat(service.search(salonId, null, 0, 20).cards()).extracting(CustomerCard::firstName)
                .containsExactly("Alice");
    }

    @Test
    void find_retourne_la_fiche_d_un_client_anonymise() {
        CustomerProfile alice = profile("Alice");
        alice.anonymize(NOW);
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, alice.getId()))
                .thenReturn(Optional.of(link(alice.getId())));
        when(customerProfileRepository.findAliveById(alice.getId())).thenReturn(Optional.of(alice));
        when(visitLookup.findVisits(eq(salonId), anyCollection(), eq(NOW))).thenReturn(Map.of());

        assertThat(service.find(salonId, alice.getId()))
                .hasValueSatisfying(card -> assertThat(card.anonymized()).isTrue());
    }

    @Test
    void find_est_vide_si_le_client_n_est_pas_dans_ce_salon() {
        UUID unknown = UUID.randomUUID();
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, unknown))
                .thenReturn(Optional.empty());

        assertThat(service.find(salonId, unknown)).isEmpty();
        verifyNoInteractions(customerProfileRepository, visitLookup);
    }

    @Test
    void history_delegue_au_port_avec_page_et_taille_bornees() {
        UUID customerId = UUID.randomUUID();
        CustomerHistoryPage page = new CustomerHistoryPage(List.of(), 0, 100, 0);
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, customerId))
                .thenReturn(Optional.of(link(customerId)));
        when(appointmentHistory.list(salonId, customerId, 0, 100)).thenReturn(page);

        assertThat(service.history(salonId, customerId, -1, 1000)).isSameAs(page);
    }

    @Test
    void history_leve_client_introuvable_hors_du_salon() {
        UUID customerId = UUID.randomUUID();
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, customerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(salonId, customerId, 0, 20))
                .isInstanceOf(CustomerNotFoundException.class);
        verifyNoInteractions(appointmentHistory);
    }
}

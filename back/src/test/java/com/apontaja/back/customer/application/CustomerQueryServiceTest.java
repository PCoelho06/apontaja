package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SalonCustomerLinkRepository salonCustomerLinkRepository;

    private CustomerQueryService queryService;

    private final UUID salonId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        queryService = new CustomerQueryService(customerProfileRepository, salonCustomerLinkRepository);
    }

    private CustomerProfile profile(String firstName) {
        return new CustomerProfile(UUID.randomUUID(), firstName, "Nom", firstName + "@example.com", null, now);
    }

    private SalonCustomerLink link(UUID profileId) {
        return new SalonCustomerLink(UUID.randomUUID(), salonId, profileId, CustomerLinkSource.MANUAL, null, now);
    }

    @Test
    void findAliveBySalonId_combine_les_liens_et_les_profils_dans_l_ordre_des_liens() {
        CustomerProfile alice = profile("Alice");
        CustomerProfile bob = profile("Bob");
        SalonCustomerLink aliceLink = link(alice.getId());
        SalonCustomerLink bobLink = link(bob.getId());

        when(salonCustomerLinkRepository.findAliveBySalonId(salonId)).thenReturn(List.of(aliceLink, bobLink));
        when(customerProfileRepository.findAliveByIds(any())).thenReturn(List.of(bob, alice));

        List<CustomerSummary> result = queryService.findAliveBySalonId(salonId);

        assertThat(result).extracting(CustomerSummary::firstName).containsExactly("Alice", "Bob");
    }

    @Test
    void findAliveBySalonId_ignore_un_lien_dont_le_profil_n_est_plus_vivant() {
        CustomerProfile alice = profile("Alice");
        SalonCustomerLink aliceLink = link(alice.getId());
        SalonCustomerLink orphanLink = link(UUID.randomUUID());

        when(salonCustomerLinkRepository.findAliveBySalonId(salonId)).thenReturn(List.of(aliceLink, orphanLink));
        when(customerProfileRepository.findAliveByIds(any())).thenReturn(List.of(alice));

        assertThat(queryService.findAliveBySalonId(salonId)).extracting(CustomerSummary::firstName)
                .containsExactly("Alice");
    }

    @Test
    void findAliveInSalon_est_vide_si_le_lien_ou_le_profil_manque() {
        CustomerProfile alice = profile("Alice");
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, alice.getId()))
                .thenReturn(Optional.empty());
        when(customerProfileRepository.findAliveById(alice.getId())).thenReturn(Optional.of(alice));

        assertThat(queryService.findAliveInSalon(salonId, alice.getId())).isEmpty();
    }

    @Test
    void findAliveInSalon_retourne_le_client_quand_lien_et_profil_existent() {
        CustomerProfile alice = profile("Alice");
        SalonCustomerLink aliceLink = link(alice.getId());
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, alice.getId()))
                .thenReturn(Optional.of(aliceLink));
        when(customerProfileRepository.findAliveById(alice.getId())).thenReturn(Optional.of(alice));

        assertThat(queryService.findAliveInSalon(salonId, alice.getId()))
                .hasValueSatisfying(summary -> assertThat(summary.firstName()).isEqualTo("Alice"));
    }
}

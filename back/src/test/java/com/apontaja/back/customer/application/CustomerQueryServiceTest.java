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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void findAliveInSalon_est_vide_pour_un_client_anonymise() {
        CustomerProfile alice = profile("Alice");
        alice.anonymize(now);
        SalonCustomerLink aliceLink = link(alice.getId());
        when(salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, alice.getId()))
                .thenReturn(Optional.of(aliceLink));
        when(customerProfileRepository.findAliveById(alice.getId())).thenReturn(Optional.of(alice));

        assertThat(queryService.findAliveInSalon(salonId, alice.getId())).isEmpty();
    }
}

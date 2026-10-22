package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.customer.domain.ClaimedProfileRepository;
import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSelfServiceTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private ClaimedProfileRepository claimedProfileRepository;
    @Mock
    private CustomerClaimService claimService;
    @Mock
    private CustomerProfileRepository profileRepository;
    @Mock
    private SalonCustomerLinkRepository linkRepository;

    private CustomerSelfService service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AtomicInteger counter = new AtomicInteger();
        service = new CustomerSelfService(accountQueryService, claimedProfileRepository, claimService,
                profileRepository, linkRepository, () -> new UUID(0, counter.incrementAndGet()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void accountVerified(boolean verified) {
        when(accountQueryService.findAliveById(accountId)).thenReturn(
                Optional.of(new AccountSummary(accountId, "alice@example.com", verified, "Alice", "Martin")));
    }

    @Test
    void refuse_un_compte_introuvable() {
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveProfileForBooking(accountId, salonId, null, true))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(claimedProfileRepository, claimService, profileRepository, linkRepository);
    }

    @Test
    void refuse_un_email_non_verifie() {
        accountVerified(false);

        assertThatThrownBy(() -> service.resolveProfileForBooking(accountId, salonId, null, true))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(claimedProfileRepository, claimService, profileRepository, linkRepository);
    }

    @Test
    void reutilise_le_profil_deja_reclame_sans_exiger_le_consentement() {
        accountVerified(true);
        UUID existing = UUID.randomUUID();
        when(claimedProfileRepository.findClaimedProfileId(accountId, salonId)).thenReturn(Optional.of(existing));

        assertThat(service.resolveProfileForBooking(accountId, salonId, "0600000000", false)).isEqualTo(existing);

        verifyNoInteractions(claimService, profileRepository, linkRepository);
    }

    @Test
    void exige_le_consentement_quand_aucun_profil_n_est_reclame() {
        accountVerified(true);
        when(claimedProfileRepository.findClaimedProfileId(accountId, salonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveProfileForBooking(accountId, salonId, null, false))
                .isInstanceOf(SharingConsentRequiredException.class);

        verifyNoInteractions(claimService, profileRepository, linkRepository);
    }

    @Test
    void rattache_le_profil_du_carnet_portant_l_email_du_compte() {
        accountVerified(true);
        UUID claimed = UUID.randomUUID();
        when(claimedProfileRepository.findClaimedProfileId(accountId, salonId)).thenReturn(Optional.empty(),
                Optional.of(claimed));

        assertThat(service.resolveProfileForBooking(accountId, salonId, null, true)).isEqualTo(claimed);

        verify(claimService).claimMatchingProfiles(accountId, "alice@example.com");
        verify(profileRepository, never()).save(any());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void cree_un_profil_self_reprenant_l_identite_du_compte() {
        accountVerified(true);
        when(claimedProfileRepository.findClaimedProfileId(accountId, salonId)).thenReturn(Optional.empty(),
                Optional.empty());
        when(profileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(linkRepository.save(any(SalonCustomerLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID profileId = service.resolveProfileForBooking(accountId, salonId, " 0600000000 ", true);

        ArgumentCaptor<CustomerProfile> profile = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getId()).isEqualTo(profileId);
        assertThat(profile.getValue().getFirstName()).isEqualTo("Alice");
        assertThat(profile.getValue().getLastName()).isEqualTo("Martin");
        assertThat(profile.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(profile.getValue().getPhone()).isEqualTo("0600000000");
        assertThat(profile.getValue().getAccountId()).isEqualTo(accountId);

        ArgumentCaptor<SalonCustomerLink> link = ArgumentCaptor.forClass(SalonCustomerLink.class);
        verify(linkRepository).save(link.capture());
        assertThat(link.getValue().getSalonId()).isEqualTo(salonId);
        assertThat(link.getValue().getCustomerProfileId()).isEqualTo(profileId);
        assertThat(link.getValue().getSource()).isEqualTo(CustomerLinkSource.SELF);
        assertThat(link.getValue().getId()).isNotEqualTo(profileId);
    }

    @Test
    void un_telephone_vide_donne_un_profil_sans_telephone() {
        accountVerified(true);
        when(claimedProfileRepository.findClaimedProfileId(accountId, salonId)).thenReturn(Optional.empty(),
                Optional.empty());
        when(profileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resolveProfileForBooking(accountId, salonId, "   ", true);

        ArgumentCaptor<CustomerProfile> profile = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getPhone()).isNull();
    }
}

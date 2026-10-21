package com.apontaja.back.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apontaja.back.customer.domain.ClaimCandidate;
import com.apontaja.back.customer.domain.CustomerClaimLock;
import com.apontaja.back.customer.domain.CustomerClaimRepository;
import com.apontaja.back.customer.domain.CustomerMergeLock;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CustomerClaimServiceTest {

    private static final UUID ACCOUNT = new UUID(0, 99);
    private static final UUID SALON_A = new UUID(0, 1);
    private static final UUID SALON_B = new UUID(0, 2);
    private static final UUID P1 = new UUID(1, 1);
    private static final UUID P2 = new UUID(1, 2);
    private static final UUID P3 = new UUID(1, 3);
    private static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");
    private static final String EMAIL = "alice@example.com";

    @Mock
    private CustomerClaimRepository claimRepository;
    @Mock
    private CustomerClaimLock claimLock;
    @Mock
    private CustomerMergeLock mergeLock;
    @Mock
    private CustomerProfileRepository profileRepository;

    private CustomerClaimService service;

    @BeforeEach
    void setUp() {
        service = new CustomerClaimService(claimRepository, claimLock, mergeLock, profileRepository);
    }

    private static CustomerProfile profile(UUID id) {
        return new CustomerProfile(id, "Alice", "Martin", EMAIL, null, T0);
    }

    private static ClaimCandidate candidate(UUID profileId, UUID salonId, long daysAfter) {
        return new ClaimCandidate(profileId, salonId, T0.plusSeconds(daysAfter * 86_400));
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> loadedIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(profileRepository).findAliveByIds(captor.capture());
        return captor.getValue();
    }

    @Test
    void rattache_le_profil_le_plus_ancien_de_chaque_salon_et_verrouille_dans_l_ordre() {
        when(claimRepository.findClaimCandidates(EMAIL))
                .thenReturn(List.of(candidate(P1, SALON_A, 0), candidate(P2, SALON_A, 1), candidate(P3, SALON_B, 2)));
        when(claimRepository.findSalonIdsHoldingClaimedProfile(ACCOUNT)).thenReturn(Set.of());
        CustomerProfile first = profile(P1);
        CustomerProfile third = profile(P3);
        when(profileRepository.findAliveByIds(anyCollection())).thenReturn(List.of(first, third));

        int claimed = service.claimMatchingProfiles(ACCOUNT, "  " + EMAIL + " ");

        assertThat(claimed).isEqualTo(2);
        assertThat(first.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(third.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(loadedIds()).containsExactlyInAnyOrder(P1, P3);
        verify(profileRepository, times(2)).save(any(CustomerProfile.class));
        InOrder order = inOrder(claimLock, mergeLock);
        order.verify(claimLock).lockAccount(ACCOUNT);
        order.verify(mergeLock).lockSalon(SALON_A);
        order.verify(mergeLock).lockSalon(SALON_B);
    }

    @Test
    void ignore_les_salons_ou_le_compte_a_deja_un_profil_reclame() {
        when(claimRepository.findClaimCandidates(EMAIL))
                .thenReturn(List.of(candidate(P1, SALON_A, 0), candidate(P3, SALON_B, 2)));
        when(claimRepository.findSalonIdsHoldingClaimedProfile(ACCOUNT)).thenReturn(Set.of(SALON_A));
        CustomerProfile third = profile(P3);
        when(profileRepository.findAliveByIds(anyCollection())).thenReturn(List.of(third));

        assertThat(service.claimMatchingProfiles(ACCOUNT, EMAIL)).isEqualTo(1);

        assertThat(loadedIds()).containsExactly(P3);
        verify(mergeLock, never()).lockSalon(SALON_A);
    }

    @Test
    void ne_touche_pas_un_profil_reclame_ou_anonymise_entre_temps() {
        when(claimRepository.findClaimCandidates(EMAIL))
                .thenReturn(List.of(candidate(P1, SALON_A, 0), candidate(P3, SALON_B, 2)));
        when(claimRepository.findSalonIdsHoldingClaimedProfile(ACCOUNT)).thenReturn(Set.of());
        CustomerProfile takenByAnother = profile(P1);
        UUID otherAccount = new UUID(0, 77);
        takenByAnother.claimBy(otherAccount);
        CustomerProfile anonymized = profile(P3);
        anonymized.anonymize(T0);
        when(profileRepository.findAliveByIds(anyCollection())).thenReturn(List.of(takenByAnother, anonymized));

        assertThat(service.claimMatchingProfiles(ACCOUNT, EMAIL)).isZero();

        assertThat(takenByAnother.getAccountId()).isEqualTo(otherAccount);
        verify(profileRepository, never()).save(any(CustomerProfile.class));
    }

    @Test
    void ne_verrouille_aucun_salon_quand_rien_n_est_a_rattacher() {
        when(claimRepository.findClaimCandidates(EMAIL)).thenReturn(List.of());
        when(claimRepository.findSalonIdsHoldingClaimedProfile(ACCOUNT)).thenReturn(Set.of());

        assertThat(service.claimMatchingProfiles(ACCOUNT, EMAIL)).isZero();

        verify(claimLock).lockAccount(ACCOUNT);
        verifyNoInteractions(mergeLock, profileRepository);
    }

    @Test
    void ne_fait_rien_sans_email() {
        assertThat(service.claimMatchingProfiles(ACCOUNT, null)).isZero();
        assertThat(service.claimMatchingProfiles(ACCOUNT, "   ")).isZero();

        verifyNoInteractions(claimLock, claimRepository, mergeLock, profileRepository);
    }

    @Test
    void onEmailVerified_declenche_le_rattachement() {
        when(claimRepository.findClaimCandidates(EMAIL)).thenReturn(List.of());
        when(claimRepository.findSalonIdsHoldingClaimedProfile(ACCOUNT)).thenReturn(Set.of());

        service.onEmailVerified(ACCOUNT, EMAIL);

        verify(claimRepository).findClaimCandidates(EMAIL);
    }
}

package com.apontaja.back.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerMergeLock;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CustomerMergeServiceTest {

        private static final UUID SALON = new UUID(0, 100);
        private static final UUID S = new UUID(0, 1);
        private static final UUID A = new UUID(0, 2);
        private static final UUID B = new UUID(0, 3);
        private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");
        private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");

        @Mock
        private CustomerProfileRepository profiles;
        @Mock
        private SalonCustomerLinkRepository links;
        @Mock
        private CustomerSearchRepository search;
        @Mock
        private CustomerMergeLock lock;
        @Mock
        private CustomerAppointmentReassignment reassignment;
        @Mock
        private CustomerVisitLookup visits;

        private CustomerMergeService service;

        @BeforeEach
        void setUp() {
                service = new CustomerMergeService(profiles, links, search, lock, reassignment, visits,
                                Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private CustomerProfile profile(UUID id, String email, String phone, long createdOffset) {
                CustomerProfile profile = new CustomerProfile(id, "Prénom", "Nom", email, phone,
                                T0.plusSeconds(createdOffset));
                lenient().when(profiles.findAliveById(id)).thenReturn(Optional.of(profile));
                return profile;
        }

        private SalonCustomerLink link(UUID profileId, String notes) {
                SalonCustomerLink link = new SalonCustomerLink(UUID.randomUUID(), SALON, profileId,
                                CustomerLinkSource.MANUAL, notes, T0);
                lenient().when(links.findAliveBySalonIdAndCustomerProfileId(SALON, profileId))
                                .thenReturn(Optional.of(link));
                return link;
        }

        @Test
        void mergesIdentityNotesConsentAndReassignsAppointments() {
                CustomerProfile survivor = profile(S, "s@example.fr", null, 0);
                SalonCustomerLink survivorLink = link(S, "VIP");
                CustomerProfile absorbedA = profile(A, null, "0612345678", 1);
                SalonCustomerLink linkA = link(A, "Allergie");
                linkA.grantMarketingConsent(T0.plusSeconds(10));
                CustomerProfile absorbedB = profile(B, "b@example.fr", "0611111111", 2);
                SalonCustomerLink linkB = link(B, "VIP");

                CustomerCard card = service.merge(new MergeCustomersCommand(SALON, S, List.of(A, B)));

                verify(lock).lockSalon(SALON);
                verify(reassignment).reassign(SALON, List.of(A, B), S);
                assertThat(survivor.getPhone()).isEqualTo("0612345678");
                assertThat(survivor.getEmail()).isEqualTo("s@example.fr");
                assertThat(survivorLink.getInternalNotes()).isEqualTo("VIP\n\nAllergie");
                assertThat(survivorLink.hasMarketingConsent()).isTrue();
                assertThat(absorbedA.isDeleted()).isTrue();
                assertThat(absorbedA.getMergedIntoProfileId()).isEqualTo(S);
                assertThat(absorbedA.getEmail()).isNull();
                assertThat(absorbedB.isDeleted()).isTrue();
                assertThat(linkA.isDeleted()).isTrue();
                assertThat(linkA.getInternalNotes()).isNull();
                assertThat(linkB.isDeleted()).isTrue();
                assertThat(card.customerProfileId()).isEqualTo(S);
                assertThat(card.phone()).isEqualTo("0612345678");
                assertThat(card.internalNotes()).isEqualTo("VIP\n\nAllergie");
        }

        @Test
        void refusesEmptyListSelfMergeAndTooManyAbsorbed() {
                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of())))
                                .isInstanceOf(InvalidMergeRequestException.class);
                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of(A, S))))
                                .isInstanceOf(InvalidMergeRequestException.class);
                List<UUID> tooMany = java.util.stream.IntStream.range(10, 22).mapToObj(i -> new UUID(0, i)).toList();
                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, tooMany)))
                                .isInstanceOf(InvalidMergeRequestException.class);
                verify(reassignment, never()).reassign(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void refusesUnknownCustomer() {
                profile(S, "s@example.fr", null, 0);
                link(S, null);
                when(links.findAliveBySalonIdAndCustomerProfileId(SALON, A)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of(A))))
                                .isInstanceOf(CustomerNotFoundException.class);
        }

        @Test
        void refusesAnonymizedProfile() {
                profile(S, "s@example.fr", null, 0);
                link(S, null);
                CustomerProfile anonymized = profile(A, "a@example.fr", null, 1);
                anonymized.anonymize(T0);
                link(A, null);

                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of(A))))
                                .isInstanceOf(CustomerAnonymizedException.class);
        }

        @Test
        void refusesClaimedProfile() {
                profile(S, "s@example.fr", null, 0);
                link(S, null);
                CustomerProfile claimed = profile(A, "a@example.fr", null, 1);
                // accountId n'est pas atteignable par l'API avant la tranche 4d : posé par
                // réflexion.
                ReflectionTestUtils.setField(claimed, "accountId", UUID.randomUUID());
                link(A, null);

                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of(A))))
                                .isInstanceOf(CustomerSharedException.class);
                verify(reassignment, never()).reassign(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void refusesProfileLinkedToAnotherSalon() {
                profile(S, "s@example.fr", null, 0);
                link(S, null);
                profile(A, "a@example.fr", null, 1);
                link(A, null);
                lenient().when(search.existsAliveLinkInOtherSalon(A, SALON)).thenReturn(true);

                assertThatThrownBy(() -> service.merge(new MergeCustomersCommand(SALON, S, List.of(A))))
                                .isInstanceOf(CustomerSharedException.class);
        }
}

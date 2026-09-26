package com.apontaja.back.customer.application;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerManagementServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SalonCustomerLinkRepository salonCustomerLinkRepository;

    private CustomerManagementService service;

    private final Instant fixedNow = Instant.parse("2026-09-24T10:00:00Z");
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CustomerManagementService(customerProfileRepository, salonCustomerLinkRepository,
                new SequentialTestIdGenerator(), Clock.fixed(fixedNow, ZoneOffset.UTC));
        // lenient : refuse_un_client_sans_email_ni_telephone lève avant tout appel à save().
        lenient().when(customerProfileRepository.save(any(CustomerProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(salonCustomerLinkRepository.save(any(SalonCustomerLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Un id distinct par appel : new UUID(0, 1) partout aurait fait entrer en collision les deux
     * profils du test deux_creations_pour_le_meme_email_..., qui doivent rester des profils
     * distincts (même principe que RegisterAccountServiceTest.SequentialTestIdGenerator). */
    private static final class SequentialTestIdGenerator implements com.apontaja.back.shared.domain.IdGenerator {
        private int counter = 0;

        @Override
        public UUID generate() {
            counter++;
            return new UUID(0, counter);
        }
    }

    @Test
    void refuse_un_client_sans_email_ni_telephone() {
        assertThatThrownBy(() -> service
                .create(new CreateCustomerCommand(salonId, "Alice", "Martin", "   ", "  ", null)))
                        .isInstanceOf(ContactRequiredException.class);

        verify(customerProfileRepository, never()).save(any());
        verify(salonCustomerLinkRepository, never()).save(any());
    }

    @Test
    void cree_un_profil_et_un_lien_manuel_avec_les_champs_normalises() {
        CustomerSummary result = service.create(new CreateCustomerCommand(salonId, " Alice ", " Martin ",
                " alice@example.com ", "  ", "  Allergie au henné  "));

        assertThat(result.firstName()).isEqualTo("Alice");
        assertThat(result.lastName()).isEqualTo("Martin");
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.phone()).isNull();
        assertThat(result.internalNotes()).isEqualTo("Allergie au henné");
        assertThat(result.salonId()).isEqualTo(salonId);

        ArgumentCaptor<SalonCustomerLink> linkCaptor = ArgumentCaptor.forClass(SalonCustomerLink.class);
        verify(salonCustomerLinkRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getSource()).isEqualTo(CustomerLinkSource.MANUAL);
    }

    @Test
    void un_client_avec_seulement_un_telephone_est_accepte() {
        CustomerSummary result = service
                .create(new CreateCustomerCommand(salonId, "Bob", "Dupont", null, "0612345678", null));

        assertThat(result.email()).isNull();
        assertThat(result.phone()).isEqualTo("0612345678");
    }

    @Test
    void deux_creations_pour_le_meme_email_produisent_deux_profils_distincts_aucun_matching() {
        CustomerSummary first = service
                .create(new CreateCustomerCommand(salonId, "Carol", "A", "carol@example.com", null, null));
        CustomerSummary second = service
                .create(new CreateCustomerCommand(salonId, "Carol", "B", "carol@example.com", null, null));

        assertThat(first.customerProfileId()).isNotEqualTo(second.customerProfileId());
    }
}

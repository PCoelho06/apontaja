package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientContactQueryServiceTest {

    private final UUID accountId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private CustomerProfileRepository customerProfileRepository;

    private ClientContactQueryService service() {
        return new ClientContactQueryService(accountQueryService, customerProfileRepository);
    }

    /** Profil simulé : le constructeur de l'entité n'est pas utile ici. */
    private CustomerProfile profile(UUID account, String email, boolean anonymized) {
        CustomerProfile profile = mock(CustomerProfile.class);
        lenient().when(profile.getAccountId()).thenReturn(account);
        lenient().when(profile.getEmail()).thenReturn(email);
        lenient().when(profile.getFirstName()).thenReturn("Paul");
        lenient().when(profile.isAnonymized()).thenReturn(anonymized);
        when(customerProfileRepository.findAliveById(profileId)).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void renvoie_l_email_et_le_prenom_du_compte() {
        when(accountQueryService.findAliveById(accountId))
                .thenReturn(Optional.of(new AccountSummary(accountId, "a@example.com", true, "Alice", "Martin")));

        assertThat(service().find(accountId)).contains(new ClientContact("a@example.com", "Alice"));
    }

    @Test
    void est_vide_si_le_compte_n_existe_plus() {
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.empty());

        assertThat(service().find(accountId)).isEmpty();
    }

    @Test
    void un_profil_rattache_a_un_compte_utilise_l_email_du_compte() {
        profile(accountId, "ancien@example.com", false);
        when(accountQueryService.findAliveById(accountId))
                .thenReturn(Optional.of(new AccountSummary(accountId, "a@example.com", true, "Alice", "Martin")));

        assertThat(service().findForProfile(profileId)).contains(new ClientContact("a@example.com", "Alice"));
    }

    @Test
    void un_profil_non_rattache_utilise_son_propre_email() {
        profile(null, "  paul@example.com ", false);

        assertThat(service().findForProfile(profileId)).contains(new ClientContact("paul@example.com", "Paul"));
    }

    @Test
    void un_profil_sans_email_anonymise_ou_inconnu_n_a_pas_de_destinataire() {
        profile(null, null, false);
        assertThat(service().findForProfile(profileId)).isEmpty();

        profile(null, "   ", false);
        assertThat(service().findForProfile(profileId)).isEmpty();

        profile(null, "paul@example.com", true);
        assertThat(service().findForProfile(profileId)).isEmpty();

        UUID unknown = UUID.randomUUID();
        when(customerProfileRepository.findAliveById(unknown)).thenReturn(Optional.empty());
        assertThat(service().findForProfile(unknown)).isEmpty();
    }

    @Test
    void un_profil_rattache_a_un_compte_supprime_n_a_pas_de_destinataire() {
        profile(accountId, "paul@example.com", false);
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.empty());

        assertThat(service().findForProfile(profileId)).isEmpty();
    }
}

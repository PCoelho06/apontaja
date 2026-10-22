package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientContactQueryServiceTest {

    private final UUID accountId = UUID.randomUUID();

    @Mock
    private AccountQueryService accountQueryService;

    @Test
    void renvoie_l_email_et_le_prenom_du_compte() {
        when(accountQueryService.findAliveById(accountId))
                .thenReturn(Optional.of(new AccountSummary(accountId, "a@example.com", true, "Alice", "Martin")));

        assertThat(new ClientContactQueryService(accountQueryService).find(accountId))
                .contains(new ClientContact("a@example.com", "Alice"));
    }

    @Test
    void est_vide_si_le_compte_n_existe_plus() {
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.empty());

        assertThat(new ClientContactQueryService(accountQueryService).find(accountId)).isEmpty();
    }
}

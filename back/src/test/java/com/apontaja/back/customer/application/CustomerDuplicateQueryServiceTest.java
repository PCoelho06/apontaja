package com.apontaja.back.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apontaja.back.customer.domain.CustomerDuplicateRepository;
import com.apontaja.back.customer.domain.DuplicateCandidate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CustomerDuplicateQueryServiceTest {

    private static final UUID SALON = new UUID(0, 100);
    private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");

    @Mock
    private CustomerDuplicateRepository repository;
    @Mock
    private CustomerVisitLookup visitLookup;

    private CustomerDuplicateQueryService service() {
        return new CustomerDuplicateQueryService(repository, visitLookup,
                Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void returnsNothingWithoutDuplicates() {
        when(repository.findCandidates(SALON)).thenReturn(List.of(
                new DuplicateCandidate(new UUID(0, 1), "A", "A", "a@example.fr", null, null, T0)));

        DuplicateReport report = service().findDuplicates(SALON);

        assertThat(report.groups()).isEmpty();
        assertThat(report.truncated()).isFalse();
    }

    @Test
    void flagsClaimedAndSharedMembersAsNotMergeable() {
        UUID claimed = new UUID(0, 1);
        UUID shared = new UUID(0, 2);
        UUID plain = new UUID(0, 3);
        when(repository.findCandidates(SALON)).thenReturn(List.of(
                new DuplicateCandidate(claimed, "A", "A", "x@example.fr", null, UUID.randomUUID(), T0),
                new DuplicateCandidate(shared, "B", "B", "x@example.fr", null, null, T0.plusSeconds(1)),
                new DuplicateCandidate(plain, "C", "C", "x@example.fr", null, null, T0.plusSeconds(2))));
        when(repository.findProfileIdsLinkedElsewhere(eq(SALON), anyCollection())).thenReturn(Set.of(shared));
        when(visitLookup.findVisits(eq(SALON), anyList(), any(Instant.class)))
                .thenReturn(Map.of(plain, new CustomerVisits(T0, null)));

        DuplicateReport report = service().findDuplicates(SALON);

        assertThat(report.groups()).hasSize(1);
        DuplicateReport.Group group = report.groups().get(0);
        assertThat(group.reasons()).containsExactly("EMAIL");
        assertThat(group.members()).extracting(DuplicateReport.Member::notMergeableReason)
                .containsExactly("CLAIMED", "SHARED", null);
        assertThat(group.members().get(2).mergeable()).isTrue();
        assertThat(group.members().get(2).lastVisitAt()).isEqualTo(T0);
        assertThat(group.members().get(0).lastVisitAt()).isNull();
    }
}

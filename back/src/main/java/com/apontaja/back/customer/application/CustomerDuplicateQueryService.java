package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerDuplicateRepository;
import com.apontaja.back.customer.domain.DuplicateCandidate;
import com.apontaja.back.customer.domain.DuplicateDetector;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Détection des doublons d'un salon (calculée à la demande, rien n'est stocké). */
@Service
public class CustomerDuplicateQueryService {

    static final int MAX_GROUPS = 100;

    private final CustomerDuplicateRepository repository;
    private final CustomerVisitLookup visitLookup;
    private final Clock clock;

    CustomerDuplicateQueryService(CustomerDuplicateRepository repository, CustomerVisitLookup visitLookup,
            Clock clock) {
        this.repository = repository;
        this.visitLookup = visitLookup;
        this.clock = clock;
    }

    public DuplicateReport findDuplicates(UUID salonId) {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(repository.findCandidates(salonId));
        boolean truncated = clusters.size() > MAX_GROUPS;
        List<DuplicateDetector.Cluster> kept = truncated ? clusters.subList(0, MAX_GROUPS) : clusters;
        if (kept.isEmpty()) {
            return new DuplicateReport(List.of(), false);
        }

        List<UUID> ids = kept.stream().flatMap(cluster -> cluster.members().stream())
                .map(DuplicateCandidate::profileId).toList();
        Set<UUID> shared = repository.findProfileIdsLinkedElsewhere(salonId, ids);
        Map<UUID, CustomerVisits> visits = visitLookup.findVisits(salonId, ids, clock.instant());

        List<DuplicateReport.Group> groups = kept.stream().map(cluster -> new DuplicateReport.Group(
                cluster.reasons().stream().map(Enum::name).sorted().toList(),
                cluster.members().stream().map(member -> toMember(member, shared, visits)).toList())).toList();
        return new DuplicateReport(groups, truncated);
    }

    private static DuplicateReport.Member toMember(DuplicateCandidate c, Set<UUID> shared,
            Map<UUID, CustomerVisits> visits) {
        CustomerVisits v = visits.get(c.profileId());
        String reason = null;
        if (c.accountId() != null) {
            reason = "CLAIMED";
        } else if (shared.contains(c.profileId())) {
            reason = "SHARED";
        }
        return new DuplicateReport.Member(c.profileId(), c.firstName(), c.lastName(), c.email(), c.phone(),
                c.createdAt(), v == null ? null : v.lastVisitAt(), v == null ? null : v.nextVisitAt(), reason);
    }
}

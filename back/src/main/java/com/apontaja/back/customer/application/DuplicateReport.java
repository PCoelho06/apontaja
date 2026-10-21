package com.apontaja.back.customer.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Groupes de doublons d'un salon. Motifs : {@code EMAIL}, {@code PHONE}. Raison
 * de non-fusion : {@code CLAIMED} (profil réclamé par un compte : conservable
 * comme survivant, jamais absorbé) ou {@code SHARED} (lié à un autre salon),
 * sinon {@code null}.
 */
public record DuplicateReport(List<Group> groups, boolean truncated) {

    public record Group(List<String> reasons, List<Member> members) {
    }

    public record Member(UUID customerProfileId, String firstName, String lastName, String email, String phone,
            Instant createdAt, Instant lastVisitAt, Instant nextVisitAt, String notMergeableReason) {

        public boolean mergeable() {
            return notMergeableReason == null;
        }

        /**
         * Profil réclamé par un compte client (raison testée en premier : il masque
         * {@code SHARED}).
         */
        public boolean claimed() {
            return "CLAIMED".equals(notMergeableReason);
        }
    }
}

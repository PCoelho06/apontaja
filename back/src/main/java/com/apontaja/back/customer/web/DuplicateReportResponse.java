package com.apontaja.back.customer.web;

import com.apontaja.back.customer.application.DuplicateReport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DuplicateReportResponse(List<GroupResponse> groups, boolean truncated) {

    public record GroupResponse(List<String> reasons, List<MemberResponse> customers) {
    }

    public record MemberResponse(UUID customerProfileId, String firstName, String lastName, String email,
            String phone, Instant createdAt, Instant lastVisitAt, Instant nextVisitAt, boolean mergeable,
            String notMergeableReason) {
    }

    static DuplicateReportResponse of(DuplicateReport report) {
        List<GroupResponse> groups = report.groups().stream()
                .map(g -> new GroupResponse(g.reasons(), g.members().stream().map(DuplicateReportResponse::member)
                        .toList()))
                .toList();
        return new DuplicateReportResponse(groups, report.truncated());
    }

    private static MemberResponse member(DuplicateReport.Member m) {
        return new MemberResponse(m.customerProfileId(), m.firstName(), m.lastName(), m.email(), m.phone(),
                m.createdAt(), m.lastVisitAt(), m.nextVisitAt(), m.mergeable(), m.notMergeableReason());
    }
}
